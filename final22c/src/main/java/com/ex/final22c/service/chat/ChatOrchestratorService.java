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
            -- Oracle / 화이트리스트 (대문자 컬럼)
            
            -- 👤 사용자 관련
            -- USERS(USERNO PK, USERNAME UK, PASSWORD, EMAIL UK, NAME, BIRTH, GENDER, TELECOM, PHONE UK, REG, STATUS, BANREG, ROLE, LOGINTYPE, KAKAOID UK, MILEAGE, AGE)
            
            -- 🛒 주문/결제 관련  
            -- ORDERS(ORDERID PK, USERNO FK->USERS.USERNO, USEDPOINT, TOTALAMOUNT, STATUS, REGDATE/regDate, DELIVERYSTATUS, CONFIRMMILEAGE)
            -- ORDERDETAIL(ORDERDETAILID PK, ORDERID FK->ORDERS.ORDERID, ID FK->PRODUCT.ID, QUANTITY, SELLPRICE, TOTALPRICE, CONFIRMQUANTITY)
            -- PAYMENT(PAYMENTID PK, ORDERID FK->ORDERS.ORDERID, AMOUNT, STATUS, TID UK, AID, APPROVEDAT, REG)
            
            -- 🛍️ 상품 관련
            -- PRODUCT(ID PK, NAME, IMGNAME, IMGPATH, PRICE, COUNT, DESCRIPTION, SINGLENOTE, TOPNOTE, MIDDLENOTE, BASENOTE, 
            --         BRAND_BRANDNO FK->BRAND.BRANDNO, VOLUME_VOLUMENO FK->VOLUME.VOLUMENO, 
            --         GRADE_GRADENO FK->GRADE.GRADENO, MAINNOTE_MAINNOTENO FK->MAINNOTE.MAINNOTENO,
            --         ISPICKED, STATUS, SELLPRICE, DISCOUNT, COSTPRICE)
            -- BRAND(BRANDNO PK, BRANDNAME, IMGNAME, IMGPATH)
            -- GRADE(GRADENO PK, GRADENAME)  
            -- MAINNOTE(MAINNOTENO PK, MAINNOTENAME)
            -- VOLUME(VOLUMENO PK, VOLUMENAME)
            
            -- 🛒 장바구니 관련
            -- CART(CARTID PK, USERNO FK->USERS.USERNO UK, CREATEDATE, UPDATEDATE)
            -- CARTDETAIL(CARTDETAILID PK, CARTID FK->CART.CARTID, ID FK->PRODUCT.ID, QUANTITY, SELLPRICE, TOTALPRICE, CREATEDATE)
            
            -- ⭐ 리뷰 관련  
            -- REVIEW(REVIEWID PK, PRODUCT_ID FK->PRODUCT.ID, WRITER_USERNO FK->USERS.USERNO, CONTENT, CREATEDATE, STATUS, RATING)
            
            -- 💰 환불 관련
            -- REFUND(REFUNDID PK, ORDERID FK->ORDERS.ORDERID UK, USERNO FK->USERS.USERNO, STATUS, TOTALREFUNDAMOUNT, 
            --        REQUESTEDREASON, PAYMENTID FK->PAYMENT.PAYMENTID, PGREFUNDID, PGPAYLOADJSON, REJECTEDREASON,
            --        REFUNDMILEAGE, CONFIRMMILEAGE, CREATEDATE, UPDATEDATE)
            -- REFUNDDETAIL(REFUNDDETAILID PK, REFUND_REFUNDID FK->REFUND.REFUNDID, ORDERDETAILID FK->ORDERDETAIL.ORDERDETAILID UK,
            --              QUANTITY, REFUNDQTY, UNITREFUNDAMOUNT, DETAILREFUNDAMOUNT)
            
            -- 📦 발주 관련
            -- PURCHASE(PURCHASEID PK, COUNT, TOTALPRICE, REG)
            -- PURCHASEDETAIL(PDID PK, PURCHASEID FK->PURCHASE.PURCHASEID, ID FK->PRODUCT.ID, QTY, TOTALPRICE)
            
            -- 🔗 주요 조인 관계:
            -- USERS 1:N ORDERS, CART, REFUND, REVIEW
            -- ORDERS 1:N ORDERDETAIL, 1:1 PAYMENT, 1:1 REFUND  
            -- PRODUCT 1:N ORDERDETAIL, CARTDETAIL, PURCHASEDETAIL, REVIEW
            -- PRODUCT N:1 BRAND, GRADE, MAINNOTE, VOLUME
            -- CART 1:N CARTDETAIL
            -- REFUND 1:N REFUNDDETAIL
            -- PURCHASE 1:N PURCHASEDETAIL
            -- ORDERDETAIL 1:1 REFUNDDETAIL
            
            -- 📊 비즈니스 규칙 (매우 중요)
            -- 1) '판매량'(수량) = SUM(ORDERDETAIL.CONFIRMQUANTITY) (환불 시 차감 반영)
            -- 2) '매출'(금액) = SUM(ORDERDETAIL.CONFIRMQUANTITY * ORDERDETAIL.SELLPRICE)  
            -- 3) 집계 대상 주문 = ORDERS.STATUS IN ('PAID','CONFIRMED','REFUNDED') 만 포함
            -- 4) 매출/판매량 계산에는 PAYMENT 테이블을 사용하지 않음
            -- 5) 제품별 집계 시 ORDERDETAIL.ID = PRODUCT.ID 로 조인
            -- 6) 발주량 = SUM(PURCHASEDETAIL.QTY), 매입원가 = SUM(PURCHASEDETAIL.QTY * PRODUCT.COSTPRICE)
            -- 7) 환불률 = (환불수량 / 확정수량(CONFIRMQUANTITY)) * 100
            -- 8) 상품 통계에서 REVIEW는 직접 JOIN 금지. 반드시
            --    (SELECT PRODUCT_ID, COUNT(*) AS TOTAL_REVIEWS, ROUND(AVG(RATING),1) AS AVG_RATING FROM REVIEW GROUP BY PRODUCT_ID)
            --    서브쿼리/CTE로 집계 후 LEFT JOIN (중복 집계 방지)
            -- 9) 기간이 명시되지 않은 '상품 통계/누적/총계' 질문은 기본을 '전체 기간'으로 가정

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

    /* 🆕 통계/누적 키워드 & 기간 명시 키워드 */
    private static final Pattern STATS_KEYWORDS = Pattern.compile(
            "(?i)(통계|누적|총계|전체\\s*내역|전기간|lifetime|all\\s*-?time)"
    );
    private static final Pattern EXPLICIT_PERIOD_KEYWORDS = Pattern.compile(
            "(?i)(오늘|어제|이번|지난|작년|올해|전년|전월|월별|주별|일별|분기|상반기|하반기|최근\\s*\\d+\\s*(일|주|개월|달|년)|\\bQ[1-4]\\b|\\d{4}\\s*년|\\d{1,2}\\s*월|this|last|previous)"
    );
    // "샤넬 브랜드", "브랜드 샤넬" 모두 허용
    private static String extractBrandName(String msg){
        if (msg == null) return null;
        // 앞에 브랜드가 오는 형태
        Matcher m1 = Pattern.compile("([\\p{L}\\p{N}][\\p{L}\\p{N}\\s]{0,40}?)\\s*브랜드").matcher(msg);
        if (m1.find()) return m1.group(1).trim();

        // 뒤에 브랜드가 오는 형태
        Matcher m2 = Pattern.compile("브랜드\\s*([\\p{L}\\p{N}][\\p{L}\\p{N}\\s]{0,40})").matcher(msg);
        if (m2.find()) return m2.group(1).trim();

        return null;
    }
    
    // ✅ 전체기간 키워드 매칭
    private static boolean isAllTimeQuery(String userMsg) {
        if (userMsg == null) return false;
        return ALL_TIME_KEYWORDS.matcher(userMsg).find();
    }
