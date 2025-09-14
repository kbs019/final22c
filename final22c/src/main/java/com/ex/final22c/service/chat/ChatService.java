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

    private final ObjectMapper om = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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

    /* -------------------- 차트 스펙 생성 -------------------- */
    public ChartSpec generateChartSpec(String userMsg, String schemaDoc) {
        String system = """
            너는 Oracle SQL과 데이터시각화 어시스턴트다.
            사용자가 요구하는 "차트"를 그릴 수 있도록 아래 형식의 JSON만 출력해라.
            다른 텍스트/설명/마크다운 금지. 오직 하나의 JSON 오브젝트만.

            출력 JSON 스키마:
            {
              "sql": "SELECT ...",           // 필수. 결과 컬럼에 label, value, (optional) quantity 별칭 필요
              "title": "차트 제목",           // 선택
              "valueColLabel": "y축 라벨",    // 선택
              "topN": 5,                     // 선택. 없으면 5
              "type": "bar",                 // 선택: bar | line | pie | doughnut
              "format": "currency"           // 선택: currency | count | percent
            }

            제약/비즈니스 규칙:
            - 판매수량 = SUM(ORDERDETAIL.CONFIRMQUANTITY)
            - 매출     = SUM(ORDERDETAIL.CONFIRMQUANTITY * ORDERDETAIL.SELLPRICE)
            - 집계 대상 주문 = ORDERS.STATUS IN ('PAID','CONFIRMED','REFUNDED')
            - PAYMENT 테이블은 매출/판매량 계산에 사용하지 않음
            - 제품별 집계는 ORDERDETAIL.ID = PRODUCT.ID 로 조인
            - ORDER BY value DESC 포함
            - :limit 네임드 파라미터 사용 가능. 세미콜론 금지.
            - 위치 바인드(?, :1 등) 금지. 네임드 바인드만 사용.

            🔒 날짜 규칙:
            - WHERE에서는 날짜 컬럼에 함수 금지(EXTRACT/TRUNC 금지)
            - WHERE 날짜 필터는 REGDATE >= :start AND REGDATE < :end
            - 월/주/일 버킷팅은 SELECT/GROUP BY에서만 TRUNC(o.REGDATE,'MM'|'IW'|'DD') 사용
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
        String raw = extract(resp);
        String json = stripCodeFence(raw);

        try {
            ChartSpec spec = om.readValue(json, ChartSpec.class);
            if (spec.sql() == null || spec.sql().isBlank()) throw new IllegalArgumentException("sql 비어있음");
            return spec;
        } catch (Exception e) {
            throw new RuntimeException("ChartSpec JSON 파싱 실패: " + e.getMessage() + " / raw=" + raw);
        }
    }

    public String generateSql(String question, String schemaDoc) {
        var sys = """
            너는 Oracle SQL 생성기다.
            - 단일 SELECT 한 개만. 세미콜론 금지. DML/DDL 금지.
            - 허용 테이블만 사용: USERS, ORDERS, ORDERDETAIL, PAYMENT, PRODUCT, BRAND, GRADE, MAINNOTE, VOLUME, REFUND, REFUNDDETAIL, CART, CARTDETAIL, REVIEW, PURCHASE, PURCHASEDETAIL.
            - 텍스트 비교는 대소문자 무시(UPPER(...) = UPPER(:v)).
            - 성별 동의어는 'M'/'F'로 정규화 예시 포함.
            - 위치바인드(?, :1 등) 금지. 네임드 바인드만 사용.

            🔒 날짜/집계 규칙:
            - WHERE 절에는 날짜 함수(EXTRACT/TRUNC/TO_DATE 등) 금지.
            - WHERE 날짜 필터는 아래 두 줄만 포함:
                AND o.REGDATE >= :start
                AND o.REGDATE <  :end
            - 버킷팅(TRUNC)은 SELECT/GROUP BY에서만 사용.
            - 상태 필터:
                o.STATUS IN ('PAID','CONFIRMED','REFUNDED')
            - PAYMENT는 매출/판매량 계산에 사용하지 않음.
            - 제품별 집계는 ORDERDETAIL.ID = PRODUCT.ID 로 조인.
            결과는 반드시 ```sql ... ``` 코드블록 하나로만 출력.
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
                Map.of("role", "system", "content",
                    // ✅ 톤 & 형식 가이드
                    "너는 매장 데이터 분석 도우미야. 결과를 한 줄로 아주 간단히, 친근한 존댓말로 요약해.\n" +
                    "- 문장 끝은 '~예요/네요' 위주(지나친 격식 '입니다'는 가급적 피함)\n" +
                    "- 핵심만 1문장: 기간·지표·숫자 중심\n" +
                    "- 금액엔 '원' 붙이고, 숫자는 천 단위 콤마\n" +
                    "- 표/코드블록/불필요한 설명 금지, 이모지는 최대 1개"),
                Map.of("role", "user", "content",
                    "질문:\n" + question + "\n\nSQL:\n" + sql + "\n\n결과표:\n" + table)
            ),
            "temperature", 0.3
        );
        var resp = call(body);
        return extract(resp);
    }
    /* -------------------- AI SQL 실행(표준화 → 가드 → 실행) -------------------- */
    public AiRunResult runAiSqlWithPeriod(String question,
                                          String schemaDoc,
                                          PeriodResolver.ResolvedPeriod period) {
        String aiSqlRaw = generateSql(question, schemaDoc);
        String normalized = SqlNormalizer.enforceDateRangeWhere(aiSqlRaw, true);
        String checked = SqlGuard.ensureSelect(normalized);
        checked = SqlGuard.ensureLimit(checked, 10000);

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
        String aiSqlRaw,
        String normalizedSql,
        String checkedSql,
        List<Object[]> rows
    ) {}

    /* ----- 추가 텍스트 생성(그대로 유지) ----- */
    public String generateProductDescription(String prompt) {
        var body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content",
                    "당신은 향수 전문가입니다. 매력적이고 전문적인 상품 설명문을 작성해주세요.\n\n" +
                    "형식:\n1문단: 향수의 주요 특징과 노트 구성\n2문단: 어떤 상황/사용자에게 어울리는지\n\n" +
                    "규칙:\n- 순수 한국어\n- HTML/특수문자 금지\n- 문단 사이 한 줄 공백\n" +
                    "- 각 문단 180~220자, 전체 2문단(과도하게 길지 않게)\n- 자연스러운 존댓말"),
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", 0.5,
            "max_tokens", 800 
        );

        try {
            var resp = call(body);
            String result = extract(resp);
            if (result == null) return null;

            result = result.replace("\r\n", "\n")
                           .replaceAll("[ \t]+", " ")
                           .replaceAll("\n{3,}", "\n\n")
                           .trim();

            // ⬇ 끊겼으면 1회 보정
            if (seemsCut(result)) {
                String tail = finishTail(result);
                if (tail != null && !tail.isBlank()) {
                    result = (result + " " + tail).replaceAll("[ \t]+", " ").trim();
                }
            }
            return result;
        } catch (Exception e) {
            log.error("상품 설명문 생성 실패: {}", e.getMessage());
            return null;
        }
    }

    public String generatePersonaRecommendation(String prompt) {
        var body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content",
                    "당신은 친근한 향수 전문가입니다. 특정 성별과 나이대의 사람이 주어진 향수를 사용했을 때 " +
                    "어떤 매력을 발산할지 따뜻하게 설명해주세요.\n\n" +
                    "다음 내용을 3개 문단으로 나누어 자연스럽고 부드러운 말투로 작성하세요:\n" +
                    "- 이 향수가 해당 연령대/성별과 얼마나 잘 어울리는지\n" +
                    "- 주변 사람들이 느낄 수 있는 좋은 인상들\n" +
                    "- 사용자에게 선사할 특별한 분위기\n\n" +
                    "**작성 가이드:**\n" +
                    "- 한국어만 사용하고 자연스러운 표현 사용\n" +
                    "- 친근하고 따뜻한 말투로 작성\n" +
                    "- 각 문단 사이에 빈 줄을 넣어 구분\n" +
                    "- '착용자' 대신 '사용자'라는 표현 사용\n" +
                    "- 150-200자 내외로 작성"),
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", 0.7,
            "max_tokens", 300
        );
        try {
            var resp = call(body);
            String result = extract(resp);
            if (result == null) return null;

            result = result.replace("\r\n", "\n")
                           .replaceAll("[ \t]+", " ")
                           .replaceAll("\n{3,}", "\n\n")
                           .trim();

            // ⬇ 끊겼으면 1회 보정
            if (seemsCut(result)) {
                String tail = finishTail(result);
                if (tail != null && !tail.isBlank()) {
                    result = (result + " " + tail).replaceAll("[ \t]+", " ").trim();
                }
            }
            return result;
        } catch (Exception e) {
            log.error("상품 설명문 생성 실패: {}", e.getMessage());
            return null;
        }
    }
 // 텍스트가 문장 중간에서 끝났는지 간단 점검
    private static boolean seemsCut(String s) {
        if (s == null) return false;
        // 마침표/물음표/느낌표 혹은 '요.' '다.' 등으로 끝나지 않으면 끊긴 걸로 간주
        return !s.trim().matches("(?s).*[.!?]|.*(요|다|함)\\.$");
    }

    // 끊긴 경우, 마지막 1~2문장만 자연스럽게 끝맺도록 모델에 한 번 더 요청
    private String finishTail(String partial) {
        var body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content",
                    "다음 한국어 텍스트가 문장 중간에서 끊겼습니다. 의미 바꾸지 말고 1~2문장으로 자연스럽게 마무리만 해주세요. " +
                    "앞부분을 반복하거나 요약하지 말고, 뒤에 이어질 문장만 작성하세요."),
                Map.of("role", "user", "content", partial)
            ),
            "temperature", 0.3,
            "max_tokens", 200  // 마무리만
        );
        var resp = call(body);
        return extract(resp);
    }
}
