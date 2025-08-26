// src/main/java/com/ex/final22c/service/chat/ChatOrchestratorService.java
package com.ex.final22c.service.chat;

import com.ex.final22c.controller.chat.AiResult;
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

    private static final Set<String> ID_PARAMS = Set.of(
            ":id", ":productId", ":orderId", ":paymentId",
            ":brandNo", ":gradeNo", ":mainNoteNo", ":volumeNo"
    );
    private static final Pattern FIRST_INT = Pattern.compile("\\b\\d+\\b");
    private static final Pattern INTENT_ANY_CHART =
            Pattern.compile("(차트|그래프|chart)", Pattern.CASE_INSENSITIVE);

    public AiResult handle(String userMsg, Principal principal){
        if (isChartIntent(userMsg)) {
            try {
                return handleChartGeneric(userMsg, principal);
            } catch (Exception ignore) {
                // 차트 실패 시 일반 경로 시도
            }
        }

        var route = router.route(userMsg);
        if (route.mode() == RouteService.Mode.CHAT) {
            return new AiResult(chat.ask(userMsg), null, List.of(), null);
        }

        String sqlGen = chat.generateSql(userMsg, SCHEMA_DOC);

        String safe;
        try {
            safe = SqlGuard.ensureSelect(sqlGen);
            safe = SqlGuard.ensureLimit(safe, 300);
        } catch (Exception e){
            String msg = "생성된 SQL이 안전하지 않습니다: " + e.getMessage() + "\n"
                    + "해당 질문은 대화로 답변합니다.";
            return new AiResult(msg + "\n" + chat.ask(userMsg), null, List.of(), null);
        }

        var params = new HashMap<String,Object>();
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

        List<Map<String,Object>> rows = params.isEmpty()
                ? sqlExec.runSelect(safe)
                : sqlExec.runSelectNamed(safe, params);

        String tableMd = sqlExec.formatAsMarkdownTable(rows);
        String summary;
        if (rows == null || rows.isEmpty()) {
            summary = "조건에 맞는 데이터가 없습니다.";
        } else {
            try { summary = chat.summarize(userMsg, safe, tableMd); }
            catch (Exception ignore) { summary = null; }
            if (summary == null ||
                summary.toLowerCase(Locale.ROOT).contains("null") ||
                summary.contains("존재하지 않")) {

                Map<String,Object> r = rows.get(0);
                String name  = getStr(r, "PRODUCTNAME","NAME","LABEL");
                String brand = getStr(r, "BRANDNAME");
                Number qty   = getNum(r, "TOTALQUANTITY","TOTAL_SOLD_QUANTITY","QUANTITY");
                Number sales = getNum(r, "TOTALSALES","TOTAL_SALES_AMOUNT","VALUE");

                StringBuilder sb = new StringBuilder();
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

    private AiResult handleChartGeneric(String userMsg, Principal principal) {
        ChartSpec spec = chat.generateChartSpec(userMsg, SCHEMA_DOC);
        if (spec == null || spec.sql() == null || spec.sql().isBlank()) {
            return new AiResult("차트 스펙 생성에 실패했어요. 요청을 더 구체적으로 적어주세요.", null, List.of(), null);
        }

        String safe = SqlGuard.ensureSelect(spec.sql().trim());

        // 위치 바인드 → 네임드로 교정
        boolean hasPositional = safe.contains("?") || safe.matches(".*:\\d+.*");
        if (hasPositional) safe = safe.replace("?", ":limit").replaceAll(":(\\d+)", ":limit");

        // limit 보장
        if (!safe.contains(":limit")) {
            safe = "SELECT label, value, quantity FROM (" + safe + ") WHERE ROWNUM <= :limit";
        }

        int limit = (spec.topN()!=null && spec.topN()>0 && spec.topN()<=50) ? spec.topN() : 5;
        Map<String,Object> params = new HashMap<>();
        params.put("limit", limit);

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

        // 타입/가로/포맷 추론
        String type = guessType(userMsg, spec.type());
        boolean horizontal = containsAny(userMsg, "가로", "horizontal");
        String format = (spec.format()!=null && !spec.format().isBlank())
                ? spec.format()
                : inferFormat(spec.valueColLabel());

        AiResult.ChartPayload chart = new AiResult.ChartPayload(
                labels,
                values,
                qtys,
                (spec.valueColLabel()==null||spec.valueColLabel().isBlank()) ? "값" : spec.valueColLabel(),
                (spec.title()==null||spec.title().isBlank()) ? "차트" : spec.title(),
                type,
                horizontal,
                format
        );

        String msg = rows.isEmpty() ? "조건에 맞는 데이터가 없습니다." : "요청하신 차트를 표시했습니다.";
        return new AiResult(msg, safe, rows, chart);
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
