package com.ex.final22c;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import net.nurigo.sdk.message.exception.NurigoMessageNotReceivedException;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.service.DefaultMessageService;

@Component
@RequiredArgsConstructor
public class CoolSmsSender {

    private final DefaultMessageService messageService; // RefundSmsService와 동일하게 사용

    @Value("${sms.from-number}")
    private String from;

    // 숫자만 남기기 (하이픈 제거)
    private static String digits(String s) {
        return (s == null) ? "" : s.replaceAll("\\D", "");
    }

    public boolean sendPlainText(String to, String text) {
        net.nurigo.sdk.message.model.Message m = new net.nurigo.sdk.message.model.Message();
        m.setFrom(from);
        m.setTo(digits(to));
        m.setText(text);
        try {
            messageService.send(m);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
