// package com.ex.final22c.service.mypage;

// import java.time.LocalDateTime;
// import java.util.*;
// import org.springframework.data.domain.*;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;

// import com.ex.final22c.data.order.Order;
// import com.ex.final22c.data.refund.Refund;
// import com.ex.final22c.data.user.MileageRowWithBalance;
// import com.ex.final22c.repository.mypage.MileageReadRepository;
// import com.ex.final22c.repository.order.OrderRepository;
// import com.ex.final22c.repository.user.UserRepository;

// import lombok.RequiredArgsConstructor;

// @Service
// @RequiredArgsConstructor
// public class MyMileageService {

//     private final MileageReadRepository mileageReadRepository;
//     private final UserRepository userRepository;
//     private final OrderRepository orderRepository;

//     /**
//      * - 차감: PAID (payment_detail.used_mileage SUM 또는 order.usedPoint 집계 결과)
//      * - 적립: order.confirmMileage 스냅샷(+)
//      * - 환불: 사용포인트 복구 없음, 환불된 금액 비례 적립 회수(−refundItemsTotal × 0.05)
//      * - 누계: 과거→현재 오름차순으로 누적 후, 화면 정렬은 최근순
//      */
//     @Transactional(readOnly = true)
//     public Page<MileageRowWithBalance> getMileageHistory(Long userNo, int page, int size) {

//         List<Order> orders = mileageReadRepository.findOrdersForMileage(userNo);
//         if (orders.isEmpty()) {
//             return Page.empty(PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1)));
//         }

//         // orderId → Order 빠른 조회용
//         Map<Long, Order> orderById = new HashMap<>();
//         for (Order o : orders) {
//             orderById.put(o.getOrderId(), o);
//         }

//         List<MileageRowWithBalance> rows = new ArrayList<>();

//         // (A) 결제 차감
//         for (Order o : orders) {
//             long used = mileageReadRepository.sumUsedMileageOfOrder(o.getOrderId());
//             if (used > 0) {
//                 LocalDateTime paidAt = mileageReadRepository.findPaidAt(o.getOrderId());
//                 rows.add(MileageRowWithBalance.builder()
//                         .orderId(o.getOrderId())
//                         .processedAt(paidAt != null ? paidAt : o.getRegDate())
//                         .usedPointRaw((int) used) // 양수 = 차감
//                         .earnPointVisible(0)
//                         .balanceAt(0)
//                         .status("PAID")
//                         .build());
//             }
//         }

//         // (B) 적립 (확정 스냅샷만)
//         for (Order o : orders) {
//             Integer snap = o.getConfirmMileage();
//             if (snap != null && snap > 0) {
//                 rows.add(MileageRowWithBalance.builder()
//                         .orderId(o.getOrderId())
//                         .processedAt(o.getRegDate()) // 별도 confirm 시각 없으니 regDate 사용
//                         .usedPointRaw(0)
//                         .earnPointVisible(snap) // 양수 = 적립
//                         .balanceAt(0)
//                         .status("CONFIRMED")
//                         .build());
//             }
//         }

//         // (C) 환불: 환불 금액 기준으로 적립 회수
//         List<Refund> refunds = mileageReadRepository.findRefundedByUser(userNo);

//         // 주문별 최신 환불 시각 선택
//         Map<Long, LocalDateTime> latestRefundedAt = new HashMap<>();
//         for (Refund r : refunds) {
//             Long oid = r.getOrder().getOrderId();
//             LocalDateTime when = (r.getUpdateDate() != null) ? r.getUpdateDate() : r.getCreateDate();
//             latestRefundedAt.merge(oid, when, (a, b) -> a.isAfter(b) ? a : b);
//         }

//         for (Map.Entry<Long, LocalDateTime> e : latestRefundedAt.entrySet()) {
//             Long orderId = e.getKey();
//             LocalDateTime when = e.getValue();

//             // 환불된 상품 금액 총합 조회
//             int refundedItemsTotal = orderRepository.sumRefundedItemsOfOrder(orderId);

//             // 환불로 회수해야 할 적립 포인트 = 환불금액 × 0.05
//             int refundedEarn = (int) Math.floor(refundedItemsTotal * 0.05);

//             if (refundedEarn > 0) {
//                 rows.add(MileageRowWithBalance.builder()
//                         .orderId(orderId)
//                         .processedAt(when)
//                         .usedPointRaw(0) // 사용포인트 복구 없음
//                         .earnPointVisible(-refundedEarn) // 환불된 만큼만 회수
//                         .balanceAt(0)
//                         .status("REFUNDED")
//                         .build());
//             }
//         }

//         if (rows.isEmpty()) {
//             return Page.empty(PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1)));
//         }

