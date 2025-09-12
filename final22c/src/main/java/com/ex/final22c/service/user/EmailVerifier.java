package com.ex.final22c.service.user;

import java.net.IDN;
import java.util.Set;
import java.util.regex.Pattern;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.springframework.stereotype.Component;

@Component
public class EmailVerifier {

    // 로컬파트: RFC 5322 중 실무 안전 서브셋
    // 1) 허용 문자: A-Z a-z 0-9 ! # $ % & ' * + / = ? ^ _ ` { | } ~ . -
    // 2) 시작/끝 '.' 금지, 연속 '..' 금지
    private static final Pattern LOCAL_PART_PATTERN = Pattern.compile(
            "^(?=.{1,64}$)(?!\\.)(?!.*\\.\\.)[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+(?<!\\.)$");

    // 도메인: 라벨 1~63, 전체 <=255, 하이픈 시작/끝 금지, TLD 2+ 문자
    private static final Pattern DOMAIN_LABEL = Pattern.compile("^(?!-)[A-Za-z0-9-]{1,63}(?<!-)$");

    // 예: 실무에서 차단할 일회용 도메인 목록(샘플)
    private static final Set<String> DISPOSABLE = Set.of(
            "mailinator.com",
            "10minutemail.com",
            "tempmail.dev",
            "tempmail.com",
            "guerrillamail.com",
            "yopmail.com",
            "sharklasers.com",
            "trashmail.com");

    /** 공백 제거 + 소문자 + IDN 처리 */
    public String normalize(String emailRaw) {
        if (emailRaw == null)
            return "";
        String v = emailRaw.trim();
        int at = v.lastIndexOf('@');
        if (at <= 0 || at == v.length() - 1)
            return "";
        String local = v.substring(0, at);
        String domain = v.substring(at + 1);
        // 소문자화(로컬도 보통 소문자로 정규화)
        local = local.toLowerCase();
        domain = domain.toLowerCase();
        // punycode (국제화 도메인 대응)
        try {
            domain = IDN.toASCII(domain);
        } catch (Exception ignored) {
            return "";
        }
        return local + "@" + domain;
    }

    /** 구문 검증(로컬/도메인 모두) */
    public boolean isSyntaxValid(String normalized) {
        if (normalized == null || normalized.isBlank())
            return false;
        int at = normalized.lastIndexOf('@');
        if (at <= 0 || at == normalized.length() - 1)
            return false;
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at + 1);

        if (!LOCAL_PART_PATTERN.matcher(local).matches())
            return false;

        // 도메인 라벨 점검
        String[] labels = domain.split("\\.");
        if (labels.length < 2)
            return false;
        int total = 0;
        for (String label : labels) {
            if (!DOMAIN_LABEL.matcher(label).matches())
                return false;
            total += label.length() + 1;
        }
        if (total - 1 > 255)
            return false;
        // TLD 최소 2자
        if (labels[labels.length - 1].length() < 2)
            return false;

        return true;
    }

    /** 일회용 도메인 여부 */
    public boolean isDisposable(String domain) {
        if (domain == null)
            return false;
        return DISPOSABLE.contains(domain.toLowerCase());
    }

    public boolean hasMxOrA(String domain) {
        if (domain == null || domain.isBlank())
            return false;

        try {
            var env = new java.util.Hashtable<String, String>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            DirContext ictx = new InitialDirContext(env);

            // MX가 있으면 통과
            Attributes mxAttrs = ictx.getAttributes(domain, new String[] { "MX" });
            Attribute mx = mxAttrs.get("MX");
            if (mx != null && mx.size() > 0)
                return true;

            // MX가 없어도 A 레코드가 있으면(일부 소형 도메인) 통과
            Attributes aAttrs = ictx.getAttributes(domain, new String[] { "A" });
            Attribute a = aAttrs.get("A");
            return a != null && a.size() > 0;

        } catch (NamingException e) {
            return false; // 조회 실패는 없다고 간주(차단)
        }
    }

    /** 회원가입 정책 최종 판정(구문 OK + 비일회용 + MX/A OK) */
    public boolean isAcceptableForSignup(String normalized) {
        if (!isSyntaxValid(normalized))
            return false;
        String domain = normalized.substring(normalized.lastIndexOf('@') + 1).toLowerCase();
        if (isDisposable(domain))
            return false;
        if (!hasMxOrA(domain))
            return false;
        return true;
    }
}
