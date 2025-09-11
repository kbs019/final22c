package com.ex.final22c.service.chat;

import com.ex.final22c.controller.chat.AiResult;
import com.ex.final22c.service.ai.SqlExecService;
import com.ex.final22c.sql.PeriodResolver;
import com.ex.final22c.sql.SqlGuard;
import com.ex.final22c.sql.SqlNormalizer;
import com.ex.final22c.sql.SqlTokenInjector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatOrchestratorService {

    private final RouteService router;
    private final ChatService chat;
    private final SqlExecService sqlExec;
    private final SqlTokenInjector tokenInjector;

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
            -- ⚠️ 중요: PRODUCT.NAME에 이미 용량이 포함되어 있음 (예: "샹스 오드 뚜왈렛 150ml")
            -- ⚠️ 상품명에 용량 조건이 있다면 VOLUME 테이블 조인하지 말고 PRODUCT.NAME으로만 필터링할 것
            -- ⚠️ 예시: "샹스 오드 뚜왈렛 150ml" → WHERE UPPER(p.NAME) LIKE UPPER('%샹스%오드%뚜왈렛%150ml%')
            -- BRAND(BRANDNO PK, BRANDNAME, IMGNAME, IMGPATH)
            -- GRADE(GRADENO PK, GRADENAME)
            -- MAINNOTE(MAINNOTENO PK, MAINNOTENAME)
            -- VOLUME(VOLUMENO PK, VOLUMENAME) -- 이 테이블은 상품 통계 조회시 사용하지 않음

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

            -- 📊 비즈니스 규칙 (매우 중요)  *개정판*
			-- 1) '판매량'(수량) = SUM(ORDERDETAIL.CONFIRMQUANTITY)
			-- 2) '매출'(금액) = SUM(ORDERDETAIL.CONFIRMQUANTITY * ORDERDETAIL.SELLPRICE)
			-- 3) 집계 대상 주문 = ORDERS.STATUS IN ('PAID','CONFIRMED','REFUNDED') 만 포함
			-- 4) 매출/판매량 계산에는 PAYMENT 테이블을 사용하지 않음
			-- 5) 제품별 집계 조인 키 = ORDERDETAIL.ID = PRODUCT.ID   -- (스키마 기준 유지)
			-- 6) 발주량 = SUM(PURCHASEDETAIL.QTY), 매입원가 = SUM(PURCHASEDETAIL.QTY * PRODUCT.COSTPRICE)
			-- 7) 환불률(%) = CASE WHEN SUM(od.CONFIRMQUANTITY)>0
			--                  THEN ROUND( NVL(SUM(rd.REFUNDQTY),0) / SUM(od.CONFIRMQUANTITY) * 100, 2 )
			--                  ELSE 0 END
			-- 8) REVIEW는 직접 JOIN 금지.
			--    반드시 (SELECT PRODUCT_ID, COUNT(*) TOTAL_REVIEWS, ROUND(AVG(RATING),1) AVG_RATING FROM REVIEW GROUP BY PRODUCT_ID)
			--    서브쿼리/CTE로 집계 후 LEFT JOIN (중복 집계 방지)
			-- 9) 기간이 명시되지 않은 '상품 통계/누적/총계' 질문은 기본을 '전체 기간'으로 가정
			-- 10) ⚠️상품명 검색 규칙(용량 포함, 공백/대소문자 차이 허용):
			--     WHERE UPPER(REPLACE(p.NAME,' ','')) LIKE UPPER('%' || REPLACE(NVL(:q,''), ' ', '') || '%')
			--     예) :q = '샹스 오드 뚜왈렛 150ml'
			--     (VOLUME 조인 금지. NAME만으로 필터링)
			-- 11) 필요한 컬럼만 SELECT 하고, 그 컬럼을 위해서만 최소 조인:
			--     - BRAND/GRADE/MAINNOTE는 해당 이름을 SELECT에 넣을 때만 조인
			--     - REFUNDDETAIL은 환불 지표를 요구할 때만 LEFT JOIN
			--     - REVIEW 서브쿼리도 리뷰 지표 요청시에만 포함
			-- 12) 세미콜론 금지, 네임드 바인드만 사용(:start, :end, :q, :limit 등)

            -- 🔒 날짜 규칙:
            -- - WHERE 절에서는 TRUNC/EXTRACT 금지
            -- - 날짜 WHERE: o.REGDATE >= :start AND o.REGDATE < :end (반열림)
            -- - 버킷팅(TRUNC)은 SELECT/GROUP BY에서만 사용
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

    private static final Pattern ORDERS_RELATED_KEYWORDS =
        Pattern.compile("(?i)\\b(매출|주문|결제|판매량|매출액|revenue|sales|orders?|payments?)\\b");

    private static final Pattern ALL_TIME_KEYWORDS =
        Pattern.compile("(?i)(전체|전체기간|누적|전기간|모든|총|all\\s*time|total|cumulative)");

    private static final Pattern STATS_KEYWORDS =
        Pattern.compile("(?i)(통계|누적|총계|전체\\s*내역|전기간|lifetime|all\\s*-?time)");

    private static final Pattern EXPLICIT_PERIOD_KEYWORDS =
        Pattern.compile("(?i)(오늘|어제|이번|지난|작년|올해|전년|전월|월별|주별|일별|분기|상반기|하반기|최근\\s*\\d+\\s*(일|주|개월|달|년)|\\bQ[1-4]\\b|\\d{4}\\s*년|\\d{1,2}\\s*월|this|last|previous)");

    private static String extractBrandName(String msg){
        if (msg == null) return null;
        Matcher m1 = Pattern.compile("([\\p{L}\\p{N}][\\p{L}\\p{N}\\s]{0,40}?)\\s*브랜드").matcher(msg);
        if (m1.find()) return m1.group(1).trim();
        Matcher m2 = Pattern.compile("브랜드\\s*([\\p{L}\\p{N}][\\p{L}\\p{N}\\s]{0,40})").matcher(msg);
        if (m2.find()) return m2.group(1).trim();
        return null;
    }

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

    private static boolean isOrdersRelatedQuery(String userMsg, String generatedSql) {
        if (generatedSql != null && generatedSql.toUpperCase().contains("ORDERS")) return true;
        if (userMsg != null && ORDERS_RELATED_KEYWORDS.matcher(userMsg).find()) return true;
        return false;
    }

    private static final Pattern COMPARISON_KEYWORDS =
        Pattern.compile("(?i)(vs|대비|비교|compared|compare|차이|변화|증감|전년|전월|지난|작년|last)");

    private static boolean isComparisonQuery(String userMsg) {
        if (userMsg == null) return false;
        return COMPARISON_KEYWORDS.matcher(userMsg).find();
    }

    // WHERE절 TRUNC 방어용 (간단 교정)
    private String fixWhereClauseTrunc(String sql) {
        if (sql == null) return null;
        String fixed = sql;
        fixed = fixed.replaceAll("(?i)WHERE\\s+TRUNC\\s*\\([^)]+\\)\\s*=\\s*[^\\s]+",
                "WHERE o.REGDATE >= :start AND o.REGDATE < :end");
        fixed = fixed.replaceAll("(?i)AND\\s+TRUNC\\s*\\([^)]+\\)\\s+IN\\s*\\([^)]+\\)", "");
        return fixed;
    }

    public AiResult handle(String userMsg, Principal principal){
        PeriodResolver.ResolvedPeriod period;
        
        if (isAllTimeQuery(userMsg)) {
            // "전체", "누적", "모든" 등이 포함된 경우
            LocalDateTime startTime = LocalDateTime.of(2020, 1, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "전체 기간");
            
        } else if (isComparisonQuery(userMsg)) {
            LocalDateTime endTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime startTime = endTime.minusMonths(3);
            period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "최근 3개월");
        } else if (hasExplicitPeriodWords(userMsg)) {
            // 명확한 기간 표현이 있는 경우만 PeriodResolver 사용
            period = PeriodResolver.resolveFromUtterance(userMsg);
        } else {
            // 기간 미지정 시 적절한 기본값 설정
            if (isOrdersRelatedQuery(userMsg, null)) {
                LocalDateTime endTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                LocalDateTime startTime = endTime.minusDays(30);
                period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "최근 30일");
            } else {
                LocalDateTime startTime = LocalDateTime.of(2020, 1, 1, 0, 0);
                LocalDateTime endTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "전체 기간");
            }
        }

        if (isChartIntent(userMsg)) {
            try { return handleChartGeneric(userMsg, principal, period); }
            catch (Exception ignore) { /* 실패 시 일반 경로 */ }
        }

        var route = router.route(userMsg);
        if (route.mode() == RouteService.Mode.CHAT) {
            return new AiResult(chat.ask(userMsg), null, List.of(), null);
        }

        String ai = chat.generateSql(userMsg, SCHEMA_DOC);

        // 브랜드 질의 폴백(안전 템플릿)
        if (userMsg.contains("브랜드별") || (userMsg.contains("브랜드") &&
            (userMsg.contains("매출") || userMsg.contains("판매") || userMsg.contains("점수")))) {

            if (userMsg.contains("리뷰") || userMsg.contains("점수") || userMsg.contains("평점")) {
                ai = """
                    SELECT 
                        b.BRANDNAME,
                        COUNT(DISTINCT p.ID) as product_count,
                        SUM(od.CONFIRMQUANTITY * od.SELLPRICE) as total_sales,
                        SUM(od.CONFIRMQUANTITY) as total_quantity,
                        (SELECT ROUND(AVG(r.RATING), 1) 
                         FROM REVIEW r 
                         JOIN PRODUCT p2 ON r.PRODUCT_ID = p2.ID 
                         WHERE p2.BRAND_BRANDNO = b.BRANDNO) as avg_rating
                    FROM BRAND b
                    LEFT JOIN PRODUCT p ON b.BRANDNO = p.BRAND_BRANDNO
                    LEFT JOIN ORDERDETAIL od ON p.ID = od.ID
                    LEFT JOIN ORDERS o ON od.ORDERID = o.ORDERID
                    WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED') OR o.STATUS IS NULL
                    GROUP BY b.BRANDNO, b.BRANDNAME
                    ORDER BY total_sales DESC NULLS LAST
                    """;
            } else {
                ai = """
                    SELECT 
                        b.BRANDNAME,
                        SUM(od.CONFIRMQUANTITY * od.SELLPRICE) as total_sales,
                        SUM(od.CONFIRMQUANTITY) as total_quantity,
                        COUNT(DISTINCT o.ORDERID) as order_count
                    FROM BRAND b
                    LEFT JOIN PRODUCT p ON b.BRANDNO = p.BRAND_BRANDNO
                    LEFT JOIN ORDERDETAIL od ON p.ID = od.ID
                    LEFT JOIN ORDERS o ON od.ORDERID = o.ORDERID
                    WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED') OR o.STATUS IS NULL
                    GROUP BY b.BRANDNO, b.BRANDNAME
                    ORDER BY total_sales DESC NULLS LAST
                    """;
            }
        }

        ai = smartSqlPostprocess(ai);

        String normalized = SqlNormalizer.enforceDateRangeWhere(ai, true);
        normalized = fixWhereClauseTrunc(normalized);
        normalized = fixCommonJoinMistakes(normalized);
        normalized = fixProductStatsQuery(normalized, userMsg);

        String safe;
        try {
            safe = SqlGuard.ensureSelect(normalized);
            safe = SqlGuard.ensureLimit(safe, 2000);
        } catch (Exception e) {
            String fallback = createFallbackQuery(userMsg, period);
            try {
                safe = SqlGuard.ensureSelect(fallback);
                safe = SqlGuard.ensureLimit(safe,2000);
            } catch (Exception e2) {
                return new AiResult("죄송합니다. 서버 오류가 발생했습니다. 다시 시도해주세요.", null, List.of(), null);
            }
        }

        var params = buildFlexibleParams(safe, period, principal, userMsg);
        List<Map<String,Object>> rows = sqlExec.runSelectNamed(safe, params);

        String tableMd = sqlExec.formatAsMarkdownTable(rows);
        String summary;
        if (rows == null || rows.isEmpty()) {
            summary = isOrdersRelatedQuery(userMsg, safe)
                    ? "%s 기준 조건에 맞는 데이터가 없습니다.".formatted(period.label())
                    : "조건에 맞는 데이터가 없습니다.";
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
                if (isOrdersRelatedQuery(userMsg, safe)) sb.append("%s 기준 ".formatted(period.label()));
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

        if (spec == null || spec.sql() == null ||
            !spec.sql().toUpperCase(Locale.ROOT).contains("LABEL") ||
            !spec.sql().toUpperCase(Locale.ROOT).contains("VALUE")) {
            spec = buildFallbackSpec(userMsg);
        }
        if (spec == null) {
            return new AiResult("차트 스펙 생성에 실패했어요. 요청을 더 구체적으로 적어주세요.", null, List.of(), null);
        }

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

        String normalized = SqlNormalizer.enforceDateRangeWhere(spec.sql().trim(), true);
        // 토큰 치환(템플릿에 {{date:o}}가 있을 경우)
        normalized = tokenInjector.inject(normalized);
        normalized = fixCommonJoinMistakes(normalized);
        String safe = SqlGuard.ensureSelect(normalized);

        boolean hasPositional = safe.contains("?") || safe.matches(".*:\\d+.*");
        if (hasPositional) safe = safe.replace("?", ":limit").replaceAll(":(\\d+)", ":limit");

        String up = safe.toUpperCase(Locale.ROOT);
        if (!up.contains("ROWNUM") && !up.contains("FETCH FIRST")) {
            safe = "SELECT * FROM (" + safe + ") WHERE ROWNUM <= :limit";
        }

        int limit = (spec.topN()!=null && spec.topN()>0 && spec.topN()<=50) ? spec.topN() : 12;
        Map<String,Object> params = new HashMap<>();
        params.put("limit", limit);
        params.put("start", overrideStart != null ? overrideStart : Timestamp.valueOf(period.start()));
        params.put("end",   overrideEnd   != null ? overrideEnd   : Timestamp.valueOf(period.end()));

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

        final String sig = safe.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        normalizeLabelsBySql(sig, labels);

        if (thisWeek) {
            LocalDate s = overrideStart.toLocalDateTime().toLocalDate();
            LocalDate e = overrideEnd.toLocalDateTime().minusDays(1).toLocalDate();
            padDaily(labels, values, s, e);
        } else if (sig.contains("TRUNC(O.REGDATE,'IW')") || sig.contains("'IYYY-IW'")) {
            padWeekly(labels, values,
                (overrideStart!=null?overrideStart:Timestamp.valueOf(period.start())).toLocalDateTime().toLocalDate(),
                (overrideEnd!=null?overrideEnd:Timestamp.valueOf(period.end())).toLocalDateTime().minusDays(1).toLocalDate(),
                1);
        } else if (sig.contains("TRUNC(O.REGDATE,'DD')") || sig.contains("'YYYY-MM-DD'")) {
            padDaily(labels, values,
                (overrideStart!=null?overrideStart:Timestamp.valueOf(period.start())).toLocalDateTime().toLocalDate(),
                (overrideEnd!=null?overrideEnd:Timestamp.valueOf(period.end())).toLocalDateTime().minusDays(1).toLocalDate());
        } else if (sig.contains("TRUNC(O.REGDATE,'MM')") || sig.contains("'YYYY-MM'")) {
            padMonthly(labels, values, period.start().getYear());
        }

        heuristicNormalizeLabels(labels, values);

        String type = guessType(userMsg, spec.type());
        if (values == null || values.size() <= 1) {
            type = "bar";
        }
        boolean horizontal = containsAny(userMsg, "가로", "horizontal");

        String valueLabel = Optional.ofNullable(spec.valueColLabel())
                .filter(s -> !s.isBlank())
                .orElse("매출(원)");

        String format = (spec.format() != null && !spec.format().isBlank())
                ? spec.format()
                : inferFormat(valueLabel);

        String title = Optional.ofNullable(spec.title())
                .filter(s -> !s.isBlank())
                .orElse("차트 · " + period.label());

        AiResult.ChartPayload chart = new AiResult.ChartPayload(
                labels, values, qtys,
                valueLabel,
                title,
                type, horizontal, format
        );


        String msg = rows.isEmpty()
            ? "%s 기준 조건에 맞는 데이터가 없습니다.".formatted(period.label())
            : "%s 기준 요청하신 차트를 표시했습니다.".formatted(period.label());
        return new AiResult(msg, safe, rows, chart);
    }

    /* -------------------- 폴백 차트 스펙 -------------------- */
    private ChartSpec buildFallbackSpec(String userMsg) {
        String brand = extractBrandName(userMsg);
        boolean byBrand = brand != null && !brand.isBlank();

        String sql;
        String title;

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

        if (containsAny(userMsg, "이번주","금주","this week")) {
            sql = """
                SELECT
                  TO_CHAR(TRUNC(o.REGDATE,'DD'),'YYYY-MM-DD') AS label,
                  SUM(od.CONFIRMQUANTITY * od.SELLPRICE)     AS value
                """ + fromJoins + "\n" + whereCore + brandFilter + """
                GROUP BY TRUNC(o.REGDATE,'DD')
                ORDER BY TRUNC(o.REGDATE,'DD')
                """;
            title = (byBrand ? (brand + " ") : "") + "이번주 일별 매출";
        } else if (containsAny(userMsg, "주별","주간","주 단위")) {
            sql = """
                SELECT
                  TO_CHAR(TRUNC(o.REGDATE,'IW'),'IYYY-IW') AS label,
                  SUM(od.CONFIRMQUANTITY * od.SELLPRICE)   AS value
                """ + fromJoins + "\n" + whereCore + brandFilter + """
                GROUP BY TRUNC(o.REGDATE,'IW')
                ORDER BY TRUNC(o.REGDATE,'IW')
                """;
            title = (byBrand ? (brand + " ") : "") + "주별 매출";
        } else if (containsAny(userMsg, "월별")) {
            sql = """
                SELECT
                  TO_CHAR(TRUNC(o.REGDATE,'MM'),'YYYY-MM') AS label,
                  SUM(od.CONFIRMQUANTITY * od.SELLPRICE)   AS value
                """ + fromJoins + "\n" + whereCore + brandFilter + """
                GROUP BY TRUNC(o.REGDATE,'MM')
                ORDER BY TRUNC(o.REGDATE,'MM')
                """;
            title = (byBrand ? (brand + " ") : "") + "월별 매출";
        } else {
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
        return new ChartSpec(sql, title, "매출(원)", 12, "line", "currency");
    }

    /* -------------------- 조인 오류 교정 -------------------- */
    private static String fixCommonJoinMistakes(String sql) {
        if (sql == null) return null;
        String s = sql;
        s = s.replaceAll("JOIN\\s+ORDERDETAIL\\s+od\\s+ON\\s+o\\.ID\\s*=\\s*od\\.ORDERID",
                "JOIN ORDERDETAIL od ON o.ORDERID = od.ORDERID");
        s = s.replaceAll("JOIN\\s+ORDERDETAIL\\s+od\\s+ON\\s+od\\.ID\\s*=\\s*o\\.ORDERID",
                "JOIN ORDERDETAIL od ON o.ORDERID = od.ORDERID");
        s = s.replaceAll("(?i)JOIN\\s+ORDERDETAIL\\s+od\\s+ON\\s+o\\.ORDERNO\\s*=\\s*od\\.ORDERNO",
                "JOIN ORDERDETAIL od ON o.ORDERID = od.ORDERID");

        s = s.replaceAll("(?i)JOIN\\s+PRODUCT\\s+p\\s+ON\\s+od\\.PRODUCTID\\s*=\\s*p\\.ID",
                "JOIN PRODUCT p ON od.ID = p.ID");
        s = s.replaceAll("(?i)JOIN\\s+PRODUCT\\s+p\\s+ON\\s+p\\.ID\\s*=\\s*od\\.PRODUCTID",
                "JOIN PRODUCT p ON p.ID = od.ID");

        s = s.replaceAll("(?i)JOIN\\s+BRAND\\s+b\\s+ON\\s+p\\.BRANDID\\s*=\\s*b\\.ID",
                "JOIN BRAND b ON p.BRAND_BRANDNO = b.BRANDNO");
        s = s.replaceAll("(?i)JOIN\\s+BRAND\\s+b\\s+ON\\s+b\\.ID\\s*=\\s*p\\.BRANDID",
                "JOIN BRAND b ON b.BRANDNO = p.BRAND_BRANDNO");

        s = s.replaceAll("(?i)\\bod\\.PRODUCTID\\b", "od.ID");
        s = s.replaceAll("(?i)\\bp\\.BRANDID\\b", "p.BRAND_BRANDNO");
        s = s.replaceAll("(?i)\\bb\\.ID\\b", "b.BRANDNO");
        return s;
    }

    /* -------------------- 상품 통계 쿼리 교정 -------------------- */
    private static String fixProductStatsQuery(String sql, String userMsg) {
        if (sql == null) return null;
        String s = sql;
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

        s = s.replaceAll("(?is)\\s+(LEFT|INNER|RIGHT)\\s+JOIN\\s+REVIEW\\s+r\\s+ON\\s+[^\\n]*", " ");
        s = s.replaceAll("(?i),\\s*r\\.[A-Z_]+", "");
        s = s.replaceAll("(?i)GROUP BY\\s*r\\.[A-Z_]+\\s*(,)?", "GROUP BY ");

        s = s.replaceAll(
            "(?is)CASE\\s+WHEN\\s+SUM\\(\\s*od\\.QUANTITY\\s*\\)\\s*>\\s*0\\s*THEN\\s*ROUND\\s*\\(\\s*\\(\\s*SUM\\([^)]*?rd\\.REFUNDQTY[^)]*\\)\\s*/\\s*SUM\\(\\s*od\\.QUANTITY\\s*\\)\\s*\\)\\s*\\*\\s*100\\s*,\\s*2\\s*\\)",
            "CASE WHEN SUM(od.CONFIRMQUANTITY) > 0 THEN ROUND( SUM(NVL(rd.REFUNDQTY,0)) / SUM(od.CONFIRMQUANTITY) * 100, 2)"
        );

        s = fixNameFilterExact(s, userMsg);
        return s;
    }

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

    private static Timestamp[] weekRangeKST() {
        ZoneId KST = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(KST);
        WeekFields wf = WeekFields.ISO;
        LocalDate monday = today.with(wf.dayOfWeek(), 1);
        LocalDate nextMonday = monday.plusWeeks(1);
        return new Timestamp[]{
            Timestamp.valueOf(monday.atStartOfDay()),
            Timestamp.valueOf(nextMonday.atStartOfDay())
        };
    }

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
        boolean allDateLike = labels.stream().allMatch(s -> s != null && s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-');
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
    private static void padWeekly(List<String> labels, List<Number> values, LocalDate from, LocalDate to, int stepWeeks) {
        Map<String, Number> baseline = new LinkedHashMap<>();
        WeekFields wf = WeekFields.ISO;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusWeeks(stepWeeks)) {
            int week = d.get(wf.weekOfWeekBasedYear());
            int wyear = d.get(wf.weekBasedYear());
            baseline.put(String.format("%04d-%02d", wyear, week), 0);
        }
        for (int i = 0; i < labels.size(); i++) baseline.put(toIsoWeek(labels.get(i)), values.get(i));
        labels.clear(); values.clear();
        baseline.forEach((k,v) -> { labels.add(k); values.add(v); });
    }

    private static String toYearMonth(String s) { return (s == null) ? null : (s.length() >= 7 ? s.substring(0,7) : s); }
    private static String toYmd(String s)       { return (s == null) ? null : (s.length() >= 10 ? s.substring(0,10) : s); }
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
    private static String toYear(String s)      { return (s == null) ? null : (s.length() >= 4 ? s.substring(0,4) : s); }

    private static boolean containsAny(String s, String... ks){
        if (s==null) return false;
        String t = s.toLowerCase(Locale.ROOT);
        for (String k: ks) if (t.contains(k.toLowerCase(Locale.ROOT))) return true;
        return false;
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
            if (v != null) { try { return new BigDecimal(v.toString()); } catch (Exception ignore) {} }
        }
        return 0;
    }

    private static boolean containsAnyNamedParam(String sql, Set<String> keys) {
        for (String k : keys) if (sql.contains(k)) return true;
        return false;
    }

    private static final Pattern ID_TOKEN = Pattern.compile("(?i)(?:\\b(?:id|product\\s*id|상품(?:번호)?|제품(?:번호)?)\\s*[:#]??\\s*)(\\d+)\\b");
    private static Long extractContextualId(String text) {
        if (text == null) return null;
        Matcher m = ID_TOKEN.matcher(text);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    private String smartSqlPostprocess(String sql) {
        if (sql == null) return null;
        String processed = sql;
        processed = processed.replaceAll("\\s*>=\\s*\\?", " >= :start");
        processed = processed.replaceAll("\\s*<\\s*\\?",  " < :end");
        processed = processed.replaceAll("\\s*=\\s*\\?",  " = :param");
        processed = processed.replaceAll("\\bO\\.REGDATE\\b", "o.REGDATE");
        processed = processed.replaceAll("TRUNC\\(SYSDATE,\\s*'IW'\\)", "TRUNC(:currentDate, 'IW')");
        processed = processed.replaceAll("TRUNC\\(SYSDATE\\s*-\\s*7,\\s*'IW'\\)", "TRUNC(:currentDate - 7, 'IW')");
        processed = processed.replaceAll("SYSDATE", ":currentDate");
        return balanceParentheses(processed);
    }

    private String balanceParentheses(String sql) {
        int open = 0, close = 0;
        for (char c : sql.toCharArray()) { if (c == '(') open++; if (c == ')') close++; }
        StringBuilder balanced = new StringBuilder(sql);
        while (close < open) { balanced.append(")"); close++; }
        if (open < close) { log.warn("SQL 닫는 괄호가 더 많음: {}", sql); }
        return balanced.toString();
    }

    private Map<String,Object> buildFlexibleParams(String sql, PeriodResolver.ResolvedPeriod period,
                                                   Principal principal, String userMsg) {
        Map<String,Object> params = new HashMap<>();

        if (sql.contains(":q")) {
            params.put("q", extractSearchKeyword(userMsg)); // null이면 "" 리턴되게
        }
        if (sql.contains(":currentDate")) {
            params.put("currentDate", new Timestamp(System.currentTimeMillis()));
        }
        if (sql.contains(":start")) {
            params.put("start", Timestamp.valueOf(period.start()));
        }
        if (sql.contains(":end")) {
            params.put("end", Timestamp.valueOf(period.end()));
        }
        if (sql.contains(":param")) params.put("param", Timestamp.valueOf(period.start()));

        if (containsAnyNamedParam(sql, ID_PARAMS)) {
            Long contextId = extractContextualId(userMsg);
            if (contextId == null) contextId = 1L;
            for (String idParam : ID_PARAMS) {
                if (sql.contains(idParam)) params.put(idParam.substring(1), contextId);
            }
        }
        if (sql.contains(":userNo")) {
            Long userNo = (principal == null) ? null : 0L; // TODO 실제 조회
            params.put("userNo", userNo != null ? userNo : 1L);
        }
        if (sql.contains(":limit")) params.put("limit", 3000);

        String brand = extractBrandName(userMsg);
        if (brand != null && sql.contains(":brandName")) params.put("brandName", brand);

        return params;
    }

    private static String extractSearchKeyword(String msg) {
        if (msg == null) return "";
        // 큰따옴표 안에 상품명이 들어오면 그걸 우선 사용: 예) "샹스 오드 뚜왈렛 150ml"
        var m = Pattern.compile("\"([^\"]{2,80})\"").matcher(msg);
        if (m.find()) return m.group(1).trim();

        // 흔한 불용어 제거 후 남은 텍스트를 q로 사용
        String t = msg.replaceAll("\\s+", " ")
                      .replaceAll("(상품|제품|통계|누적|총계|알려줘|보여줘|조회|검색|데이터|매출|판매량|수량|리뷰|평점)", "")
                      .trim();
        return t.isEmpty() ? "" : t;
    }
    private String createFallbackQuery(String userMsg, PeriodResolver.ResolvedPeriod period) {
        if (isOrdersRelatedQuery(userMsg, null)) {
            return """
                SELECT 
                    SUM(od.CONFIRMQUANTITY * od.SELLPRICE) AS total_sales,
                    COUNT(DISTINCT o.ORDERID) AS order_count,
                    SUM(od.CONFIRMQUANTITY) AS total_quantity
                FROM ORDERS o
                JOIN ORDERDETAIL od ON o.ORDERID = od.ORDERID
                WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                  AND o.REGDATE >= :start 
                  AND o.REGDATE < :end
                """;
        } else {
            return """
                SELECT 
                    p.NAME as product_name,
                    b.BRANDNAME as brand_name,
                    p.PRICE as price
                FROM PRODUCT p
                LEFT JOIN BRAND b ON p.BRAND_BRANDNO = b.BRANDNO
                WHERE ROWNUM <= 10
                """;
        }
    }
    
 // --- chart helpers (추가) ---
    private static String guessType(String msg, String fromSpec) {
        // 스펙에 명시된 타입이 우선
        String t = (fromSpec == null ? "" : fromSpec.trim().toLowerCase(Locale.ROOT));
        if (Set.of("bar", "line", "pie", "doughnut").contains(t)) return t;

        // 요청 문구로 추론
        String m = (msg == null ? "" : msg);
        if (containsAny(m, "추이", "월별", "주별", "일자별", "시간대", "트렌드", "변화", "경향", "시계열", "trend"))
            return "line";
        if (containsAny(m, "비율", "구성비", "점유율", "퍼센트", "비중", "파이", "도넛", "pie", "doughnut"))
            return "doughnut";
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
