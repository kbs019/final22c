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
    private final UserRepository usersRepository; // ★ 추가

    @Transactional
    public Order createPendingOrder(String userId, int perfumeNo, int qty) {
        Users user = usersRepository.findByUserName(userId)
                .orElseThrow(() -> new IllegalStateException("로그인이 필요합니다. userId=" + userId));

        Perfume p = perfumeService.getPerfume(perfumeNo);
        if (p == null) throw new IllegalArgumentException("상품 없음: " + perfumeNo);

        int unit  = p.getSellPrice();
        int total = unit * Math.max(qty, 1);

        Order order = new Order();
        order.setUser(user);            // ★ FK(userNo) 채움 → ORA-01400 해결
        order.setTotalAmount(total);    // ★ NOT NULL

        return orderRepository.save(order);
    }

    // 로그인 없이 테스트만 급할 때(실존 userNo 필요)
    @Transactional
    public Order createPendingOrderForTest(long userNo, int perfumeNo, int qty) {
        Perfume p = perfumeService.getPerfume(perfumeNo);
        int total = p.getSellPrice() * Math.max(qty, 1);

        Order order = new Order();
        order.setUser(usersRepository.getReferenceById(userNo)); // ★ FK만 세움
        order.setTotalAmount(total);
        return orderRepository.save(order);
    }
}