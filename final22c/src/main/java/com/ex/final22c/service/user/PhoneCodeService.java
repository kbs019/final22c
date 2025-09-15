package com.ex.final22c.service.user;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ex.final22c.CoolSmsSender;

import lombok.RequiredArgsConstructor;

/**
 * 휴대폰 인증번호 발송/검증 서비스.
 * - send(phone): 인증번호 발송, 쿨다운/일일한도/유효시간 관리
 * - verify(phone, code): 인증번호 검증(해시 비교), 성공 시 10분간 isVerified=true
 * - isVerified(phone): 서버 측 최종 검증(예: 회원가입/탈퇴 직전)
 * - clearVerified(phone): 사용 후 정리
 */
@Service
@RequiredArgsConstructor
public class PhoneCodeService {

    private final CoolSmsSender coolSmsSender;
    private final PasswordEncoder passwordEncoder;

    // ===== 정책 =====
    private static final int CODE_LEN = 6;                 // 인증번호 자리수
    private static final int EXPIRE_MINUTES = 5;           // 인증코드 유효시간(분)
    private static final int RESEND_COOLDOWN_SEC = 60;     // 재전송 쿨다운(초)
    private static final int MAX_DAILY_SEND = 5;           // 하루 발송 제한(회)
    private static final int MAX_VERIFY_ATTEMPTS = 5;      // 최대 검증 시도(회)
    private static final int VERIFIED_HOLD_MINUTES = 10;   // 인증 성공 후 isVerified 유효(분)

    // ===== 인메모리 저장소 =====
    // phone -> CodeEntry (암호화된 코드, 만료시각, 시도횟수)
    private static final ConcurrentHashMap<String, CodeEntry> CODE_MAP = new ConcurrentHashMap<>();
    // phone:yyyymmdd -> count (일일 발송 횟수)
    private static final ConcurrentHashMap<String, Integer> DAILY_COUNT = new ConcurrentHashMap<>();
    // phone -> epochSec (재전송 가능 시각)
    private static final ConcurrentHashMap<String, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    // phone -> epochMs (인증성공 유효시각)
    private static final ConcurrentHashMap<String, Long> VERIFIED_UNTIL = new ConcurrentHashMap<>();

    private static final Random RNG = new Random();

    /** 내부 보관용 코드 엔트리 */
    private static final class CodeEntry {
        final String codeHash;
        final long expireEpochMs;
        final int attempts;
        CodeEntry(String codeHash, long expireEpochMs, int attempts) {
            this.codeHash = codeHash;
            this.expireEpochMs = expireEpochMs;
            this.attempts = attempts;
        }
    }

