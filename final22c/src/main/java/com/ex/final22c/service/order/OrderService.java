package com.ex.final22c.service.order;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.perfume.Perfume;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.service.perfume.PerfumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final PerfumeService perfumeService;

    /** 단건 바로구매 Pending 주문 생성 (필요 최소 필드만) */
    public Order createPendingOrder(String userId, int perfumeNo, int qty) {
        Perfume p = perfumeService.getPerfume(perfumeNo);
        if (p == null) throw new IllegalArgumentException("상품 없음: " + perfumeNo);

        int unit  = p.getSellPrice();
        int total = unit * Math.max(qty, 1);

        Order order = new Order();
        // TODO: 너희 Order 엔티티 필드명에 맞게 세팅
        // order.setUserId(userId);
        // order.setStatus("PENDING");
        // order.setTotalAmount(total);
        // order.addLine(p, qty, unit);  // 라인아이템 엔티티가 있다면

        return orderRepository.save(order);
    }
}
