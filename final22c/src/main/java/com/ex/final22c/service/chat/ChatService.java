package com.ex.final22c.service.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class ChatService {

    private final WebClient aiWebClient;

    public ChatService(@Qualifier("aiWebClient") WebClient aiWebClient) {
        this.aiWebClient = aiWebClient;
    }

    @Value("${deepseek.api.model:deepseek-chat}")
    private String model;

    @Value("${deepseek.api.path:/chat/completions}")
    private String path;

    // ✅ JSON 파싱용 (추가)
    private final ObjectMapper om = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /* -------------------- 공통 호출 -------------------- */
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
            return Map.of("error", e.getMessage());
        }
    }

    /* -------------------- 기존 기능 -------------------- */
    public String ask(String userMsg) {
        var body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "간결하게 한국어로 답하세요."),
                        Map.of("role", "user", "content", userMsg)
                ),
                "temperature", 0.3
        );
        var resp = call(body);
        return extract(resp);
    }

    public String generateSql(String question, String schemaDoc) {
        var sys = """
                너는 Oracle SQL 생성기다.
                - 오직 단일 SELECT 한 개만. DUAL/DML/DDL 금지.
                - 허용 테이블: USERS, ORDERS, ORDERDETAIL, PAYMENT, PRODUCT, BRAND, GRADE, MAINNOTE, VOLUME.
                - 텍스트 비교는 항상 대소문자 무시: UPPER(컬럼) = UPPER(:v) 또는 REGEXP_LIKE(...,'i').
                - ✅ '성비/남녀비율' 질의 등 GENDER는 동의어를 코드로 정규화해서 집계:
                  예) 남/남자/남성/M/Male → 'M', 여/여자/여성/F/Female → 'F'
                  집계 예시:
                    SUM(CASE WHEN REGEXP_LIKE(GENDER,'^(M|MALE|남|남자|남성)$','i') THEN 1 ELSE 0 END) AS MALE_COUNT
                - 바인딩 변수 사용 (:userNo, :limit 등). 값 인라인 금지.
                - 최대 300행 제한. 반드시 ```sql ... ``` 코드블록 한 개만 출력.
                """;
        var user = "스키마 요약:\n" + schemaDoc + "\n\n질문:\n" + question + "\n\n반드시 코드블록으로 SQL만 출력.";
        var body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", sys),
                        Map.of("role", "user", "content", user)
                ),
                "temperature", 0.1
        );
        var resp = call(body);
        return extract(resp);
    }

    public String summarize(String question, String sql, String table) {
        var body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "한 줄로 아주 간단히 한국어 요약."),
                        Map.of("role", "user", "content", "질문: " + question + "\nSQL:\n" + sql + "\n결과표:\n" + table)
                ),
                "temperature", 0.3
        );
        var resp = call(body);
        return extract(resp);
    }

    /* -------------------- 범용 차트 스펙 생성 (추가) -------------------- */
    /**
     * 사용자의 자연어 요청을 읽고 "차트 사양(JSON)"을 생성한다.
     * 반환 JSON은 아래 키만 포함:
     *  - sql (필수): 결과 컬럼 별칭이 정확히 label/value/(optional)quantity
     *  - title (선택)
     *  - valueColLabel (선택)
     *  - topN (선택)
     *
     * LLM이 코드펜스(```json ... ```)로 감싸는 경우가 있어 stripCodeFence로 정리 후 파싱한다.
     */
    public ChartSpec generateChartSpec(String userMsg, String schemaDoc) {
        String system = """
            너는 Oracle SQL과 데이터시각화 어시스턴트다.
            사용자가 요구하는 "차트"를 그릴 수 있도록 아래 형식의 JSON만 출력해라.
            다른 텍스트/설명/마크다운 금지. 오직 하나의 JSON 오브젝트만.

            출력 JSON 스키마:
            {
              "sql": "SELECT ...",           // 필수. 단일 SELECT만. 결과 컬럼에 반드시 label, value, (optional) quantity 별칭 사용.
              "title": "차트 제목",           // 선택
              "valueColLabel": "y축 라벨",    // 선택 (예: "순매출(원)")
              "topN": 5                      // 선택. 없으면 5로 간주
            }

            제약/비즈니스 규칙:
            - 판매수량 = SUM(ORDERDETAIL.CONFIRMQUANTITY)
            - 매출 = SUM(ORDERDETAIL.CONFIRMQUANTITY * ORDERDETAIL.SELLPRICE)
            - 집계 대상 주문 = ORDERS.STATUS IN ('CONFIRMED','REFUNDED')
            - PAYMENT 테이블은 매출/판매량 계산에 사용하지 않음
            - 제품별 집계는 ORDERDETAIL.ID = PRODUCT.ID 로 조인
            - 화이트리스트 스키마만 사용 (USERS, ORDERS, ORDERDETAIL, PRODUCT, BRAND, GRADE, MAINNOTE, VOLUME)
            - ORDER BY value DESC 포함 (상위 제한을 위해)
            - :limit 네임드 파라미터 사용 가능. 세미콜론 금지.
            - "type" 키(선택): bar, line, pie, doughnut 중 하나. 생략 시 bar.
        	- 절대 '?'나 ':1' 같은 위치바인드 쓰지 말고 네임드 바인드만(:limit 등) 써라.
            """;

        String user = """
            [사용자 요청]
            %s

            [스키마/규칙]
            %s

            위 형식의 JSON만 순수 텍스트로 출력해라. 코드블록, 설명, 접두/접미 문구 금지.
            """.formatted(userMsg, schemaDoc);

        var body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)
                ),
                "temperature", 0.1
        );

        var resp = call(body);
        String raw = extract(resp);            // LLM 텍스트
        String json = stripCodeFence(raw);     // ```json ...``` 제거

        try {
            ChartSpec spec = om.readValue(json, ChartSpec.class);
            // 간단 검증
            if (spec.sql() == null || spec.sql().isBlank()) {
                throw new IllegalArgumentException("sql 비어있음");
            }
            return spec;
        } catch (Exception e) {
            throw new RuntimeException("ChartSpec JSON 파싱 실패: " + e.getMessage() + " / raw=" + raw);
        }
    }

    /* -------------------- 유틸 -------------------- */
    @SuppressWarnings("unchecked")
    private String extract(Map resp) {
        if (resp == null) return "(응답 없음)";
        if (resp.containsKey("error")) return "(API 오류) " + resp.get("error");
        try {
            var choices = (List<Map>) resp.get("choices");
            var msg = (Map) choices.get(0).get("message");
            String content = String.valueOf(msg.getOrDefault("content", ""));
            if (content.isBlank()) return "(빈 응답) raw=" + resp;
            return content;
        } catch (Exception e) {
            return "(파싱 실패) raw=" + resp;
        }
    }

    // ```json ... ``` 또는 ``` ... ``` 래퍼 제거 (추가)
    private String stripCodeFence(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("```")) {
            // 첫 줄 ```(json) 제거
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) t = t.substring(firstNl + 1);
            // 끝쪽 ``` 제거
            int lastFence = t.lastIndexOf("```");
            if (lastFence >= 0) t = t.substring(0, lastFence);
        }
        // DeepSeek이 앞뒤에 이상한 토큰/설명을 붙일 경우 대비, 양끝 공백 제거
        return t.trim();
    }
}
