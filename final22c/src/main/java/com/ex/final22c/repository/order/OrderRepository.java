package com.ex.final22c.repository.order;

import com.ex.final22c.data.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {
}
