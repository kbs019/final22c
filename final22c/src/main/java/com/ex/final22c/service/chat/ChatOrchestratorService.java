package com.ex.final22c.service.chat;

import com.ex.final22c.controller.chat.AiResult;   // ★ AiResult 사용
import com.ex.final22c.service.ai.SqlExecService;
import com.ex.final22c.sql.SqlGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChatOrchestratorService {

    private final RouteService router;
    private final ChatService chat;
    private final SqlExecService sqlExec;

    // 🔎 스키마 요약 (DB 실제 컬럼명과 1:1로 맞춤)
    private static final String SCHEMA_DOC = """
          -- Oracle / 화이트리스트 (대문자 컬럼)
          -- USERS(USERNO PK, USERNAME, NAME, EMAIL, PHONE, GENDER, STATUS, ROLE, REG)
          -- ORDERS(ORDERID PK, USERNO FK->USERS.USERNO, TOTALAMOUNT, USEDPOINT, STATUS, REGDATE, DELIVERYSTATUS)
          -- ORDERDETAIL(ORDERDETAILID PK, ORDERID FK->ORDERS.ORDERID, ID(=PRODUCT.ID), QUANTITY, SELLPRICE, TOTALPRICE, CONFIRMQUANTITY)
          -- PAYMENT(PAYMENTID PK, ORDERID FK->ORDERS.ORDERID, AMOUNT, STATUS, TID, AID, APPROVEDAT, REG)
          -- PRODUCT(
          --   ID PK, NAME, IMGNAME, IMGPATH, PRICE, COUNT, DESCRIPTION, SINGLENOTE, TOPNOTE, MIDDLENOTE, BASENOTE,
          --   BRAND_BRANDNO FK->BRAND.BRANDNO,
          --   VOLUME_VOLUMENO FK->VOLUME.VOLUMENO,
          --   GRADE_GRADENO FK->GRADE.GRADENO,
          --   MAINNOTE_MAINNOTENO FK->MAINNOTE.MAINNOTENO,
          --   ISPICKED, STATUS, SELLPRICE, DISCOUNT, COSTPRICE
          -- )
          -- BRAND(BRANDNO PK, BRANDNAME, IMGNAME, IMGPATH)
          -- GRADE(GRADENO PK, GRADENAME)
          -- MAINNOTE(MAINNOTENO PK, MAINNOTENAME)
          -- VOLUME(VOLUMENO PK, VOLUMENAME)
          -- 조인: PRODUCT.BRAND_BRANDNO=BRAND.BRANDNO
          --     / PRODUCT.GRADE_GRADENO=GRADE.GRADENO
          --     / PRODUCT.MAINNOTE_MAINNOTENO=MAINNOTE.MAINNOTENO
          --     / PRODUCT.VOLUME_VOLUMENO=VOLUME.VOLUMENO
          -- 규칙: 단일 SELECT / 허용 테이블만 / 최대 300행
          --
          -- 📌 비즈니스 규칙 (매우 중요)
          -- 1) '판매량'(수량)은 ORDERDETAIL.CONFIRMQUANTITY 합계로 계산한다. (환불 시 차감 반영)
          -- 2) '매출'(금액)은 ORDERDETAIL.CONFIRMQUANTITY * ORDERDETAIL.SELLPRICE 합계로 계산한다.
          -- 3) 집계 대상 주문은 ORDERS.STATUS IN ('CONFIRMED','REFUNDED') 만 포함한다.
          -- 4) 매출/판매량 계산에는 PAYMENT 테이블을 사용하지 않는다.
          -- 5) 제품별 집계 시 ORDERDETAIL.ID = PRODUCT.ID 로 조인한다.
          """;

    // ▶ 자주 쓰는 네임드 파라미터 키 목록 (id 계열)
    private static final Set<String> ID_PARAMS = Set.of(
            ":id", ":productId", ":orderId", ":paymentId",
            ":brandNo", ":gradeNo", ":mainNoteNo", ":volumeNo"
    );

    // 숫자 추출 (예: "237번 제품 가격" → 237)
    private static final Pattern FIRST_INT = Pattern.compile("\\b\\d+\\b");

    // ===== 차트 의도 감지 (매출/판매 topN 차트/그래프) =====
    private static final Pattern INTENT_TOP_SALES_CHART = Pattern.compile(
            "(매출|판매).*(top\\s*\\d+|상위\\s*\\d+).*(차트|그래프)|chart\\.?js.*(매출|판매).*(top\\s*\\d+|상위\\s*\\d+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EXTRACT_TOPN = Pattern.compile("(?:top|상위)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    /** 오케스트레이션 진입점: 항상 AiResult 반환 */
    public AiResult handle(String userMsg, Principal principal){
        // 0) “매출 topN 차트” 의도면 전용 처리
        if (isAskTopSalesChart(userMsg)) {
            int topN = extractTopN(userMsg).orElse(5);
            return handleTopSalesChart(topN);
        }

        // 1) 일반 라우팅
        var route = router.route(userMsg);
        if (route.mode() == RouteService.Mode.CHAT) {
            return new AiResult(
                    chat.ask(userMsg),   // answer
                    null,                // sql
                    List.of(),           // rows
                    null                 // chart
            );
        }

        // 2) SQL 생성 (LLM)
        String sqlGen = chat.generateSql(userMsg, SCHEMA_DOC);

        // 3) 가드 + 행 제한
        String safe;
        try {
            safe = SqlGuard.ensureSelect(sqlGen);
            safe = SqlGuard.ensureLimit(safe, 300);
        } catch (Exception e){
            String msg = "생성된 SQL이 안전하지 않습니다: " + e.getMessage() + "\n"
                    + "해당 질문은 대화로 답변합니다.";
            return new AiResult(
                    msg + "\n" + chat.ask(userMsg),
                    null,
                    List.of(),
                    null
            );
        }

        // 4) 네임드 파라미터 바인딩
        var params = new HashMap<String,Object>();

        // 4-1) 로그인 필요한 경우
        if (safe.contains(":userNo")) {
            // TODO: principal → userNo 조회 로직으로 교체
            Long userNo = (principal == null) ? null : 0L;
            if (userNo == null) return new AiResult("로그인이 필요한 요청이에요.", null, List.of(), null);
            params.put("userNo", userNo);
        }

        // 4-2) limit
        if (safe.contains(":limit")) {
            params.put("limit", 300);
        }

        // 4-3) id 계열 자동 바인딩 (질문에서 첫 숫자 사용)
        if (containsAnyNamedParam(safe, ID_PARAMS)) {
            Long n = extractFirstNumber(userMsg);
            if (n == null) {
                return new AiResult("식별자(ID)가 필요해요. 예: \"제품 237 가격\"", null, List.of(), null);
            }
            for (String key : ID_PARAMS) {
                if (safe.contains(key)) {
                    params.put(key.substring(1), n); // ':id' → 'id'
                }
            }
        }

        // 5) 실행
        List<Map<String,Object>> rows = params.isEmpty()
                ? sqlExec.runSelect(safe)
                : sqlExec.runSelectNamed(safe, params);

     // 6) 결과 요약 (✅ tableMd 전달 + 폴백 메시지)
        String tableMd = sqlExec.formatAsMarkdownTable(rows);
        String summary;

        if (rows == null || rows.isEmpty()) {
            summary = "조건에 맞는 데이터가 없습니다.";
        } else {
            try {
                summary = chat.summarize(userMsg, safe, tableMd); // ← 반드시 tableMd 전달
            } catch (Exception ignore) {
                summary = null;
            }

            // 요약이 비었거나 'null/없다' 같은 오판 문구면 안전한 폴백 제공
            if (summary == null ||
                summary.toLowerCase(Locale.ROOT).contains("null") ||
                summary.contains("존재하지 않")) {

                Map<String,Object> r = rows.get(0);
                String name  = getStr(r, "PRODUCTNAME","NAME");
                String brand = getStr(r, "BRANDNAME");
                Number qty   = getNum(r, "TOTALQUANTITY","TOTAL_SOLD_QUANTITY");
                Number sales = getNum(r, "TOTALSALES","TOTAL_SALES_AMOUNT");

                StringBuilder sb = new StringBuilder();
                sb.append("조회 결과 ").append(rows.size()).append("행을 찾았습니다.");
                if (name != null) {
                    sb.append(" 1위: ").append(name);
                    if (brand != null) sb.append(" (").append(brand).append(")");
                    if (qty != null)   sb.append(", 판매수량 ").append(qty);
                    if (sales != null) sb.append(", 매출 ").append(sales).append("원");
                    sb.append(".");
                }
                summary = sb.toString();
            }
        }

        // 7) 응답 (차트 없음)
        return new AiResult(summary, safe, rows, null);
    }
    /* ===================== 차트 전용 처리 ===================== */

    private boolean isAskTopSalesChart(String msg){
        if (msg == null) return false;
        String t = msg.replaceAll("\\s+","").toLowerCase();
        return INTENT_TOP_SALES_CHART.matcher(t).find();
    }

    private Optional<Integer> extractTopN(String msg){
        if (msg == null) return Optional.empty();
        Matcher m = EXTRACT_TOPN.matcher(msg);
        return m.find() ? Optional.of(Integer.parseInt(m.group(1))) : Optional.empty();
    }

    private AiResult handleTopSalesChart(int topN) {
        if (topN <= 0 || topN > 50) topN = 5;

        // CONFIRMQUANTITY는 환불 시 차감 → 항상 "순판매수량"
        String sql = """
            SELECT productName, brandName, totalSales, totalQuantity FROM (
              SELECT 
                p.NAME AS productName,
                b.BRANDNAME AS brandName,
                SUM(od.CONFIRMQUANTITY * od.SELLPRICE) AS totalSales,
                SUM(od.CONFIRMQUANTITY) AS totalQuantity
              FROM ORDERDETAIL od
              JOIN ORDERS  o ON od.ORDERID = o.ORDERID
              JOIN PRODUCT p ON od.ID = p.ID
              JOIN BRAND   b ON p.BRAND_BRANDNO = b.BRANDNO
              WHERE o.STATUS IN ('CONFIRMED', 'REFUNDED')
              GROUP BY p.NAME, b.BRANDNAME
              ORDER BY totalSales DESC
            )
            WHERE ROWNUM <= :limit
            """;

        // 보안: SELECT만 허용
        String safe = SqlGuard.ensureSelect(sql);

        // 실행
        Map<String,Object> params = Map.of("limit", topN);
        List<Map<String,Object>> rows = sqlExec.runSelectNamed(safe, params);

        // 차트 페이로드
        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        List<Number> qtys   = new ArrayList<>();

        for (Map<String,Object> r : rows) {
            String pn = getStr(r, "productname", "PRODUCTNAME");
            String bn = getStr(r, "brandname",   "BRANDNAME");
            Number ts = getNum(r, "totalsales",  "TOTALSALES");
            Number tq = getNum(r, "totalquantity","TOTALQUANTITY");

            labels.add(String.format("%s (%s)", pn, (bn != null ? bn : "-")));
            values.add(ts != null ? ts : 0);
            qtys.add(tq != null ? tq : 0);
        }

        AiResult.ChartPayload chart = new AiResult.ChartPayload(
                labels,
                values,
                qtys,
                "순매출(원)",
                "매출 Top " + labels.size() + " (환불 반영)"
        );

        String msg = "요청하신 매출 Top " + topN + " 차트를 표시했습니다. "
                + "(기준: CONFIRMQUANTITY×SELLPRICE, 환불 반영)";

        return new AiResult(msg, safe, rows, chart);
    }

    /* ===================== helpers ===================== */

    private static boolean containsAnyNamedParam(String sql, Set<String> keys) {
        for (String k : keys) {
            if (sql.contains(k)) return true;
        }
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
}
