package com.ex.final22c.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RouteService {
    private final ChatService chat; // LLM 호출 재사용
    private final ObjectMapper om = new ObjectMapper();

    public enum Mode { SQL, CHAT }
    public record RouteResult(Mode mode, String reason){}

    public RouteResult route(String userMsg){
        String sys = """
            You are a router. Return ONLY JSON: {"mode":"SQL"|"CHAT","reason":"..."}.
            Rules:
            - mode=SQL: Only if the answer can come from DB tables (USERS, ORDERS, ORDER_DETAIL, ORDERDETAIL, PRODUCT, PAYMENT).
            - mode=CHAT: opinions, jokes, personal questions, or anything not answerable from DB.
        """;
        String prompt = sys + "\nUser: " + userMsg;
        String raw = chat.ask(prompt); // ask는 문자열만 주니까 그대로 사용

        try {
            var node = om.readTree(raw.trim());
            var mode = "SQL".equalsIgnoreCase(node.path("mode").asText()) ? Mode.SQL : Mode.CHAT;
            return new RouteResult(mode, node.path("reason").asText());
        } catch (Exception e){
            // 파싱 실패 시 보수적으로 CHAT
            return new RouteResult(Mode.CHAT, "parse_failed");
        }
    }
}
