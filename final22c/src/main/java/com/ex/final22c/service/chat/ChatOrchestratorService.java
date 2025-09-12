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
    private static final Pattern MOM_KEYWORDS =
    	    Pattern.compile("(?i)(전월\\s*대비|MoM|month\\s*over\\s*month|월\\s*대비|달\\s*대비)");

    	private static boolean isMoM(String msg){
    	    return msg != null && MOM_KEYWORDS.matcher(msg).find();
    	}
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
    private static boolean mentionsThisWeek(String msg){
        return mentionsThisWeekStrict(msg);
    }
    
    private static final Pattern TOPN_PATTERN =
    	    Pattern.compile("(?i)(?:top\\s*(\\d+)|상위\\s*(\\d+))");
    	private static final Pattern MIN_REVIEW_PATTERN =
    	    Pattern.compile("(?:리뷰|후기)\\s*(\\d+)\\s*건\\s*이상");

    	private static int extractTopN(String msg, int def) {
    	    if (msg == null) return def;
    	    Matcher m = TOPN_PATTERN.matcher(msg);
    	    if (m.find()) {
    	        for (int i=1;i<=m.groupCount();i++) {
    	            String g = m.group(i);
    	            if (g!=null) try { return Math.max(1, Math.min(Integer.parseInt(g), 100)); } catch (Exception ignore) {}
    	        }
    	    }
    	    return def;
    	}
    	private static int extractMinReviews(String msg, int def) {
    	    if (msg == null) return def;
    	    Matcher m = MIN_REVIEW_PATTERN.matcher(msg);
    	    if (m.find()) try { return Math.max(1, Integer.parseInt(m.group(1))); } catch (Exception ignore) {}
    	    return def;
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
   
    private static final Pattern YOY_KEYWORDS =
    	    Pattern.compile("(?i)(전년\\s*동기|전년\\s*동기간|전년\\s*대비|작년\\s*동기|작년\\s*동기간|작년\\s*대비|YoY|year\\s*over\\s*year|yoy)");

    private static boolean isYoY(String msg){
        return msg != null && YOY_KEYWORDS.matcher(msg).find();
    }
    
    
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
    // 클래스 안 어딘가에 유틸 추가
    private static boolean hasOrdersDateRange(String sql){
        if (sql == null) return false;
        String startBind = "(?:\\?|:start)";
        String endBind   = "(?:\\?|:end)";
        boolean ge = Pattern.compile("(?is)\\bo\\s*\\.\\s*regdate\\s*>?=\\s*" + startBind).matcher(sql).find();
        boolean lt = Pattern.compile("(?is)\\bo\\s*\\.\\s*regdate\\s*<\\s*"  + endBind  ).matcher(sql).find();
        return ge && lt;
    }

    // ORDER BY 뒤에 잘못 붙은 WHERE 1=1 …를 앞으로 당겨줌(벨트+서스펜더)
    private static String fixMisplacedDateWhere(String sql){
        if (sql == null) return null;
        Pattern p = Pattern.compile(
            "(?is)(ORDER\\s+BY[\\s\\S]*?)\\s*"
          + "(WHERE\\s+1\\s*=\\s*1\\s+AND\\s+O\\.REGDATE\\s*>?=\\s*(?:\\?|:start)\\s+AND\\s+O\\.REGDATE\\s*<\\s*(?:\\?|:end))"
        );
        Matcher m = p.matcher(sql);
        if (m.find()){
            return sql.substring(0, m.start(1)) + m.group(2) + " " + m.group(1) + sql.substring(m.end(2));
        }
        return sql;
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
        ":reviewId"
    );

    private static final Pattern USERS_RELATED_KEYWORDS =
    	    Pattern.compile("(?i)(회원|가입|신규|고객|사용자|마일리지|\\bTOP\\b|\\bVIP\\b|\\bmembers?\\b|\\busers?\\b|\\bcustomers?\\b)");

    private static boolean isUsersRelatedQuery(String userMsg) {
        if (userMsg != null && USERS_RELATED_KEYWORDS.matcher(userMsg).find()) {
            log.info("회원 관련 질문 감지: {}", userMsg);
            return true;
        }
        return false;
    }
    private static final Pattern ORDER_COUNT_KEYWORDS =
    	    Pattern.compile("(?i)(주문\\s*건수|건수|몇\\s*건|order\\s*count|주문\\s*수)");

    	private static boolean wantsOrderCount(String msg){
    	    return msg != null && ORDER_COUNT_KEYWORDS.matcher(msg).find();
    	}
    	
    private static boolean saysMonthly(String msg){
        return containsAny(msg, "월별", "monthly", "month");
    }
    private static final Pattern INTENT_ANY_CHART =
    	    Pattern.compile("(차트|그래프|분포|비율|파이|도넛|chart|distribution)", Pattern.CASE_INSENSITIVE);

    private static final Pattern ORDERS_RELATED_KEYWORDS =
    	    Pattern.compile("(?i)(매출|주문|결제|판매량|매출액|\\brevenue\\b|\\bsales\\b|\\borders?\\b|\\bpayments?\\b)");

    private static final Pattern ALL_TIME_KEYWORDS =
            Pattern.compile("(?i)(전체|전체기간|누적|전기간|모든|총|all\\s*time|total|cumulative)");

    private static final Pattern STATS_KEYWORDS =
            Pattern.compile("(?i)(통계|누적|총계|전체\\s*내역|전기간|lifetime|all\\s*-?time)");

    private static final Pattern EXPLICIT_PERIOD_KEYWORDS =
    	    Pattern.compile("(?i)(오늘|어제|이번|지난|작년|올해|전년|전월|월별|주별|일별|분기|상반기|하반기"
    	        + "|최근\\s*\\d+\\s*(일|주|개월|달|년)"
    	        + "|(일주일|1주일|한\\s*주|한주)"
    	        + "|\\bQ[1-4]\\b|\\d{4}\\s*년|\\d{1,2}\\s*월|this|last|previous)");

    private static String extractBrandName(String msg){
        if (msg == null) return null;
        Matcher m1 = Pattern.compile("([\\p{L}\\p{N}][\\p{L}\\p{N}\\s]{0,40}?)\\s*브랜드").matcher(msg);
        if (m1.find()) return m1.group(1).trim();
        Matcher m2 = Pattern.compile("브랜드\\s*([\\p{L}\\p{N}][\\p{L}\\p{N}\\s]{0,40})").matcher(msg);
        if (m2.find()) return m2.group(1).trim();
        return null;
    }
    private static Object norm(Object v) {
        if (v == null) return null;
        String cn = v.getClass().getName();
        try {
            if (cn.startsWith("oracle.sql.TIMESTAMP")) {
                Object ts = Class.forName(cn).getMethod("timestampValue").invoke(v);
                return ts.toString();
            }
            if (cn.equals("oracle.sql.DATE")) {
                Object d = Class.forName(cn).getMethod("dateValue").invoke(v);
                return d.toString();
            }
            if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toString();
            if (v instanceof java.sql.Date d)       return d.toLocalDate().toString();
            if (v instanceof java.sql.Time t)       return t.toLocalTime().toString();
        } catch (Exception ignore) {}
        return v;
    }

    private static List<Map<String,Object>> sanitize(List<Map<String,Object>> rows){
        List<Map<String,Object>> out = new ArrayList<>();
        for (var row: rows) {
            Map<String,Object> m = new LinkedHashMap<>();
            row.forEach((k,val) -> m.put(k, norm(val)));
            out.add(m);
        }
        return out;
    }
    private static boolean isAllTimeQuery(String userMsg) {
        if (userMsg == null) return false;
        return ALL_TIME_KEYWORDS.matcher(userMsg).find();
    }
    private static boolean hasExplicitPeriodWords(String msg){
        return msg != null && EXPLICIT_PERIOD_KEYWORDS.matcher(msg).find();
    }
    // "이번 주" 단독 의도만 잡기 (주간 비교/복합표현은 제외)
    private static boolean mentionsThisWeekStrict(String msg){
        if (msg == null) return false;

        String s = msg.toLowerCase();
        String sNoSpace = s.replaceAll("\\s+", "");

        boolean hasThisWeek =
                s.contains("이번 주") || sNoSpace.contains("이번주")
             || s.contains("금주")   || s.contains("this week");

        boolean looksLikeWeekCompare =
                s.contains("지난주") || s.contains("지난 주") || s.contains("전주")
             || s.matches(".*이번\\s*주\\s*(vs|대비|비교).*");

        // YYYY-MM-DD, YYYY/MM/DD, "8월 1일" 등 대략적인 절대 날짜 탐지
        boolean hasAbsoluteDate =
                s.matches(".*\\d{4}\\s*[-/.년]\\s*\\d{1,2}(\\s*[-/.월]\\s*\\d{1,2}(\\s*일)?)?.*")
             || s.matches(".*\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일.*");

        return hasThisWeek && !looksLikeWeekCompare && !hasAbsoluteDate;
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
    // 두 상품 리뷰/평점 비교
    private static String buildTwoProductCompareReviewSql() {
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
              NVL(rv.TOTAL_REVIEWS, 0) AS TOTAL_REVIEWS,
              NVL(rv.AVG_RATING,  0)  AS AVG_RATING
            FROM P p
            LEFT JOIN (
              SELECT PRODUCT_ID,
                     COUNT(*)              AS TOTAL_REVIEWS,
                     ROUND(AVG(RATING),1)  AS AVG_RATING
              FROM REVIEW
              WHERE CREATEDATE >= :start AND CREATEDATE < :end
              GROUP BY PRODUCT_ID
            ) rv
              ON rv.PRODUCT_ID = p.PRODUCT_ID
            ORDER BY p.MATCHED, AVG_RATING DESC, TOTAL_REVIEWS DESC
            FETCH FIRST 2000 ROWS ONLY
            """;
    }
    
    // 전월대비 당월
    private static String buildMoMOrdersSalesSql() {
        return """
            WITH B AS (
              SELECT
                TRUNC(:currentDate,'MM')                           AS THIS_START,
                ADD_MONTHS(TRUNC(:currentDate,'MM'), 1)           AS THIS_END,
                ADD_MONTHS(TRUNC(:currentDate,'MM'),-1)           AS PREV_START,
                TRUNC(:currentDate,'MM')                          AS PREV_END
              FROM DUAL
            )
            -- 당월
            SELECT
              'THIS' AS BUCKET,
              TO_CHAR(B.THIS_START,'YYYY-MM')        AS MONTH,
              COUNT(DISTINCT o.ORDERID)              AS ORDER_COUNT,
              NVL(SUM(od.CONFIRMQUANTITY*od.SELLPRICE),0) AS TOTAL_SALES
            FROM B
            JOIN ORDERS o      ON o.REGDATE >= B.THIS_START AND o.REGDATE < B.THIS_END
                              AND o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
            JOIN ORDERDETAIL od ON od.ORDERID = o.ORDERID
            UNION ALL
            -- 전월
            SELECT
              'PREV',
              TO_CHAR(B.PREV_START,'YYYY-MM'),
              COUNT(DISTINCT o.ORDERID),
              NVL(SUM(od.CONFIRMQUANTITY*od.SELLPRICE),0)
            FROM B
            JOIN ORDERS o      ON o.REGDATE >= B.PREV_START AND o.REGDATE < B.PREV_END
                              AND o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
            JOIN ORDERDETAIL od ON od.ORDERID = o.ORDERID
            FETCH FIRST 2000 ROWS ONLY
            """;
    }
    // top 평점
    private static String buildTopRatedProductsSql(int topN, int minReviews) {
        return ("""
            SELECT *
            FROM (
              SELECT
                p.ID   AS PRODUCT_ID,
                p.NAME AS PRODUCT_NAME,
                NVL(rv.TOTAL_REVIEWS, 0) AS TOTAL_REVIEWS,
                NVL(rv.AVG_RATING , 0)  AS AVG_RATING
              FROM PRODUCT p
              LEFT JOIN (
                SELECT PRODUCT_ID,
                       COUNT(*)              AS TOTAL_REVIEWS,
                       ROUND(AVG(RATING),1)  AS AVG_RATING
                FROM REVIEW
                WHERE CREATEDATE >= :start AND CREATEDATE < :end
                GROUP BY PRODUCT_ID
              ) rv ON rv.PRODUCT_ID = p.ID
              WHERE NVL(rv.TOTAL_REVIEWS, 0) >= %d
              ORDER BY rv.AVG_RATING DESC, rv.TOTAL_REVIEWS DESC, p.ID
            )
            FETCH FIRST %d ROWS ONLY
            """).formatted(minReviews, topN);
    }

    // 두 상품 환불률 비교
    private static String buildTwoProductCompareRefundSql() {
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
              NVL(SUM(rd.REFUNDQTY), 0)                      AS TOTAL_REFUND_QTY,
              CASE WHEN SUM(od.CONFIRMQUANTITY) > 0
                   THEN ROUND(NVL(SUM(rd.REFUNDQTY),0) / SUM(od.CONFIRMQUANTITY) * 100, 2)
                   ELSE 0 END                                AS REFUND_RATE
            FROM P p
            LEFT JOIN ORDERDETAIL od
                   ON od.ID = p.PRODUCT_ID
            LEFT JOIN ORDERS o
                   ON o.ORDERID = od.ORDERID
                  AND o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                  AND o.REGDATE >= :start
                  AND o.REGDATE <  :end
            LEFT JOIN REFUND rf
                   ON rf.ORDERID = o.ORDERID
            LEFT JOIN REFUNDDETAIL rd
                   ON rd.REFUND_REFUNDID = rf.REFUNDID
                  AND rd.ORDERDETAILID   = od.ORDERDETAILID
            GROUP BY p.MATCHED, p.Q, p.PRODUCT_ID, p.PRODUCT_NAME
            ORDER BY p.MATCHED, REFUND_RATE ASC, TOTAL_SOLD_QTY DESC
            FETCH FIRST 2000 ROWS ONLY
            """;
    }
    private static String buildYoySalesQtyForProductSql() {
        return """
            WITH P AS (
              SELECT p.ID
              FROM PRODUCT p
              WHERE (
                (REGEXP_LIKE(:q, '\\d+\\s*ml', 'i')
                 AND UPPER(REPLACE(p.NAME,' ','')) = UPPER(REPLACE(:q,' ','')))
                OR
                (NOT REGEXP_LIKE(:q, '\\d+\\s*ml', 'i')
                 AND UPPER(REPLACE(p.NAME,' ','')) LIKE UPPER('%' || REPLACE(:q,' ','') || '%'))
              )
            )
            SELECT 'THIS' AS BUCKET,
                   NVL(SUM(od.CONFIRMQUANTITY),0)                AS TOTAL_SOLD_QTY,
                   NVL(SUM(od.CONFIRMQUANTITY * od.SELLPRICE),0) AS TOTAL_SALES_AMOUNT
            FROM ORDERS o
            JOIN ORDERDETAIL od ON o.ORDERID = od.ORDERID
            JOIN P             ON P.ID = od.ID
            WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
              AND o.REGDATE >= :start AND o.REGDATE < :end
            UNION ALL
            SELECT 'PREV',
                   NVL(SUM(od.CONFIRMQUANTITY),0),
                   NVL(SUM(od.CONFIRMQUANTITY * od.SELLPRICE),0)
            FROM ORDERS o
            JOIN ORDERDETAIL od ON o.ORDERID = od.ORDERID
            JOIN P             ON P.ID = od.ID
            WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
              AND o.REGDATE >= :start_prev AND o.REGDATE < :end_prev
            FETCH FIRST 2000 ROWS ONLY
            """;
    }

    
    // 두상품 매출비교 
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
        boolean asksReview = (msg.contains("리뷰") || msg.contains("평점") || msg.contains("별점"));
        boolean wantsTopRated = asksReview && (containsAny(msg, "top", "TOP", "상위", "최고", "베스트"));
        
        // 1) 상품통계/회원 의도면 라우터 우회
        boolean forceSql = isProductStatsIntent(msg) 
                || isUsersRelatedQuery(msg) 
                || isTwoProductCompare(msg)
                || wantsTopRated;   // ← 추가

        if (!forceSql) {
            var preRoute = router.route(msg);
            if (preRoute.mode() == RouteService.Mode.CHAT) {
                return new AiResult(chat.ask(msg), null, List.of(), null);
            }
        }

        // 2) 기간 결정
        PeriodResolver.ResolvedPeriod period;

        boolean yoy = isYoY(msg);
        boolean yoyApplied = false;
        boolean momApplied = false;
        
        boolean vipMode = msg.toUpperCase(Locale.ROOT).contains("VIP")
                || (msg.contains("누적") && (msg.contains("구매") || msg.contains("구매액")));

        if (vipMode) {
            // VIP/누적: 기본 전체 기간 고정 (덮어쓰기 방지 위해 else-if 체인 상단 배치)
            LocalDateTime startTime = LocalDateTime.of(2020, 1, 1, 0, 0);
            LocalDateTime endTime   = LocalDateTime.now()
                    .plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "전체 기간(VIP)");

        } else if (mentionsThisWeek(msg)) {
            // 이번 주 (월요일 00:00 ~ 다음 주 월요일 00:00)
            var range = weekRangeKST();
            period = new PeriodResolver.ResolvedPeriod(
                    range[0].toLocalDateTime(),
                    range[1].toLocalDateTime(),
                    "이번 주"
            );

        } else if (hasExplicitPeriodWords(msg)) {
            // "지난주/전월/8월 1일~8월 31일/최근 7일" 등 명시적 기간
            period = PeriodResolver.resolveFromUtterance(msg);

        } else if (isAllTimeQuery(msg)) {
            // "전체/누적/전기간/total/all time" 등
            LocalDateTime startTime = LocalDateTime.of(2020, 1, 1, 0, 0);
            LocalDateTime endTime   = LocalDateTime.now()
                    .plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "전체 기간");

        } else if (isTwoProductCompare(msg)) {
            // 비교 질의 기본: 전체 기간
            LocalDateTime startTime = LocalDateTime.of(2020, 1, 1, 0, 0);
            LocalDateTime endTime   = LocalDateTime.now()
                    .plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "전체 기간(비교)");

        } else {
            // 기본값: 주문/회원 관련이면 최근 30일, 그 외 전체기간
        	if ((isOrdersRelatedQuery(msg, null) && !isProductStatsIntent(msg)) || isUsersRelatedQuery(msg)) {
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
        if (isUsersRelatedQuery(msg) && saysMonthly(msg)) {
            long days = Duration.between(period.start(), period.end()).toDays();
            if (days <= 40) { // 1~2개월만 잡힌 경우 보정
                LocalDate today = LocalDate.now();
                LocalDateTime startTime = today.minusMonths(11).withDayOfMonth(1).atStartOfDay();
                LocalDateTime endTime   = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
                period = new PeriodResolver.ResolvedPeriod(startTime, endTime, "최근 12개월");
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

        boolean mom = isMoM(msg);
        if (mom && !isTwoProductCompare(msg)) {
            ai = buildMoMOrdersSalesSql();
            momApplied = true;
        }
        TwoProducts tpProbe = extractTwoProducts(msg);
        boolean wantCompare = isTwoProductCompare(msg) &&
                              !tpProbe.a().isBlank() && !tpProbe.b().isBlank();

        boolean wantRefundCompare =
            wantCompare && (msg.contains("환불률") || msg.contains("환불 비율")
                         || msg.toLowerCase(Locale.ROOT).contains("refund rate"));

        boolean wantReviewCompare =
            wantCompare && (msg.contains("리뷰") || msg.contains("평점")
                         || msg.toLowerCase(Locale.ROOT).contains("rating"));

        if (wantCompare) {
            ai = wantRefundCompare ? buildTwoProductCompareRefundSql()
                : wantReviewCompare ? buildTwoProductCompareReviewSql()
                : buildTwoProductCompareSql(); // 매출 비교 기본
        }
        
        if (yoy && !isTwoProductCompare(msg)) {
            if (extractProductQuery(msg) != null) {
                ai = buildYoySalesQtyForProductSql();
                yoyApplied = true;             // ★ 실제 YoY 적용됐음을 표시
            }
        }
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
                SELECT
				  u.USERNO, u.NAME, u.EMAIL,
				  SUM(od.CONFIRMQUANTITY * od.SELLPRICE)          AS total_purchase_amount,
				  COUNT(DISTINCT o.ORDERID)                        AS total_orders,
				  TO_CHAR(MIN(o.REGDATE),'YYYY-MM-DD HH24:MI:SS')  AS first_purchase_date,
				  TO_CHAR(MAX(o.REGDATE),'YYYY-MM-DD HH24:MI:SS')  AS last_purchase_date
				FROM USERS u
				JOIN ORDERS o      ON u.USERNO = o.USERNO
				JOIN ORDERDETAIL od ON od.ORDERID = o.ORDERID
				WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
				  AND u.STATUS = 'active'
				  AND o.REGDATE >= :start
				  AND o.REGDATE <  :end
				GROUP BY u.USERNO, u.NAME, u.EMAIL
				HAVING SUM(od.CONFIRMQUANTITY * od.SELLPRICE) >= 1000000
				ORDER BY total_purchase_amount DESC
				FETCH FIRST 50 ROWS ONLY
                """;
        }
        // ---------- [복잡한 회원 분석 템플릿 분기] 끝 ----------

        // ---------- [하드코딩 템플릿 분기] 시작 ----------
        boolean asksRefund = msg.contains("환불");
        boolean asksSales  = (msg.contains("판매") || msg.contains("매출") || msg.toLowerCase(Locale.ROOT).contains("sales"));


        if (!yoyApplied && !wantCompare && wantsTopRated) {
            int topN = extractTopN(msg, 5);
            int minReviews = extractMinReviews(msg, 3); // 기본 3건 이상
            ai = buildTopRatedProductsSql(topN, minReviews);
        } else if (!yoyApplied && !wantCompare && isProductStatsIntent(msg) && (asksRefund || asksReview || asksSales)) {
            String orderBy = asksSales ? "TOTAL_SALES_AMOUNT" : "TOTAL_SOLD_QTY";
        	ai = ("""
        	        SELECT
        	            p.ID   AS PRODUCT_ID,
        	            p.NAME AS PRODUCT_NAME,
        	            SUM(od.CONFIRMQUANTITY)                             AS TOTAL_SOLD_QTY,
        	            SUM(od.CONFIRMQUANTITY * od.SELLPRICE)              AS TOTAL_SALES_AMOUNT,
        	            NVL(SUM(rd.REFUNDQTY), 0)                           AS TOTAL_REFUND_QTY,
        	            CASE WHEN SUM(od.CONFIRMQUANTITY) > 0
        	                 THEN ROUND(NVL(SUM(rd.REFUNDQTY), 0) / SUM(od.CONFIRMQUANTITY) * 100, 2)
        	                 ELSE 0 END                                     AS REFUND_RATE,
        	            NVL(rv.TOTAL_REVIEWS, 0)                            AS TOTAL_REVIEWS,
        	            NVL(rv.AVG_RATING, 0)                               AS AVG_RATING
        	        FROM PRODUCT p
        	        JOIN ORDERDETAIL od ON p.ID = od.ID
        	        JOIN ORDERS o       ON od.ORDERID = o.ORDERID
        	        LEFT JOIN REFUND rf       ON o.ORDERID = rf.ORDERID
        	        LEFT JOIN REFUNDDETAIL rd ON rf.REFUNDID = rd.REFUND_REFUNDID
        	                                  AND rd.ORDERDETAILID = od.ORDERDETAILID
        	        LEFT JOIN (
        	            SELECT PRODUCT_ID, COUNT(*) AS TOTAL_REVIEWS, ROUND(AVG(RATING), 1) AS AVG_RATING
        	            FROM REVIEW
        	            GROUP BY PRODUCT_ID
        	        ) rv ON p.ID = rv.PRODUCT_ID
        	        WHERE UPPER(REPLACE(p.NAME,' ','')) LIKE UPPER('%' || REPLACE(NVL(:q,''),' ','') || '%')
        	          AND o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
        	          AND o.REGDATE >= :start
        	          AND o.REGDATE <  :end
        	        GROUP BY p.ID, p.NAME, rv.TOTAL_REVIEWS, rv.AVG_RATING
        	        ORDER BY ${ORDER_BY} DESC NULLS LAST
        	        FETCH FIRST 2000 ROWS ONLY
        	        """).replace("${ORDER_BY}", orderBy);

        }
        // ---------- [하드코딩 템플릿 분기] 끝 ----------

        // 6) 브랜드 폴백
        if (ai != null && !wantsTopRated && (  // ← 이 부분 추가
        	    msg.contains("브랜드별") || (msg.contains("브랜드") &&
        	    (msg.contains("매출") || msg.contains("판매") || msg.contains("점수")))
        	)) {

            if (msg.contains("리뷰") || msg.contains("점수") || msg.contains("평점")) {
                ai = """
                    SELECT 
					  b.BRANDNAME,
					  COUNT(DISTINCT p.ID)                     AS product_count,
					  SUM(od.CONFIRMQUANTITY * od.SELLPRICE)   AS total_sales,
					  SUM(od.CONFIRMQUANTITY)                  AS total_quantity,
					  (SELECT ROUND(AVG(r.RATING), 1)
					   FROM REVIEW r
					   JOIN PRODUCT p2 ON r.PRODUCT_ID = p2.ID
					  WHERE p2.BRAND_BRANDNO = b.BRANDNO
					    AND r.CREATEDATE >= :start
					    AND r.CREATEDATE <  :end
					) AS avg_rating
					FROM BRAND b
					LEFT JOIN PRODUCT p      ON b.BRANDNO = p.BRAND_BRANDNO
					LEFT JOIN ORDERDETAIL od ON p.ID       = od.ID
					LEFT JOIN ORDERS o       ON od.ORDERID = o.ORDERID
					                        AND o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
					                        AND o.REGDATE >= :start
					                        AND o.REGDATE <  :end
					GROUP BY b.BRANDNO, b.BRANDNAME
					ORDER BY total_sales DESC NULLS LAST
                    """;
            } else {
                ai = """
                    SELECT 
					    b.BRANDNAME,
					    SUM(od.CONFIRMQUANTITY * od.SELLPRICE) AS total_sales,
					    SUM(od.CONFIRMQUANTITY)                AS total_quantity,
					    COUNT(DISTINCT o.ORDERID)              AS order_count
					FROM BRAND b
					LEFT JOIN PRODUCT p      ON b.BRANDNO = p.BRAND_BRANDNO
					LEFT JOIN ORDERDETAIL od ON p.ID       = od.ID
					LEFT JOIN ORDERS o       ON od.ORDERID = o.ORDERID
					                        AND o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
					                        AND o.REGDATE >= :start
					                        AND o.REGDATE <  :end
					GROUP BY b.BRANDNO, b.BRANDNAME
					ORDER BY total_sales DESC NULLS LAST
                    """;
            }
        }

     // 7) SQL 교정/가드
        ai = smartSqlPostprocess(ai);
        ai = fixUsersDateQuery(ai);

        String normalized;
        if (wantCompare || yoyApplied || momApplied) {
            normalized = ai;  // 템플릿에 기간/상태 포함
        } else {
            if (ai == null || ai.isBlank()) {
                ai = createFallbackQuery(msg, period);
            }

            // ✅ 이미 기간 조건이 있으면 주입 스킵, 없을 때만 주입
            if (hasOrdersDateRange(ai)) {
                normalized = ai;
            } else {
                normalized = SqlNormalizer.enforceDateRangeWhere(ai, true);
            }

            // ✅ 혹시 ORDER BY 뒤에 WHERE 1=1 …가 붙었으면 앞으로 재배치
            normalized = fixMisplacedDateWhere(normalized);

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
            if (wantCompare || yoyApplied || momApplied) {
                log.warn("Guard rejected trusted template (compare/YoY); executing as-is. err={}", e.toString());
                safe = normalized; // 템플릿 내부에 FETCH FIRST 포함
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
        rows = sanitize(rows);

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
        if (yoyApplied && rows != null && rows.size() >= 2) {
            double thisAmt = 0, prevAmt = 0; long thisQty = 0, prevQty = 0;
            for (Map<String,Object> r : rows) {
                String b = Optional.ofNullable(getStr(r, "BUCKET","bucket")).orElse("");
                long q   = Optional.ofNullable(getNum(r,"TOTAL_SOLD_QTY")).orElse(0).longValue();
                double a = Optional.ofNullable(getNum(r,"TOTAL_SALES_AMOUNT")).orElse(0).doubleValue();
                if ("THIS".equalsIgnoreCase(b)) { thisQty += q; thisAmt += a; }
                else if ("PREV".equalsIgnoreCase(b)) { prevQty += q; prevAmt += a; }
            }
            double amtDiff = thisAmt - prevAmt;
            double amtRate = (prevAmt != 0) ? (amtDiff / prevAmt * 100.0) : (thisAmt==0 ? 0 : 100.0);
            long   qtyDiff = thisQty - prevQty;
            double qtyRate = (prevQty != 0) ? (qtyDiff * 100.0 / prevQty) : (thisQty==0 ? 0 : 100.0);

            summary += String.format(
            		  " · 전년 동기 대비: 올해 %,d원 vs 전년 %,d원 · 증감 %+,d원 (%+.1f%%), " +
            		  "수량 %,d개 vs %,d개 · 증감 %+,d개 (%+.1f%%).",
            		  Math.round(thisAmt), Math.round(prevAmt), Math.round(amtDiff), amtRate,
            		  thisQty, prevQty, (thisQty - prevQty), qtyRate
            		);
        }
        if (wantCompare) {
            boolean refundMode = wantRefundCompare;
            boolean reviewMode = wantReviewCompare;

            boolean hasA = false, hasB = false;
            double aAmt = 0d, bAmt = 0d; long aQty = 0L, bQty = 0L;
            long aRefund = 0L, bRefund = 0L;

            // ✅ 가중평점 계산을 위한 누적 변수
            long aReviewsSum = 0L, bReviewsSum = 0L;
            double aRatingWeightedSum = 0d, bRatingWeightedSum = 0d;

            String aLabel = null, bLabel = null;

            if (rows != null) {
                for (Map<String,Object> r : rows) {
                    int matched = Optional.ofNullable(getNum(r, "MATCHED")).orElse(0).intValue();
                    double amt  = Optional.ofNullable(getNum(r, "TOTAL_SALES_AMOUNT","TOTALSALES")).orElse(0).doubleValue();
                    long   qty  = Optional.ofNullable(getNum(r, "TOTAL_SOLD_QTY","TOTALQUANTITY")).orElse(0).longValue();
                    long   rfd  = Optional.ofNullable(getNum(r, "TOTAL_REFUND_QTY")).orElse(0).longValue();
                    long   rvn  = Optional.ofNullable(getNum(r, "TOTAL_REVIEWS")).orElse(0).longValue();
                    double rat  = Optional.ofNullable(getNum(r, "AVG_RATING")).orElse(0).doubleValue();

                    // ✅ 라벨은 입력문구를 우선 사용 (여러 상품 매칭시 혼란 방지)
                    String label= Optional.ofNullable(getStr(r, "MATCHED_QUERY","PRODUCT_NAME")).orElse("");

                    if (matched == 1) {
                        hasA = true; aAmt += amt; aQty += qty; aRefund += rfd;
                        aReviewsSum += rvn; aRatingWeightedSum += rvn * rat;
                        if (aLabel==null) aLabel = label;
                    } else if (matched == 2) {
                        hasB = true; bAmt += amt; bQty += qty; bRefund += rfd;
                        bReviewsSum += rvn; bRatingWeightedSum += rvn * rat;
                        if (bLabel==null) bLabel = label;
                    }
                }
            }

            if (hasA && hasB) {
                if (refundMode) {
                    double aRate = (aQty > 0) ? ((double)aRefund / aQty) * 100.0 : 0.0;
                    double bRate = (bQty > 0) ? ((double)bRefund / bQty) * 100.0 : 0.0;
                    String better = (aRate <= bRate) ? aLabel : bLabel;
                    double diff   = Math.abs(aRate - bRate);
                    summary += " · 비교요약(환불률): \"" + better + "\"가 더 낮음 (차이 " + String.format("%.2f", diff) + "%).";
                } else if (reviewMode) {
                    // ✅ 가중평균 평점
                    double aRating = (aReviewsSum > 0) ? (aRatingWeightedSum / aReviewsSum) : 0.0;
                    double bRating = (bReviewsSum > 0) ? (bRatingWeightedSum / bReviewsSum) : 0.0;

                    if (Double.compare(aRating, bRating) != 0) {
                        String better = (aRating > bRating) ? aLabel : bLabel;
                        summary += " · 비교요약(리뷰/평점): \"" + better + "\" 평점 우위 (" +
                                   String.format("%.1f", Math.max(aRating, bRating)) + " vs " +
                                   String.format("%.1f", Math.min(aRating, bRating)) + "), " +
                                   "리뷰수 " + aReviewsSum + " vs " + bReviewsSum + ".";
                    } else {
                        String better = (aReviewsSum >= bReviewsSum) ? aLabel : bLabel;
                        summary += " · 비교요약(리뷰/평점): 평점 동률(" + String.format("%.1f", aRating) +
                                   "), \"" + better + "\"가 리뷰 수 우위 (" + aReviewsSum + " vs " + bReviewsSum + ").";
                    }
                } else {
                    String winner = (aAmt >= bAmt) ? aLabel : bLabel;
                    long diffAmt  = Math.round(Math.abs(aAmt - bAmt));
                    long diffQty  = Math.abs(aQty - bQty);
                    summary += " · 비교요약: \"" + winner + "\"가 매출 우위 (금액 차이 " + fmtAmt(diffAmt) + ", 수량 차이 " + fmtQty(diffQty) + ").";
                }
            } else if (hasA ^ hasB) {
                String only = hasA ? (aLabel == null ? "첫번째 항목" : aLabel)
                                   : (bLabel == null ? "두번째 항목" : bLabel);
                summary += " · 참고: \"" + only + "\"만 매칭되어 비교 대상이 없습니다.";
            }
        }
        if (momApplied && rows != null && rows.size() >= 2) {
            long thisOrders = 0, prevOrders = 0;
            double thisSales = 0d, prevSales = 0d;
            for (Map<String,Object> r : rows) {
                String b = Optional.ofNullable(getStr(r,"BUCKET","bucket")).orElse("");
                long oc   = Optional.ofNullable(getNum(r,"ORDER_COUNT")).orElse(0).longValue();
                double ts = Optional.ofNullable(getNum(r,"TOTAL_SALES")).orElse(0).doubleValue();
                if ("THIS".equalsIgnoreCase(b)) { thisOrders += oc; thisSales += ts; }
                else if ("PREV".equalsIgnoreCase(b)) { prevOrders += oc; prevSales += ts; }
            }
            long   diffOrders = thisOrders - prevOrders;
            double rateOrders = (prevOrders != 0) ? (diffOrders * 100.0 / prevOrders) : (thisOrders==0 ? 0 : 100.0);
            double diffSales  = thisSales - prevSales;
            double rateSales  = (prevSales != 0) ? (diffSales  * 100.0 / prevSales) : (thisSales==0 ? 0 : 100.0);

            summary += String.format(
                " · 전월 대비: 주문 %,d건 → %,d건 (%+d, %+.1f%%), 매출 %,d원 → %,d원 (%+,.0f원, %+.1f%%).",
                prevOrders, thisOrders, diffOrders, rateOrders,
                Math.round(prevSales), Math.round(thisSales), diffSales, rateSales
            );
        }

        log.info("AI 최종 SQL(원본): {}", ai);
        log.info("실행 SQL(safe): {}", safe);
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
        try { spec = chat.generateChartSpec(userMsg, SCHEMA_DOC); spec = coerceStatusDistribution(spec, userMsg);} catch (Exception ignore) {}

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

        String normalized = spec.sql().trim();
        if (!hasOrdersDateRange(normalized)) {
            normalized = SqlNormalizer.enforceDateRangeWhere(normalized, true);
        }
        normalized = fixMisplacedDateWhere(normalized);
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

        // ▶ 추가: USERS 쿼리/월별/주별/일별 의도 감지 + 기간 기반 버킷 개수 추정
        boolean usersSql     = up.contains(" FROM USERS ") || up.contains(" JOIN USERS ");
        boolean wantsMonthly = containsAny(userMsg, "월별","monthly","month");
        boolean wantsWeekly  = containsAny(userMsg, "주별","주간","주 단위","weekly");
        boolean wantsDaily   = containsAny(userMsg, "일별","daily","일자별");

        LocalDate fromL = (overrideStart != null ? overrideStart.toLocalDateTime() : period.start()).toLocalDate();
        LocalDate toL   = (overrideEnd   != null ? overrideEnd.toLocalDateTime()   : period.end()).minusDays(1).toLocalDate();

        int bucketGuess = 12;
        if (wantsMonthly) {
            bucketGuess = (int) java.time.temporal.ChronoUnit.MONTHS.between(fromL.withDayOfMonth(1), toL.withDayOfMonth(1)) + 1;
        } else if (wantsWeekly) {
            bucketGuess = (int) java.time.temporal.ChronoUnit.WEEKS.between(fromL, toL) + 1;
        } else if (wantsDaily) {
            bucketGuess = (int) java.time.temporal.ChronoUnit.DAYS.between(fromL, toL) + 1;
        }

        int limit = (spec.topN()!=null && spec.topN()>0 && spec.topN()<=50)
                ? spec.topN()
                : Math.min(Math.max(12, bucketGuess), 400);

        Map<String,Object> params = new HashMap<>();
        params.put("limit", limit);

        // ▶ 수정: USERS면 DATE로, 그 외 TIMESTAMP로 바인딩
        if (usersSql) {
            params.put("start", java.sql.Date.valueOf(fromL));
            params.put("end",   java.sql.Date.valueOf(toL.plusDays(1)));
        } else {
            params.put("start", overrideStart != null ? overrideStart : Timestamp.valueOf(period.start()));
            params.put("end",   overrideEnd   != null ? overrideEnd   : Timestamp.valueOf(period.end()));
        }

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
        rows = sanitize(rows);

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

        // ▶ 수정: 의도 기반 패딩 보강 (LLM이 포맷 토큰을 못 넣어도 월/주/일 패딩 수행)
        if (thisWeek) {
            LocalDate s = fromL;
            LocalDate e = toL;
            padDaily(labels, values, s, e);
        } else if (sig.contains("TRUNC(O.REGDATE,'IW')") || sig.contains("'IYYY-IW'") || wantsWeekly) {
            padWeekly(labels, values, fromL, toL, 1);
        } else if (sig.contains("TRUNC(O.REGDATE,'DD')") || sig.contains("'YYYY-MM-DD'") || wantsDaily) {
            padDaily(labels, values, fromL, toL);
        } else if (sig.contains("TRUNC(O.REGDATE,'MM')") || sig.contains("'YYYY-MM'") || wantsMonthly) {
            padMonthlyByPeriod(labels, values, fromL, toL);
        }

        heuristicNormalizeLabels(labels, values);

        String type = guessType(userMsg, spec.type());
        if (values == null || values.size() <= 1) type = "bar";
        boolean horizontal = containsAny(userMsg, "가로", "horizontal");

        String valueLabel = Optional.ofNullable(spec.valueColLabel()).filter(s -> !s.isBlank()).orElse("매출(원)");
        String format = (spec.format() != null && !spec.format().isBlank()) ? spec.format() : inferFormat(valueLabel);
        String title = Optional.ofNullable(spec.title()).filter(s -> !s.isBlank()).orElse("차트 · " + period.label());

        AiResult.ChartPayload chart = new AiResult.ChartPayload(
                labels, values, qtys, valueLabel, title, type, horizontal, format
        );


        String msg;
        if (rows.isEmpty()){
            msg = "%s 기준 조건에 맞는 데이터가 없습니다.".formatted(period.label());
        } else {
        	boolean looksStatusDistribution =
        		    safe.toUpperCase(Locale.ROOT).matches(".*\\bSTATUS\\s+AS\\s+LABEL\\b.*")
        		    || title.contains("상태"); // 기존 로직 + 정규식
            boolean wantsCount = wantsOrderCount(userMsg);

            if ("count".equalsIgnoreCase(format) && (looksStatusDistribution || wantsCount)) {
                long total = 0L;
                for (Number v : values) if (v != null) total += v.longValue();

                // 상태별 상세도 같이 표현
                StringBuilder byStatus = new StringBuilder();
                for (int i = 0; i < labels.size(); i++) {
                    if (i > 0) byStatus.append(", ");
                    String lab = labels.get(i) == null ? "-" : labels.get(i);
                    long val = (values.get(i) == null) ? 0L : values.get(i).longValue();
                    byStatus.append(lab).append(" ").append(String.format("%,d건", val));
                }
                msg = String.format("%s 기준 총 주문 %,d건. 상태별: %s.", period.label(), total, byStatus);
            } else {
                msg = "%s 기준 요청하신 차트를 표시했습니다.".formatted(period.label());
            }
        }

        return new AiResult(msg, safe, rows, chart);
    }
    
    private ChartSpec coerceStatusDistribution(ChartSpec spec, String userMsg) {
        if (spec == null || spec.sql() == null) return spec;
        String up = spec.sql().toUpperCase(Locale.ROOT);
        boolean wantsStatus = containsAny(userMsg, "상태별","상태 분포","분포","distribution","status");
        if (wantsStatus && (up.contains("STATUS") || up.contains("GROUP BY STATUS"))) {
            return new ChartSpec("""
                SELECT o.STATUS AS label,
                       COUNT(*)  AS value
                FROM ORDERS o
                WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                  AND o.REGDATE >= :start
                  AND o.REGDATE <  :end
                GROUP BY o.STATUS
                ORDER BY value DESC
            """, "주문 상태별 분포", "주문 건수", 6, "doughnut", "count");
        }
        return spec;
    }

    /* -------------------- 폴백 차트 스펙 -------------------- */
    private ChartSpec buildFallbackSpec(String userMsg) {
        String brand = extractBrandName(userMsg);
        boolean byBrand = brand != null && !brand.isBlank();
        boolean usersIntent = isUsersRelatedQuery(userMsg) || containsAny(userMsg, "가입", "신규", "회원");
        boolean byStatus = containsAny(userMsg, "상태별","상태 분포","분포","distribution","status");
        if (byStatus) {
            return new ChartSpec("""
                SELECT o.STATUS AS label,
                       COUNT(*)  AS value
                FROM ORDERS o
                WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                  AND o.REGDATE >= :start
                  AND o.REGDATE <  :end
                GROUP BY o.STATUS
                ORDER BY value DESC
            """, "주문 상태별 분포", "주문 건수", 6, "doughnut", "count");
        }
        if (usersIntent) {
            String title;
            String sql;
            if (containsAny(userMsg, "월별", "monthly", "month")) {
                sql = """
                    SELECT
                      TO_CHAR(TRUNC(u.REG,'MM'),'YYYY-MM') AS label,
                      COUNT(*)                             AS value
                    FROM USERS u
                    WHERE u.STATUS = 'active'
                      AND u.REG >= :start AND u.REG < :end
                    GROUP BY TRUNC(u.REG,'MM')
                    ORDER BY TRUNC(u.REG,'MM')
                """;
                title = "월별 신규 가입자";
            } else if (containsAny(userMsg, "주별","주간","주 단위")) {
                sql = """
                    SELECT
                      TO_CHAR(TRUNC(u.REG,'IW'),'IYYY-IW') AS label,
                      COUNT(*)                              AS value
                    FROM USERS u
                    WHERE u.STATUS = 'active'
                      AND u.REG >= :start AND u.REG < :end
                    GROUP BY TRUNC(u.REG,'IW')
                    ORDER BY TRUNC(u.REG,'IW')
                """;
                title = "주별 신규 가입자";
            } else {
                sql = """
                    SELECT
                      TO_CHAR(TRUNC(u.REG,'DD'),'YYYY-MM-DD') AS label,
                      COUNT(*)                                 AS value
                    FROM USERS u
                    WHERE u.STATUS = 'active'
                      AND u.REG >= :start AND u.REG < :end
                    GROUP BY TRUNC(u.REG,'DD')
                    ORDER BY TRUNC(u.REG,'DD')
                """;
                title = "일별 신규 가입자";
            }
            return new ChartSpec(sql, title, "가입자 수", 12, "line", "count");
        }

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

    private static void padMonthlyByPeriod(List<String> labels, List<Number> values, LocalDate from, LocalDate to) {
        Map<String, Number> baseline = new LinkedHashMap<>();
        LocalDate cur = from.withDayOfMonth(1);
        LocalDate end = to.withDayOfMonth(1);
        while (!cur.isAfter(end)) {
            baseline.put(String.format("%04d-%02d", cur.getYear(), cur.getMonthValue()), 0);
            cur = cur.plusMonths(1);
        }
        for (int i = 0; i < labels.size(); i++) {
            String ym = toYearMonth(labels.get(i));
            if (ym != null) baseline.put(ym, values.get(i));
        }
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
        int open=0, close=0;
        for (char c : sql.toCharArray()) { if (c=='(') open++; else if (c==')') close++; }
        if (open != close) {
            log.warn("Unbalanced parentheses detected. open={}, close={}", open, close);
            // return sql; // 그대로 두거나, 여기서 createFallbackQuery(...)로 대체
        }
        return sql;
    }

    private Map<String,Object> buildFlexibleParams(String sql, PeriodResolver.ResolvedPeriod period,
                                                   Principal principal, String userMsg) {
        Map<String,Object> params = new HashMap<>();
        if (sql.contains(":start")) {
            if (sql.toUpperCase().contains("FROM USERS") && sql.toUpperCase().contains(" REG")) {
                params.put("start", java.sql.Date.valueOf(period.start().toLocalDate()));
                params.put("end",   java.sql.Date.valueOf(period.end().toLocalDate()));
            } else {
                params.put("start", Timestamp.valueOf(period.start()));
                params.put("end",   Timestamp.valueOf(period.end()));
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
        if (sql.contains(":start_prev")) {
            params.put("start_prev", Timestamp.valueOf(period.start().minusYears(1)));
        }
        if (sql.contains(":end_prev")) {
            params.put("end_prev",   Timestamp.valueOf(period.end().minusYears(1)));
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
        if (isOrdersRelatedQuery(userMsg, null)
            && containsAny(userMsg, "상태별","상태 분포","분포","distribution","status")) {
            return """
                SELECT o.STATUS AS label,
                       COUNT(*)  AS value
                FROM ORDERS o
                WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
                  AND o.REGDATE >= :start 
                  AND o.REGDATE <  :end
                GROUP BY o.STATUS
                ORDER BY value DESC
            """;
        }
        // 기존 총합 폴백
        return """
            SELECT 
                SUM(od.CONFIRMQUANTITY * od.SELLPRICE) AS total_sales,
                COUNT(DISTINCT o.ORDERID)              AS order_count,
                SUM(od.CONFIRMQUANTITY)                AS total_quantity
            FROM ORDERS o
            JOIN ORDERDETAIL od ON o.ORDERID = od.ORDERID
            WHERE o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
              AND o.REGDATE >= :start 
              AND o.REGDATE < :end
        """;
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
