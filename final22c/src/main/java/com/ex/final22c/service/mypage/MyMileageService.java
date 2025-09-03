package com.ex.final22c.service.mypage;

import java.util.List;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.order.OrderRepository.MileageRow;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyMileageService {

    private final OrderRepository orderRepository;

    private static final List<String> MILEAGE_STATUSES = List.of("PAID", "CONFIRMED", "REFUNDED");

    @Transactional(readOnly = true)
    public Page<OrderRepository.MileageRowWithBalance> getMileageHistory(Long userNo, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1));
        return orderRepository.findMileageWithBalanceByUserAndStatuses(userNo, MILEAGE_STATUSES, pageable);
    }
}