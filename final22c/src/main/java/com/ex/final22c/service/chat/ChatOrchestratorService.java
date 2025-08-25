package com.ex.final22c.service.chat;

import com.ex.final22c.service.ai.SqlExecService;
import com.ex.final22c.sql.SqlGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatOrchestratorService {
    private final RouteService router;
    private final ChatService chat;
    private final SqlExecService sqlExec;

    // TODO: 실제 스키마 introspection으로 대체 가능. 우선 고정 요약.
    private static final String SCHEMA_DOC = """
        -- Oracle / 화이트리스트 (대문자 컬럼)
		-- USERS(USERNO PK, USERNAME, NAME, EMAIL, PHONE, GENDER, STATUS, ROLE, REG)
		--   GENDER codes: 'M'(남/남자/남성/Male), 'F'(여/여자/여성/Female)
		--   STATUS codes: 'ACTIVE','INACTIVE' (비교는 대소문자 무시)
		-- ORDERS(ORDERID PK, USERNO FK->USERS.USERNO, TOTALAMOUNT, USEDPOINT, STATUS, REGDATE, DELIVERYSTATUS)
		-- ORDERDETAIL(ORDERDETAILID PK, ORDERID FK->ORDERS.ORDERID, ID, QUANTITY, SELLPRICE, TOTALPRICE, CONFIRMQUANTITY)
		-- PAYMENT(PAYMENTID PK, ORDERID FK->ORDERS.ORDERID, AMOUNT, STATUS, TID, AID, APPROVEDAT, REG)
		-- 조인: ORDERS.USERNO=USERS.USERNO / ORDERDETAIL.ORDERID=ORDERS.ORDERID / PAYMENT.ORDERID=ORDERS.ORDERID
		-- 규칙: 단일 SELECT / 허용 테이블만 / 최대 50행
		""";

    public record ChatAnswer(String message, String sql, String tableMd, List<Map<String,Object>> rows){}

    public ChatAnswer handle(String userMsg, Principal principal){
        var route = router.route(userMsg);
        if (route.mode() == RouteService.Mode.CHAT) {
            return new ChatAnswer(chat.ask(userMsg), null, null, null);
        }

        // === SQL 모드 ===
        String sqlGen = chat.generateSql(userMsg, SCHEMA_DOC);
        String safe;
        try {
            // 가드 + 50행 안전 제한
        	safe = SqlGuard.ensureSelect(sqlGen);
            safe = SqlGuard.ensureLimit(safe, 50);
        } catch (Exception e){
            // 가드에 걸리면 일반 챗으로 폴백
            String msg = "생성된 SQL이 안전하지 않습니다: " + e.getMessage() + "\n"
                       + "해당 질문은 대화로 답변합니다.";
            return new ChatAnswer(msg + "\n" + chat.ask(userMsg), null, null, null);
        }

        // 바인딩 필요 시: :userNo, :limit 처리
        var params = new HashMap<String,Object>();
        if (safe.contains(":userNo")) {
            Long userNo = (principal == null) ? null : 0L; // 필요 시 실제 userNo 로직 넣기
            if (userNo == null) {
                return new ChatAnswer("로그인이 필요한 요청이에요.", null, null, null);
            }
            params.put("userNo", userNo);
        }
        if (safe.contains(":limit")) params.put("limit", 50);

        List<Map<String,Object>> rows = params.isEmpty()
                ? sqlExec.runSelect(safe)
                : sqlExec.runSelectNamed(safe, params);

        String tableMd = sqlExec.formatAsMarkdownTable(rows);
        String summary = chat.summarize(userMsg, safe, tableMd);

        return new ChatAnswer(summary, safe, tableMd, rows);
    }
}
