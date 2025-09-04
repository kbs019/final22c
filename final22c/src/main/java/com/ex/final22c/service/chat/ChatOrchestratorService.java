// src/main/java/com/ex/final22c/service/chat/ChatOrchestratorService.java
package com.ex.final22c.service.chat;

import com.ex.final22c.controller.chat.AiResult;
import com.ex.final22c.service.ai.SqlExecService;
import com.ex.final22c.sql.PeriodResolver;
import com.ex.final22c.sql.SqlGuard;
import com.ex.final22c.sql.SqlNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChatOrchestratorService {

    private final RouteService router;
    private final ChatService chat;
    private final SqlExecService sqlExec;

    private static final String SCHEMA_DOC = """
            -- (생략) 기존 스키마 설명 동일
            -- 핵심 규칙: 매출=SUM(ORDERDETAIL.CONFIRMQUANTITY*ORDERDETAIL.SELLPRICE)
            -- 집계 대상 주문: ORDERS.STATUS IN ('PAID','CONFIRMED','REFUNDED')
            -- 날짜 WHERE: o.REGDATE >= :start AND o.REGDATE < :end (반열림)
            -- 버킷팅은 SELECT/GROUP BY에서만 TRUNC 사용
            """;

    private static final Set<String> ID_PARAMS = Set.of(
            ":id", ":productId", ":orderId", ":paymentId",
            ":brandNo", ":gradeNo", ":mainNoteNo", ":volumeNo",
            ":cartId", ":cartDetailId",
            ":refundId", ":refundDetailId",
            ":purchaseId", ":pdId",
            ":reviewId", ":userNo"
    );

    private static final Pattern FIRST_INT = Pattern.compile("\\b\\d+\\b");
    private static final Pattern INTENT_ANY_CHART =
            Pattern.compile("(차트|그래프|chart)", Pattern.CASE_INSENSITIVE);

    /* ✅ ORDERS 관련 쿼리인지 판단하는 패턴들 */
    private static final Pattern ORDERS_RELATED_KEYWORDS = Pattern.compile(
            "(?i)\\b(매출|주문|결제|판매량|매출액|revenue|sales|orders?|payments?)\\b"
    );

    /* ✅ 전체 기간 키워드 패턴 - 한글 단어 경계 개선 */
    private static final Pattern ALL_TIME_KEYWORDS = Pattern.compile(
            "(?i)(전체|전체기간|누적|전기간|모든|총|all\\s*time|total|cumulative)"
    );

    /**
     * ORDERS 테이블과 관련된 쿼리인지 휴리스틱으로 판단
     */
    private static boolean isOrdersRelatedQuery(String userMsg, String generatedSql) {
        if (userMsg == null && generatedSql == null) return false;
        
        // 1) 생성된 SQL에 ORDERS 테이블이 포함되어 있으면 확실히 ORDERS 관련
        if (generatedSql != null && generatedSql.toUpperCase().contains("ORDERS")) {
            return true;
        }
        
        // 2) 사용자 메시지에 매출/주문 관련 키워드가 있으면 ORDERS 관련
        if (userMsg != null && ORDERS_RELATED_KEYWORDS.matcher(userMsg).find()) {
            return true;
        }
        
        return false;
    }

    /**
     * 전체 기간을 요청하는 질문인지 판단
     */
    private static boolean isAllTimeQuery(String userMsg) {
        if (userMsg == null) return false;
        return ALL_TIME_KEYWORDS.matcher(userMsg).find();
    }

    public AiResult handle(String userMsg, Principal principal){
        // ✅ 전체 기간 요청이면 넓은 범위로 설정
        PeriodResolver.ResolvedPeriod period;
        if (isAllTimeQuery(userMsg)) {
            // 전체 기간: 2020년부터 현재까지 (충분히 넓은 범위)
            LocalDateTime startTime = LocalDateTime.of(2020, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            // record는 단순 생성자 사용
            period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "전체 기간");
        } else {
            period = PeriodResolver.resolveFromUtterance(userMsg);
        }

        // 1) 차트 먼저 시도
        if (isChartIntent(userMsg)) {
            try {
                return handleChartGeneric(userMsg, principal, period);
            } catch (Exception ignore) {
                // 실패 시 일반 경로로
            }
        }

        // 2) 일반 SQL 경로
        var route = router.route(userMsg);
        if (route.mode() == RouteService.Mode.CHAT) {
            return new AiResult(chat.ask(userMsg), null, List.of(), null);
        }

        String ai = chat.generateSql(userMsg, SCHEMA_DOC);

        // ✅ 핵심 수정: ORDERS 관련 쿼리인지 판단 후 선별적으로 정규화 적용
        String normalized;
        if (isOrdersRelatedQuery(userMsg, ai)) {
            // 매출/주문 관련 쿼리면 날짜 WHERE 표준화 + 상태 필터 적용
            normalized = SqlNormalizer.enforceDateRangeWhere(ai, true);
        } else {
            // 단순 조회 쿼리면 정규화 없이 그대로 사용
            normalized = SqlNormalizer.enforceDateRangeWhere(ai, false);
        }
        
        normalized = fixCommonJoinMistakes(normalized);

        String safe;
        try {
            safe = SqlGuard.ensureSelect(normalized);
            safe = SqlGuard.ensureLimit(safe, 300);
        } catch (Exception e){
            String msg = "생성된 SQL이 안전하지 않습니다: " + e.getMessage() + "\n"
                    + "생성된 SQL:\n" + ai + "\n\n"
                    + "해당 질문은 대화로 답변합니다.";
            return new AiResult(msg + "\n" + chat.ask(userMsg), ai, List.of(), null);
        }

        var params = new HashMap<String,Object>();
        
        // ✅ ORDERS 관련 쿼리만 날짜 파라미터 추가
        if (isOrdersRelatedQuery(userMsg, safe)) {
            params.put("start", Timestamp.valueOf(period.start()));
            params.put("end",   Timestamp.valueOf(period.end()));
        }
        
        if (safe.contains(":userNo")) {
            Long userNo = (principal == null) ? null : 0L; // TODO 실제 조회
            if (userNo == null) return new AiResult("로그인이 필요한 요청이에요.", null, List.of(), null);
            params.put("userNo", userNo);
        }
        if (safe.contains(":limit")) params.put("limit", 300);
        if (containsAnyNamedParam(safe, ID_PARAMS)) {
            Long n = extractFirstNumber(userMsg);
            if (n == null) return new AiResult("식별자(ID)가 필요해요. 예: \"제품 237 가격\"", null, List.of(), null);
            for (String key : ID_PARAMS) if (safe.contains(key)) params.put(key.substring(1), n);
        }

        List<Map<String,Object>> rows = sqlExec.runSelectNamed(safe, params);

        String tableMd = sqlExec.formatAsMarkdownTable(rows);
        String summary;
        if (rows == null || rows.isEmpty()) {
            // ✅ ORDERS 관련이 아니면 기간 정보 제외
            if (isOrdersRelatedQuery(userMsg, safe)) {
                summary = "%s 기준 조건에 맞는 데이터가 없습니다.".formatted(period.label());
            } else {
                summary = "조건에 맞는 데이터가 없습니다.";
            }
        } else {
            try {
                String contextMsg = isOrdersRelatedQuery(userMsg, safe) 
                    ? userMsg + " (기간: " + period.label() + ")"
                    : userMsg;
                summary = chat.summarize(contextMsg, safe, tableMd);
            } catch (Exception ignore) { summary = null; }
            if (summary == null ||
                summary.toLowerCase(Locale.ROOT).contains("null") ||
                summary.contains("존재하지 않")) {

                Map<String,Object> r = rows.get(0);
                String name  = getStr(r, "PRODUCTNAME","NAME","LABEL");
                String brand = getStr(r, "BRANDNAME");
                Number qty   = getNum(r, "TOTALQUANTITY","TOTAL_SOLD_QUANTITY","QUANTITY");
                Number sales = getNum(r, "TOTALSALES","TOTAL_SALES_AMOUNT","VALUE");

                StringBuilder sb = new StringBuilder();
                // ✅ ORDERS 관련이 아니면 기간 정보 제외
                if (isOrdersRelatedQuery(userMsg, safe)) {
                    sb.append("%s 기준 ".formatted(period.label()));
                }
                sb.append("조회 결과 ").append(rows.size()).append("행을 찾았습니다.");
                if (name != null) {
                    sb.append(" 1위: ").append(name);
                    if (brand != null) sb.append(" (").append(brand).append(")");
                    if (qty != null)   sb.append(", 수량 ").append(qty);
                    if (sales != null) sb.append(", 값 ").append(sales);
                    sb.append(".");
                }
                summary = sb.toString();
            }
        }
        return new AiResult(summary, safe, rows, null);
    }

    /* -------------------- 차트 처리 -------------------- */
    private boolean isChartIntent(String msg){
        if (msg == null) return false;
        return INTENT_ANY_CHART.matcher(msg).find();
    }

    private AiResult handleChartGeneric(String userMsg, Principal principal, PeriodResolver.ResolvedPeriod period) {
        ChartSpec spec = null;
        try {
            spec = chat.generateChartSpec(userMsg, SCHEMA_DOC);
        } catch (Exception ignore) {}

        // 폴백: 월/주/일 키워드면 고정 SQL 생성
        if (spec == null || spec.sql() == null ||
                !spec.sql().toUpperCase(Locale.ROOT).contains("LABEL") ||
                !spec.sql().toUpperCase(Locale.ROOT).contains("VALUE")) {
            spec = buildFallbackSpec(userMsg);
        }
        if (spec == null) {
            return new AiResult("차트 스펙 생성에 실패했어요. 요청을 더 구체적으로 적어주세요.", null, List.of(), null);
        }

        // "이번주/금주"는 이번 주(월~일) 범위 + 일별 버킷으로 강제
        boolean thisWeek = containsAny(userMsg, "이번주","금주","this week");
        Timestamp overrideStart = null, overrideEnd = null;
        if (thisWeek) {
            var range = weekRangeKST();
            overrideStart = range[0];
            overrideEnd   = range[1];
            spec = new ChartSpec(
                    """
                    SELECT
                      TO_CHAR(TRUNC(o.REGDATE,'DD'),'YYYY-MM-DD') AS label,
                      SUM(od.CONFIRMQUANTITY * od.SELLPRICE)     AS value
                    FROM ORDERS o
                      JOIN ORDERDETAIL od ON od.ORDERID = o.ORDERID
                    WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                      AND o.REGDATE >= :start AND o.REGDATE < :end
                    GROUP BY TRUNC(o.REGDATE,'DD')
                    ORDER BY TRUNC(o.REGDATE,'DD')
                    """,
                    "이번주 일별 매출", "매출(원)", 7, "bar", "currency"
            );
        }

        // ✅ 차트는 항상 ORDERS 관련이므로 날짜 WHERE 표준화 적용
        String normalized = SqlNormalizer.enforceDateRangeWhere(spec.sql().trim(), true);
        normalized = fixCommonJoinMistakes(normalized);
        String safe = SqlGuard.ensureSelect(normalized);

        // 위치 바인드 교정
        boolean hasPositional = safe.contains("?") || safe.matches(".*:\\d+.*");
        if (hasPositional) safe = safe.replace("?", ":limit").replaceAll(":(\\d+)", ":limit");

        // limit 감싸기
        String up = safe.toUpperCase(Locale.ROOT);
        if (!up.contains("ROWNUM") && !up.contains("FETCH FIRST")) {
            safe = "SELECT * FROM (" + safe + ") WHERE ROWNUM <= :limit";
        }

        int limit = (spec.topN()!=null && spec.topN()>0 && spec.topN()<=50) ? spec.topN() : 12;
        Map<String,Object> params = new HashMap<>();
        params.put("limit", limit);
        params.put("start", overrideStart != null ? overrideStart : Timestamp.valueOf(period.start()));
        params.put("end",   overrideEnd   != null ? overrideEnd   : Timestamp.valueOf(period.end()));
        if (safe.contains(":userNo")) {
            Long userNo = (principal == null) ? null : 0L; // TODO 실제 조회
            if (userNo == null) return new AiResult("로그인이 필요한 요청이에요.", null, List.of(), null);
            params.put("userNo", userNo);
        }

        List<Map<String,Object>> rows = sqlExec.runSelectNamed(safe, params);

        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        List<Number> qtys   = new ArrayList<>();
        for (Map<String,Object> r : rows) {
            labels.add(getStr(r, "label","LABEL"));
            values.add(getNum(r, "value","VALUE"));
            qtys.add(getNum(r, "quantity","QUANTITY"));
        }

        // 라벨 정규화 + 타임시리즈 패딩
        final String sig = safe.toUpperCase(Locale.ROOT).replaceAll("\\s+","");
        normalizeLabelsBySql(sig, labels);

        if (thisWeek) {
            // 이번주: 월~일 일별 패딩
            LocalDate s = overrideStart.toLocalDateTime().toLocalDate();
            LocalDate e = overrideEnd.toLocalDateTime().minusDays(1).toLocalDate();
            padDaily(labels, values, s, e);
        } else if (sig.contains("TRUNC(O.REGDATE,'IW')") || sig.contains("'IYYY-IW'")) {
            padWeekly(labels, values,
                    (overrideStart!=null?overrideStart:Timestamp.valueOf(period.start())).toLocalDateTime().toLocalDate(),
                    (overrideEnd!=null?overrideEnd:Timestamp.valueOf(period.end())).toLocalDateTime().minusDays(1).toLocalDate(),
                    1            // ✅ 주별 패딩은 1주 간격
            );
        } else if (sig.contains("TRUNC(O.REGDATE,'DD')") || sig.contains("'YYYY-MM-DD'")) {
            padDaily(labels, values,
                    (overrideStart!=null?overrideStart:Timestamp.valueOf(period.start())).toLocalDateTime().toLocalDate(),
                    (overrideEnd!=null?overrideEnd:Timestamp.valueOf(period.end())).toLocalDateTime().minusDays(1).toLocalDate());
        } else if (sig.contains("TRUNC(O.REGDATE,'MM')") || sig.contains("'YYYY-MM'")) {
            padMonthly(labels, values, period.start().getYear());
        }

        heuristicNormalizeLabels(labels, values);

        // 시각화 타입 보정(포인트 한 개면 선 그래프 금지)
        String type = guessType(userMsg, spec.type());
        if (values.size() <= 1) type = "bar";
        boolean horizontal = containsAny(userMsg, "가로", "horizontal");
        String format = (spec.format()!=null && !spec.format().isBlank())
                ? spec.format()
                : inferFormat(spec.valueColLabel());

        AiResult.ChartPayload chart = new AiResult.ChartPayload(
                labels, values, qtys,
                (spec.valueColLabel()==null||spec.valueColLabel().isBlank()) ? "매출(원)" : spec.valueColLabel(),
                (spec.title()==null||spec.title().isBlank()) ? ("차트 · " + period.label()) : spec.title(),
                type, horizontal, format
        );

        String msg = rows.isEmpty()
                ? "%s 기준 조건에 맞는 데이터가 없습니다.".formatted(period.label())
                : "%s 기준 요청하신 차트를 표시했습니다.".formatted(period.label());
        return new AiResult(msg, safe, rows, chart);
    }

    /* -------------------- 폴백 차트 스펙 -------------------- */
    private ChartSpec buildFallbackSpec(String userMsg) {
        String msg = userMsg == null ? "" : userMsg;
        String sql = null;
        String title = null;

        if (containsAny(msg, "이번주","금주","this week")) {
            sql = """
                  SELECT
                    TO_CHAR(TRUNC(o.REGDATE,'DD'),'YYYY-MM-DD') AS label,
                    SUM(od.CONFIRMQUANTITY * od.SELLPRICE)     AS value
                  FROM ORDERS o
                    JOIN ORDERDETAIL od ON od.ORDERID = o.ORDERID
                  WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                    AND o.REGDATE >= :start AND o.REGDATE < :end
                  GROUP BY TRUNC(o.REGDATE,'DD')
                  ORDER BY TRUNC(o.REGDATE,'DD')
                  """;
            title = "이번주 일별 매출";
        } else if (containsAny(msg, "주별","주간","주 단위")) {
            sql = """
                  SELECT
                    TO_CHAR(TRUNC(o.REGDATE,'IW'),'IYYY-IW') AS label,
                    SUM(od.CONFIRMQUANTITY * od.SELLPRICE)    AS value
                  FROM ORDERS o
                    JOIN ORDERDETAIL od ON od.ORDERID = o.ORDERID
                  WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                    AND o.REGDATE >= :start AND o.REGDATE < :end
                  GROUP BY TRUNC(o.REGDATE,'IW')
                  ORDER BY TRUNC(o.REGDATE,'IW')
                  """;
            title = "주별 매출";
        } else if (containsAny(msg, "월별")) {
            sql = """
                  SELECT
                    TO_CHAR(TRUNC(o.REGDATE,'MM'),'YYYY-MM') AS label,
                    SUM(od.CONFIRMQUANTITY * od.SELLPRICE)   AS value
                  FROM ORDERS o
                    JOIN ORDERDETAIL od ON od.ORDERID = o.ORDERID
                  WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                    AND o.REGDATE >= :start AND o.REGDATE < :end
                  GROUP BY TRUNC(o.REGDATE,'MM')
                  ORDER BY TRUNC(o.REGDATE,'MM')
                  """;
            title = "월별 매출";
        } else if (containsAny(msg, "일별","일자별","일 단위")) {
            sql = """
                  SELECT
                    TO_CHAR(TRUNC(o.REGDATE,'DD'),'YYYY-MM-DD') AS label,
                    SUM(od.CONFIRMQUANTITY * od.SELLPRICE)     AS value
                  FROM ORDERS o
                    JOIN ORDERDETAIL od ON od.ORDERID = o.ORDERID
                  WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                    AND o.REGDATE >= :start AND o.REGDATE < :end
                  GROUP BY TRUNC(o.REGDATE,'DD')
                  ORDER BY TRUNC(o.REGDATE,'DD')
                  """;
            title = "일별 매출";
        }

        if (sql == null) return null;
        return new ChartSpec(sql, title, "매출(원)", 12, "line", "currency");
    }

    /* -------------------- 조인 오류 자동 교정 (강화) -------------------- */
    private static String fixCommonJoinMistakes(String sql) {
        if (sql == null) return null;
        String s = sql;
        
        // 1. 기존 조인 오류 교정
        s = s.replaceAll("JOIN\\s+ORDERDETAIL\\s+od\\s+ON\\s+o\\.ID\\s*=\\s*od\\.ORDERID",
                         "JOIN ORDERDETAIL od ON o.ORDERID = od.ORDERID");
        s = s.replaceAll("JOIN\\s+ORDERDETAIL\\s+od\\s+ON\\s+od\\.ID\\s*=\\s*o\\.ORDERID",
                         "JOIN ORDERDETAIL od ON o.ORDERID = od.ORDERID");
        
        // 2. 핵심 수정: PRODUCT 조인 시 잘못된 컬럼명 교정
        // ORDERDETAIL의 상품 FK는 ID이지 PRODUCTID가 아님
        s = s.replaceAll("(?i)JOIN\\s+PRODUCT\\s+p\\s+ON\\s+od\\.PRODUCTID\\s*=\\s*p\\.ID",
                         "JOIN PRODUCT p ON od.ID = p.ID");
        s = s.replaceAll("(?i)JOIN\\s+PRODUCT\\s+p\\s+ON\\s+p\\.ID\\s*=\\s*od\\.PRODUCTID", 
                         "JOIN PRODUCT p ON p.ID = od.ID");
                         
        // 3. WHERE절에서도 동일한 실수 교정
        s = s.replaceAll("(?i)\\bod\\.PRODUCTID\\b", "od.ID");
        
        return s;
    }

    /* -------------------- KST 이번주 범위 -------------------- */
    private static Timestamp[] weekRangeKST() {
        ZoneId KST = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(KST);
        WeekFields wf = WeekFields.ISO; // 월~일
        LocalDate monday = today.with(wf.dayOfWeek(), 1);
        LocalDate nextMonday = monday.plusWeeks(1);
        return new Timestamp[]{
                Timestamp.valueOf(monday.atStartOfDay()),
                Timestamp.valueOf(nextMonday.atStartOfDay())
        };
    }

    /* -------------------- 라벨 정규화 & 패딩 -------------------- */
    private static void normalizeLabelsBySql(String sqlSignature, List<String> labels) {
        if (labels == null || labels.isEmpty()) return;
        final String s = sqlSignature;
        if (s.contains("TRUNC(O.REGDATE,'MM')") || s.contains("'YYYY-MM'")) {
            for (int i = 0; i < labels.size(); i++) labels.set(i, toYearMonth(labels.get(i)));
        } else if (s.contains("TRUNC(O.REGDATE,'DD')") || s.contains("'YYYY-MM-DD'")) {
            for (int i = 0; i < labels.size(); i++) labels.set(i, toYmd(labels.get(i)));
        } else if (s.contains("TRUNC(O.REGDATE,'IW')") || s.contains("'IYYY-IW'")) {
            for (int i = 0; i < labels.size(); i++) labels.set(i, toIsoWeek(labels.get(i)));
        } else if (s.contains("TRUNC(O.REGDATE,'YYYY')") || s.contains("'YYYY'")) {
            for (int i = 0; i < labels.size(); i++) labels.set(i, toYear(labels.get(i)));
        }
    }

    private static void heuristicNormalizeLabels(List<String> labels, List<Number> values) {
        if (labels == null || labels.isEmpty()) return;
        boolean allDateLike = labels.stream().allMatch(s ->
                s != null && s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-');
        if (allDateLike) {
            for (int i = 0; i < labels.size(); i++) labels.set(i, toYmd(labels.get(i)));
            try {
                boolean allFirst = labels.stream()
                        .map(x -> Integer.parseInt(x.substring(8,10)))
                        .allMatch(d -> d == 1);
                if (allFirst) for (int i = 0; i < labels.size(); i++) labels.set(i, labels.get(i).substring(0,7));
            } catch (Exception ignore) {}
        }
        if (labels.stream().allMatch(s -> s != null && s.contains("T"))) {
            try {
                boolean allFirst = labels.stream()
                        .map(ChatOrchestratorService::toYmd)
                        .map(x -> Integer.parseInt(x.substring(8,10)))
                        .allMatch(d -> d == 1);
                if (allFirst) {
                    for (int i = 0; i < labels.size(); i++) labels.set(i, toYearMonth(labels.get(i)));
                } else {
                    for (int i = 0; i < labels.size(); i++) labels.set(i, toYmd(labels.get(i)));
                }
            } catch (Exception ignore) {}
        }
    }

    private static void padMonthly(List<String> labels, List<Number> values, int year) {
        Map<String, Number> baseline = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) baseline.put(String.format("%04d-%02d", year, m), 0);
        for (int i = 0; i < labels.size(); i++) if (labels.get(i) != null)
            baseline.put(toYearMonth(labels.get(i)), values.get(i));
        labels.clear(); values.clear();
        baseline.forEach((k,v) -> { labels.add(k); values.add(v); });
    }

    private static void padDaily(List<String> labels, List<Number> values, LocalDate from, LocalDate to) {
        Map<String, Number> baseline = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) baseline.put(d.toString(), 0);
        for (int i = 0; i < labels.size(); i++) if (labels.get(i) != null)
            baseline.put(toYmd(labels.get(i)), values.get(i));
        labels.clear(); values.clear();
        baseline.forEach((k,v) -> { labels.add(k); values.add(v); });
    }

    private static void padWeekly(List<String> labels, List<Number> values,
                                  LocalDate from, LocalDate to, int stepWeeks) {
        Map<String, Number> baseline = new LinkedHashMap<>();
        WeekFields wf = WeekFields.ISO;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusWeeks(stepWeeks)) {
            int week = d.get(wf.weekOfWeekBasedYear());
            int wyear = d.get(wf.weekBasedYear());
            baseline.put(String.format("%04d-%02d", wyear, week), 0);
        }
        for (int i = 0; i < labels.size(); i++) {
            baseline.put(toIsoWeek(labels.get(i)), values.get(i));
        }
        labels.clear(); values.clear();
        baseline.forEach((k,v) -> { labels.add(k); values.add(v); });
    }

    private static void padYearly(List<String> labels, List<Number> values,
                                  int startYear, int endYear, int stepYears) {
        Map<String, Number> baseline = new LinkedHashMap<>();
        for (int y = startYear; y <= endYear; y += stepYears) baseline.put(String.format("%04d", y), 0);
        for (int i = 0; i < labels.size(); i++) baseline.put(toYear(labels.get(i)), values.get(i));
        labels.clear(); values.clear();
        baseline.forEach((k,v) -> { labels.add(k); values.add(v); });
    }

    private static String toYearMonth(String s) {
        if (s == null) return null;
        return s.length() >= 7 ? s.substring(0,7) : s;
    }
    private static String toYmd(String s) {
        if (s == null) return null;
        return s.length() >= 10 ? s.substring(0,10) : s;
    }
    private static String toIsoWeek(String s) {
        try {
            String ymd = toYmd(s);
            LocalDate d = LocalDate.parse(ymd, DateTimeFormatter.ISO_LOCAL_DATE);
            WeekFields wf = WeekFields.ISO;
            return String.format("%04d-%02d", d.get(wf.weekBasedYear()), d.get(wf.weekOfWeekBasedYear()));
        } catch (Exception e) {
            return s != null && s.length() >= 7 ? s.substring(0,7) : s;
        }
    }
    private static String toYear(String s) {
        if (s == null) return null;
        return s.length() >= 4 ? s.substring(0,4) : s;
    }

    /* -------------------- helpers -------------------- */
    private static boolean containsAnyNamedParam(String sql, Set<String> keys) {
        for (String k : keys) if (sql.contains(k)) return true;
        return false;
    }
    private static Long extractFirstNumber(String text) {
        if (text == null) return null;
        Matcher m = FIRST_INT.matcher(text);
        return m.find() ? Long.parseLong(m.group()) : null;
    }
    private static String getStr(Map<String,Object> m, String... keys){
        for (String k : keys){
            Object v = m.get(k);
            if (v != null) return String.valueOf(v);
        }
        return null;
    }
    private static Number getNum(Map<String,Object> m, String... keys){
        for (String k : keys){
            Object v = m.get(k);
            if (v instanceof Number n) return n;
            if (v != null) {
                try { return new BigDecimal(v.toString()); } catch (Exception ignore) {}
            }
        }
        return 0;
    }
    private static boolean containsAny(String s, String... ks){
        if (s==null) return false;
        String t = s.toLowerCase(Locale.ROOT);
        for (String k: ks) if (t.contains(k.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
    private static String guessType(String msg, String fromSpec){
        String t = (fromSpec==null? "" : fromSpec.trim().toLowerCase(Locale.ROOT));
        if (Set.of("bar","line","pie","doughnut").contains(t)) return t;
        String m = (msg==null? "" : msg);
        if (containsAny(m, "추이","월별","주별","일자별","시간대","트렌드","변화")) return "line";
        if (containsAny(m, "비율","구성비","점유율","퍼센트","비중","파이","도넛")) return "doughnut";
        return "bar";
    }
    private static String inferFormat(String valueColLabel){
        if (valueColLabel == null) return "count";
        String s = valueColLabel;
        if (s.contains("원") || s.contains("액") || s.contains("매출")) return "currency";
        if (s.contains("율") || s.contains("%")) return "percent";
        return "count";
    }
}