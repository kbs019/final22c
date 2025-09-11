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
            - USERS.REG 날짜 비교는 반드시 파라미터 사용: WHERE REG >= :start AND REG < :end
            - Oracle 날짜 함수(SYSDATE, TRUNC, ADD_MONTHS) 사용 금지
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

    // 관리자페이지 AI
    public String summarize(String question, String sql, String table) {
        var body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content",
                    "너는 매장 데이터 분석 도우미야. 결과를 한 줄로 아주 간단히, 친근한 존댓말로 요약해.\n" +
                    "- 문장 끝은 '~예요/네요' 위주(지나친 격식 '입니다'는 가급적 피함)\n" +
                    "- 결과는 우리 테이블에 있는 값만 언급(외부 플랫폼/추정 수치 금지)\n" +
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

        if (checked.contains(":q")) {
            q.setParameter("q", ""); // 상품명이 없을 땐 빈 문자열 → LIKE '%%'
        }

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

    /* ----- 관리자가 상품 등록할때 AIGUIDE 생성 ----- */
    public String generateProductDescription(String prompt) {
        var body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content",
                    "당신은 향수 전문가입니다. 사용자가 제공하는 모든 조건과 요구사항을 정확히 따라 상품 설명문을 작성해주세요.\n\n" +
                    "기본 규칙:\n" +
                    "- 반드시 순수 한국어로만 작성 (중국어, 영어 절대 금지)\n" +
                    "- HTML 태그나 특수문자 사용 금지\n" +
                    "- 자연스럽고 완전한 한국어 문장으로 구성\n" +
                    "- 사용자의 모든 요구사항을 빠짐없이 포함\n" +
                    "- 문단 사이에는 빈 줄로 구분\n" +
                    "- 맞춤법과 띄어쓰기 정확히 준수"),
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", 0.1,
            "max_tokens", 1100
        );

        try {
            var resp = call(body);
            String result = extract(resp);
            if (result == null) return null;

            // 기본 포맷 정리
            result = result.replace("\r\n", "\n")
                           .replaceAll("[ \t]+", " ")
                           .replaceAll("\n{3,}", "\n\n")
                           .trim();

            // 중국어 한자 제거 + 허용 문자 확장(콜론, 세미콜론, 앰퍼샌드, 슬래시, 중점 등 보존)
            result = result.replaceAll("[一-龯]", "");
            result = result.replaceAll(
                "[^가-힣a-zA-Z0-9\\s\\.,!?()\\-:;/&·—'\"%\\n]",
                ""
            ).trim();

            // 짤림 보정: "자르지" 않고 "마무리만 추가"
            if (seemsCut(result)) {
                log.warn("AI 응답 마무리 보정");
                result = finishTail(result);
            }

            // 섹션이 없다면 기본 섹션 추가(싱글/복합 추정은 prompt로 판단)
            boolean hasGuide = result.contains("활용 가이드") || result.contains("활용 꿀팁");
            if (!hasGuide) {
                if (prompt != null && prompt.contains("싱글노트:")) {
                    result += "\n\n활용 꿀팁:\n- 일상에서 부담 없이 사용하기 좋아요.\n- 다른 향수와 레이어링하기에도 적합해요.";
                } else {
                    result += "\n\n향의 시간별 변화 & 활용 가이드:\n- 시간에 따라 다양한 매력을 선사하는 향입니다.\n- 하루 종일 변화하는 향의 여정을 즐겨보세요.";
                }
            }

            return result;
        } catch (Exception e) {
            log.error("상품 설명문 생성 실패: {}", e.getMessage());
            return null;
        }
    }

    /* ----- 상품 상세페이지에서 유저가 AI 맞춤 가이드할때 사용 ----- */
    /* ----- 상품 상세페이지: AI 맞춤 가이드 (seemsCut 건드리지 않는 안정화 버전) ----- */
    public String generatePersonaRecommendation(String prompt) {
        final String END_MARK = "<<END>>";

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
                    "- 반드시 한국어만 사용하고 자연스러운 표현 사용\n" +
                    "- 친근하고 따뜻한 말투로 작성\n" +
                    "- 각 문단 사이에 빈 줄을 넣어 구분\n" +
                    "- '착용자' 대신 '사용자'라는 표현 사용\n" +
                    "- 150-200자 내외로 작성\n" +
                    "- 마지막 줄에 정확히 " + END_MARK + " 를 붙여서 글이 끝났음을 표시하세요."),
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", 0.6,
            "max_tokens", 800 // 여유 넉넉히
        );

        try {
            var resp = call(body);
            String result = extract(resp);
            if (result == null) return null;

            result = normalizeTextKeep(result);
            boolean hasEnd = hasEndMarker(result, END_MARK);
            if (!hasEnd && isFinishByLength(resp)) {
                // 1회 이어쓰기: 기존 텍스트를 수정하지 말고 END_MARK까지 마무리
                String cont = continueToEndMarker(result, END_MARK);
                if (cont != null && !cont.isBlank()) {
                    result = mergeTail(result, cont);
                }
                hasEnd = hasEndMarker(result, END_MARK);
            }

            // END 마커 제거
            result = stripEndMarker(result, END_MARK);

            // 그래도 너무 불안하면(초단문 등) 기존 seemsCut로 보조만 수행
            if (seemsCut(result)) {
                result = safeFinishTail(result);
            }
            return result;

        } catch (Exception e) {
            log.error("상품 설명문 생성 실패: {}", e.getMessage());
            return null;
        }
    }

    /* ===== 보조 유틸 (새로 추가) ===== */
    private boolean hasEndMarker(String s, String end) {
        if (s == null) return false;
        return s.trim().endsWith(end);
    }
    private String stripEndMarker(String s, String end) {
        if (s == null) return null;
        String t = s.trim();
        if (t.endsWith(end)) t = t.substring(0, t.length() - end.length()).trim();
        return t;
    }
    /** 이어쓰기: 본문은 수정하지 말고 END_MARK까지 1~2문장으로 마무리 */
    private String continueToEndMarker(String partial, String END_MARK) {
        try {
            var body = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "system", "content",
                        "아래 글을 수정하지 말고, 자연스럽게 마무리 문장 1~2개만 이어서 작성하세요. " +
                        "반드시 마지막에 " + END_MARK + " 를 붙이세요."),
                    Map.of("role", "user", "content", partial + "\n\n[마무리만 이어쓰기]")
                ),
                "temperature", 0.3,
                "max_tokens", 160
            );
            var resp = call(body);
            String tail = extract(resp);
            if (tail == null) return null;
            return normalizeTextKeep(tail);
        } catch (Exception e) {
            log.warn("이어쓰기 실패: {}", e.getMessage());
            return null;
        }
    }

    /** 텍스트 정리: 불필요 개행/스페이스만 정돈(마커는 보존) */
    private String normalizeTextKeep(String s) {
        if (s == null) return null;
        return s.replace("\r\n", "\n")
                .replaceAll("[\\t\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]+", " ")
                .replaceAll(" +", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /** 최종 안전 마감: seemsCut 유지하면서도 과도한 수정 없이 1문장 추가 */
    private String safeFinishTail(String cutText) {
        if (cutText == null || cutText.isBlank()) return null;
        int lastPeriod = Math.max(cutText.lastIndexOf('.'), cutText.lastIndexOf('。'));
        if (lastPeriod > 0 && cutText.length() - lastPeriod < 80) return cutText.trim();
        return (cutText.trim() + " 마지막으로, 이 향수는 일상과 특별한 순간 모두에서 사용자의 품격을 한층 돋보이게 해줍니다.").trim();
    }

    /** call() 응답 구조에 맞게 finish_reason이 length인지 확인하도록 구현 */
    @SuppressWarnings("unchecked")
    private boolean isFinishByLength(Object resp) {
        try {
            if (!(resp instanceof Map)) return false;
            Map<String, Object> map = (Map<String, Object>) resp;
            List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");
            if (choices == null || choices.isEmpty()) return false;

            Map<String, Object> choice0 = choices.get(0);
            Object fr = choice0.get("finish_reason");
            String reason = (fr == null) ? "" : String.valueOf(fr);

            // (선택) 디버깅에 도움
            Object usage = map.get("usage");
            log.debug("AI finish_reason={}, usage={}", reason, usage);

            return "length".equalsIgnoreCase(reason);
        } catch (Exception ignore) {
            return false;
        }
    }
    

    /** 기존 mergeTail 재사용 (본문 + 이어쓰기 합치기) */
    private String mergeTail(String base, String tail) {
        if (tail == null || tail.isBlank()) return base;
        if (tail.startsWith(base)) tail = tail.substring(base.length()).trim();
        if (tail.isBlank()) return base;
        String sep = base.endsWith("\n") ? "" : "\n";
        return (base + sep + tail).trim();
    }
    // 텍스트가 문장 중간에서 끝났는지 간단 점검(완화 버전)
    private boolean seemsCut(String text) {
        if (text == null) return true;
        String t = text.trim();
        if (t.isEmpty()) return true;

        // 문장 종료 기호들: . ? ! 그리고 "다." "요."
        boolean endsWithSentence =
            t.endsWith(".") || t.endsWith("?") || t.endsWith("!")
            || t.endsWith("다.") || t.endsWith("요.");

        // 너무 짧을 때만 보수적으로 (예: 80자 미만)
        boolean suspiciouslyShort = t.length() < 80;

        // "짤림"은 정말 명백할 때만 true
        return !endsWithSentence && suspiciouslyShort;
    }

    // 끊긴 경우, "잘라내지 않고" 자연스럽게 끝맺는 꼬리만 추가
    private String finishTail(String cutText) {
        if (cutText == null || cutText.isBlank()) return null;

        // 뒤쪽에 마침표가 가깝게 있으면 그대로 둠
        int lastPeriod = cutText.lastIndexOf('.');
        if (lastPeriod > 0 && cutText.length() - lastPeriod < 120) {
            return cutText;
        }

        // 자연스러운 꼬리만 추가
        return cutText + "\n\n향의 시간별 변화 & 활용 가이드:\n- 자세한 활용법은 상품 상세정보를 참고해주세요.";
    }
}