private static boolean hasExplicitPeriodWords(String msg){
        return msg != null && EXPLICIT_PERIOD_KEYWORDS.matcher(msg).find();
    }
    private static boolean singleProductFilterInSql(String sql){
        if (sql == null) return false;
        String up = sql.toUpperCase(Locale.ROOT);
        return up.contains(" P.ID = :PRODUCTID") || up.contains("UPPER(P.NAME) = UPPER(");
    }
    private static boolean shouldDefaultAllTime(String userMsg, String aiSql){
        return isOrdersRelatedQuery(userMsg, aiSql)
                && !hasExplicitPeriodWords(userMsg)
                && (STATS_KEYWORDS.matcher(userMsg).find() || singleProductFilterInSql(aiSql));
    }

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

    /* ✅ 비교 분석 키워드 패턴 */
    private static final Pattern COMPARISON_KEYWORDS = Pattern.compile(
            "(?i)(vs|대비|비교|compared|compare|차이|변화|증감|전년|전월|지난|작년|last)"
    );

    /**
     * 비교 분석이 필요한 질문인지 판단
     */
    private static boolean isComparisonQuery(String userMsg) {
        if (userMsg == null) return false;
        return COMPARISON_KEYWORDS.matcher(userMsg).find();
    }

    public AiResult handle(String userMsg, Principal principal){
        // ✅ 전체 기간 요청이면 넓은 범위로 설정
        PeriodResolver.ResolvedPeriod period;
        if (isAllTimeQuery(userMsg)) {
            // 전체 기간: 2020년부터 현재까지 (충분히 넓은 범위)
            LocalDateTime startTime = LocalDateTime.of(2020, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "전체 기간");
        } else if (isComparisonQuery(userMsg)) {
            // ✅ 비교 분석: 최근 3개월로 넓은 범위 설정
            LocalDateTime endTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime startTime = endTime.minusMonths(3);
            period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "최근 3개월");
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

        // 🆕 통계/단일상품인데 기간 미지정이면 전체 기간으로 강제
        if (shouldDefaultAllTime(userMsg, ai)) {
            LocalDateTime startTime = LocalDateTime.of(2020, 1, 1, 0, 0);
            LocalDateTime endTime   = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "전체 기간");
        }

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
        // ✅ 리뷰 중복/환불률/이름매칭 교정
        normalized = fixProductStatsQuery(normalized, userMsg);

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
            Long n = extractContextualId(userMsg); // 👈 맥락형 ID 추출
            if (n == null) return new AiResult("식별자(ID)가 필요해요. 예: \"제품 ID 239 판매 통계\"", null, List.of(), null);
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
                Number qty   = getNum(r, "TOTALQUANTITY","TOTAL_SOLD_QUANTITY","QUANTITY","TOTAL_SALES_QUANTITY");
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
        try { spec = chat.generateChartSpec(userMsg, SCHEMA_DOC); } catch (Exception ignore) {}

        // 폴백 생성 (여기서 이미 브랜드 반영됨)
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

        // 🆕 브랜드 파라미터 채우기 (폴백 SQL에 :brandName가 있으면 자동 바인드)
        String brand = extractBrandName(userMsg);
        if (brand != null && !brand.isBlank() && safe.contains(":brandName")) {
            params.put("brandName", brand.trim());
        }
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
        final String sig = safe.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
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
        String brand = extractBrandName(userMsg);              // 🆕 브랜드 추출
        boolean byBrand = brand != null && !brand.isBlank();

        String msg = userMsg == null ? "" : userMsg;
        String sql = null, title = null;

        // 공통 조인/필터(브랜드가 있으면 PRODUCT/BRAND까지 조인)
        String fromJoins = byBrand
                ? """
                   FROM ORDERS o
                     JOIN ORDERDETAIL od ON od.ORDERID = o.ORDERID
                     JOIN PRODUCT p      ON p.ID       = od.ID
                     JOIN BRAND   b      ON b.BRANDNO  = p.BRAND_BRANDNO
                  """
                : """
                   FROM ORDERS o
                     JOIN ORDERDETAIL od ON od.ORDERID = o.ORDERID
                  """;

        String whereCore = """
                WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                  AND o.REGDATE >= :start AND o.REGDATE < :end
            """;

        String brandFilter = byBrand ? " AND UPPER(b.BRANDNAME) = UPPER(:brandName)\n" : "";

        if (containsAny(msg, "이번주","금주","this week")) {
            sql = """
                  SELECT
                    TO_CHAR(TRUNC(o.REGDATE,'DD'),'YYYY-MM-DD') AS label,
                    SUM(od.CONFIRMQUANTITY * od.SELLPRICE)     AS value
                  """ + fromJoins + "\n" + whereCore + brandFilter + """
                  GROUP BY TRUNC(o.REGDATE,'DD')
                  ORDER BY TRUNC(o.REGDATE,'DD')
                  """;
            title = (byBrand ? (brand + " ") : "") + "이번주 일별 매출";
        } else if (containsAny(msg, "주별","주간","주 단위")) {
            sql = """
                  SELECT
                    TO_CHAR(TRUNC(o.REGDATE,'IW'),'IYYY-IW') AS label,
                    SUM(od.CONFIRMQUANTITY * od.SELLPRICE)   AS value
                  """ + fromJoins + "\n" + whereCore + brandFilter + """
                  GROUP BY TRUNC(o.REGDATE,'IW')
                  ORDER BY TRUNC(o.REGDATE,'IW')
                  """;
            title = (byBrand ? (brand + " ") : "") + "주별 매출";
        } else if (containsAny(msg, "월별")) {
            sql = """
                  SELECT
                    TO_CHAR(TRUNC(o.REGDATE,'MM'),'YYYY-MM') AS label,
                    SUM(od.CONFIRMQUANTITY * od.SELLPRICE)   AS value
                  """ + fromJoins + "\n" + whereCore + brandFilter + """
                  GROUP BY TRUNC(o.REGDATE,'MM')
                  ORDER BY TRUNC(o.REGDATE,'MM')
                  """;
            title = (byBrand ? (brand + " ") : "") + "월별 매출";
        } else if (containsAny(msg, "일별","일자별","일 단위")) {
            sql = """
                  SELECT
                    TO_CHAR(TRUNC(o.REGDATE,'DD'),'YYYY-MM-DD') AS label,
                    SUM(od.CONFIRMQUANTITY * od.SELLPRICE)     AS value
                  """ + fromJoins + "\n" + whereCore + brandFilter + """
                  GROUP BY TRUNC(o.REGDATE,'DD')
                  ORDER BY TRUNC(o.REGDATE,'DD')
                  """;
            title = (byBrand ? (brand + " ") : "") + "일별 매출";
        }

        if (sql == null) return null;
        return new ChartSpec(sql, title, "매출(원)", 12, "line", "currency");
    }

    /* -------------------- 조인 오류 자동 교정 (완전 강화) -------------------- */
    private static String fixCommonJoinMistakes(String sql) {
        if (sql == null) return null;
        String s = sql;

        // 1. ORDERS ↔ ORDERDETAIL 조인 교정
        s = s.replaceAll("JOIN\\s+ORDERDETAIL\\s+od\\s+ON\\s+o\\.ID\\s*=\\s*od\\.ORDERID",
                "JOIN ORDERDETAIL od ON o.ORDERID = od.ORDERID");
        s = s.replaceAll("JOIN\\s+ORDERDETAIL\\s+od\\s+ON\\s+od\\.ID\\s*=\\s*o\\.ORDERID",
                "JOIN ORDERDETAIL od ON o.ORDERID = od.ORDERID");
        s = s.replaceAll("(?i)JOIN\\s+ORDERDETAIL\\s+od\\s+ON\\s+o\\.ORDERNO\\s*=\\s*od\\.ORDERNO",
                "JOIN ORDERDETAIL od ON o.ORDERID = od.ORDERID");

        // 2. ORDERDETAIL ↔ PRODUCT 조인 교정
        s = s.replaceAll("(?i)JOIN\\s+PRODUCT\\s+p\\s+ON\\s+od\\.PRODUCTID\\s*=\\s*p\\.ID",
                "JOIN PRODUCT p ON od.ID = p.ID");
        s = s.replaceAll("(?i)JOIN\\s+PRODUCT\\s+p\\s+ON\\s+p\\.ID\\s*=\\s*od\\.PRODUCTID",
                "JOIN PRODUCT p ON p.ID = od.ID");
        
        // 3. PRODUCT ↔ BRAND 조인 교정 (핵심 추가!)
        s = s.replaceAll("(?i)JOIN\\s+BRAND\\s+b\\s+ON\\s+p\\.BRANDID\\s*=\\s*b\\.ID",
                "JOIN BRAND b ON p.BRAND_BRANDNO = b.BRANDNO");
        s = s.replaceAll("(?i)JOIN\\s+BRAND\\s+b\\s+ON\\s+b\\.ID\\s*=\\s*p\\.BRANDID",
                "JOIN BRAND b ON b.BRANDNO = p.BRAND_BRANDNO");

        // 4. WHERE절에서도 잘못된 컬럼명 교정
        s = s.replaceAll("(?i)\\bod\\.PRODUCTID\\b", "od.ID");
        s = s.replaceAll("(?i)\\bp\\.BRANDID\\b", "p.BRAND_BRANDNO");
        s = s.replaceAll("(?i)\\bb\\.ID\\b", "b.BRANDNO");

        return s;
    }

    /* -------------------- 상품 통계 쿼리 교정(리뷰/환불률/이름 매칭) -------------------- */
    private static String fixProductStatsQuery(String sql, String userMsg) {
        if (sql == null) return null;
        String s = sql;

        // 1) SELECT 절의 리뷰 집계는 서브쿼리로 대체
        s = s.replaceAll(
                "(?i)COUNT\\s*\\(\\s*DISTINCT\\s*r\\.REVIEWID\\s*\\)\\s*AS\\s*TOTAL_REVIEWS",
                "(SELECT COUNT(*) FROM REVIEW r2 WHERE r2.PRODUCT_ID = p.ID) AS TOTAL_REVIEWS"
        );
        s = s.replaceAll(
                "(?i)COUNT\\s*\\(\\s*r\\.REVIEWID\\s*\\)\\s*AS\\s*TOTAL_REVIEWS",
                "(SELECT COUNT(*) FROM REVIEW r2 WHERE r2.PRODUCT_ID = p.ID) AS TOTAL_REVIEWS"
        );
        s = s.replaceAll(
                "(?i)ROUND\\s*\\(\\s*AVG\\s*\\(\\s*r\\.RATING\\s*\\)\\s*,\\s*1\\s*\\)\\s*AS\\s*AVG_RATING",
                "(SELECT ROUND(AVG(r2.RATING),1) FROM REVIEW r2 WHERE r2.PRODUCT_ID = p.ID) AS AVG_RATING"
        );
        s = s.replaceAll(
                "(?i)AVG\\s*\\(\\s*r\\.RATING\\s*\\)\\s*AS\\s*AVG_RATING",
                "(SELECT ROUND(AVG(r2.RATING),1) FROM REVIEW r2 WHERE r2.PRODUCT_ID = p.ID) AS AVG_RATING"
        );

        // 2) REVIEW 조인 제거(LEFT/INNER/RIGHT 모두)
        s = s.replaceAll("(?is)\\s+(LEFT|INNER|RIGHT)\\s+JOIN\\s+REVIEW\\s+r\\s+ON\\s+[^\\n]*", " ");

        // 3) GROUP BY에서 r.* 제거
        s = s.replaceAll("(?i),\\s*r\\.[A-Z_]+", "");
        s = s.replaceAll("(?i)GROUP BY\\s*r\\.[A-Z_]+\\s*(,)?", "GROUP BY ");

        // 4) 환불률 정의 교정: (환불수량 / 확정수량) * 100
        s = s.replaceAll(
                "(?is)CASE\\s+WHEN\\s+SUM\\(\\s*od\\.QUANTITY\\s*\\)\\s*>\\s*0\\s*THEN\\s*ROUND\\s*\\(\\s*\\(\\s*SUM\\([^)]*?rd\\.REFUNDQTY[^)]*\\)\\s*/\\s*SUM\\(\\s*od\\.QUANTITY\\s*\\)\\s*\\)\\s*\\*\\s*100\\s*,\\s*2\\s*\\)",
                "CASE WHEN SUM(od.CONFIRMQUANTITY) > 0 THEN ROUND( SUM(NVL(rd.REFUNDQTY,0)) / SUM(od.CONFIRMQUANTITY) * 100, 2)"
        );

        // 5) 제품명 LIKE 두 번 → 정확 일치로 교정 (NAME에 용량 포함 구조)
        s = fixNameFilterExact(s, userMsg);

        return s;
    }

    // LIKE 두 개(이름 + 용량) → 정확 일치, 또는 LIKE 한 개 안에 이미 NNNml 포함 시 정확 일치
    private static final Pattern NAME_THEN_ML = Pattern.compile(
            "(?is)UPPER\\(\\s*p\\.NAME\\s*\\)\\s*LIKE\\s*UPPER\\('%\\s*([^']*?)\\s*%'\\)\\s*" +
            "AND\\s*UPPER\\(\\s*p\\.NAME\\s*\\)\\s*LIKE\\s*UPPER\\('%\\s*([0-9]+\\s*ml)\\s*%'\\)"
    );
    private static final Pattern ML_THEN_NAME = Pattern.compile(
            "(?is)UPPER\\(\\s*p\\.NAME\\s*\\)\\s*LIKE\\s*UPPER\\('%\\s*([0-9]+\\s*ml)\\s*%'\\)\\s*" +
            "AND\\s*UPPER\\(\\s*p\\.NAME\\s*\\)\\s*LIKE\\s*UPPER\\('%\\s*([^']*?)\\s*%'\\)"
    );
    private static final Pattern SINGLE_LIKE_WITH_ML = Pattern.compile(
            "(?is)UPPER\\(\\s*p\\.NAME\\s*\\)\\s*LIKE\\s*UPPER\\('%\\s*([^']*?\\b[0-9]+\\s*ml\\b[^']*?)\\s*%'\\)"
    );

    private static String fixNameFilterExact(String sql, String userMsg) {
        if (sql == null) return null;
        String s = sql;
        s = NAME_THEN_ML.matcher(s).replaceAll("UPPER(p.NAME) = UPPER('$1 $2')");
        s = ML_THEN_NAME.matcher(s).replaceAll("UPPER(p.NAME) = UPPER('$2 $1')");
        s = SINGLE_LIKE_WITH_ML.matcher(s).replaceAll("UPPER(p.NAME) = UPPER('$1')");
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

    // ⚠️ 예전: FIRST_INT (연도/용량 숫자를 ID로 오인) → 맥락형 토큰으로 대체
    private static final Pattern ID_TOKEN = Pattern.compile(
            "(?i)(?:\\b(?:id|product\\s*id|상품(?:번호)?|제품(?:번호)?)\\s*[:#]??\\s*)(\\d+)\\b"
    );
    private static Long extractContextualId(String text) {
        if (text == null) return null;
        Matcher m = ID_TOKEN.matcher(text);
        return m.find() ? Long.parseLong(m.group(1)) : null;
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
