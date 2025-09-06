package com.ex.final22c.service.user;

import java.net.IDN;
import java.util.Hashtable;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

@Service
public class EmailVerifier {

    private static final Pattern SIMPLE =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"); // 1차 문법

    private final Set<String> disposableDomains;
    private final boolean smtpCheckEnabled; // 필요시 확장용(기본 false)

    public EmailVerifier(DisposableDomains disposable,
                         @Value("${email.verify.smtp:false}") boolean smtpCheckEnabled) {
        this.disposableDomains = disposable.domains();
        this.smtpCheckEnabled = smtpCheckEnabled;
    }

    /** 공백 trim + 도메인을 punycode(ASCII)로 정규화 + 소문자 */
    public String normalize(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        int at = t.lastIndexOf('@');
        if (at < 0) return t.toLowerCase();
        String local = t.substring(0, at);
        String domain = t.substring(at + 1);
        String ascii = IDN.toASCII(domain).toLowerCase();
        return (local + "@" + ascii);
    }

    /** 간단 문법 검사(필요 시 더 강화 가능) */
    public boolean isSyntaxValid(String email) {
        return email != null && SIMPLE.matcher(email).matches();
    }

    /** MX 레코드 존재 여부 (없을 때 A 레코드 fallback 허용 여부는 정책에 맞춰 조절) */
    public boolean hasMxOrA(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ictx = new InitialDirContext(env);

            Attributes mx = ictx.getAttributes(domain, new String[] { "MX" });
            Attribute mxAttr = mx.get("MX");
            if (mxAttr != null && mxAttr.size() > 0) return true;

            Attributes a = ictx.getAttributes(domain, new String[] { "A" });
            return a.get("A") != null;
        } catch (NamingException e) {
            return false;
        }
    }

    /** 공개 블랙리스트 기반 임시메일 도메인 여부 */
    public boolean isDisposable(String domain) {
        if (domain == null) return false;
        String d = domain.toLowerCase();
        if (disposableDomains.contains(d)) return true;
        // 하위 도메인까지 차단하고 싶으면 아래 한 줄 유지
        return disposableDomains.stream().anyMatch(d::endsWith);
    }

    /** 정책 판단: 가입 허용/차단 */
    public boolean isAcceptableForSignup(String normalizedEmail) {
        if (!isSyntaxValid(normalizedEmail)) return false;

        String domain = normalizedEmail.substring(normalizedEmail.lastIndexOf('@') + 1);
        if (isDisposable(domain)) return false;

        // MX 또는 A가 없으면 수신 불가 가능성 높음 → 차단
        if (!hasMxOrA(domain)) return false;

        // (선택) SMTP 레벨 검증까지 하고 싶으면 여기에 추가
        if (smtpCheckEnabled) {
            // 시간/복잡도 문제 때문에 기본은 비활성. 필요 시 구현하세요.
        }
        return true;
    }
}
