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

    private final DefaultMessageService messageService;

    @Value("${sms.from-number}")
    private String from;

    /** 숫자만 남기기 (하이픈 제거) */
    private static String digits(String s) {
        return (s == null) ? "" : s.replaceAll("\\D", "");
    }

    /** 전송 결과 DTO */
    public record SmsSendResult(boolean ok, String code, String message, String messageId) {}

    /** 상세 응답 반환 */
    public SmsSendResult sendPlainTextResult(String to, String text) {
        try {
            Message m = new Message();
            m.setFrom(digits(from));
            m.setTo(digits(to));
            m.setText(text);

            SingleMessageSentResponse resp =
                    messageService.sendOne(new SingleMessageSendingRequest(m));

            String code = (resp == null) ? null : resp.getStatusCode();
            String msg = (resp == null) ? "no response" : resp.getStatusMessage();
            String id = (resp == null) ? null : resp.getMessageId();
            boolean ok = "2000".equals(code); // Solapi 성공 코드

            // 디버깅/운영 로그
            System.out.printf("[SMS] to=%s code=%s msg=%s id=%s%n",
                    to, code, msg, id);

            return new SmsSendResult(ok, code, msg, id);

        } catch (Exception e) {
            return new SmsSendResult(false, "EXCEPTION", e.getMessage(), null);
        }
    }

    /** 기존 boolean 방식도 유지 */
    public boolean sendPlainText(String to, String text) {
        return sendPlainTextResult(to, text).ok();
    }
}
