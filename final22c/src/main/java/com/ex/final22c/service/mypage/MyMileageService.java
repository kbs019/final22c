package com.ex.final22c.service.mypage;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ex.final22c.data.user.MileageUsageDto;
import com.ex.final22c.repository.mypage.MileageRepository;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyMileageService {

    private final OrderRepository orderRepository;
    private final MileageRepository mileageRepository;
    private final UserRepository userRepository;
    
    private static final List<String> MILEAGE_STATUSES = List.of("PAID", "CONFIRMED", "REFUNDED");

    // @Transactional(readOnly = true)
    // public Page<OrderRepository.MileageRowWithBalance> getMileageHistory(Long userNo, int page, int size) {
    //     Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1));
    //     return orderRepository.findMileageWithBalanceByUserAndStatuses(userNo, MILEAGE_STATUSES, pageable);
    // }
    
    public List<MileageUsageDto> getMileageUsage(Long userId) {
        return mileageRepository.findMileageUsage(userId);
    }

    public Long getUserIdByUsername(String username) {
        return userRepository.findByUserName(username)
                             .map(u -> u.getUserNo())
                             .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

}