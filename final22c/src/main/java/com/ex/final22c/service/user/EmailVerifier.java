package com.ex.final22c.service.user;

import java.net.IDN;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class EmailVerifier {

    // 로컬파트: RFC 5322 중 실무 안전 서브셋
    // 1) 허용 문자: A-Z a-z 0-9 ! # $ % & ' * + / = ? ^ _ ` { | } ~ . -
    // 2) 시작/끝 '.' 금지, 연속 '..' 금지
    private static final Pattern LOCAL_PART_PATTERN = Pattern.compile(
        "^(?=.{1,64}$)(?!\\.)(?!.*\\.\\.)[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+(?<!\\.)$"
    );

    // 도메인: 라벨 1~63, 전체 <=255, 하이픈 시작/끝 금지, TLD 2+ 문자
    private static final Pattern DOMAIN_LABEL = Pattern.compile("^(?!-)[A-Za-z0-9-]{1,63}(?<!-)$");

    // 예: 실무에서 차단할 일회용 도메인 목록(샘플)
    private static final Set<String> DISPOSABLE = Set.of(
        "mailinator.com","guerrillamail.com","10minutemail.com","tempmail.com"
    );

    /** 공백 제거 + 소문자 + IDN 처리 */
    public String normalize(String emailRaw) {
        if (emailRaw == null) return "";
        String v = emailRaw.trim();
        int at = v.lastIndexOf('@');
        if (at <= 0 || at == v.length()-1) return "";
        String local = v.substring(0, at);
        String domain = v.substring(at + 1);
        // 소문자화(로컬도 보통 소문자로 정규화)
        local = local.toLowerCase();
        domain = domain.toLowerCase();
        // punycode (국제화 도메인 대응)
        try {
            domain = IDN.toASCII(domain);
        } catch (Exception ignored) { return ""; }
        return local + "@" + domain;
    }

    /** 구문 검증(로컬/도메인 모두) */
    public boolean isSyntaxValid(String normalized) {
        if (normalized == null || normalized.isBlank()) return false;
        int at = normalized.lastIndexOf('@');
        if (at <= 0 || at == normalized.length()-1) return false;
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at + 1);

        if (!LOCAL_PART_PATTERN.matcher(local).matches()) return false;

        // 도메인 라벨 점검
        String[] labels = domain.split("\\.");
        if (labels.length < 2) return false;
        int total = 0;
        for (String label : labels) {
            if (!DOMAIN_LABEL.matcher(label).matches()) return false;
            total += label.length() + 1;
        }
        if (total - 1 > 255) return false;
        // TLD 최소 2자
        if (labels[labels.length - 1].length() < 2) return false;

        return true;
    }

    /** 일회용 도메인 여부 */
    public boolean isDisposable(String domain) {
        if (domain == null) return false;
        return DISPOSABLE.contains(domain.toLowerCase());
    }

    /** MX 또는 A 레코드 존재 여부(간단화: 실제 환경에서는 DNS 조회 권장) */
    public boolean hasMxOrA(String domain) {
        // TODO: 프로덕션에서는 DNS 조회 라이브러리 사용 권장
        // 임시로 true 처리(또는 캐시 기반의 간단한 화이트리스트/블랙리스트)
        return true;
    }

    /** 회원가입 정책 최종 판정(구문 OK + 비일회용 + MX/A OK) */
    public boolean isAcceptableForSignup(String normalized) {
        if (!isSyntaxValid(normalized)) return false;
        String domain = normalized.substring(normalized.lastIndexOf('@') + 1).toLowerCase();
        if (isDisposable(domain)) return false;
        if (!hasMxOrA(domain)) return false;
        return true;
    }
}
