package com.ex.final22c.service.chat;

import com.ex.final22c.service.ai.SqlExecService;
import com.ex.final22c.sql.SqlGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
      """;

    // ✅ 응답 DTO
    public record ChatAnswer(String message, String sql, String tableMd, List<Map<String,Object>> rows){}

    // ▶ 자주 쓰는 네임드 파라미터 키 목록 (id 계열)
    private static final Set<String> ID_PARAMS = Set.of(
        ":id", ":productId", ":orderId", ":paymentId",
        ":brandNo", ":gradeNo", ":mainNoteNo", ":volumeNo"
    );

    // 숫자 추출 (예: "237번 제품 가격" → 237)
    private static final Pattern FIRST_INT = Pattern.compile("\\b\\d+\\b");

    public ChatAnswer handle(String userMsg, Principal principal){
        var route = router.route(userMsg);
        if (route.mode() == RouteService.Mode.CHAT) {
            return new ChatAnswer(chat.ask(userMsg), null, null, null);
        }

        // 1) SQL 생성
        String sqlGen = chat.generateSql(userMsg, SCHEMA_DOC);

        // 2) 가드 + 행 제한
        String safe;
        try {
            safe = SqlGuard.ensureSelect(sqlGen);
            // (SqlGuard에 rejectPositionalParams를 구현했다면 여기서 호출해도 좋음)
            // safe = SqlGuard.rejectPositionalParams(safe);
            safe = SqlGuard.ensureLimit(safe, 300);
        } catch (Exception e){
            String msg = "생성된 SQL이 안전하지 않습니다: " + e.getMessage() + "\n"
                       + "해당 질문은 대화로 답변합니다.";
            return new ChatAnswer(msg + "\n" + chat.ask(userMsg), null, null, null);
        }

        // 3) 네임드 파라미터 바인딩
        var params = new HashMap<String,Object>();

        // 3-1) 로그인 필요한 경우
        if (safe.contains(":userNo")) {
            // TODO: principal → userNo 조회 로직으로 교체
            Long userNo = (principal == null) ? null : 0L;
            if (userNo == null) return new ChatAnswer("로그인이 필요한 요청이에요.", null, null, null);
            params.put("userNo", userNo);
        }

        // 3-2) limit
        if (safe.contains(":limit")) {
            params.put("limit", 300);
        }

        // 3-3) id 계열 자동 바인딩 (질문에서 첫 숫자 사용)
        if (containsAnyNamedParam(safe, ID_PARAMS)) {
            Long n = extractFirstNumber(userMsg);
            if (n == null) {
                return new ChatAnswer("식별자(ID)가 필요해요. 예: \"제품 237 가격\"", null, null, null);
            }
            // 어떤 키가 실제로 쓰였는지 확인 후 일괄 바인딩
            for (String key : ID_PARAMS) {
                if (safe.contains(key)) {
                    params.put(key.substring(1), n); // ':id' → 'id'
                }
            }
        }

        // 4) 실행
        List<Map<String,Object>> rows = params.isEmpty()
                ? sqlExec.runSelect(safe)
                : sqlExec.runSelectNamed(safe, params);

        // 5) 결과 변환/요약
        String tableMd = sqlExec.formatAsMarkdownTable(rows);
        String summary = chat.summarize(userMsg, safe, tableMd);

        // 6) 응답
        return new ChatAnswer(summary, safe, tableMd, rows);
    }

    /* ===== helpers ===== */

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
}
