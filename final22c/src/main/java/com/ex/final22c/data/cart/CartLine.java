package com.ex.final22c.data.cart;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CartLine {
    private Long cartDetailId;
    private Long id;
    private String name;
    private String brand;
    private int unitPrice;
    private int quantity;
    private String imgPath;
    private String imgName;

    // 파생값 -- 라인 합계
    public int getLineTotal(){
        return unitPrice * quantity;
    }
    
    // ▼ 변경: static 팩토리 메서드로 공개
    public static CartLine from(com.ex.final22c.data.cart.CartDetail d) {
        return CartLine.builder()
            .cartDetailId(d.getCartDetailId())
            .id(d.getProduct().getId())
            .name(d.getProduct().getName())
            .brand(d.getProduct().getBrand().getBrandName())
            .unitPrice(d.getUnitPrice())          // 스냅샷 단가
            .quantity(d.getQuantity())
            .imgPath(d.getProduct().getImgPath())
            .imgName(d.getProduct().getImgName())
            .build();
    }


}
