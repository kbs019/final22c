package com.ex.final22c.service.product;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductDescriptionService {

    private final ChatService chatService;
    private final Map<String, String> fragranceCacheMap = new ConcurrentHashMap<>();

    // ====== Public API ======
    public String generateEnhancedDescription(Product product) {
        String key = generateFragranceKey(product);

        // 캐시 비활성(테스트 중이면 주석 유지)

        String cached = fragranceCacheMap.get(key);
        if (cached != null) {
            log.debug("향수 캐시 사용: {}", key);
            return cached;
        }

        
        boolean hasSingle = !isEmpty(product.getSingleNote());
        boolean hasComplex = !isEmpty(product.getTopNote())
                || !isEmpty(product.getMiddleNote())
                || !isEmpty(product.getBaseNote());

        String prompt = buildPromptBasedOnNoteStructure(product, hasSingle, hasComplex);

        try {
            String result = chatService.generateProductDescription(prompt);

            // 싱글노트: 대표 1개 노트만 보정
            if (hasSingle) {
                String primary = extractPrimarySingleNote(product.getSingleNote());
                result = pruneOtherNotesForSingle(result, primary);
                result = StyleEnforcer.enforce(result, StyleEnforcer.Mode.SINGLE, primary);
            } else {
                result = StyleEnforcer.enforce(result, StyleEnforcer.Mode.COMPLEX, null);
            }

            String clean = cleanTextForStorage(result);
            if (!isEmpty(clean)) {
                fragranceCacheMap.put(key, clean);
            }
            return clean;

        } catch (Exception e) {
            log.error("AI 설명문 생성 실패: {}", e.getMessage(), e);
            return generateFallbackDescription(product, hasSingle, hasComplex);
        }
    }

    // ====== Cache ======
    private String generateFragranceKey(Product p) {
        String cleanName = cleanProductName(p.getName());
        return String.format("%s_%s_%s_%s",
                p.getBrand().getBrandName().replaceAll("[^a-zA-Z0-9가-힣]", ""),
                cleanName.replaceAll("[^a-zA-Z0-9가-힣]", ""),
                p.getGrade().getGradeName().replaceAll("[^a-zA-Z0-9가-힣]", ""),
                p.getMainNote().getMainNoteName().replaceAll("[^a-zA-Z0-9가-힣]", "")
        );
    }

    public void clearFragranceCache(Product product) {
        fragranceCacheMap.remove(generateFragranceKey(product));
    }
    public void clearAllCache() { fragranceCacheMap.clear(); }

    // ====== Display (가독성 강조: 불릿은 <ul><li>로 렌더링) ======
    public String formatForDisplay(String rawText, Product product) {
        if (isEmpty(rawText)) return null;

        // 0) 전처리: 제목과 불릿이 붙어버린 경우 줄바꿈 강제
        String normalized = rawText.replace("\r\n", "\n")
        	    // 0-a) 제목 '앞'에 줄바꿈 강제: 문장 끝(.) 뒤에 제목이 붙어온 경우
        	    .replaceAll("(?<=\\.)\\s*(\\[.+?\\]\\s*활용\\s*꿀팁:)", "\n$1")
        	    .replaceAll("(?<=\\.)\\s*(향의\\s*시간별\\s*변화\\s*&\\s*활용\\s*가이드:)", "\n$1")

        	    // 0-b) 제목 '뒤'에 줄바꿈 보장(기존)
        	    .replaceAll("(향의\\s*시간별\\s*변화\\s*&\\s*활용\\s*가이드:)\\s*", "$1\n")
        	    .replaceAll("(\\[.+?\\]\\s*활용\\s*꿀팁:)\\s*", "$1\n")

        	    // 0-c) 제목 직후에 곧바로 불릿이 붙어온 경우: "...: - ..." → "...:\n- ..."
        	    .replaceAll("(향의\\s*시간별\\s*변화\\s*&\\s*활용\\s*가이드:)\\s*-\\s*", "$1\n- ")
        	    .replaceAll("(\\[.+?\\]\\s*활용\\s*꿀팁:)\\s*-\\s*", "$1\n- ")

        	    // 0-d) 문장/콜론/대괄호 뒤 바로 이어지는 불릿 분리
        	    .replaceAll("(?<=\\.)\\s*-\\s+", "\n- ")
        	    .replaceAll("(?<=:)\\s*-\\s+", "\n- ")
        	    .replaceAll("(?<=\\])\\s*-\\s+", "\n- ")

        	    // 0-e) 점/중점 불릿을 하이픈으로 통일
        	    .replaceAll("\\s*[•·]\\s*", "\n- ");
        String[] lines = normalized.split("\n");

        StringBuilder html = new StringBuilder();
        boolean blockOpen = false;   // <div class="desc-block"> 열린 상태
        boolean listOpen  = false;   // <ul class="desc-list">  열린 상태

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            boolean isSectionTitle =
                line.endsWith("가이드:") ||
                line.matches("^\\[.*?\\]\\s*활용\\s*꿀팁:$");

            // 1) 섹션 제목
            if (isSectionTitle) {
                // 이전 블록/리스트 정리
                if (listOpen) { html.append("</ul>\n"); listOpen = false; }
                if (blockOpen) { html.append("</div>\n"); blockOpen = false; }

                html.append("<div class=\"desc-block\">\n")
                    .append("<h4 class=\"desc-title\">")
                    .append(escapeHtml(line))
                    .append("</h4>\n");
                blockOpen = true;
                continue;
            }

            // 2) 불릿
            if (line.startsWith("- ")) {
                if (!blockOpen) { // 제목 없이 불릿만 오는 경우도 세련되게 감싸기
                    html.append("<div class=\"desc-block\">\n");
                    blockOpen = true;
                }
                if (!listOpen) {
                    html.append("<ul class=\"desc-list\">\n");
                    listOpen = true;
                }
                html.append("  <li>")
                    .append(escapeHtml(line.substring(2).trim()))
                    .append("</li>\n");
                continue;
            }

            // 3) 일반 문단
            if (listOpen) { html.append("</ul>\n"); listOpen = false; }
            if (blockOpen) { html.append("</div>\n"); blockOpen = false; }
            html.append("<p>").append(escapeHtml(line)).append("</p>\n");
        }

        // 마무리
        if (listOpen)  html.append("</ul>\n");
        if (blockOpen) html.append("</div>\n");

        return html.toString().trim();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    
    // ====== Storage sanitize ======
    private String cleanTextForStorage(String s) {
        if (isEmpty(s)) return null;
        String t = s.replaceAll("<[^>]+>", "")     // 태그 제거
                .replace("\r\n", "\n")
                .replaceAll("[ \t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        // 오타/표기 정규화
        t = normalizeTypos(t);
        return t;
    }

    // ====== Fallback ======
    private String generateFallbackDescription(Product product, boolean hasSingle, boolean hasComplex) {
        StringBuilder f = new StringBuilder();
        f.append(product.getBrand().getBrandName()).append("의 ");
        if (hasSingle) {
            String primary = extractPrimarySingleNote(product.getSingleNote());
            f.append("싱글노트 향수로, ").append(primary)
                    .append("의 순수한 매력을 담았습니다.\n\n")
                    .append("레이어링의 베이스로 좋고 일상에서 부담 없이 사용할 수 있습니다.");
        } else if (hasComplex) {
            f.append("복합적인 향 구조로 시간에 따라 다양한 매력을 선사합니다.\n\n");
            if (!isEmpty(product.getTopNote()))    f.append("첫인상은 ").append(product.getTopNote()).append("로 시작하며 ");
            if (!isEmpty(product.getBaseNote()))   f.append(product.getBaseNote()).append("로 깊이 있게 마무리됩니다.");
        } else {
            f.append("세련된 무드로 특별한 순간을 빛내 줍니다.");
        }
        return f.toString();
    }

    // ====== Helpers ======
    private String cleanProductName(String name) {
        if (name == null) return "";
        return name.replaceAll("\\s*\\d+\\s*[mM][lL]\\s*", " ")
                .replaceAll("\\s*\\d+ml\\s*", " ")
                .replaceAll("\\s*\\d+ML\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String extractPrimarySingleNote(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty()) return "";
        String[] parts = t.split("\\s*[,/·|]\\s*|\\s+");
        return parts.length > 0 ? parts[0] : t;
    }

    private String pruneOtherNotesForSingle(String result, String primary) {
        if (result == null) return null;
        String r = result;

        // 어떤 형태든 → "[primary] 활용 꿀팁:"으로 정규화
        r = r.replaceAll("(?m)^\\s*\\(?.*"+ Pattern.quote(primary) +".*\\)?\\s*활용\\s*꿀팁\\s*[:：]?", "[" + primary + "] 활용 꿀팁:");

        // '싱글 노트: ...' 나열 문장 → 대표만
        r = r.replaceAll("(?i)싱글\\s*노트\\s*[:：]\\s*.+", "싱글 노트: " + primary);

        // 제목 앞 한 줄 공백 보장
        r = r.replaceAll("(?m)^\\s*\\[" + Pattern.quote(primary) + "\\]\\s*활용\\s*꿀팁:", "\n\n[" + primary + "] 활용 꿀팁:");
        r = r.replaceAll("\\n{3,}", "\n\n").trim();
        return r;
    }

    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    // ====== Prompt builder ======
    private String buildPromptBasedOnNoteStructure(Product product, boolean hasSingle, boolean hasComplex) {
        StringBuilder p = new StringBuilder();
        p.append("향수 가이드를 작성해주세요:\n\n");

        String cleanName = cleanProductName(product.getName());
        p.append("브랜드: ").append(product.getBrand().getBrandName()).append("\n");
        p.append("제품명: ").append(cleanName).append("\n");
        p.append("부향률: ").append(product.getGrade().getGradeName()).append("\n");

        if (hasSingle) {
            String primary = extractPrimarySingleNote(product.getSingleNote());
            p.append("싱글노트: ").append(primary).append("\n\n")
                    .append("형식:\n")
                    .append("1문단: 향수 소개 (약 100자)\n")
                    .append("2섹션: '[").append(primary).append("] 활용 꿀팁:' 제목 + 정확히 4개 불릿포인트\n\n")
                    .append("팁 내용: 사용시간대, 레이어링, 상황별 활용법, 특별효과\n\n")
                    .append("규칙:\n")
                    .append("- 한국어만 사용, 자연스럽고 구체적으로 작성\n")
                    .append("- 싱글노트 제품이므로 '").append(primary).append("'만 다루고 다른 노트 이름 언급 금지\n")
                    .append("- '부향률' 표기는 그대로 사용(예: '오 드 퍼퓸'); '농도'라는 단어로 변형·첨삭 금지\n")
                    .append("- '오 드 퍼퓸' 철자/띄어쓰기 정확히 유지(오타 금지: 퍼퓸→퍼털 등 금지)\n")
                    .append("- 완전한 문장으로 작성하고 문장 끝은 마침표로 마무리\n");

        } else if (hasComplex) {
            if (!isEmpty(product.getTopNote()))    p.append("탑노트: ").append(product.getTopNote()).append("\n");
            if (!isEmpty(product.getMiddleNote())) p.append("미들노트: ").append(product.getMiddleNote()).append("\n");
            if (!isEmpty(product.getBaseNote()))   p.append("베이스노트: ").append(product.getBaseNote()).append("\n");

            p.append("\n형식:\n")
                    .append("1문단: 향수 소개 (약 100자)\n")
                    .append("2섹션: '향의 시간별 변화 & 활용 가이드:' 제목 + 4개 불릿포인트\n\n")
                    .append("가이드 내용: 시간별변화, 상황별활용, 사용팁, 특별효과\n")
                    .append("\n규칙: 한국어, 쉬운표현, 구체적내용, 완전한문장\n")
                    .append("- '부향률' 표기는 그대로 사용(예: '오 드 퍼퓸'); '농도'라는 단어로 변형·첨삭 금지\n")
                    .append("- '오 드 퍼퓸' 철자/띄어쓰기 정확히 유지(오타 금지: 퍼퓸→퍼털 등 금지)");
        }

        return p.toString();
    }

    // ====== 오타/표기 정규화 ======
    private static String normalizeTypos(String text) {
        if (text == null) return null;
        String t = text;

        // 전각 콜론 정규화
        t = t.replace('：', ':');

        // '오 드 퍼퓸' 관련 흔한 오타/붙임표 변형
        t = t.replace("오 드 퍼털", "오 드 퍼퓸")
                .replace("오드 퍼털", "오 드 퍼퓸")
                .replace("오드퍼털", "오 드 퍼퓸")
                .replace("오드퍼퓸", "오 드 퍼퓸")
                .replace("오 드퍼퓸", "오 드 퍼퓸")
                .replace("오 드 퍼품", "오 드 퍼퓸")
                .replace("오 드 퍼퓸므", "오 드 퍼퓸");

        // “… 농도” → ‘…’
        t = t.replaceAll("(오\\s*드\\s*퍼퓸)\\s*농도", "$1")
                .replaceAll("(오\\s*드\\s*뚜왈렛)\\s*농도", "$1")
                .replaceAll("(오\\s*드\\s*코롱)\\s*농도", "$1");

        // 과도한 공백 정리
        t = t.replaceAll("\\s{2,}", " ");

        return t;
    }

    // ====== Style Enforcer ======
    private static class StyleEnforcer {
        enum Mode { SINGLE, COMPLEX }

        // 구어체 → 존댓말
        private static final String[][] ENDING_RULES = new String[][]{
                {"해요\\.", "합니다."},
                {"돼요\\.", "됩니다."},
                {"줘요\\.", "줍니다."},
                {"예요\\.", "입니다."},
                {"이에요\\.", "입니다."},
                {"느껴져요\\.", "느껴집니다."},
                {"어울려요\\.", "어울립니다."},
                {"만들어줘요\\.", "만들어줍니다."},
                {"좋아요\\.", "좋습니다."},
                {"수 있어요\\.", "수 있습니다."},
                {"보여줘요\\.", "보여줍니다."}
        };

        static String enforce(String raw, Mode mode, String primaryNote) {
            if (raw == null) return null;

            String t = raw.replace("\r\n", "\n")
                    .replaceAll("[ \t]+", " ")
                    .replaceAll("\\n{3,}", "\n\n")
                    .trim();

            // 섹션 제목 보장
            if (mode == Mode.SINGLE) {
                String title = "[" + primaryNote + "] 활용 꿀팁:";
                if (!t.contains(title)) {
                    t = ensureTwoParagraphs(t) + "\n\n" + title + "\n";
                } else {
                    t = t.replace(title, "\n\n" + title);
                }
            } else {
                String title = "향의 시간별 변화 & 활용 가이드:";
                if (!t.contains(title)) {
                    t = ensureTwoParagraphs(t) + "\n\n" + title + "\n";
                } else {
                    t = t.replace(title, "\n\n" + title);
                }
            }

            // 섹션 이하 불릿 정규화 (복합노트는 소제목 자동 부여)
            t = bulletizeSection(t, 4, mode);

            // 문체/마침표 보정
            t = unifyPoliteEndings(t);
            t = ensureSentencePeriod(t);

            // 라벨 중복 제거
            t = deduplicateLabels(t);

            // 가독성 간격 보장
            t = ensureReadableSpacing(t);

            // 최종 오타/표기 정규화
            t = normalizeTypos(t);

            return t.trim();
        }

        private static String ensureTwoParagraphs(String t) {
            if (t.split("\\n\\n").length >= 2) return t;
            int idx = t.indexOf('.');
            if (idx > 0 && idx + 1 < t.length()) {
                return t.substring(0, idx + 1).trim() + "\n\n" + t.substring(idx + 1).trim();
            }
            return t;
        }

        private static String bulletizeSection(String t, int limit, Mode mode) {
            String[] lines = t.split("\\n");
            List<String> out = new ArrayList<>(lines.length);
            boolean inSection = false;
            int count = 0;

            for (String line0 : lines) {
                String s = line0.trim();

                // 섹션 시작 감지
                if (s.endsWith("활용 가이드:") || s.matches("^\\[.+?\\]\\s*활용\\s*꿀팁:$")) {
                    inSection = true;
                    count = 0;
                    out.add(s);
                    continue;
                }

                if (inSection) {
                    if (s.isEmpty()) { out.add(""); continue; }

                    // 기존 불릿/숫자 → "- "로 통일
                    s = s.replaceFirst("^(?:[-•·]\\s*|\\d+[\\).]\\s*)", "");
                    s = "- " + s;

                    // 복합노트에서만 라벨 부여
                    if (mode == Mode.COMPLEX) {
                        switch (count) {
                            case 0 -> s = "- 시간별 변화: " + s.substring(2);
                            case 1 -> s = "- 상황별 활용: " + s.substring(2);
                            case 2 -> s = "- 사용 팁: " + s.substring(2);
                            case 3 -> s = "- 특별 효과: " + s.substring(2);
                        }
                    }

                    out.add(s);
                    count++;
                    if (count >= limit) inSection = false;
                } else {
                    out.add(line0);
                }
            }
            // 항목/타이틀 사이 간격
            return String.join("\n\n", out);
        }

        private static String unifyPoliteEndings(String t) {
            String r = t;
            for (String[] pair : ENDING_RULES) r = r.replaceAll(pair[0], pair[1]);
            r = r.replaceAll("(?m)(합니다|됩니다|줍니다|있습니다|좋습니다|만듭니다)$", "$1.");
            return r;
        }

        private static String ensureSentencePeriod(String t) {
            String[] parts = t.split("\\n");
            for (int i = 0; i < parts.length; i++) {
                String s = parts[i].trim();
                if (s.isEmpty()) continue;
                boolean isTitle = s.endsWith("가이드:") || s.matches("^\\[.*\\]\\s*활용\\s*꿀팁:$");
                if (!isTitle && !s.startsWith("- ")) {
                    if (!s.matches(".*[\\.\\?!]$")) parts[i] = parts[i] + ".";
                }
                if (s.startsWith("- ") && !s.matches(".*[\\.\\?!]$")) {
                    parts[i] = parts[i] + ".";
                }
            }
            return String.join("\n", parts);
        }

        private static String deduplicateLabels(String text) {
            if (text == null) return null;
            text = text.replace('：', ':');
            text = text.replaceAll("(?m)^(-\\s*)(시간별\\s*변화)\\s*:\\s*\\2\\s*:?\\s*", "$1$2: ");
            text = text.replaceAll("(?m)^(-\\s*)(상황별\\s*활용)\\s*:\\s*\\2\\s*:?\\s*", "$1$2: ");
            text = text.replaceAll("(?m)^(-\\s*)(사용\\s*팁)\\s*:\\s*(사용\\s*팁)\\s*:?\\s*", "$1$2: ");
            text = text.replaceAll("(?m)^(-\\s*)(특별\\s*효과)\\s*:\\s*\\2\\s*:?\\s*", "$1$2: ");
            text = text.replaceAll("(?m)^(-\\s*(?:시간별\\s*변화|상황별\\s*활용|사용\\s*팁|특별\\s*효과):)\\s{2,}", "$1 ");
            return text;
        }

        private static String ensureReadableSpacing(String t) {
            if (t == null) return null;
            // 타이틀 다음에 빈 줄 1개
            t = t.replaceAll("(?m)^(\\[.+?\\]\\s*활용\\s*꿀팁:)\\s*", "$1\n\n");
            t = t.replaceAll("(?m)^(향의 시간별 변화\\s*&\\s*활용 가이드:)\\s*", "$1\n\n");
            // 각 불릿 앞에 한 줄 띄우기(연속 공백은 1개로)
            t = t.replaceAll("(?m)\\n\\s*\\- ", "\n\n- ");
            t = t.replaceAll("\\n{3,}", "\n\n");
            return t.trim();
        }
    }
}