//         // 1) 누적 계산용 정렬: 시간↑ → orderId↑ → (차감→환불→적립)
//         rows.sort(Comparator
//                 .comparing(MileageRowWithBalance::getProcessedAt,
//                         Comparator.nullsFirst(LocalDateTime::compareTo))
//                 .thenComparing(r -> Optional.ofNullable(r.getOrderId()).orElse(Long.MIN_VALUE))
//                 .thenComparingInt(r -> switch (String.valueOf(r.getStatus())) {
//                     case "PAID" -> 1; // 차감
//                     case "REFUNDED" -> 2; // 환불(적립 회수)
//                     case "CONFIRMED" -> 3; // 적립
//                     default -> 99;
//                 }));

//         // 시작 잔액: DB 보유치
//         int current = userRepository.findById(userNo)
//                 .map(u -> Optional.ofNullable(u.getMileage()).orElse(0))
//                 .orElse(0);

//         // running = 현재 - (모든 행 변화치 합) 부터 시작 → 과거→현재 누적
//         int delta = rows.stream().mapToInt(r -> r.getEarnPointVisible() - r.getUsedPointRaw()).sum();
//         int running = current - delta;

//         for (var r : rows) {
//             running += (r.getEarnPointVisible() - r.getUsedPointRaw());
//             r.setBalanceAt(running);
//         }

//         // 2) 화면 정렬: 시간↓ → orderId↓ → (적립→환불→차감)
//         rows.sort(Comparator
//                 .comparing(MileageRowWithBalance::getProcessedAt,
//                         Comparator.nullsLast(LocalDateTime::compareTo))
//                 .reversed()
//                 .thenComparing((MileageRowWithBalance r) -> Optional.ofNullable(r.getOrderId())
//                                 .orElse(Long.MIN_VALUE),
//                         Comparator.reverseOrder())
//                 .thenComparingInt(r -> {
//                     int rank = switch (String.valueOf(r.getStatus())) {
//                         case "CONFIRMED" -> 3; // 적립
//                         case "REFUNDED" -> 2; // 환불(회수)
//                         case "PAID" -> 1; // 차감
//                         default -> 0;
//                     };
//                     return -rank; // 내림차순
//                 }));

//         // 페이지네이션
//         int pageIndex = Math.max(page - 1, 0);
//         int pageSize = Math.max(size, 1);
//         int from = Math.min(pageIndex * pageSize, rows.size());
//         int to = Math.min(from + pageSize, rows.size());

//         return new PageImpl<>(rows.subList(from, to), PageRequest.of(pageIndex, pageSize), rows.size());
//     }
// }

// package com.ex.final22c.service.mypage;

// import java.time.LocalDateTime;
// import java.util.*;
// import org.springframework.data.domain.*;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;

// import com.ex.final22c.data.order.Order;
// import com.ex.final22c.data.refund.Refund;
// import com.ex.final22c.data.user.MileageRowWithBalance;
// import com.ex.final22c.repository.mypage.MileageReadRepository;
// import com.ex.final22c.repository.user.UserRepository;

// import lombok.RequiredArgsConstructor;

// @Service
// @RequiredArgsConstructor
// public class MyMileageService {

//     private final MileageReadRepository mileageReadRepository;
//     private final UserRepository userRepository;

//     /** 칩(보유 마일리지)용: 환불로 복구된 포인트를 제외한 “보정 현재 마일리지” */
//     @Transactional(readOnly = true)
//     public int getAdjustedCurrentMileage(Long userNo) {
//         int dbNow = userRepository.findById(userNo)
//                 .map(u -> Optional.ofNullable(u.getMileage()).orElse(0))
//                 .orElse(0);

//         // 환불로 복구된 포인트 총합(표시 정책상 제외)
//         int restoredByRefund = mileageReadRepository.findRefundedByUser(userNo).stream()
//                 .mapToInt(r -> Optional.ofNullable(r.getRefundMileage()).orElse(0))
//                 .sum();

//         return Math.max(0, dbNow - restoredByRefund);
//     }

//     /**
//      * 화면 이력
//      * - PAID  : 사용포인트 + (차감)
//      * - CONFIRMED: confirmMileage + (적립)
//      * - REFUNDED : refund.confirmMileage − (적립 회수)
//      * - 환불의 포인트 복구(refund.refundMileage)는 표시/누계 모두 반영하지 않음
//      * - 러닝밸런스: 과거→현재(시간 오름차순)로 누적 후, 화면은 최신순 정렬
//      */
//     @Transactional(readOnly = true)
//     public Page<MileageRowWithBalance> getMileageHistory(Long userNo, int page, int size) {

//         List<Order> orders = mileageReadRepository.findOrdersForMileage(userNo);
//         if (orders.isEmpty()) {
//             return Page.empty(PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1)));
//         }

//         // orderId → Order
//         Map<Long, Order> orderById = new HashMap<>();
//         for (Order o : orders) orderById.put(o.getOrderId(), o);

//         List<MileageRowWithBalance> rows = new ArrayList<>();

