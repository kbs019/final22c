package com.ex.final22c.service.order;

import org.springframework.stereotype.Service;

import com.ex.final22c.repository.order.OrderMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderTestService {
	private final OrderMapper orderMapper;
}
