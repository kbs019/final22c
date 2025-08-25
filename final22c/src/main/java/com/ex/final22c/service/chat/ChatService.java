package com.ex.final22c.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

//ChatService.java
@Service
@Slf4j
public class ChatService {
    private final WebClient aiWebClient;
    
    public ChatService(@Qualifier("aiWebClient") WebClient aiWebClient) {
        this.aiWebClient = aiWebClient;
    }

    @Value("${deepseek.api.model:deepseek-chat}") // ← DeepSeek native면 이게 맞음
    private String model;

    @Value("${deepseek.api.path:/chat/completions}")
    private String path;

    private Map call(Map<String, Object> body) {
        try {
            return aiWebClient.post().uri(path)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(s -> s.value() >= 400, r -> r.bodyToMono(String.class)
                            .map(msg -> new RuntimeException("DeepSeek HTTP " + r.statusCode() + ": " + msg)))
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.error("[AI CALL FAIL] {}", e.toString(), e);
            // 실패 원인을 그대로 문자열로 돌려주기 위해 Map으로 감쌈
            return Map.of("error", e.getMessage());
        }
    }

    public String ask(String userMsg) {
        var body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "간결하게 한국어로 답하세요."),
                        Map.of("role", "user", "content", userMsg)),
                "temperature", 0.3);
        var resp = call(body);
        return extract(resp);
    }

    public String generateSql(String question, String schemaDoc) {
    	var sys = """
    			너는 Oracle SQL 생성기다.
    			- 오직 단일 SELECT 한 개만. DUAL/DML/DDL 금지.
    			- 허용 테이블: USERS, ORDERS, ORDERDETAIL, PAYMENT, PRODUCT.
    			- 텍스트 비교는 항상 대소문자 무시: UPPER(컬럼) = UPPER(:v) 또는 REGEXP_LIKE(...,'i').
    			- ✅ '성비/남녀비율' 질의 등 GENDER는 동의어를 코드로 정규화해서 집계:
    			  예) 남/남자/남성/M/Male → 'M', 여/여자/여성/F/Female → 'F'
    			  집계 예시:
    			    SUM(CASE WHEN REGEXP_LIKE(GENDER,'^(M|MALE|남|남자|남성)$','i') THEN 1 ELSE 0 END) AS MALE_COUNT
    			- 바인딩 변수 사용 (:userNo, :limit 등). 값 인라인 금지.
    			- 최대 50행 제한. 반드시 ```sql ... ``` 코드블록 한 개만 출력.
    			""";
        var user = "스키마 요약:\n" + schemaDoc + "\n\n질문:\n" + question + "\n\n반드시 코드블록으로 SQL만 출력.";
        var body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", sys),
                        Map.of("role", "user", "content", user)),
                "temperature", 0.1);
        var resp = call(body);
        return extract(resp);
    }

    public String summarize(String question, String sql, String table) {
        var body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "한 줄로 아주 간단히 한국어 요약."),
                        Map.of("role", "user", "content", "질문: " + question + "\nSQL:\n" + sql + "\n결과표:\n" + table)),
                "temperature", 0.3);
        var resp = call(body);
        return extract(resp);
    }

    @SuppressWarnings("unchecked")
    private String extract(Map resp) {
        if (resp == null)
            return "(응답 없음)";
        if (resp.containsKey("error")) {
            return "(API 오류) " + resp.get("error");
        }
        try {
            var choices = (List<Map>) resp.get("choices");
            var msg = (Map) choices.get(0).get("message");
            String content = String.valueOf(msg.getOrDefault("content", ""));
            if (content.isBlank())
                return "(빈 응답) raw=" + resp;
            return content;
        } catch (Exception e) {
            return "(파싱 실패) raw=" + resp;
        }
    }
}
