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
    
    private static final Pattern NAMED_POSITIONAL = Pattern.compile(":\\d+\\b");
    // === 상품명 자동 따옴표 감지 ===
    private static final Pattern PRODUCT_PHRASE =
            Pattern.compile("([\\p{L}\\p{N}][\\p{L}\\p{N}\\s\\-·’'()]+?\\s*\\d+\\s*ml)\\b",
                    Pattern.CASE_INSENSITIVE);
    private static String keepMlPhrase(String s){
        if (s == null) return "";
        Matcher m = Pattern.compile(
            "([\\p{L}\\p{N}][\\p{L}\\p{N}\\s\\-·’'()]+?\\s*\\d+\\s*ml)",
            Pattern.CASE_INSENSITIVE
        ).matcher(s);
        return m.find() ? m.group(1).trim() : s.trim();
    }

    private String autoQuoteProductName(String msg) {
        if (msg == null) return null;
        if (msg.contains("\"")) return msg; // 이미 따옴표 있으면 그대로
        Matcher m = PRODUCT_PHRASE.matcher(msg);
        StringBuffer sb = new StringBuffer();
        boolean quoted = false;
        while (m.find()) {
            String phrase = m.group(1).trim();
            if (phrase.matches(".*\\d+\\s*ml.*")) {
                m.appendReplacement(sb, "\"" + Matcher.quoteReplacement(phrase) + "\"");
                quoted = true;
            }
        }
        m.appendTail(sb);
        return quoted ? sb.toString() : msg;
    }
    
    // 파일 상단 클래스 안에 (record 지원 안되면 작은 POJO로)
    private static record TwoProducts(String a, String b) {}

    
    // 기존 split 그대로 사용하되, 잘 안 잘리는 케이스 대비 트리밍 강화
    private static TwoProducts extractTwoProducts(String msg) {
        if (msg == null) return new TwoProducts("", "");

        Matcher m = TWO_QUOTED.matcher(msg);
        if (m.find()) return new TwoProducts(keepMlPhrase(m.group(1)), keepMlPhrase(m.group(2)));

        m = TWO_ML.matcher(msg);
        if (m.find()) return new TwoProducts(keepMlPhrase(m.group(1)), keepMlPhrase(m.group(2)));

        String cleaned = msg.replaceAll("[\"'`]", " ").trim();
        String[] parts = P_VS.split(cleaned);
        if (parts.length >= 2) {
            String a = parts[0].trim(), b = parts[1].trim();
            if (looksLikeProduct(a) && looksLikeProduct(b)) {
                return new TwoProducts(keepMlPhrase(a), keepMlPhrase(b));
            }
        }
        return new TwoProducts("", "");
    }
    // === 상품명 추출 (따옴표 없어도 :q 보장) ===
    private String extractProductQuery(String msg) {
        if (msg == null) return null;
        Matcher quoted = Pattern.compile("\"([^\"]{2,80}?)\"").matcher(msg);
        if (quoted.find()) return quoted.group(1);
        Matcher m = PRODUCT_PHRASE.matcher(msg);
        if (m.find()) return m.group(1);
        return null;
    }
    
    private static int extractMonthsFromMessage(String msg) {
        // "3개월", "6개월" 등에서 숫자 추출
        Pattern monthPattern = Pattern.compile("(\\d+)개월");
        Matcher m = monthPattern.matcher(msg);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 3; // 기본값
    }
    private static final String SCHEMA_DOC = """
            -- Oracle / 화이트리스트 (대문자 컬럼)

            -- 👤 사용자 관련
            -- USERS(USERNO PK, USERNAME UK, PASSWORD, EMAIL UK, NAME, BIRTH, GENDER, TELECOM, PHONE UK, REG, STATUS, BANREG, ROLE, LOGINTYPE, KAKAOID UK, MILEAGE, AGE)
            -- ⚠️ 중요: USERS.REG는 LocalDate 타입 (가입일)
            -- ⚠️ USERS.STATUS 값: 'active', 'suspended', 'banned' 등
			-- ⚠️ USERS.ROLE 값: 'user', 'admin' 등
            -- ⚠️ 회원 질문 처리 예시:
			-- 신규 가입자: SELECT COUNT(*) FROM USERS WHERE REG >= :start AND REG < :end
			-- 전체 회원: SELECT COUNT(*) FROM USERS WHERE STATUS = 'active'
			-- 마일리지 상위: SELECT USERNO, NAME, MILEAGE FROM USERS WHERE STATUS = 'active' ORDER BY MILEAGE DESC
            -- 성별 분포: SELECT GENDER, COUNT(*) FROM USERS GROUP BY GENDER
            -- 연령대 분포: SELECT 
            --   CASE WHEN AGE BETWEEN 10 AND 19 THEN '10대'
            --        WHEN AGE BETWEEN 20 AND 29 THEN '20대'
            --        WHEN AGE BETWEEN 30 AND 39 THEN '30대'
            --        WHEN AGE BETWEEN 40 AND 49 THEN '40대'
            --        ELSE '기타' END as age_group,
            --   COUNT(*) as member_count
            --   FROM USERS GROUP BY 
            --   CASE WHEN AGE BETWEEN 10 AND 19 THEN '10대'
            --        WHEN AGE BETWEEN 20 AND 29 THEN '20대'
            --        WHEN AGE BETWEEN 30 AND 39 THEN '30대'
            --        WHEN AGE BETWEEN 40 AND 49 THEN '40대'
            --        ELSE '기타' END

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

    private static final Pattern USERS_RELATED_KEYWORDS =
    	    Pattern.compile("(?i)(회원|가입|신규|고객|사용자|마일리지|일주일|7일|\\bTOP\\b|\\bVIP\\b|\\bmembers?\\b|\\busers?\\b|\\bcustomers?\\b)");

    private static boolean isUsersRelatedQuery(String userMsg) {
        if (userMsg != null && USERS_RELATED_KEYWORDS.matcher(userMsg).find()) {
            log.info("회원 관련 질문 감지: {}", userMsg);
            return true;
        }
        return false;
    }

    private static final Pattern INTENT_ANY_CHART =
            Pattern.compile("(차트|그래프|chart)", Pattern.CASE_INSENSITIVE);

    private static final Pattern ORDERS_RELATED_KEYWORDS =
    	    Pattern.compile("(?i)(매출|주문|결제|판매량|매출액|\\brevenue\\b|\\bsales\\b|\\borders?\\b|\\bpayments?\\b)");

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

    // === 상품 통계 의도 ===
    private static final Pattern P_PRODUCT_NAME_WITH_ML =
            Pattern.compile("(?i)([\\p{L}\\p{N}][\\p{L}\\p{N}\\s]{1,80})\\s*([0-9]{1,4})\\s*m\\s*l");

    private static final Pattern P_METRIC_KEYWORDS =
            Pattern.compile("(?i)(환불|환불률|리뷰|평점|별점|판매|판매량|매출|주문|통계|누적|총계)");

    private static boolean isProductStatsIntent(String msg) {
        if (msg == null) return false;
        return P_PRODUCT_NAME_WITH_ML.matcher(msg).find()
                && P_METRIC_KEYWORDS.matcher(msg).find();
    }

    // === 비교 의도 & 2개 상품 추출 ===
    private static final Pattern P_VS =
            Pattern.compile("(?i)\\s*(?:vs\\.?|대비|비교|그리고|&|/|,)\\s*");
    private static final Pattern TWO_QUOTED =
            Pattern.compile("\"([^\"]{2,80})\".*?\"([^\"]{2,80})\"");
    private static final Pattern TWO_ML =
            Pattern.compile("([\\p{L}\\p{N} ].*?\\d+\\s*m\\s*l).*?([\\p{L}\\p{N} ].*?\\d+\\s*m\\s*l)",
                    Pattern.CASE_INSENSITIVE);
    private static boolean isTwoProductCompare(String msg) {
        if (msg == null) return false;
        if (TWO_QUOTED.matcher(msg).find()) return true;   // "…ml" "…ml"
        if (TWO_ML.matcher(msg).find()) return true;       // …ml …ml

        // ① 메트릭 나열에 슬래시가 있으면 비교 아님 (상품 패턴이 2개 없으면)
        if (msg.contains("/") && METRIC_WORDS.matcher(msg).find()
            && !PRODUCT_PHRASE.matcher(msg).find()) return false;

        // ② VS/대비/쉼표/슬래시로 나눠 보되, 양쪽이 모두 '상품처럼' 보여야만 비교
        String cleaned = msg.replaceAll("[\"'`]", " ").trim();
        String[] parts = P_VS.split(cleaned);
        if (parts.length >= 2) {
            String a = parts[0].trim(), b = parts[1].trim();
            return looksLikeProduct(a) && looksLikeProduct(b);
        }
        return false;
    }
    private static String buildTwoProductCompareSql() {
        return """
            WITH S AS (
              SELECT 1 AS MATCHED, :q1 AS Q FROM DUAL
              UNION ALL
              SELECT 2 AS MATCHED, :q2 AS Q FROM DUAL
            ),
            P AS (
              SELECT
                s.MATCHED,
                s.Q,
                p.ID   AS PRODUCT_ID,
                NVL(p.NAME, s.Q) AS PRODUCT_NAME
              FROM S
              LEFT JOIN PRODUCT p
                ON UPPER(REPLACE(p.NAME,' ','')) LIKE UPPER('%' || REPLACE(s.Q,' ','') || '%')
            )
            SELECT
              p.MATCHED,
              p.Q                 AS MATCHED_QUERY,
              p.PRODUCT_ID,
              p.PRODUCT_NAME,
              NVL(SUM(od.CONFIRMQUANTITY), 0)                AS TOTAL_SOLD_QTY,
              NVL(SUM(od.CONFIRMQUANTITY * od.SELLPRICE), 0) AS TOTAL_SALES_AMOUNT
            FROM P p
            LEFT JOIN ORDERDETAIL od
                   ON od.ID = p.PRODUCT_ID
            LEFT JOIN ORDERS o
                   ON o.ORDERID = od.ORDERID
                  AND o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                  AND o.REGDATE >= :start
                  AND o.REGDATE <  :end
            GROUP BY p.MATCHED, p.Q, p.PRODUCT_ID, p.PRODUCT_NAME
            ORDER BY p.MATCHED, TOTAL_SALES_AMOUNT DESC NULLS LAST
            FETCH FIRST 2000 ROWS ONLY
            """;
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
        // 0) 메시지 전처리
        String msg = (userMsg == null ? "" : userMsg);
        msg = autoQuoteProductName(msg);
        if (!msg.contains("\"")) {
            Matcher m = P_PRODUCT_NAME_WITH_ML.matcher(msg);
            if (m.find()) {
                String phrase = m.group(0).trim();
                msg = msg.replace(phrase, "\"" + phrase + "\"");
            }
        }

        // 1) 상품통계/회원 의도면 라우터 우회
        boolean forceSql = isProductStatsIntent(msg) 
                || isUsersRelatedQuery(msg) 
                || isTwoProductCompare(msg);

        if (!forceSql) {
            var preRoute = router.route(msg);
            if (preRoute.mode() == RouteService.Mode.CHAT) {
                return new AiResult(chat.ask(msg), null, List.of(), null);
            }
        }

        // 2) 기간 결정
        PeriodResolver.ResolvedPeriod period;

        if (hasExplicitPeriodWords(msg)) {
            // 사용자가 "이번달", "지난주", "최근 10일" 등 기간을 명시한 경우
            period = PeriodResolver.resolveFromUtterance(msg);

        } else if (isAllTimeQuery(msg)) {
            // "전체/누적/all time" 등 전체기간 키워드
            LocalDateTime startTime = LocalDateTime.of(2020, 1, 1, 0, 0);
            LocalDateTime endTime   = LocalDateTime.now()
                .plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "전체 기간");

        } else if (isTwoProductCompare(msg)) {
            // 비교 의도인데 기간 언급이 없으면 기본을 전체기간으로
            LocalDateTime startTime = LocalDateTime.of(2020, 1, 1, 0, 0);
            LocalDateTime endTime   = LocalDateTime.now()
                .plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "전체 기간(비교)");

        } else {
            // 그 외: 주문/회원 관련은 최근 30일, 아니면 전체기간
            if (isOrdersRelatedQuery(msg, null) || isUsersRelatedQuery(msg)) {
                LocalDateTime endTime   = LocalDateTime.now()
                    .plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                LocalDateTime startTime = endTime.minusDays(30);
                period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "최근 30일");
            } else {
                LocalDateTime startTime = LocalDateTime.of(2020, 1, 1, 0, 0);
                LocalDateTime endTime   = LocalDateTime.now()
                    .plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "전체 기간");
            }
        }

        // 3) 차트 의도면 차트 핸들러
        if (isChartIntent(msg)) {
            try { return handleChartGeneric(msg, principal, period); }
            catch (Exception ignore) { }
        }

        // 4) (재)라우팅도 forceSql 이면 무시
        if (!forceSql) {
            var route = router.route(msg);
            if (route.mode() == RouteService.Mode.CHAT) {
                return new AiResult(chat.ask(msg), null, List.of(), null);
            }
        }

        // 5) SQL 생성 (AI)
        String ai = chat.generateSql(msg, SCHEMA_DOC);

        TwoProducts tpProbe = extractTwoProducts(msg);
        boolean wantCompare = isTwoProductCompare(msg) &&
                              !tpProbe.a().isBlank() && !tpProbe.b().isBlank();

        if (wantCompare) {
            ai = buildTwoProductCompareSql();
        }
        log.info("AI 생성 SQL: {}", ai);

        // ---------- [복잡한 회원 분석 템플릿 분기] 시작 ----------
        if (msg.contains("첫 구매") && (msg.contains("기간") || msg.contains("시간"))) {
            ai = """
                SELECT 
                    ROUND(AVG(EXTRACT(DAY FROM (first_purchase_date - join_date))), 1) as avg_days_to_first_purchase,
                    MIN(EXTRACT(DAY FROM (first_purchase_date - join_date))) as min_days,
                    MAX(EXTRACT(DAY FROM (first_purchase_date - join_date))) as max_days,
                    COUNT(*) as total_customers
                FROM (
                    SELECT u.USERNO,
                           CAST(u.REG AS DATE) as join_date,
                           MIN(o.REGDATE) as first_purchase_date
                    FROM USERS u
                    JOIN ORDERS o ON u.USERNO = o.USERNO
                    WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                      AND u.REG >= :start AND u.REG < :end
                    GROUP BY u.USERNO, u.REG
                )
                """;
        }

       
        if ((msg.contains("재구매") || msg.contains("일회성")) && msg.contains("고객")) {
            ai = """
                SELECT 
                    customer_type,
                    customer_count,
                    ROUND(customer_count * 100.0 / SUM(customer_count) OVER(), 2) as percentage
                FROM (
                    SELECT 
                        CASE WHEN order_count = 1 THEN '일회성 구매 고객'
                             WHEN order_count >= 2 THEN '재구매 고객'
                             ELSE '미구매 고객' END as customer_type,
                        COUNT(*) as customer_count
                    FROM (
                        SELECT u.USERNO, 
                               COUNT(o.ORDERID) as order_count
                        FROM USERS u
                        LEFT JOIN ORDERS o ON u.USERNO = o.USERNO 
                                           AND o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                        WHERE u.STATUS = 'active'
                        GROUP BY u.USERNO
                    )
                    GROUP BY CASE WHEN order_count = 1 THEN '일회성 구매 고객'
                                  WHEN order_count >= 2 THEN '재구매 고객'
                                  ELSE '미구매 고객' END
                )
                ORDER BY customer_count DESC
                """;
        }

        if (msg.contains("VIP") || (msg.contains("100만원") && msg.contains("이상"))) {
            ai = """
                SELECT u.USERNO, u.NAME, u.EMAIL,
                       SUM(o.TOTALAMOUNT + NVL(o.USEDPOINT, 0)) as total_purchase_amount,
                       COUNT(o.ORDERID) as total_orders,
                       MIN(o.REGDATE) as first_purchase_date,
                       MAX(o.REGDATE) as last_purchase_date
                FROM USERS u
                JOIN ORDERS o ON u.USERNO = o.USERNO
                WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                  AND u.STATUS = 'active'
                GROUP BY u.USERNO, u.NAME, u.EMAIL
                HAVING SUM(o.TOTALAMOUNT + NVL(o.USEDPOINT, 0)) >= 1000000
                ORDER BY total_purchase_amount DESC
                FETCH FIRST 50 ROWS ONLY
                """;
        }
        // ---------- [복잡한 회원 분석 템플릿 분기] 끝 ----------

        // ---------- [하드코딩 템플릿 분기] 시작 ----------
        boolean asksRefund = msg.contains("환불");
        boolean asksReview = (msg.contains("리뷰") || msg.contains("평점") || msg.contains("별점"));
        boolean asksSales  = (msg.contains("판매") || msg.contains("매출") || msg.toLowerCase(Locale.ROOT).contains("sales"));

        if (!wantCompare && isProductStatsIntent(msg) && (asksRefund || asksReview || asksSales)) {
            ai = """
                SELECT
                    p.ID   AS PRODUCT_ID,
                    p.NAME AS PRODUCT_NAME,
                    SUM(od.CONFIRMQUANTITY)                             AS TOTAL_SOLD_QTY,
                    NVL(SUM(rd.REFUNDQTY), 0)                           AS TOTAL_REFUND_QTY,
                    CASE WHEN SUM(od.CONFIRMQUANTITY) > 0
                         THEN ROUND(NVL(SUM(rd.REFUNDQTY), 0) / SUM(od.CONFIRMQUANTITY) * 100, 2)
                         ELSE 0 END                                     AS REFUND_RATE,
                    NVL(rv.TOTAL_REVIEWS, 0)                            AS TOTAL_REVIEWS,
                    NVL(rv.AVG_RATING, 0)                               AS AVG_RATING
                FROM PRODUCT p
                JOIN ORDERDETAIL od
                  ON p.ID = od.ID
                JOIN ORDERS o
                  ON od.ORDERID = o.ORDERID
                LEFT JOIN REFUND rf
                  ON o.ORDERID = rf.ORDERID
                LEFT JOIN REFUNDDETAIL rd
                  ON rf.REFUNDID = rd.REFUND_REFUNDID
                 AND rd.ORDERDETAILID = od.ORDERDETAILID
                LEFT JOIN (
                    SELECT PRODUCT_ID,
                           COUNT(*)               AS TOTAL_REVIEWS,
                           ROUND(AVG(RATING), 1)  AS AVG_RATING
                    FROM REVIEW
                    GROUP BY PRODUCT_ID
                ) rv
                  ON p.ID = rv.PRODUCT_ID
                WHERE UPPER(REPLACE(p.NAME,' ','')) LIKE UPPER('%' || REPLACE(NVL(:q,''),' ','') || '%')
                  AND o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                  AND o.REGDATE >= :start
                  AND o.REGDATE <  :end
                GROUP BY p.ID, p.NAME, rv.TOTAL_REVIEWS, rv.AVG_RATING
                ORDER BY TOTAL_SOLD_QTY DESC NULLS LAST
                FETCH FIRST 2000 ROWS ONLY
                """;
        }
        // ---------- [하드코딩 템플릿 분기] 끝 ----------

        // 6) 브랜드 폴백
        if (ai != null && (msg.contains("브랜드별") || (msg.contains("브랜드") &&
                (msg.contains("매출") || msg.contains("판매") || msg.contains("점수"))))) {

            if (msg.contains("리뷰") || msg.contains("점수") || msg.contains("평점")) {
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

     // 7) SQL 교정/가드
        ai = smartSqlPostprocess(ai);
        ai = fixUsersDateQuery(ai);

        String normalized;
        if (wantCompare) {
            // 비교 전용은 JOIN ON에 기간/상태가 포함되어 있어 일반 정규화 스킵
            normalized = ai;
        } else {
            normalized = SqlNormalizer.enforceDateRangeWhere(ai, true);
            normalized = fixWhereClauseTrunc(normalized);
            normalized = fixCommonJoinMistakes(normalized);
            normalized = fixProductStatsQuery(normalized, msg);

            if (!isProductStatsIntent(msg) && !msg.contains("리뷰") && !msg.contains("평점") && !msg.contains("별점")) {
                normalized = stripReviewColsFromGroupBy(normalized);
            }
        }

        String safe;
        try {
            safe = SqlGuard.ensureSelect(normalized);
            safe = SqlGuard.ensureLimit(safe, 2000);
        } catch (Exception e) {
            if (wantCompare) {
                // ✅ 비교 템플릿은 신뢰 가능: 가드 실패 시에도 폴백 금지하고 그대로 실행
                log.warn("Guard rejected compare SQL; running compare template as-is. err={}", e.toString());
                safe = normalized; // 이미 FETCH FIRST가 포함됨
            } else {
                String fallback = createFallbackQuery(msg, period);
                try {
                    safe = SqlGuard.ensureSelect(fallback);
                    safe = SqlGuard.ensureLimit(safe, 2000);
                } catch (Exception e2) {
                    return new AiResult("죄송합니다. 서버 오류가 발생했습니다. 다시 시도해주세요.", null, List.of(), null);
                }
            }
        }



        // 8) 실행
        Map<String,Object> params;
        if (safe.contains(":q1") || safe.contains(":q2")) {
            TwoProducts tp = extractTwoProducts(msg);
            params = buildFlexibleParamsForCompare(safe, period, principal, msg, tp);
        } else {
            params = buildFlexibleParams(safe, period, principal, msg);
        }

        // 휴면/미구매용 cutoffDate 보정(해당 템플릿일 때만 계산해서 주입)
        if (safe.contains(":cutoffDate") && (msg.contains("휴면") || msg.contains("미구매"))) {
            int months = extractMonthsFromMessage(msg);
            // Oracle TIMESTAMP 비교를 위해 자정 기준 Timestamp 사용
            LocalDateTime cutoffLdt = LocalDateTime.now().minusMonths(months);
            params.put("cutoffDate", Timestamp.valueOf(cutoffLdt));
        }

        List<Map<String,Object>> rows = sqlExec.runSelectNamed(safe, params);


        // 9) 응답 생성
        String tableMd = sqlExec.formatAsMarkdownTable(rows);
        String summary;
        if (rows == null || rows.isEmpty()) {
            summary = isOrdersRelatedQuery(msg, safe)
                    ? "%s 기준 조건에 맞는 데이터가 없습니다.".formatted(period.label())
                    : "조건에 맞는 데이터가 없습니다.";
        } else {
            try {
                String contextMsg = isOrdersRelatedQuery(msg, safe)
                        ? msg + " (기간: " + period.label() + ")"
                        : msg;
                summary = chat.summarize(contextMsg, safe, tableMd);
            } catch (Exception ignore) { summary = null; }

            if (summary == null ||
                    summary.toLowerCase(Locale.ROOT).contains("null") ||
                    summary.contains("존재하지 않")) {

                Map<String,Object> r = rows.get(0);
                String name  = getStr(r, "PRODUCT_NAME","PRODUCTNAME","NAME","LABEL");
                String brand = getStr(r, "BRANDNAME");
                Number qty   = getNum(r, "TOTALQUANTITY","TOTAL_SOLD_QTY","TOTAL_SOLD_QUANTITY","QUANTITY","TOTAL_SALES_QUANTITY");
                Number sales = getNum(r, "TOTALSALES","TOTAL_SALES_AMOUNT","VALUE");

                StringBuilder sb = new StringBuilder();
                if (isOrdersRelatedQuery(msg, safe)) sb.append("%s 기준 ".formatted(period.label()));
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
     // --- 비교 요약 덧붙이기 (두 상품 비교 전용) ---
        if (wantCompare) {
            boolean hasA = false, hasB = false;

            double aAmt = 0d, bAmt = 0d;
            long   aQty = 0L, bQty = 0L;
            String aLabel = null, bLabel = null;

            if (rows != null) {
                for (Map<String,Object> r : rows) {
                    Number matchedN = getNum(r, "MATCHED");
                    int matched = (matchedN == null ? 0 : matchedN.intValue());

                    double amt = getNum(r, "TOTAL_SALES_AMOUNT", "TOTALSALES").doubleValue();
                    long   qty = getNum(r, "TOTAL_SOLD_QTY", "TOTALQUANTITY").longValue();
                    String label = Optional.ofNullable(getStr(r, "PRODUCT_NAME", "MATCHED_QUERY")).orElse("");

                    if (matched == 1) { hasA = true; aAmt += amt; aQty += qty; if (aLabel == null) aLabel = label; }
                    else if (matched == 2) { hasB = true; bAmt += amt; bQty += qty; if (bLabel == null) bLabel = label; }
                }
            }

            if (hasA && hasB) {
                // 둘 다 매칭된 경우에만 비교 요약 출력
                String winner, loser;
                double winAmt, loseAmt; long winQty, loseQty;

                if (aAmt >= bAmt) {
                    winner = aLabel; loser = bLabel;
                    winAmt = aAmt;   loseAmt = bAmt;
                    winQty = aQty;   loseQty = bQty;
                } else {
                    winner = bLabel; loser = aLabel;
                    winAmt = bAmt;   loseAmt = aAmt;
                    winQty = bQty;   loseQty = aQty;
                }

                long diffAmt = Math.round(Math.abs(winAmt - loseAmt));
                long diffQty = Math.abs(winQty - loseQty);
                summary += " · 비교요약: \"" + winner + "\"가 매출 우위"
                         + " (금액 차이 " + fmtAmt(diffAmt) + ", 수량 차이 " + fmtQty(diffQty) + ").";
            } else if (hasA ^ hasB) {
                // 한쪽만 매칭되면 안내만 붙이고 비교요약은 생략
                String only = hasA ? (aLabel == null ? "첫번째 항목" : aLabel)
                                   : (bLabel == null ? "두번째 항목" : bLabel);
                summary += " · 참고: \"" + only + "\"만 매칭되어 비교 대상이 없습니다.";
            }
        }

        return new AiResult(summary, safe, rows, null);
    }


    // USERS 전용 처리 함수
    private String fixUsersDateQuery(String sql) {
        if (sql == null) return null;
        String upperSql = sql.toUpperCase();
        if (!upperSql.contains("FROM USERS") && !upperSql.contains("JOIN USERS")) return sql;

        String s = sql;
        // 대소문자 무시
        s = s.replaceAll("(?i)TRUNC\\(\\s*:currentDate\\s*,\\s*'MM'\\s*\\)", ":start");
        s = s.replaceAll("(?i)ADD_MONTHS\\(\\s*TRUNC\\(\\s*:currentDate\\s*,\\s*'MM'\\s*\\)\\s*,\\s*1\\s*\\)", ":end");
        s = s.replaceAll("(?i)TRUNC\\(\\s*SYSDATE\\s*,\\s*'MM'\\s*\\)", ":start");
        s = s.replaceAll("(?i)ADD_MONTHS\\(\\s*TRUNC\\(\\s*SYSDATE\\s*,\\s*'MM'\\s*\\)\\s*,\\s*1\\s*\\)", ":end");
        s = s.replaceAll("(?i)TRUNC\\(\\s*SYSDATE\\s*,\\s*'DD'\\s*\\)", ":start");
        s = s.replaceAll("(?i)SYSDATE\\s*-\\s*\\d+", ":start");
        s = s.replaceAll("(?i)\\bSYSDATE\\b", ":end");

        // EXTRACT(YEAR FROM (u.)?REG) = EXTRACT(YEAR FROM SYSDATE) [- 1]
        s = s.replaceAll("(?i)EXTRACT\\(\\s*YEAR\\s*FROM\\s*(?:\\w+\\.)?REG\\s*\\)\\s*=\\s*EXTRACT\\(\\s*YEAR\\s*FROM\\s*SYSDATE\\s*\\)\\s*-\\s*1",
                         "EXTRACT(YEAR FROM REG) = :lastYear");
        s = s.replaceAll("(?i)EXTRACT\\(\\s*YEAR\\s*FROM\\s*(?:\\w+\\.)?REG\\s*\\)\\s*=\\s*EXTRACT\\(\\s*YEAR\\s*FROM\\s*SYSDATE\\s*\\)",
                         "EXTRACT(YEAR FROM REG) = :currentYear");

        log.info("변환 후 SQL: {}", s);
        return s;
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
        normalized = tokenInjector.inject(normalized);
        normalized = fixCommonJoinMistakes(normalized);
        String safe = SqlGuard.ensureSelect(normalized);
        
        boolean hasPositional = safe.contains("?") || NAMED_POSITIONAL.matcher(safe).find();
        if (hasPositional) {
            safe = safe.replace("?", ":limit");
            safe = NAMED_POSITIONAL.matcher(safe).replaceAll(":limit");
        }

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
    private static String stripReviewColsFromGroupBy(String sql) {
        if (sql == null) return null;
        // GROUP BY ... [ORDER BY | FETCH | ) | 끝] 사이만 안전하게 수정
        Pattern p = Pattern.compile("(?is)GROUP\\s+BY\\s+(.*?)(?=(ORDER\\s+BY|FETCH\\b|\\)\\s*WHERE|\\)\\s*ORDER|\\)\\s*FETCH|$))");
        Matcher m = p.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String groupExpr = m.group(1);
            String cleaned = groupExpr
                    .replaceAll("(?i)\\brv\\.TOTAL_REVIEWS\\b\\s*,?\\s*", "")
                    .replaceAll("(?i)\\brv\\.AVG_RATING\\b\\s*,?\\s*", "")
                    .replaceAll("(?i),\\s*(?=$)", ""); // 끝의 콤마 정리
            m.appendReplacement(sb, "GROUP BY " + Matcher.quoteReplacement(cleaned));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /* -------------------- 상품 통계 쿼리 교정 -------------------- */
    private static String fixProductStatsQuery(String sql, String userMsg) {
        if (sql == null) return null;
        String s = sql;

        s = s.replaceAll("(?is)\\s+(LEFT|INNER|RIGHT)\\s+JOIN\\s+REVIEW\\s+r\\s+ON\\s+[^\\n]*", " ");

        s = s.replaceAll(
                "(?i)COUNT\\s*\\(\\s*DISTINCT\\s*r\\.REVIEWID\\s*\\)\\s*AS\\s*TOTAL_REVIEWS",
                "(SELECT COUNT(*) FROM REVIEW r2 WHERE r2.PRODUCT_ID = p.ID) AS TOTAL_REVIEWS"
        );
        s = s.replaceAll(
                "(?i)ROUND\\s*\\(\\s*AVG\\s*\\(\\s*r\\.RATING\\s*\\)\\s*,\\s*1\\s*\\)\\s*AS\\s*AVG_RATING",
                "(SELECT ROUND(AVG(r2.RATING),1) FROM REVIEW r2 WHERE r2.PRODUCT_ID = p.ID) AS AVG_RATING"
        );

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
        if (sql.contains(":start")) {
            if (sql.toUpperCase().contains("USERS") && sql.toUpperCase().contains("REG")) {
                params.put("start", period.start().toLocalDate());
                params.put("end", period.end().toLocalDate());
            } else {
                params.put("start", Timestamp.valueOf(period.start()));
                params.put("end", Timestamp.valueOf(period.end()));
            }
        }
        if (sql.contains(":q")) {
            String qVal = extractProductQuery(userMsg);
            if (qVal == null || qVal.isBlank()) {
                qVal = userMsg == null ? "" : userMsg.replaceAll("[\"'`]", "").trim();
            }
            qVal = qVal.replaceAll("[\"'`]", "").trim();
            params.put("q", qVal);
        }
        // 연도 파라미터 추가
        if (sql.contains(":currentYear")) {
            params.put("currentYear", LocalDate.now().getYear());
        }
        if (sql.contains(":lastYear")) {
            params.put("lastYear", LocalDate.now().getYear() - 1);
        }
        if (sql.contains(":currentDate")) {
            params.put("currentDate", new Timestamp(System.currentTimeMillis()));
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
            Long userNo = (principal == null) ? null : 0L;
            params.put("userNo", userNo != null ? userNo : 1L);
        }
        if (sql.contains(":limit")) params.put("limit", 2000);
        String brand = extractBrandName(userMsg);
        if (brand != null && sql.contains(":brandName")) params.put("brandName", brand);
        return params;
    }

    // 비교 질의용 파라미터 바인딩
    private Map<String,Object> buildFlexibleParamsForCompare(
            String sql, PeriodResolver.ResolvedPeriod period, Principal principal,
            String userMsg, TwoProducts tp) {

        Map<String,Object> params = new HashMap<>();
        params.put("start", Timestamp.valueOf(period.start()));
        params.put("end",   Timestamp.valueOf(period.end()));

        String a = Optional.ofNullable(tp.a()).orElse("").replaceAll("[\"'`]", "").trim();
        String b = Optional.ofNullable(tp.b()).orElse("").replaceAll("[\"'`]", "").trim();

        // 둘 중 하나라도 비면 비교용으로 돌리면 안 됨 → 단일 상품 질의로 폴백하게 상단 로직에서 걸러주세요.
        if (sql.contains(":q1")) params.put("q1", a);
        if (sql.contains(":q2")) params.put("q2", b);

        if (sql.contains(":limit")) params.put("limit", 2000);
        return params;
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

    // --- chart helpers ---
    private static String guessType(String msg, String fromSpec) {
        String t = (fromSpec == null ? "" : fromSpec.trim().toLowerCase(Locale.ROOT));
        if (Set.of("bar", "line", "pie", "doughnut").contains(t)) return t;
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
    private static String fmtAmt(double v) { return String.format("%,d", Math.round(v)); }
    private static String fmtQty(long v)   { return String.format("%,d", v); }
    // 추가: 메트릭 단어 목록
    private static final Pattern METRIC_WORDS =
    	    Pattern.compile("(?i)(판매|매출|환불|리뷰|평점|별점|주문|통계|수량|금액|sales?|revenue|refunds?|reviews?|ratings?|orders?)");


    // 추가: 이 토큰이 '상품처럼' 보이는지
    private static boolean looksLikeProduct(String t){
        if (t == null) return false;
        return PRODUCT_PHRASE.matcher(t).find(); // “… 75ml” 같은 패턴
    }
}
