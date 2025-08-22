package com.ex.final22c.service.order;

import java.security.Principal;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.user.UsersService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyOrderService {
    private final UsersService usersService;
    private final OrderRepository orderRepository;
    private final UserRepository usersRepository;

    /**
     * 마이페이지 목록(페이징): PENDING 제외하고(PAID + CANCELED) 최신순
     */

    @Transactional(readOnly = true)
    public Page<Order> listMyOrders(String username, int page, int size) {
        Users me = usersService.getUser(username);
        Pageable pageable = PageRequest.of(page, size, Sort.by("regDate").descending());


        return orderRepository.findByUser_UserNoAndStatusNotOrderByRegDateDesc(
            me.getUserNo(), "PENDING", pageable);
    }

    /**
     * 세부 포함 전체 리스트(페이징 없음): PENDING 제외하고(PAID + CANCELED) 최신순
     */
    @Transactional(readOnly = true)
    public List<Order> listMyOrdersWithDetails(String username) {
        Users me = usersService.getUser(username);
        return orderRepository.findAllByUser_UserNoAndStatusNotOrderByRegDateDesc(
                me.getUserNo(), "PENDING"
        );
    }

    /**
     * (옵션) 상태 필터를 명시하고 싶을 때: IN 조건 사용
     * 예) ["PAID","CANCELED"] 만 보고 싶을 때
     */
    @Transactional(readOnly = true)
    public List<Order> listMyOrdersByStatuses(String username, Collection<String> statuses) {
        Users me = usersService.getUser(username);
        return orderRepository.findByUser_UserNoAndStatusInOrderByRegDateDesc(
                me.getUserNo(), statuses
        );
    }

    // 주문 확정
    @Transactional
    public void confirmOrder(String username, Long orderId) {
        Users me = usersService.getUser(username);

        int updated = orderRepository.updateToConfirmed(orderId, me.getUserNo() );
        if (updated == 0) {
            throw new IllegalStateException("확정 불가 상태이거나 주문이 없습니다.");
        }
    }
    
    // 주문 확정시 마일리지 지급
    @Transactional
    public Order confirmOrderAndAwardMileage(String username, Long orderId) {
        Users me = usersService.getUser(username);

        int updated = orderRepository.updateToConfirmed(orderId, me.getUserNo());
        if (updated == 0) throw new IllegalStateException("확정 불가 상태이거나 주문을 찾을 수 없습니다.");

        Order order = orderRepository.findOneWithDetails(orderId)
            .orElseThrow(() -> new IllegalStateException("주문을 찾을 수 없습니다."));

        int earnBase = Math.max(0, order.getTotalAmount() - order.getUsedPoint());
        int mileage  = (int) Math.floor(earnBase * 0.05);

        if (mileage > 0) {
            usersRepository.addMileage(me.getUserNo(), mileage);
        }
        return order;
    }
 
    @Transactional(readOnly = true)
    public Order findMyOrderWithDetails(String username, Long orderId) {
        return orderRepository.findOneWithDetailsAndProductByUser(username, orderId)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
    }

}