    // ===== 유틸 =====
    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D+", "");
    }
    private static boolean validPhone(String p) {
        // 010, 011, 016, 017, 018, 019 + 7~8자리
        return p != null && p.matches("^01[016789]\\d{7,8}$");
    }
    private static String todayKey() {
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }
    private static String dcKey(String phone) {
        return phone + ":" + todayKey();
    }

    // ===== 응답 DTO =====
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

    // ===== 내부: 인증코드 생성 =====
    private static String genCode() {
        int bound = (int) Math.pow(10, CODE_LEN);   // 10^len
        int n = RNG.nextInt(bound);                 // 0 ~ (10^len - 1)
        return String.format("%0" + CODE_LEN + "d", n); // 왼쪽 0패딩
    }

    // ===== 발송 =====
    /**
     * 인증번호 발송.
     * @param phoneRaw "010-1234-5678" 또는 "01012345678" 등
     */
    public SendResp send(String phoneRaw) {
        final String phone = digits(phoneRaw);
        if (!validPhone(phone)) {
            return new SendResp(false, "휴대폰 번호 형식이 올바르지 않습니다.", 0, 0);
        }

        // 쿨다운 체크
        long nowSec = System.currentTimeMillis() / 1000;
        Long until = COOLDOWN_UNTIL.get(phone);
        if (until != null && until > nowSec) {
            int left = (int) Math.max(1, until - nowSec);
            int remain = Math.max(0, MAX_DAILY_SEND - DAILY_COUNT.getOrDefault(dcKey(phone), 0));
            return new SendResp(false, "잠시 후 다시 시도해 주세요.", left, remain);
        }

        // 일일 카운트 선증가
        String key = dcKey(phone);
        int sentToday = DAILY_COUNT.merge(key, 1, Integer::sum);
        if (sentToday > MAX_DAILY_SEND) {
            // 롤백
            DAILY_COUNT.compute(key, (k, v) -> v == null ? 0 : Math.max(0, v - 1));
            return new SendResp(false, "하루 발송 가능 횟수를 초과했습니다.", 0, 0);
        }

        // 코드 생성 & 메시지
        String code = genCode();
        String text = "[22°C] 인증번호 " + code + " (5분 내 유효)\n타인에게 절대 공유하지 마세요.";

        // Solapi 발송 (상세 응답)
        CoolSmsSender.SmsSendResult resp = coolSmsSender.sendPlainTextResult(phone, text);
        if (!resp.ok()) {
            // 실패 시 발송 카운트 롤백
            DAILY_COUNT.compute(key, (k, v) -> v == null ? 0 : Math.max(0, v - 1));

            String failMsg = "문자 발송 실패";
            if (resp.message() != null && !resp.message().isBlank()) {
                failMsg += ": " + resp.message();
            }
            if (resp.code() != null && !resp.code().isBlank()) {
                failMsg += " (코드=" + resp.code() + ")";
            }
            int remain2 = Math.max(0, MAX_DAILY_SEND - DAILY_COUNT.getOrDefault(key, 0));
            return new SendResp(false, failMsg, 0, remain2);
        }

        // 저장/쿨다운 설정
        String hash = passwordEncoder.encode(code);
        long expireMs = System.currentTimeMillis() + EXPIRE_MINUTES * 60_000L;
        CODE_MAP.put(phone, new CodeEntry(hash, expireMs, 0));
        COOLDOWN_UNTIL.put(phone, nowSec + RESEND_COOLDOWN_SEC);

        int remain = Math.max(0, MAX_DAILY_SEND - sentToday);
        return new SendResp(true, "인증번호를 발송했습니다.", RESEND_COOLDOWN_SEC, remain);
    }

    // ===== 검증 =====
    /**
     * 인증번호 검증.
     * 성공 시 해당 번호에 대해 VERIFIED_HOLD_MINUTES 동안 isVerified=true.
     */
    public VerifyResp verify(String phoneRaw, String code) {
        final String phone = digits(phoneRaw);
        if (!validPhone(phone)) {
            return new VerifyResp(false, "휴대폰 번호 형식 오류입니다.");
        }
        if (code == null || !code.matches("^\\d{" + CODE_LEN + "}$")) {
            return new VerifyResp(false, "인증번호 " + CODE_LEN + "자리를 입력해 주세요.");
        }

        CodeEntry e = CODE_MAP.get(phone);
        if (e == null) {
            return new VerifyResp(false, "유효한 인증요청이 없습니다. 다시 발송해 주세요.");
        }
        if (System.currentTimeMillis() > e.expireEpochMs) {
            CODE_MAP.remove(phone);
            return new VerifyResp(false, "인증번호가 만료되었습니다. 다시 발송해 주세요.");
        }
        if (e.attempts >= MAX_VERIFY_ATTEMPTS) {
            CODE_MAP.remove(phone);
            return new VerifyResp(false, "시도 횟수를 초과했습니다. 다시 발송해 주세요.");
        }

        boolean match = passwordEncoder.matches(code, e.codeHash);
        if (!match) {
            CODE_MAP.computeIfPresent(phone,
                    (k, v) -> new CodeEntry(v.codeHash, v.expireEpochMs, v.attempts + 1));
            return new VerifyResp(false, "인증번호가 일치하지 않습니다.");
        }

        // 인증 성공: isVerified 10분 부여
        VERIFIED_UNTIL.put(phone, System.currentTimeMillis() + (VERIFIED_HOLD_MINUTES * 60_000L));
        CODE_MAP.remove(phone); // 사용된 코드는 폐기
        return new VerifyResp(true, "인증이 완료되었습니다.");
    }

    // ===== 서버측 최종 확인/정리 =====
    /** 서버에서 최종 보호가 필요한 시점(가입/탈퇴/변경 직전) 확인용 */
    public boolean isVerified(String phoneRaw) {
        String phone = digits(phoneRaw);
        Long until = VERIFIED_UNTIL.get(phone);
        return until != null && until >= System.currentTimeMillis();
    }

    /** 인증 성공 플래그 정리 (가입/변경 완료 후 호출 권장) */
    public void clearVerified(String phoneRaw) {
        String phone = digits(phoneRaw);
        VERIFIED_UNTIL.remove(phone);
    }

    // ===== (선택) 프론트 보조용 헬퍼 =====

    /** 남은 쿨다운 초 (없으면 0) */
    public int getCooldownRemainingSeconds(String phoneRaw) {
        String phone = digits(phoneRaw);
        Long until = COOLDOWN_UNTIL.get(phone);
        if (until == null) return 0;
        long nowSec = System.currentTimeMillis() / 1000;
        return (int) Math.max(0, until - nowSec);
    }

    /** 오늘 남은 발송 가능 횟수 */
    public int getRemainToday(String phoneRaw) {
        String phone = digits(phoneRaw);
        int used = DAILY_COUNT.getOrDefault(dcKey(phone), 0);
        return Math.max(0, MAX_DAILY_SEND - used);
    }
}
