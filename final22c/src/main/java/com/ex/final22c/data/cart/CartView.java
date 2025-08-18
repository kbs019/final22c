package com.ex.final22c.data.cart;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CartView {
    private List<CartLine> lines;
    private int subtotal;
    private int grandTotal;

    // 고정 배송비 3000 원 (장바구니가 비어있다면 0원)
    public static CartView of( List<CartLine> lines){
        int subtotal = lines.stream().mapToInt(CartLine :: getLineTotal).sum();
        int fee = lines.isEmpty() ? 0 : 3000;
        return CartView.builder()
            .lines(lines)
            .subtotal(subtotal)
            .grandTotal(subtotal + fee)
            .build();
    }
}
