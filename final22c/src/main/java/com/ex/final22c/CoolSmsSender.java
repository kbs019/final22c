package com.ex.final22c;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
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
        if (from == null || from.isBlank()) return false;

        Message m = new Message();
        m.setFrom(from);
        m.setTo(digits(to));
        m.setText(text);

        try {
            // SDK 버전에 맞춰 sendOne + 응답 체크
            SingleMessageSentResponse resp =
                    messageService.sendOne(new SingleMessageSendingRequest(m));

            // ★ 성공 코드 판정 (쿨SMS: 2000이 정상)
            String code = (resp == null) ? null : resp.getStatusCode();
            boolean ok = "2000".equals(code);

            // 필요하면 원인 로깅: resp.getStatusCode(), resp.getStatusMessage()
            return ok;

        } catch (Exception e) {
            // 네트워크/인증/기타 런타임
            return false;
        }
    }
}
