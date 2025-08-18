package com.ex.final22c.data.payment.dto;

import java.util.List;

public record PayCartRequest(Integer usedPoint, List<Item> items) {
    public record Item(Long cartDetailId, Integer quantity) {}
}