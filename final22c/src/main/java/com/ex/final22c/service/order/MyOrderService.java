package com.ex.final22c.service.order;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.service.user.UsersService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyOrderService {
    private final UsersService usersService;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Page<Order> listMyOrders(String username, int page, int size){
        Users me = usersService.getUser(username);
        Pageable pageable = PageRequest.of(page, size, Sort.by("regDate").descending());
        return orderRepository.findByUser_UserNoAndStatusOrderByRegDateDesc(
            me.getUserNo(), "PAID", pageable
        );
    }

    @Transactional(readOnly = true)
    public List<Order> listMyOrdersWithDetails(String username){
        Users me = usersService.getUser(username);
        return orderRepository.findAllByUser_UserNoAndStatusOrderByRegDateDesc(me.getUserNo(), "PAID");
    }
}
