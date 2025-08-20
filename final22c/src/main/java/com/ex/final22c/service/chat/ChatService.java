package com.ex.final22c.service.chat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    @Value("${deepseek.api.base-url}")
    private String baseUrl;

    @Value("${deepseek.api.path}")
    private String path;

    @Value("${deepseek.api.key:}")
    private String apiKey;

    @Value("${deepseek.api.model:deepseek-chat}")
    private String model;

    private final HttpClient http = HttpClient.newHttpClient();

    public String ask(String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            // 키가 없을 때는 친절한 에러 메시지
            return "서버에 DeepSeek API Key가 설정되지 않았습니다. 관리자에게 문의하세요.";
        }

        // OpenAI 호환 Chat Completions 요청 바디 (필요시 조정)
        String body = """
        {
          "model": "%s",
          "messages": [
            {"role": "system", "content": "You are a helpful assistant for a shopping site."},
            {"role": "user", "content": %s}
          ],
          "stream": false,
          "temperature": 0.7
        }
        """.formatted(model, jsonString(userMessage));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                return "[DeepSeek 오류] HTTP " + res.statusCode() + " - " + res.body();
            }
            // 응답 JSON에서 assistant 메시지 텍스트만 안전하게 뽑기
            return extractAssistantText(res.body());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[DeepSeek 호출 예외] " + e.getMessage();
        }
    }

    // 아주 간단한 JSON 이스케이프 (따옴표만 처리)
    private static String jsonString(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * 응답 예(일반형):
     * {
     *   "id":"...","object":"chat.completion",
     *   "choices":[{"index":0,"message":{"role":"assistant","content":"..."},"finish_reason":"stop"}],
     *   "usage":{...}
     * }
     *
     * 파서 없이 단순 추출 (프로덕션에선 Jackson/Gson 권장)
     */
    private static String extractAssistantText(String json) {
        // "message":{"role":"assistant","content":"..."} 에서 content 값만 단순 추출
        String key = "\"content\":";
        int i = json.indexOf(key);
        if (i < 0) return "[파싱 실패] " + json;
        int start = json.indexOf('"', i + key.length());
        if (start < 0) return "[파싱 실패] " + json;
        int end = findStringEnd(json, start + 1);
        if (end < 0) return "[파싱 실패] " + json;
        String raw = json.substring(start + 1, end);
        return raw.replace("\\n", "\n").replace("\\\"", "\"");
    }

    private static int findStringEnd(String s, int from) {
        boolean esc = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') return i;
        }
        return -1;
    }
}
