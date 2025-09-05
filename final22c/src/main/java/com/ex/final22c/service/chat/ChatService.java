package com.ex.final22c.service.chat;

import com.ex.final22c.sql.SqlGuard;
import com.ex.final22c.sql.SqlNormalizer;
import com.ex.final22c.sql.PeriodResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.sql.Timestamp;
import java.util.List;
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

    // ✅ JSON 파싱용
    private final ObjectMapper om = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // ✅ DB 실행용
    @PersistenceContext
    private EntityManager em;

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

 // -------------------- 범용 차트 스펙 생성 --------------------
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
              "topN": 5,                     // 선택. 없으면 5
              "type": "bar",                 // 선택: bar | line | pie | doughnut (생략 시 bar)
              "format": "currency"           // 선택: currency | count | percent
            }

            제약/비즈니스 규칙:
            - 판매수량 = SUM(ORDERDETAIL.CONFIRMQUANTITY)
            - 매출     = SUM(ORDERDETAIL.CONFIRMQUANTITY * ORDERDETAIL.SELLPRICE)
            - 집계 대상 주문 = ORDERS.STATUS IN ('PAID','CONFIRMED','REFUNDED')
            - PAYMENT 테이블은 매출/판매량 계산에 사용하지 않음
            - 제품별 집계는 ORDERDETAIL.ID = PRODUCT.ID 로 조인
            - ORDER BY value DESC 포함 (상위 제한을 위해)
            - :limit 네임드 파라미터 사용 가능. 세미콜론 금지.
            - 절대 '?'나 ':1' 같은 위치바인드 쓰지 말고 네임드 바인드만(:limit 등) 써라.

            🔒 날짜 규칙(대시보드와 동일):
            - WHERE 절에선 날짜 컬럼에 함수 금지(EXTRACT/TRUNC 금지)
            - 반드시 REGDATE >= :start AND REGDATE < :end (반열림 구간)만 사용
            - 월/주/일 버킷팅이 필요하면 SELECT/GROUP BY에서만 TRUNC(REGDATE,'MM'|'IW') 사용
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
        String raw = extract(resp);        // LLM 텍스트
        String json = stripCodeFence(raw); // ```json ...``` 제거

        try {
            ChartSpec spec = om.readValue(json, ChartSpec.class);
            if (spec.sql() == null || spec.sql().isBlank()) {
                throw new IllegalArgumentException("sql 비어있음");
            }
            return spec;
        } catch (Exception e) {
            throw new RuntimeException("ChartSpec JSON 파싱 실패: " + e.getMessage() + " / raw=" + raw);
        }
    }


    public String generateSql(String question, String schemaDoc) {
    	var sys = """
    			너는 Oracle SQL 생성기다.
    			- 오직 단일 SELECT 한 개만. DUAL/DML/DDL 금지. 세미콜론(;) 금지.
    			- 허용 테이블만 사용: USERS, ORDERS, ORDERDETAIL, PAYMENT, PRODUCT, BRAND, GRADE, MAINNOTE, VOLUME, REFUND, REFUNDDETAIL, CART, CARTDETAIL, REVIEW, PURCHASE, PURCHASEDETAIL.
    			- 텍스트 비교는 항상 대소문자 무시: UPPER(컬럼)=UPPER(:v) 또는 REGEXP_LIKE(...,'i').
    			- ✅ '성비/남녀비율' 질의 등 GENDER 동의어는 코드로 정규화:
    			  남/남자/남성/M/Male → 'M', 여/여자/여성/F/Female → 'F'
    			  예: SUM(CASE WHEN REGEXP_LIKE(GENDER,'^(M|MALE|남|남자|남성)$','i') THEN 1 ELSE 0 END) AS MALE_COUNT
    			- 바인딩 변수만 사용(:userNo, :limit 등). 위치바인드(?, :1 등) 금지. 리터럴 값 인라인 금지.
    			- 최대 300행 제한. 결과는 반드시 ```sql ... ``` 코드블록 한 개만 출력.

    			🔒 날짜/집계 규칙(대시보드와 완전 동일):
    			- WHERE 절에서는 날짜 컬럼에 **어떠한 함수도 금지**: EXTRACT/TRUNC/TO_DATE/TO_TIMESTAMP 등 절대 사용하지 말 것.
    			- WHERE 날짜 필터는 **반드시 아래 두 줄만** 포함하고, **다른 날짜 조건은 추가하지 말 것**:
    			    AND o.REGDATE >= :start
    			    AND o.REGDATE <  :end
    			  (BETWEEN, :end+1, EXTRACT(…SYSDATE), CURRENT_DATE/SYSDATE/SYSTIMESTAMP 호출 금지)
    			- 월/주/일/연 버킷팅은 **SELECT/GROUP BY에서만** TRUNC(o.REGDATE,'MM'|'IW'|NULL|'YYYY') 사용. WHERE에는 금지.
    			- 상태 필터는 반드시 유지:
    			    o.STATUS IN ('PAID','CONFIRMED','REFUNDED')

    			기타:
    			- PAYMENT는 매출/판매량 계산에 사용하지 않는다.
    			- 제품별 집계는 ORDERDETAIL.ID = PRODUCT.ID 로 조인.
    			- 필요 시 ORDER BY를 포함하되, 불필요한 함수/상수 호출 금지.
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

    /* -------------------- 🔥 핵심: AI SQL 실행(표준화 → 가드 → 실행) -------------------- */
    /**
     * 질문과 스키마 요약으로 AI SQL을 생성하고,
     * 1) WHERE 날짜조건을 표준 범위비교로 강제 교정
     * 2) SqlGuard 검사 통과
     * 3) 행 제한 적용
     * 4) 실제 실행
     */
    public AiRunResult runAiSqlWithPeriod(String question,
                                          String schemaDoc,
                                          PeriodResolver.ResolvedPeriod period) {
        // 0) AI가 만든 SQL
        String aiSqlRaw = generateSql(question, schemaDoc);

        // 1) WHERE 날짜 조건 표준화 (EXTRACT/TRUNC 제거 → >= :start AND < :end 강제)
        String normalized = SqlNormalizer.enforceDateRangeWhere(aiSqlRaw, true);

        // 2) 가드 통과 (EXTRACT 금지, WHERE TRUNC 금지 등)
        String checked = SqlGuard.ensureSelect(normalized);

        // 3) 행 제한
        checked = SqlGuard.ensureLimit(checked, 10000);

        // 4) 실행
        Query q = em.createNativeQuery(checked);
        q.setParameter("start", Timestamp.valueOf(period.start()));
        q.setParameter("end",   Timestamp.valueOf(period.end()));
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();

        return new AiRunResult(aiSqlRaw, normalized, checked, rows);
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

    // ```json ... ``` 또는 ``` ... ``` 래퍼 제거
    private String stripCodeFence(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) t = t.substring(firstNl + 1);
            int lastFence = t.lastIndexOf("```");
            if (lastFence >= 0) t = t.substring(0, lastFence);
        }
        return t.trim();
    }

    /* -------------------- DTO -------------------- */
    public record AiRunResult(
            String aiSqlRaw,     // 원본 AI SQL
            String normalizedSql, // WHERE 표준화 후
            String checkedSql,    // 가드+리밋 최종 실행 SQL
            List<Object[]> rows   // 결과
    ) {}
    
    /* ----- 컨텐츠에서 쓸 향수 전문가 ----- */
    public String generateProductDescription(String prompt) {
        var body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", 
                               "당신은 향수 전문가입니다. 매력적이고 전문적인 상품 설명문을 작성해주세요. " +
                               "HTML 태그는 사용하지 말고 순수 텍스트로만 작성하세요. " +
                               "150-200자 내외로 간결하게 작성하세요."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.5,  // 창의성과 일관성의 균형
                "max_tokens", 300    // 응답 길이 제한
        );
        
        try {
            var resp = call(body);
            String result = extract(resp);
            
            // 결과가 너무 길면 자르기
            if (result != null && result.length() > 500) {
                result = result.substring(0, 480) + "...";
            }
            
            return result;
        } catch (Exception e) {
            log.error("상품 설명문 생성 실패: {}", e.getMessage());
            return null; // ProductDescriptionService에서 fallback 처리됨
        }
    }
    
    
    /* ----- 개인화 향수 분석 ----- */
    public String generatePersonaRecommendation(String prompt) {
        var body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", 
                               "당신은 향수 전문가입니다. 특정 성별과 나이대의 사람이 해당 향수를 착용했을 때의 " +
                               "구체적이고 현실적인 시나리오를 분석해주세요. " +
                               "부드러운 말투로 작성해주세요. " + 
                               "추천이 아닌 예상/분석 관점으로 접근하고, 200~300자 내외로 2~3개의 문단으로 작성하세요."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.7,         // 창의적이지만 일관성 있게 (답변의 수준 : 0~2 -> 2로 갈 수록 창의적)
                "max_tokens", 300           // 토큰 (글자수 지정 -> 500 으로 설정하면, 500~600 정도로 출력됨)
        );
        
        try {
            var resp = call(body);
            String result = extract(resp);
            
            if (result != null && result.length() > 500) {
                result = result.substring(0, 400) + "...";
            }
            
            return result;
        } catch (Exception e) {
            log.error("개인화 향수 분석 실패: {}", e.getMessage());
            return null;
        }
    }
}
