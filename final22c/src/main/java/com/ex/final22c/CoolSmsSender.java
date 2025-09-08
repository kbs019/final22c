package com.ex.final22c;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;

/**
 * Solapi(SMS) 발송 어댑터
 * - 채널 A/B 두 개 계정과 발신번호 지원
 * - A 실패 시 B로 1회 페일오버
 */
@Component
public class CoolSmsSender {

    private final DefaultMessageService solapiA;
    private final DefaultMessageService solapiB;

    private final String fromA;
    private final String fromB;

    public CoolSmsSender(
            @Qualifier("solapiA") DefaultMessageService solapiA,
            @Qualifier("solapiB") DefaultMessageService solapiB,
            @Value("${sms.a.from-number}") String fromA,
            @Value("${sms.b.from-number}") String fromB) {
        this.solapiA = solapiA;
        this.solapiB = solapiB;
        this.fromA = fromA;
        this.fromB = fromB;
    }

    /** 숫자만 남기기 */
    private static String digits(String s) {
        return (s == null) ? "" : s.replaceAll("\\D+", "");
    }

    /** 전송 결과 DTO */
    public static final class SmsSendResult {
        public final boolean ok;
        public final String code;
        public final String message;
        public final String messageId;
        public final String usedChannel;
        public final String usedFrom;

        public SmsSendResult(boolean ok, String code, String message, String messageId,
                String usedChannel, String usedFrom) {
            this.ok = ok;
            this.code = code;
            this.message = message;
            this.messageId = messageId;
            this.usedChannel = usedChannel;
            this.usedFrom = usedFrom;
        }
    }

    private SmsSendResult doSend(DefaultMessageService svc, String from, String to, String text, String ch) {
        try {
            Message m = new Message();
            m.setFrom(digits(from));
            m.setTo(digits(to));
            m.setText(text);

            SingleMessageSentResponse resp = svc.sendOne(new SingleMessageSendingRequest(m));
            String code = (resp == null) ? null : resp.getStatusCode();
            String msg = (resp == null) ? "no response" : resp.getStatusMessage();
            String id = (resp == null) ? null : resp.getMessageId();
            boolean ok = "2000".equals(code);

            System.out.printf("[SMS][%s] to=%s from=%s code=%s msg=%s id=%s%n",
                    ch, to, from, code, msg, id);

            return new SmsSendResult(ok, code, msg, id, ch, digits(from));
        } catch (Exception e) {
            return new SmsSendResult(false, "EXCEPTION", e.getMessage(), null, ch, digits(from));
        }
    }

    /** 채널 A만 */
    public SmsSendResult sendWithA(String to, String text) {
        return doSend(solapiA, fromA, to, text, "A");
    }

    /** 채널 B만 */
    public SmsSendResult sendWithB(String to, String text) {
        return doSend(solapiB, fromB, to, text, "B");
    }

    /** A 실패 시 B로 자동 페일오버 */
    public SmsSendResult sendPlainTextResult(String to, String text) {
        SmsSendResult a = sendWithA(to, text);
        return a.ok ? a : sendWithB(to, text);
    }

    /** 레거시 boolean */
    public boolean sendPlainText(String to, String text) {
        return sendPlainTextResult(to, text).ok;
    }
}
