package com.ex.final22c.data.recommendation;

import lombok.*;

@Builder  
@Getter
@AllArgsConstructor
public class RecommendedProduct {
    private Long productId;
    private String name;
    private String brandName;
    private String volume;
    private Integer price;
    private String reason;
    private String imageUrl;
}