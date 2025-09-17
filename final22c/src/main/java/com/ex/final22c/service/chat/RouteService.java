package com.ex.final22c.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RouteService {

    // LLM 호출을 재사용하기 위한 ChatService (실제 모델 호출 담당)
    private final ChatService chat;

    // JSON 파싱을 위한 Jackson ObjectMapper
    private final ObjectMapper om = new ObjectMapper();

    // 라우팅 모드: DB 질의(SQL) / 일반 대화(CHAT)
    public enum Mode { SQL, CHAT }

    // 라우팅 결과를 담는 record (Java 16+ 문법)
    // -> 어떤 모드로 분류됐는지(mode), 그리고 이유(reason)
    public record RouteResult(Mode mode, String reason){}

    /**
     * 사용자가 입력한 메시지를 기반으로
     * - DB로 처리 가능한 SQL 요청인지
     * - 일반 대화(LLM 답변)인지
     * 를 판별하는 메서드
     */
    public RouteResult route(String userMsg){
        // 시스템 프롬프트: 라우터 역할 정의
        // 반드시 {"mode":"...","reason":"..."} JSON으로만 답하도록 강제
        String sys = """
            You are a router. Return ONLY JSON: {"mode":"SQL"|"CHAT","reason":"..."}.
            Rules:
            - mode=SQL: Only if the answer can come from DB tables (USERS, ORDERS, ORDER_DETAIL, ORDERDETAIL, PRODUCT, PAYMENT).
            - mode=CHAT: opinions, jokes, personal questions, or anything not answerable from DB.
        """;

        // 사용자 메시지와 합쳐서 프롬프트 완성
        String prompt = sys + "\nUser: " + userMsg;

        // ChatService를 통해 LLM 호출 → 결과는 문자열(raw)
        String raw = chat.ask(prompt);

        try {
            // 결과 문자열을 JSON으로 파싱
            var node = om.readTree(raw.trim());

            // mode 값(SQL/CHAT)을 읽어 enum으로 매핑
            var mode = "SQL".equalsIgnoreCase(node.path("mode").asText()) 
                       ? Mode.SQL : Mode.CHAT;

            // 최종 결과 반환 (mode + reason)
            return new RouteResult(mode, node.path("reason").asText());

        } catch (Exception e){
            // 파싱 실패 시 안전하게 CHAT으로 처리
            return new RouteResult(Mode.CHAT, "parse_failed");
        }
    }
}
