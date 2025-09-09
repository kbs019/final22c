package com.ex.final22c.service.mypage;

import java.util.*;
import java.util.stream.Collectors;

import java.time.LocalDateTime;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.user.MileageRowWithBalance;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.order.OrderRepository.MileageRowBare;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyMileageService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<MileageRowWithBalance> getMileageHistory(Long userNo, int page, int size) {

        // 1) 이벤트 분리 조회 (그대로)
        List<com.ex.final22c.data.user.Row> paid = orderRepository.findPaid(userNo);
        List<com.ex.final22c.data.user.Row> conf = orderRepository.findConfirmed(userNo);
        List<com.ex.final22c.data.user.Row> refd = orderRepository.findRefunded(userNo);

        // 2) 주문별로 묶어서 최종 상태 1건으로 합치기
        record Agg(Long orderId, // 원자료 모으는 임시 레코드
                Integer used, Integer confirmedEarn, Integer refundEarn,
                LocalDateTime paidAt, LocalDateTime confirmedAt, LocalDateTime refundedAt) {
        }

        Map<Long, Agg> agg = new HashMap<>();

        // 결제 사용
        for (var r : paid) {
            agg.merge(r.getOrderId(),
                    new Agg(r.getOrderId(), r.getUsedRaw(), 0, 0, r.getWhen(), null, null),
                    (a, b) -> new Agg(
                            a.orderId(),
                            (a.used() == null ? 0 : a.used()) + (b.used() == null ? 0 : b.used()),
                            a.confirmedEarn(), a.refundEarn(),
                            a.paidAt() == null ? b.paidAt() : a.paidAt(),
                            a.confirmedAt(), a.refundedAt()));
        }
        // 구매확정 적립
        for (var r : conf) {
            agg.merge(r.getOrderId(),
                    new Agg(r.getOrderId(), 0, r.getEarnRaw(), 0, null, r.getWhen(), null),
                    (a, b) -> new Agg(
                            a.orderId(),
                            a.used(),
                            (a.confirmedEarn() == null ? 0 : a.confirmedEarn())
                                    + (b.confirmedEarn() == null ? 0 : b.confirmedEarn()),
                            a.refundEarn(),
                            a.paidAt(), a.confirmedAt() == null ? b.confirmedAt() : a.confirmedAt(),
                            a.refundedAt()));
        }
        // 환불 복구
        for (var r : refd) {
            agg.merge(r.getOrderId(),
                    new Agg(r.getOrderId(), 0, 0, r.getEarnRaw(), null, null, r.getWhen()),
                    (a, b) -> new Agg(
                            a.orderId(),
                            a.used(),
                            a.confirmedEarn(),
                            (a.refundEarn() == null ? 0 : a.refundEarn())
                                    + (b.refundEarn() == null ? 0 : b.refundEarn()),
                            a.paidAt(), a.confirmedAt(),
                            // 가장 늦은 환불 시각을 사용(부분환불 여러 번 가능)
                            (a.refundedAt() == null
                                    || (b.refundedAt() != null && b.refundedAt().isAfter(a.refundedAt())))
                                            ? b.refundedAt()
                                            : a.refundedAt()));
        }

        if (agg.isEmpty()) {
            return Page.empty(PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1)));
        }

        // 3) 주문별 최종 1줄 만들기 (REFUNDED > CONFIRMED > PAID)
        List<MileageRowWithBalance> finalsAsc = new ArrayList<>();
        for (Agg a : agg.values()) {
            final int paidUsed = Math.max(Objects.requireNonNullElse(a.used(), 0), 0);
            final int confirmedEarn = Math.max(Objects.requireNonNullElse(a.confirmedEarn(), 0), 0);
            final int refundEarn = Math.max(Objects.requireNonNullElse(a.refundEarn(), 0), 0);

            String status;
            int usedVisible;
            int earnVisible;
            LocalDateTime when;

            if (a.refundedAt() != null) { // 최종: 환불완료(부분환불 포함)
                status = "REFUNDED";
                earnVisible = refundEarn; // 복구 포인트는 +로 노출
                usedVisible = Math.max(paidUsed - refundEarn, 0);// ★ 남은 순사용 (예: 25,605 - 19,758 = 5,847)
                when = a.refundedAt();
            } else if (a.confirmedAt() != null) { // 최종: 구매확정
                status = "CONFIRMED";
                earnVisible = confirmedEarn;
                usedVisible = paidUsed;
                when = a.confirmedAt();
            } else { // 최종: 결제완료
                status = "PAID";
                earnVisible = 0;
                usedVisible = paidUsed;
                when = a.paidAt();
            }

            finalsAsc.add(MileageRowWithBalance.builder()
                    .orderId(a.orderId())
                    .processedAt(when)
                    .usedPointRaw(usedVisible) // ★ 반드시 usedVisible 사용
                    .earnPointVisible(earnVisible)
                    .balanceAt(0)
                    .status(status)
                    .build());
        }

        // 4) 과거→현재 정렬 후 러닝밸런스 계산
        finalsAsc.sort(Comparator
                .comparing(MileageRowWithBalance::getProcessedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(r -> "PAID".equals(r.getStatus()) ? 1 : "CONFIRMED".equals(r.getStatus()) ? 2 : 3));

        int currentMileage = userRepository.findById(userNo)
                .map(u -> Optional.ofNullable(u.getMileage()).orElse(0))
                .orElse(0);

        int totalDelta = finalsAsc.stream().mapToInt(r -> r.getEarnPointVisible() - r.getUsedPointRaw()).sum();
        int running = currentMileage - totalDelta;
        for (var r : finalsAsc) {
            running += (r.getEarnPointVisible() - r.getUsedPointRaw());
            r.setBalanceAt(running);
        }

        // 5) 화면은 최신순 + 페이징
        finalsAsc.sort(Comparator
                .comparing(MileageRowWithBalance::getProcessedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());

        int pageIndex = Math.max(page - 1, 0);
        int pageSize = Math.max(size, 1);
        int from = Math.min(pageIndex * pageSize, finalsAsc.size());
        int to = Math.min(from + pageSize, finalsAsc.size());
        return new PageImpl<>(finalsAsc.subList(from, to), PageRequest.of(pageIndex, pageSize), finalsAsc.size());
    }
}