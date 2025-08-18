package com.ex.final22c.data.cart;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CartLine {
    private long cartDetailId;
    private long id;
    private String name;
    private int unitPrice;
    private int quantity;
    private String imgPath;
    private String imgName;

    // 파생값 -- 라인 합계
    public int getLineTotal(){
        return unitPrice * quantity;
    }
}
