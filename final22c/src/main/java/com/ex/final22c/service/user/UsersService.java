package com.ex.final22c.service.user;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

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
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.repository.qna.QuestionRepository;

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

    /** 사용자명으로 조회(없으면 DataNotFoundException) */
    public Users getUser(String userName) {
        return userRepository.findByUserName(userName)
                .orElseThrow(() -> new DataNotFoundException("사용자를 찾을 수 없습니다."));
    }

    /** 로그인 Principal로 조회(없으면 UsernameNotFoundException) */
    public Users getLoginUser(Principal principal) {
        String username = principal.getName();
        return userRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("로그인 정보 없음"));
    }

    /** 비밀번호 검증 */
    public boolean verifyPassword(String username, String raw) {
        Users u = getUser(username);
        return passwordEncoder.matches(raw, u.getPassword());
    }

    /** 회원가입 */
    @Transactional
    public Users create(UsersForm usersForm) {
        // 전화번호 정규화(숫자만)
        final String phoneDigits = digitsOnly(usersForm.getPhone());

        // 서버에서 휴대폰 인증 완료 여부 최종 검증
        if (!phoneCodeService.isVerified(phoneDigits)) {
            throw new IllegalStateException("휴대폰 인증이 완료되지 않았습니다.");
        }

        // 이메일 정규화
        final String emailNorm = emailVerifier.normalize(usersForm.getEmail());

        // 중복 검사
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
                .phone(phoneDigits) // DB에는 숫자만 저장
                .gender(usersForm.getGender())
                .loginType("local")
                .mileage(0)
                .build();

        Users saved = userRepository.save(user);

        // 사용한 인증표시는 정리(선택)
        phoneCodeService.clearVerified(phoneDigits);

        return saved;
    }

    @Transactional(readOnly = true)
    public boolean existsPhone(String phone) {
        return userRepository.existsByPhone(phone);
    }

    private static String digitsOnly(String s) {
        if (s == null)
            return null;
        String trimmed = s.trim();
        return trimmed.replaceAll("\\D", "");
    }

    public boolean isUserNameAvailable(String userName) {
        return !userRepository.existsByUserName(userName);
    }

    /** 이메일 사용 가능 여부 (정규화 + 중복) */
    public boolean isEmailAvailable(String emailRaw) {
        if (emailRaw == null)
            return false;
        String emailNorm = emailVerifier.normalize(emailRaw);
        return !emailNorm.isEmpty() && !userRepository.existsByEmail(emailNorm);
    }

    public boolean isPhoneAvailable(String phoneRaw) {
        String phone = digitsOnly(phoneRaw);
        return phone != null && phone.matches("^01[016789]\\d{8}$")
                && !userRepository.existsByPhone(phone);
    }

    /** 이메일/휴대폰/비밀번호만 업데이트 (null/blank는 미변경) + 본인 제외 중복 검사 */
    @Transactional
    public Users updateProfile(String username, String newEmail, String newPhone, String newPasswordNullable) {
        Users me = getUser(username);

        String emailNorm = isBlank(newEmail) ? null : safeLowerTrim(newEmail);
        String phoneNorm = isBlank(newPhone) ? null : newPhone.replaceAll("-", "").trim();

        // 중복 검사 (본인 제외)
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

        if (emailNorm != null)
            me.setEmail(emailNorm);
        if (phoneNorm != null)
            me.setPhone(phoneNorm);
        if (!isBlank(newPasswordNullable)) {
            me.setPassword(passwordEncoder.encode(newPasswordNullable));
        }

        // 안전하게 저장
        return userRepository.save(me);
    }

    /** 전체 Users 객체 업데이트(중복 검사 포함) */
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

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private static String safeLowerTrim(String s) {
        return s == null ? null : s.trim().toLowerCase();
    }

    /** 휴대폰/통신사만 변경: 입력은 하이푼 포함, DB엔 숫자만 저장. 본인 제외 중복검사 */
    @Transactional
    public Users updatePhoneAndTelecom(String username, String newPhone, String newTelecom) {
        Users me = getUser(username);

        String phoneHyphen = (newPhone == null) ? "" : newPhone.trim();
        String telNorm = (newTelecom == null) ? "" : newTelecom.trim();

        if (phoneHyphen.isEmpty() || telNorm.isEmpty())
            throw new IllegalArgumentException("휴대번호/통신사를 확인해 주세요.");
        if (!phoneHyphen.matches("^010-\\d{4}-\\d{4}$"))
            throw new IllegalArgumentException("휴대폰 번호 형식을 확인해 주세요. (예: 010-1234-5678)");

        String phoneDigits = phoneHyphen.replaceAll("-", ""); // "01012345678"

        userRepository.findByPhone(phoneDigits).ifPresent(other -> {
            if (!other.getUserNo().equals(me.getUserNo()))
                throw new IllegalArgumentException("이미 사용 중인 휴대폰 번호입니다.");
        });

        me.setPhone(phoneDigits);
        me.setTelecom(telNorm);

        // 👉 명시 저장해 커밋 시 확실히 반영
        return userRepository.save(me);
    }

    /** 이메일 사용 가능 여부 사전 체크(본인 이메일이면 사용 가능 처리) */
    public boolean isEmailAvailableFor(String usernameNullable, String newEmail) {
        String emailNorm = safeLowerTrim(newEmail);
        if (emailNorm == null)
            return false;

        Users self = null;
        if (usernameNullable != null) {
            try {
                self = getUser(usernameNullable);
            } catch (Exception ignored) {
            }
        }
        if (self != null && emailNorm.equals(safeLowerTrim(self.getEmail()))) {
            return true;
        }
        return userRepository.findByEmail(emailNorm).isEmpty();
    }

    // 아이디 찾기
    public String findId(String name, String email) {
        Optional<Users> userOpt = userRepository.findByNameAndEmail(name, email);
        if (userOpt.isPresent()) {
            return userOpt.get().getUserName(); // 아이디 반환
        }
        return null; // 못 찾음
    }

    private String tempAuthCode;

    // 아이디 이메일 확인 후 인증코드 발송
    public boolean sendResetPasswordAuthCode(String userName, String email) {
        Optional<Users> userOpt = userRepository.findByUserNameAndEmail(userName, email);
        if (userOpt.isEmpty())
            return false;

        String authCode = generateAuthCode();
        tempAuthCode = authCode; // 임시 저장 (⚠ 멀티유저 환경에서는 사용자별 저장 구조로 바꾸세요)
        emailService.sendResetPasswordEmail(email, authCode);
        return true;
    }

    // 인증코드 확인
    public boolean verifyAuthCode(String authCode) {
        return authCode != null && authCode.equals(tempAuthCode);
    }

    // 비밀번호 재설정
    @Transactional
    public void resetPassword(String userName, String newPassword) {
        userRepository.findByUserName(userName).ifPresent(user -> {
            String encodedPw = passwordEncoder.encode(newPassword);
            user.setPassword(encodedPw);
            userRepository.save(user);
        });
    }

    private String generateAuthCode() {
        Random random = new Random();
        return String.valueOf(100000 + random.nextInt(900000));
    }
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
            
            // 답변 추가
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

    /** 계정 비활성화 */
    @Transactional
    public void deactivateAccount(String username) {
        Users me = getUser(username);
        me.setStatus("inactive");
        userRepository.save(me);
    }

    /** 계정 삭제(Deprecated: 비활성화로 대체) */
    @Deprecated
    @Transactional
    public void deleteAccount(String username) {
        deactivateAccount(username);
    }
}
