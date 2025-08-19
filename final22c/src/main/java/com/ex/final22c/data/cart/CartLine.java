package com.ex.final22c.data.cart;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CartLine {
    private Long cartDetailId;          // 장바구니 속 하나의 행을 식별하는 번호
    private Long id;                    // 하나의 행에 속하는 상품을 식별하는 번호
    private String name;                // 상품명
    private int unitPrice;              // 현재의 단가
    private int quantity;               // 선택된 수량
    private String brand;               // 해당 상품의 브랜드
    private String imgPath;             // 해당 상품의 이미지 출력 경로
    private String imgName;             // 해당 상품의 이미지명
    private int stock;                  // 해당 상품의 현재 남은 재고

    // 파생값 -- 라인 합계
    public int getLineTotal(){
        return unitPrice * quantity;
    }
    
    // ▼ 변경: static 팩토리 메서드로 공개
    public static CartLine from(CartDetail d) {
        return CartLine.builder()
            .cartDetailId(d.getCartDetailId())
            .id(d.getProduct().getId())
            .name(d.getProduct().getName())
            .brand(d.getProduct().getBrand().getBrandName())
            .unitPrice(d.getUnitPrice())          // 스냅샷 단가
            .quantity(d.getQuantity())
            .imgPath(d.getProduct().getImgPath())
            .imgName(d.getProduct().getImgName())
            .stock(d.getProduct().getCount())
            .build();
    }


}