//         // (A) 결제 차감: 사용 포인트(+) / 시각 = paidAt(or regDate)
//         for (Order o : orders) {
//             long used = mileageReadRepository.sumUsedMileageOfOrder(o.getOrderId());
//             if (used > 0) {
//                 LocalDateTime paidAt = Optional.ofNullable(mileageReadRepository.findPaidAt(o.getOrderId()))
//                         .orElse(o.getRegDate());
//                 rows.add(MileageRowWithBalance.builder()
//                         .orderId(o.getOrderId())
//                         .processedAt(paidAt)
//                         .usedPointRaw((int) used)   // + = 차감
//                         .earnPointVisible(0)
//                         .balanceAt(0)
//                         .status("PAID")
//                         .build());
//             }
//         }

//         // (B) 구매확정 적립: order.confirmMileage(+) / 시각 = (확정시각 없으므로) regDate 사용
//         for (Order o : orders) {
//             int snap = Optional.ofNullable(o.getConfirmMileage()).orElse(0);
//             if (snap > 0) {
//                 rows.add(MileageRowWithBalance.builder()
//                         .orderId(o.getOrderId())
//                         .processedAt(o.getRegDate())
//                         .usedPointRaw(0)
//                         .earnPointVisible(snap)     // + = 적립
//                         .balanceAt(0)
//                         .status("CONFIRMED")
//                         .build());
//             }
//         }

//         // (C) 환불 완료: refund.confirmMileage(−)만 반영, 사용포인트 복구는 미반영
//         List<Refund> refunds = mileageReadRepository.findRefundedByUser(userNo);
//         for (Refund r : refunds) {
//             Long orderId = (r.getOrder() != null ? r.getOrder().getOrderId() : null);
//             if (orderId == null) continue;

//             int refundConfirmEarn = Optional.ofNullable(r.getConfirmMileage()).orElse(0);
//             if (refundConfirmEarn <= 0) continue; // 회수할 적립이 없으면 스킵

//             LocalDateTime when = Optional.ofNullable(r.getUpdateDate()).orElse(r.getCreateDate());

//             rows.add(MileageRowWithBalance.builder()
//                     .orderId(orderId)
//                     .processedAt(when)
//                     .usedPointRaw(0)
//                     .earnPointVisible(-refundConfirmEarn) // − = 적립 회수
//                     .balanceAt(0)
//                     .status("REFUNDED")
//                     .build());
//         }

//         if (rows.isEmpty()) {
//             return Page.empty(PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1)));
//         }

//         // ===== 러닝밸런스 계산(과거→현재) =====
//         // 시간↑ → orderId↑ → (PAID→REFUNDED→CONFIRMED) 순
//         rows.sort(Comparator
//                 .comparing(MileageRowWithBalance::getProcessedAt, Comparator.nullsFirst(LocalDateTime::compareTo))
//                 .thenComparing(r -> Optional.ofNullable(r.getOrderId()).orElse(Long.MIN_VALUE))
//                 .thenComparingInt(r -> typeRank(r.getStatus())));

//         // 시작 값은 "보정 현재 마일리지"에서 모든 변화량을 뺀 값
//         int adjustedNow = getAdjustedCurrentMileage(userNo);
//         int totalDelta = rows.stream()
//                 .mapToInt(r -> r.getEarnPointVisible() - r.getUsedPointRaw())
//                 .sum();
//         int running = adjustedNow - totalDelta;

//         for (var r : rows) {
//             running += (r.getEarnPointVisible() - r.getUsedPointRaw());
//             r.setBalanceAt(running);
//         }

//         // ===== 화면 정렬(최신순) =====
//         rows.sort(Comparator
//                 .comparing(MileageRowWithBalance::getProcessedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed()
//                 .thenComparing((MileageRowWithBalance r) -> Optional.ofNullable(r.getOrderId()).orElse(Long.MIN_VALUE),
//                         Comparator.reverseOrder())
//                 .thenComparingInt(r -> -typeRank(r.getStatus()))); // CONFIRMED(3) → REFUNDED(2) → PAID(1)

//         // 페이지네이션
//         int pageIndex = Math.max(page - 1, 0);
//         int pageSize = Math.max(size, 1);
//         int from = Math.min(pageIndex * pageSize, rows.size());
//         int to = Math.min(from + pageSize, rows.size());

//         return new PageImpl<>(rows.subList(from, to), PageRequest.of(pageIndex, pageSize), rows.size());
//     }

//     private static int typeRank(String status) {
//         if ("PAID".equalsIgnoreCase(status)) return 1;
//         if ("REFUNDED".equalsIgnoreCase(status)) return 2;
//         if ("CONFIRMED".equalsIgnoreCase(status)) return 3;
//         return 99;
//     }
// }

package com.ex.final22c.service.mypage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.refund.Refund;
import com.ex.final22c.data.refund.RefundDetail;
import com.ex.final22c.data.user.MileageRowWithBalance;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyMileageService {

        private final UserRepository userRepository;

        /** 칩/러닝 시작값: DB 보유치 그대로 */
        @Transactional(readOnly = true)
        public int getAdjustedCurrentMileage(Long userNo) {
                return userRepository.findById(userNo)
                                .map(u -> Optional.ofNullable(u.getMileage()).orElse(0))
                                .orElse(0);
        }
}
