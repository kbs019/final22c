package com.ex.final22c.service.order;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.perfume.Perfume;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.perfume.PerfumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final PerfumeService perfumeService;
    private final UserRepository usersRepository;

    /** 단건 바로구매 Pending 주문 생성 */
    @Transactional
    public Order createPendingOrder(String userId, int perfumeNo, int qty) {
        // 1) 사용자 조회 (USERNO 채우기 위함)
        Users user = usersRepository.findByUserName(userId)
                .orElseThrow(() -> new IllegalStateException("로그인이 필요합니다. (userId=" + userId + ")"));

        // 2) 상품/금액 계산
        Perfume p = perfumeService.getPerfume(perfumeNo);
        if (p == null) throw new IllegalArgumentException("상품 없음: " + perfumeNo);

        int unit  = p.getSellPrice();
        int total = unit * Math.max(qty, 1);

        // 3) 주문 생성
        Order order = new Order();
        order.setUser(user);              // ★ 필수: FK(userNo) 채워짐
        order.setTotalAmount(total);      // ★ NOT NULL
        // status, regDate는 @PrePersist로 기본값 세팅(PENDING, now)

        return orderRepository.save(order);
    }

    /** 로그인 없이 결제 흐름만 테스트할 때(실존 userNo 필요) */
    @Transactional
    public Order createPendingOrderForTest(long userNo, int perfumeNo, int qty) {
        Perfume p = perfumeService.getPerfume(perfumeNo);
        if (p == null) throw new IllegalArgumentException("상품 없음: " + perfumeNo);

        int total = p.getSellPrice() * Math.max(qty, 1);

        Order order = new Order();
        order.setUser(usersRepository.getReferenceById(userNo)); // FK만 세움
        order.setTotalAmount(total);
        return orderRepository.save(order);
    }
}
