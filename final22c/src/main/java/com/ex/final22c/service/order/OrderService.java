package com.ex.final22c.service.order;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.product.ProductService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final UserRepository usersRepository; // ★ 추가

    @Transactional
    public Order createPendingOrder(String userId, long id, int qty) {
        Users user = usersRepository.findByUserName(userId)
                .orElseThrow(() -> new IllegalStateException("로그인이 필요합니다. userId=" + userId));

        Product p = productService.getProduct(id);
        if (p == null) throw new IllegalArgumentException("상품 없음: " + id);

        int unit  = (int)(p.getPrice() * 0.7);
        int total = unit * Math.max(qty, 1);

        Order order = new Order();
        order.setUser(user);            // ★ FK(userNo) 채움 → ORA-01400 해결
        order.setTotalAmount(total);    // ★ NOT NULL

        return orderRepository.save(order);
    }

    // 로그인 없이 테스트만 급할 때(실존 userNo 필요)
    @Transactional
    public Order createPendingOrderForTest(long userNo, long id, int qty) {
        Product p = productService.getProduct(id);
        int total = (int)(p.getPrice() * 0.7) * Math.max(qty, 1);

        Order order = new Order();
        order.setUser(usersRepository.getReferenceById(userNo)); // ★ FK만 세움
        order.setTotalAmount(total);
        return orderRepository.save(order);
    }
}