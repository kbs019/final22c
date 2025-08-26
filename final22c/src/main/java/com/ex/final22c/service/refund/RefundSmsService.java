package com.ex.final22c.service.refund;

import lombok.RequiredArgsConstructor;
import net.nurigo.sdk.message.exception.NurigoMessageNotReceivedException;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.service.DefaultMessageService;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundSmsService {

    private final DefaultMessageService messageService;

    @Value("${sms.from-number}")
    private String from;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final NumberFormat WON = NumberFormat.getInstance(Locale.KOREA);

    /** 전체 승인(거절 없음) 전용 메시지 */
    public void sendFullApproval(
            String to,
            String userName,
            Long orderId,
            int refundAmount,     // 최종 환불 금액
            int approvedQtySum,    // 승인 수량
            LocalDateTime approvedAt
    ) {
        Message m = new Message();
        m.setFrom(from);
        m.setTo(digits(to));

        LocalDateTime when = (approvedAt == null ? LocalDateTime.now() : approvedAt);
        String text = new StringBuilder()
            .append("[환불 처리 안내]\n")
            .append("고객: ").append(userName).append('\n')
            .append("주문번호: ").append(orderId).append('\n')
            .append("환불 결과: 전체 승인 (승인 수량 ").append(approvedQtySum).append("개)\n")
            .append("환불금액: ").append(WON.format(refundAmount)).append("원\n")
            .append("처리일시: ").append(when.format(FMT)).append('\n')
            .append("문의 02-000-0000")
            .toString();

        m.setText(text);
        send(m);
    }

    /** 부분 환불(일부 거절 포함) 전용 메시지 */
    public void sendPartialApproval(
            String to,
            String userName,
            Long orderId,
            int refundAmount,        // 최종 환불 금액(승인 총액)
            int approvedQtySum,       // 승인 수량
            int rejectedQtySum,       // 거절 수량
            LocalDateTime approvedAt,
            String rejectReason      // 거절 사유(있을 때만 본문에 포함)
    ) {
        Message m = new Message();
        m.setFrom(from);
        m.setTo(digits(to));

        LocalDateTime when = (approvedAt == null ? LocalDateTime.now() : approvedAt);
        StringBuilder sb = new StringBuilder()
            .append("[환불 처리 안내]\n")
            .append("고객: ").append(userName).append('\n')
            .append("주문번호: ").append(orderId).append('\n')
            .append("환불 결과: 승인 수량 ").append(approvedQtySum).append("개 / 거절 수량 ").append(rejectedQtySum).append("개\n")
            .append("환불금액: ").append(WON.format(refundAmount)).append("원\n")
            .append("처리일시: ").append(when.format(FMT));

        if (rejectedQtySum > 0 && rejectReason != null && !rejectReason.isBlank()) {
            sb.append("\n[거절사유]\n").append(rejectReason.trim());
        }
        sb.append("\n문의 02-000-0000");

        m.setText(sb.toString());
        send(m);
    }

    /** 단일 진입점(원한다면 사용): 거절 건수로 자동 분기 */
    public void sendAuto(
            String to,
            String userName,
            Long orderId,
            int refundAmount,
            int approvedQtySum,
            int rejectedQtySum,
            LocalDateTime approvedAt,
            String rejectReasonOrNull
    ) {
        if (rejectedQtySum == 0) {
            sendFullApproval(to, userName, orderId, refundAmount, approvedQtySum, approvedAt);
        } else {
            sendPartialApproval(to, userName, orderId, refundAmount, approvedQtySum, rejectedQtySum, approvedAt, rejectReasonOrNull);
        }
    }

    /** 공통 전송 + 예외 래핑 */
    private void send(Message m) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("발신번호 설정(sms.from-number)이 비어 있습니다.");
        }
        try {
            messageService.send(m);
        } catch (NurigoMessageNotReceivedException e) {
            throw new RuntimeException("SMS 전송 실패: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("SMS 전송 중 오류: " + e.getMessage(), e);
        }
    }

    /** 숫자만 추출(하이픈 제거 등) */
    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }
}