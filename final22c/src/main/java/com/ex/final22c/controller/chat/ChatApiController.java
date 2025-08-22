package com.ex.final22c.controller.chat;

import com.ex.final22c.service.ai.SqlExecService;
import com.ex.final22c.service.chat.ChatService;
import com.ex.final22c.sql.SqlGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatApiController {

    private final ChatService chatService;   // LLM 호출
    private final SqlExecService sqlExec;    // SQL 실행

    // 🔴 네 엔티티에 맞춘 "스키마 요약" (그냥 이대로 쓰면 됨)
    private static final String SCHEMA_DOC = """
        -- Oracle / 화이트리스트 스키마 요약 (대문자 기준)
        -- USERS(USERNO PK, USERNAME, NAME, EMAIL, PHONE, STATUS, ROLE, REG)
        -- ORDERS(ORDERID PK, USERNO FK->USERS.USERNO, TOTALAMOUNT, USEDPOINT, STATUS, REGDATE, DELIVERYSTATUS)
        -- ORDERDETAIL(ORDERDETAILID PK, ORDERID FK->ORDERS.ORDERID, ID(=PRODUCT.ID), QUANTITY, SELLPRICE, TOTALPRICE, CONFIRMQUANTITY)
        -- PAYMENT(PAYMENTID PK, ORDERID FK->ORDERS.ORDERID, AMOUNT, STATUS, TID, AID, APPROVEDAT, REG)
        -- 조인: ORDERS.USERNO=USERS.USERNO / ORDERDETAIL.ORDERID=ORDERS.ORDERID / PAYMENT.ORDERID=ORDERS.ORDERID
        -- 자주 쓰는 조건: ORDERS.STATUS='PAID'
        -- 규칙: 단일 SELECT / 허용 테이블만 / 최대 50행
        """;

    private static final Pattern P_SQL_BLOCK =
            Pattern.compile("```sql\\s*(.+?)```", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ChatResponse chat(@RequestBody ChatRequest req) {
        String question = req.message() == null ? "" : req.message().trim();

        // 1) SQL 생성(코드블록 형태로만 오게 강제)
        String raw = chatService.generateSql(question, SCHEMA_DOC);
        String sql = extractSql(raw);
        if (sql == null || sql.isBlank()) {
            // 생성 실패하면 LLM 일반 답변으로 폴백(데모)
            String fallback = "SQL을 생성하지 못했어요 😅\n\n" + chatService.ask(question);
            return new ChatResponse(fallback);
        }

        // 2) 간단 가드 + 최대 50행 보장
        try {
            sql = SqlGuard.ensureSelectOnly(sql);
            sql = SqlGuard.ensureLimit(sql, 50);
        } catch (IllegalArgumentException e) {
            return new ChatResponse("생성된 SQL이 안전하지 않습니다:\n" + e.getMessage() + "\n```sql\n" + sql + "\n```");
        }

        // 3) 실행 + 표 포맷
        List<Map<String,Object>> rows = sqlExec.runSelect(sql);
        String table = sqlExec.formatAsMarkdownTable(rows);

        // 4) (선택) 결과 요약
        String summary = chatService.summarize(question, sql, table);

        // 5) 합쳐서 응답
        String answer = """
            💡 생성된 SQL:
            ```sql
            %s
            ```

            📊 결과(최대 50행):
            %s

            📝 요약: %s
            """.formatted(sql, table, summary);

        return new ChatResponse(answer);
    }

    private String extractSql(String content) {
        if (content == null) return null;
        Matcher m = P_SQL_BLOCK.matcher(content);
        return m.find() ? m.group(1).trim() : null;
    }

    /* DTO */
    public record ChatRequest(String message) {}
    public record ChatResponse(String answer) {}
}
