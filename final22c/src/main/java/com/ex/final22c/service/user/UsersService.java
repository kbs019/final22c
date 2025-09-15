package com.ex.final22c.service.user;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.DataNotFoundException;
import com.ex.final22c.data.qna.Answer;
import com.ex.final22c.data.qna.Question;
import com.ex.final22c.data.qna.QuestionDto;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.form.UsersForm;
import com.ex.final22c.repository.qna.QuestionRepository;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsersService {

    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerifier emailVerifier;
    private final EmailService emailService;
    private final PhoneCodeService phoneCodeService;

    // ===== 공통 유틸 =====
    private static String digitsOnly(String s) {
        if (s == null) return null;
        return s.trim().replaceAll("\\D", "");
    }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String safeTrim(String s) { return s == null ? null : s.trim(); }
    private static String safeLowerTrim(String s) { return s == null ? null : s.trim().toLowerCase(); }

    /** 이메일 비교용 안전 정리: 제로폭·NBSP 제거 + trim + lower */
    private static String sanitizeEmailForCompare(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[\\u200B\\u200C\\u200D\\uFEFF\\u00A0]", "")
                  .trim()
                  .toLowerCase();
    }

    // ===== 기본 도메인 로직 =====
    public Users getUser(String userName) {
        return userRepository.findByUserName(userName)
                .orElseThrow(() -> new DataNotFoundException("사용자를 찾을 수 없습니다."));
    }

    public Users getLoginUser(Principal principal) {
        String username = principal.getName();
        return userRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("로그인 정보 없음"));
    }

    public boolean verifyPassword(String username, String raw) {
        Users u = getUser(username);
        return passwordEncoder.matches(raw, u.getPassword());
    }

    // ===== 회원가입 =====
    @Transactional
    public Users create(UsersForm usersForm) {
        final String phoneDigits = digitsOnly(usersForm.getPhone());

        if (!phoneCodeService.isVerified(phoneDigits)) {
            throw new IllegalStateException("휴대폰 인증이 완료되지 않았습니다.");
        }

        final String emailNorm = emailVerifier.normalize(usersForm.getEmail());

        if (userRepository.existsByUserName(usersForm.getUserName())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (userRepository.existsByEmail(emailNorm)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        if (userRepository.existsByPhone(phoneDigits)) {
            throw new IllegalArgumentException("이미 사용 중인 휴대폰 번호입니다.");
        }

        Users user = Users.builder()
                .userName(usersForm.getUserName())
                .password(passwordEncoder.encode(usersForm.getPassword1()))
                .email(emailNorm)
                .name(usersForm.getName())
                .birth(usersForm.getBirth())
                .telecom(usersForm.getTelecom())
                .phone(phoneDigits)
                .gender(usersForm.getGender())
                .loginType("local")
                .mileage(0)
                .build();

        Users saved = userRepository.save(user);
        phoneCodeService.clearVerified(phoneDigits);
        return saved;
    }

    @Transactional(readOnly = true)
    public boolean existsPhone(String phone) { return userRepository.existsByPhone(phone); }

    public boolean isUserNameAvailable(String userName) { return !userRepository.existsByUserName(userName); }

    public boolean isEmailAvailable(String emailRaw) {
        if (emailRaw == null) return false;
        String emailNorm = emailVerifier.normalize(emailRaw);
        return !emailNorm.isEmpty() && !userRepository.existsByEmail(emailNorm);
    }

    public boolean isPhoneAvailable(String phoneRaw) {
        String phone = digitsOnly(phoneRaw);
        return phone != null && phone.matches("^01[016789]\\d{8}$") && !userRepository.existsByPhone(phone);
    }

    @Transactional
    public Users updateProfile(String username, String newEmail, String newPhone, String newPasswordNullable) {
        Users me = getUser(username);

        String emailNorm = isBlank(newEmail) ? null : safeLowerTrim(newEmail);
        String phoneNorm = isBlank(newPhone) ? null : newPhone.replaceAll("-", "").trim();

        if (emailNorm != null) {
            Optional<Users> existingEmail = userRepository.findByEmail(emailNorm);
            if (existingEmail.isPresent() && !existingEmail.get().getUserNo().equals(me.getUserNo())) {
                throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
            }
        }
        if (phoneNorm != null) {
            Optional<Users> existingPhone = userRepository.findByPhone(phoneNorm);
            if (existingPhone.isPresent() && !existingPhone.get().getUserNo().equals(me.getUserNo())) {
                throw new IllegalArgumentException("이미 사용 중인 휴대폰 번호입니다.");
            }
        }

        if (emailNorm != null) me.setEmail(emailNorm);
        if (phoneNorm != null) me.setPhone(phoneNorm);
        if (!isBlank(newPasswordNullable)) {
            me.setPassword(passwordEncoder.encode(newPasswordNullable));
        }
        return userRepository.save(me);
    }

    @Transactional
    public Users updateUser(Users user) {
        Optional<Users> existingEmail = userRepository.findByEmail(safeLowerTrim(user.getEmail()));
        if (existingEmail.isPresent() && !existingEmail.get().getUserNo().equals(user.getUserNo())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        Optional<Users> existingPhone = userRepository.findByPhone(safeTrim(user.getPhone()));
        if (existingPhone.isPresent() && !existingPhone.get().getUserNo().equals(user.getUserNo())) {
            throw new IllegalArgumentException("이미 사용 중인 휴대폰 번호입니다.");
        }

        user.setEmail(safeLowerTrim(user.getEmail()));
        user.setPhone(safeTrim(user.getPhone()));
        return userRepository.save(user);
    }

    @Transactional
    public Users updatePhoneAndTelecom(String username, String newPhone, String newTelecom) {
        Users me = getUser(username);

        String phoneHyphen = (newPhone == null) ? "" : newPhone.trim();
        String telNorm = (newTelecom == null) ? "" : newTelecom.trim();

        if (phoneHyphen.isEmpty() || telNorm.isEmpty())
            throw new IllegalArgumentException("휴대번호/통신사를 확인해 주세요.");
        if (!phoneHyphen.matches("^010-\\d{4}-\\d{4}$"))
            throw new IllegalArgumentException("휴대폰 번호 형식을 확인해 주세요. (예: 010-1234-5678)");

        String phoneDigits = phoneHyphen.replaceAll("-", "");

        userRepository.findByPhone(phoneDigits).ifPresent(other -> {
            if (!other.getUserNo().equals(me.getUserNo()))
                throw new IllegalArgumentException("이미 사용 중인 휴대폰 번호입니다.");
        });

        me.setPhone(phoneDigits);
        me.setTelecom(telNorm);
        return userRepository.save(me);
    }

    public boolean isEmailAvailableFor(String usernameNullable, String newEmail) {
        String emailNorm = safeLowerTrim(newEmail);
        if (emailNorm == null) return false;

        Users self = null;
        if (usernameNullable != null) {
            try { self = getUser(usernameNullable); } catch (Exception ignored) {}
        }
        if (self != null && emailNorm.equals(safeLowerTrim(self.getEmail()))) {
            return true;
        }
        return userRepository.findByEmail(emailNorm).isEmpty();
    }

    // ===== 아이디/비번 찾기 =====
    public String findId(String name, String email) {
        String norm = emailVerifier.normalize(email);
        if (norm == null || norm.isBlank()) return null;
        return userRepository.findByNameAndEmail(name, norm)
                .map(Users::getUserName)
                .orElse(null);
    }

    /** 인증코드 저장: userName 별로 코드와 만료시각을 관리 */
    private final Map<String, CodeInfo> resetCodes = new ConcurrentHashMap<>();
    private static class CodeInfo {
        final String code;
        final Instant expiry;
        CodeInfo(String code, Instant expiry) { this.code = code; this.expiry = expiry; }
    }

    /** 아이디 + 이메일 확인 후 인증코드 발송 (이메일은 sanitize 후 DB 값과 비교) */
    public boolean sendResetPasswordAuthCode(String userName, String email) {
        final String uname = userName == null ? "" : userName.trim();

        Optional<Users> userOpt = userRepository.findByUserName(uname);
        if (userOpt.isEmpty()) return false;

        String inputClean = sanitizeEmailForCompare(email);
        String dbClean = sanitizeEmailForCompare(userOpt.get().getEmail());
        if (!inputClean.equals(dbClean)) return false;

        String code = generateAuthCode();
        resetCodes.put(uname, new CodeInfo(code, Instant.now().plus(Duration.ofMinutes(10))));

        try {
            emailService.sendResetPasswordEmail(userOpt.get().getEmail(), code);
            return true;
        } catch (Exception e) {
            resetCodes.remove(uname);
            return false;
        }
    }

    /** 인증코드 검증 */
    public boolean verifyAuthCode(String userName, String authCode) {
        if (userName == null || authCode == null) return false;
        CodeInfo info = resetCodes.get(userName);
        if (info == null) return false;
        if (info.expiry.isBefore(Instant.now())) {
            resetCodes.remove(userName);
            return false;
        }
        return info.code.equals(authCode);
    }

    /** 비밀번호 재설정 */
    @Transactional
    public void resetPassword(String userName, String newPassword) {
        userRepository.findByUserName(userName).ifPresent(user -> {
            String encodedPw = passwordEncoder.encode(newPassword);
            user.setPassword(encodedPw);
            userRepository.save(user);
            resetCodes.remove(userName); // 재사용 방지
        });
    }

    private String generateAuthCode() {
        Random random = new Random();
        return String.valueOf(100000 + random.nextInt(900000)); // 6자리
    }

    // ===== 계정 비활성화/삭제 (누락 보완) =====
    @Transactional
    public void deactivateAccount(String username) {
        Users me = getUser(username);
        me.setStatus("inactive");   // Users에 status 필드가 있어야 합니다.
        userRepository.save(me);
    }

    /** 물리 삭제 대신 비활성화 사용 (호출부 호환 위해 유지) */
    @Deprecated
    @Transactional
    public void deleteAccount(String username) {
        deactivateAccount(username);
    }

    // ===== QnA 조회 =====
    public List<QuestionDto> getUserQuestions(String userName) {
        List<Question> questions = questionRepository.findByWriterUserName(userName);
        List<QuestionDto> questionDtos = new ArrayList<>();
        for (Question question : questions) {
            QuestionDto dto = new QuestionDto();
            dto.setQId(question.getQId());
            dto.setStatus(question.getStatus());
            dto.setTitle(question.getTitle());
            dto.setContent(question.getContent());
            dto.setQcId(question.getQc().getQcId());
            dto.setCreateDate(question.getCreateDate());
            Answer answer = question.getAnswer();
            if (answer != null) {
                dto.setAnswer(answer.getContent());
                dto.setAnswerCreateDate(answer.getCreateDate());
            }
            questionDtos.add(dto);
        }
        return questionDtos;
    }
}
