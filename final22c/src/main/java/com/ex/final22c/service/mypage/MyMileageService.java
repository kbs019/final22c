package com.ex.final22c.service.mypage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.user.MileageRowWithBalance;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyMileageService {

        private final OrderRepository orderRepository;
        private final UserRepository userRepository;

        @Transactional(readOnly = true)
        public Page<MileageRowWithBalance> getMileageHistory(Long userNo, int page, int size) {

                // 필요한 행만 조회(적립액은 confirmSnapshot만 사용)
                var paid = orderRepository.findPaid(userNo);
                var confirmed = orderRepository.findConfirmed(userNo); // 확정 시각용
                var refdMil = orderRepository.findRefunded(userNo); // 환불로 복구된 포인트
                var refdCash = orderRepository.findRefundedCash(userNo); // 현금 환불 합(시각용)
                var confirmSnap = orderRepository.findConfirmSnapshot(userNo);// orders.confirmMileage 스냅샷

                if (paid.isEmpty() && confirmed.isEmpty() && refdMil.isEmpty() && refdCash.isEmpty())
                        return Page.empty(PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1)));

                // 시각 맵
                Map<Long, LocalDateTime> paidAt = new HashMap<>();
                Map<Long, LocalDateTime> confirmedAt = new HashMap<>();
                Map<Long, LocalDateTime> refundedAt = new HashMap<>();

                // 결제 시 사용 포인트(차감)
                Map<Long, Integer> usedAtPay = new HashMap<>();
                for (var r : paid) {
                        usedAtPay.merge(r.getOrderId(), Math.max(r.getUsedRaw(), 0), Integer::sum);
                        paidAt.putIfAbsent(r.getOrderId(), r.getWhen());
                }
                for (var r : confirmed) {
                        confirmedAt.putIfAbsent(r.getOrderId(), r.getWhen());
                }

                // 환불 복구 포인트 / 환불 현금(최신시각)
                Map<Long, Integer> refundMileage = new HashMap<>();
                for (var r : refdMil) {
                        refundMileage.merge(r.getOrderId(), Math.max(r.getEarnRaw(), 0), Integer::sum);
                        refundedAt.merge(r.getOrderId(), r.getWhen(), (a, b) -> a.isAfter(b) ? a : b);
                }
                for (var r : refdCash) {
                        // 현금 환불액은 적립 계산에 쓰지 않지만, 환불 시각의 기준으로 반영
                        refundedAt.merge(r.getOrderId(), r.getWhen(), (a, b) -> a.isAfter(b) ? a : b);
                }

                // 확정 적립 스냅샷(실제 지급액)
                Map<Long, Integer> confirmMileageSnap = new HashMap<>();
                for (var r : confirmSnap) {
                        confirmMileageSnap.put(r.getOrderId(), Math.max(r.getEarnRaw(), 0));
                }

                // 대상 주문ID 모으기
                Set<Long> orderIds = new HashSet<>();
                orderIds.addAll(usedAtPay.keySet());
                orderIds.addAll(paidAt.keySet());
                orderIds.addAll(confirmedAt.keySet());
                orderIds.addAll(refundMileage.keySet());
                orderIds.addAll(refundedAt.keySet());
                orderIds.addAll(confirmMileageSnap.keySet());

                List<MileageRowWithBalance> rowsAsc = new ArrayList<>();

                for (Long oid : orderIds) {
                        int usedPaid = usedAtPay.getOrDefault(oid, 0);
                        int refMil = refundMileage.getOrDefault(oid, 0);

                        // (A) 결제 차감
                        if (usedPaid > 0) {
                                rowsAsc.add(MileageRowWithBalance.builder()
                                                .orderId(oid)
                                                .processedAt(paidAt.get(oid))
                                                .usedPointRaw(usedPaid) // +양수 = 차감
                                                .earnPointVisible(0)
                                                .balanceAt(0)
                                                .status("PAID")
                                                .build());
                        }

                        // (B) 환불 복구(사용포인트 되돌림)
                        if (refMil > 0) {
                                LocalDateTime whenRefund = refundedAt.getOrDefault(
                                                oid, confirmedAt.getOrDefault(oid, paidAt.get(oid)));
                                rowsAsc.add(MileageRowWithBalance.builder()
                                                .orderId(oid)
                                                .processedAt(whenRefund)
                                                .usedPointRaw(-refMil) // -음수 = 복구
                                                .earnPointVisible(0)
                                                .balanceAt(0)
                                                .status("REFUNDED")
                                                .build());
                        }

                        // (C) 적립 — 스냅샷만 사용(산식 fallback 제거)
                        Integer snapEarn = confirmMileageSnap.get(oid);
                        if (snapEarn != null && snapEarn > 0) {
                                LocalDateTime earnWhen = confirmedAt.getOrDefault(
                                                oid, refundedAt.getOrDefault(oid, paidAt.get(oid)));
                                rowsAsc.add(MileageRowWithBalance.builder()
                                                .orderId(oid)
                                                .processedAt(earnWhen)
                                                .usedPointRaw(0)
                                                .earnPointVisible(snapEarn)
                                                .balanceAt(0)
                                                .status("CONFIRMED") // 스냅샷 존재 = 확정
                                                .build());
                        }
                }

                // ===== 정렬 1: 러닝 밸런스 계산용(시간 오름차순 + 차감→복구→적립) =====
                Comparator<MileageRowWithBalance> forRunning = Comparator
                                .comparing(MileageRowWithBalance::getProcessedAt,
                                                Comparator.nullsFirst(Comparator.naturalOrder()))
                                .thenComparingInt(r -> {
                                        if (r.getUsedPointRaw() > 0)
                                                return 1; // 차감
                                        if (r.getUsedPointRaw() < 0)
                                                return 2; // 복구
                                        if (r.getEarnPointVisible() > 0)
                                                return 3;// 적립
                                        return 99;
                                })
                                .thenComparing(MileageRowWithBalance::getOrderId);

                rowsAsc.sort(forRunning);

                // 누적 잔액 계산(현재 잔액과 역산 일치)
                int current = userRepository.findById(userNo)
                                .map(u -> Optional.ofNullable(u.getMileage()).orElse(0))
                                .orElse(0);

                int totalDelta = rowsAsc.stream()
                                .mapToInt(r -> r.getEarnPointVisible() - r.getUsedPointRaw())
                                .sum();

                int running = current - totalDelta;
                for (var r : rowsAsc) {
                        running += (r.getEarnPointVisible() - r.getUsedPointRaw());
                        r.setBalanceAt(running);
                }
                // ===== 정렬 2: 화면 표시용 (주문번호 ↓ → 이벤트시간 ↓ → 적립>복구>차감) =====
                Comparator<MileageRowWithBalance> forDisplay = Comparator
                                // 주문번호 내림차순 (최신 주문이 위)
                                .comparing(MileageRowWithBalance::getOrderId,
                                                Comparator.nullsLast(Comparator.reverseOrder()))
                                // 같은 주문 내에서 이벤트시간 내림차순
                                .thenComparing(MileageRowWithBalance::getProcessedAt,
                                                Comparator.nullsLast(Comparator.reverseOrder()))
                                // 같은 시각이면 적립 → 복구 → 차감
                                .thenComparing(
                                                Comparator.comparingInt((MileageRowWithBalance r) -> {
                                                        if (r.getEarnPointVisible() > 0)
                                                                return 3; // 적립
                                                        if (r.getUsedPointRaw() < 0)
                                                                return 2; // 복구
                                                        if (r.getUsedPointRaw() > 0)
                                                                return 1; // 차감
                                                        return 0;
                                                }).reversed()); // 3 > 2 > 1 순으로

                rowsAsc.sort(forDisplay);

                int pageIndex = Math.max(page - 1, 0);
                int pageSize = Math.max(size, 1);
                int from = Math.min(pageIndex * pageSize, rowsAsc.size());
                int to = Math.min(from + pageSize, rowsAsc.size());
                return new PageImpl<>(rowsAsc.subList(from, to), PageRequest.of(pageIndex, pageSize), rowsAsc.size());
        }
}
