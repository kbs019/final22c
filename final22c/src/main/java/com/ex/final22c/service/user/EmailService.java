package com.ex.final22c.service.user;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
	private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // 이메일 전송
    public void sendEmail(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    // 비밀번호 재설정용 인증 코드 이메일
    public void sendResetPasswordEmail(String toEmail, String authCode) {
        String subject = "비밀번호 재설정 인증 코드";
        String body = "비밀번호 재설정을 위해 아래 인증 코드를 입력해주세요:\n\n" + authCode;
        sendEmail(toEmail, subject, body);
    }
}
