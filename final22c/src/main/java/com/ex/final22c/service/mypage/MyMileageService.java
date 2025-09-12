package com.ex.final22c.service.mypage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.ex.final22c.data.user.MileageUsageDto;
import com.ex.final22c.data.user.MileageUsageProjection;
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
    
    public Page<MileageUsageDto> getMileageUsageWithBalance(String username, Pageable pageable) {
        Long userId = getUserIdByUsername(username);

        // 1. 전체 데이터 조회
        List<MileageUsageProjection> allProjections = mileageRepository.findAllMileageUsageByUserId(userId);

        // 2. 오래된 순으로 누적 계산
        List<MileageUsageProjection> forCalc = new ArrayList<>(allProjections);
        Collections.reverse(forCalc);

        List<MileageUsageDto> dtoList = new ArrayList<>();
        int cumulativeBalance = 0;
        for (MileageUsageProjection p : forCalc) {
            cumulativeBalance += p.getConfirmedMileage() + p.getRefundMileage() + p.getRefundRebate() - p.getUsedPoint();

            MileageUsageDto dto = new MileageUsageDto();
            dto.setRegDate(p.getRegDate());
            dto.setUsedPoint(p.getUsedPoint());
            dto.setConfirmedMileage(p.getConfirmedMileage());
            dto.setRefundMileage(p.getRefundMileage());
            dto.setRefundRebate(p.getRefundRebate());
            dto.setDescription(p.getDescription());
            dto.setBalance(cumulativeBalance);

            dtoList.add(dto);
        }

        // 3. 최신순으로 정렬 (역순)
        Collections.reverse(dtoList);

        // 4. Page 단위로 잘라서 반환
        int start = Math.min((int) pageable.getOffset(), dtoList.size());
        int end = Math.min(start + pageable.getPageSize(), dtoList.size());
        List<MileageUsageDto> pageContent = dtoList.subList(start, end);

        return new PageImpl<>(pageContent, pageable, dtoList.size());
    }

    private Long getUserIdByUsername(String username) {
        return userRepository.findByUserName(username)
                .map(u -> u.getUserNo())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }
    
    // 3️⃣ userId로 마일리지 조회
    public Integer getMileageByUserName(String username) {
    	Long userId = getUserIdByUsername(username);
    	return userRepository.findById(userId)
                .map(u -> u.getMileage())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

}