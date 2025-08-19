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
    private int listPrice;              // 해당 상품의 정가 리스트
    private double listDiscount;        // 해당 상품의 할인률 리스트

    // 파생값 -- 라인 합계
    public int getLineTotal(){
        return unitPrice * quantity;
    }

    // 할인률을 정수(%) 로 사용하기 위해 변환
    public int getDiscountPercent(){
        double v = listDiscount;
        if( Double.isNaN(v) || Double.isInfinite(v) || v <= 0 ){
            return 0;
        }

        // 1.0 이상은 '이미 퍼센트'로 간주 -> 소수점 아래 삭제
        // 1.0 미만은 '비율'로 간주 -> 100 곱하고 소수점 아래 삭제
        return ( v > 1.0 ) ? (int) v : (int) ( v * 100.0 );
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
            .listPrice(d.getProduct().getPrice())
            .listDiscount(d.getProduct().getDiscount())
            .build();
    }


}
