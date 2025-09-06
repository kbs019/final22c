package com.ex.final22c.service.user;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ex.final22c.CoolSmsSender;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PhoneCodeService {

    private final CoolSmsSender coolSmsSender;
    private final PasswordEncoder passwordEncoder;

    // 정책
    private static final int CODE_LEN = 6;
    private static final int EXPIRE_MINUTES = 5; // 코드 유효시간
    private static final int RESEND_COOLDOWN_SEC = 60; // 재전송 쿨다운
    private static final int MAX_DAILY_SEND = 5; // 하루 발송 횟수 제한
    private static final int MAX_VERIFY_ATTEMPTS = 5; // 최대 검증 시도

    // 인메모리 저장소
    private static final ConcurrentHashMap<String, CodeEntry> CODE_MAP = new ConcurrentHashMap<>(); // phone -> code
    private static final ConcurrentHashMap<String, Integer> DAILY_COUNT = new ConcurrentHashMap<>(); // phone:yyyymmdd
                                                                                                     // -> count
    private static final ConcurrentHashMap<String, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>(); // phone ->
                                                                                                     // epochSec
    private static final ConcurrentHashMap<String, Long> VERIFIED_UNTIL = new ConcurrentHashMap<>(); // phone -> epochMs

    private static final Random RNG = new Random();

    private record CodeEntry(String codeHash, long expireEpochMs, int attempts) {
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D+", "");
    }

    private static boolean validPhone(String p) {
        return p != null && p.matches("^01[016789]\\d{7,8}$");
    }

    private static String todayKey() {
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static String dcKey(String phone) {
        return phone + ":" + todayKey();
    }

    public static final class SendResp {
        public final boolean ok;
        public final String msg;
        public final int cooldownSeconds;
        public final int remainToday;

        public SendResp(boolean ok, String msg, int cooldownSeconds, int remainToday) {
            this.ok = ok;
            this.msg = msg;
            this.cooldownSeconds = cooldownSeconds;
            this.remainToday = remainToday;
        }
    }

    public static final class VerifyResp {
        public final boolean ok;
        public final String msg;

        public VerifyResp(boolean ok, String msg) {
            this.ok = ok;
            this.msg = msg;
        }
    }

    private static String genCode() {
        int n = RNG.nextInt(1_000_000); // 0~999999
        return String.format("%06d", n);
    }

    /** 인증번호 발송 */
    public SendResp send(String phoneRaw) {
        final String phone = digits(phoneRaw);
        if (!validPhone(phone))
            return new SendResp(false, "휴대폰 번호 형식이 올바르지 않습니다.", 0, 0);

        // 쿨다운 체크
        long nowSec = System.currentTimeMillis() / 1000;
        Long until = COOLDOWN_UNTIL.get(phone);
        if (until != null && until > nowSec) {
            int left = (int) Math.max(1, until - nowSec);
            int remain = Math.max(0, MAX_DAILY_SEND - DAILY_COUNT.getOrDefault(dcKey(phone), 0));
            return new SendResp(false, "잠시 후 다시 시도해 주세요.", left, remain);
        }

        // 일일 카운트
        String key = dcKey(phone);
        int sentToday = DAILY_COUNT.merge(key, 1, Integer::sum);
        if (sentToday > MAX_DAILY_SEND) {
            // 롤백
            DAILY_COUNT.compute(key, (k, v) -> v == null ? 0 : Math.max(0, v - 1));
            return new SendResp(false, "하루 발송 가능 횟수를 초과했습니다.", 0, 0);
        }

        String code = genCode();
        String text = "[22°C] 인증번호 " + code + " (5분 내 유효)\n타인에게 절대 공유하지 마세요.";
        boolean sent = coolSmsSender.sendPlainText(phone, text);
        if (!sent) {
            DAILY_COUNT.compute(key, (k, v) -> v == null ? 0 : Math.max(0, v - 1));
            return new SendResp(false, "문자 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.", 0,
                    Math.max(0, MAX_DAILY_SEND - DAILY_COUNT.getOrDefault(key, 0)));
        }

        // 저장/쿨다운 설정
        String hash = passwordEncoder.encode(code);
        long expireMs = System.currentTimeMillis() + EXPIRE_MINUTES * 60_000L;
        CODE_MAP.put(phone, new CodeEntry(hash, expireMs, 0));
        COOLDOWN_UNTIL.put(phone, nowSec + RESEND_COOLDOWN_SEC);

        int remain = Math.max(0, MAX_DAILY_SEND - sentToday);
        return new SendResp(true, "인증번호를 발송했습니다.", RESEND_COOLDOWN_SEC, remain);
    }

    /** 인증 확인 */
    public VerifyResp verify(String phoneRaw, String code) {
        final String phone = digits(phoneRaw);
        if (!validPhone(phone))
            return new VerifyResp(false, "휴대폰 번호 형식 오류입니다.");
        if (code == null || !code.matches("^\\d{6}$"))
            return new VerifyResp(false, "인증번호 6자리를 입력해 주세요.");

        CodeEntry e = CODE_MAP.get(phone);
        if (e == null)
            return new VerifyResp(false, "유효한 인증요청이 없습니다. 다시 발송해 주세요.");
        if (System.currentTimeMillis() > e.expireEpochMs()) {
            CODE_MAP.remove(phone);
            return new VerifyResp(false, "인증번호가 만료되었습니다. 다시 발송해 주세요.");
        }
        if (e.attempts() >= MAX_VERIFY_ATTEMPTS) {
            CODE_MAP.remove(phone);
            return new VerifyResp(false, "시도 횟수를 초과했습니다. 다시 발송해 주세요.");
        }

        boolean match = passwordEncoder.matches(code, e.codeHash());
        if (!match) {
            CODE_MAP.computeIfPresent(phone,
                    (k, v) -> new CodeEntry(v.codeHash(), v.expireEpochMs(), v.attempts() + 1));
            return new VerifyResp(false, "인증번호가 일치하지 않습니다.");
        }

        // 인증 완료: 10분짜리 인증표시(회원가입 직전 서버검증용)
        VERIFIED_UNTIL.put(phone, System.currentTimeMillis() + (10 * 60_000L));
        CODE_MAP.remove(phone); // 사용된 코드는 폐기
        return new VerifyResp(true, "인증이 완료되었습니다.");
    }

    /** 회원가입 직전 서버에서 최종 검증 */
    public boolean isVerified(String phoneRaw) {
        String phone = digits(phoneRaw);
        Long until = VERIFIED_UNTIL.get(phone);
        return until != null && until >= System.currentTimeMillis();
    }

    /** 가입 완료 후 정리(선택) */
    public void clearVerified(String phoneRaw) {
        String phone = digits(phoneRaw);
        VERIFIED_UNTIL.remove(phone);
    }
}