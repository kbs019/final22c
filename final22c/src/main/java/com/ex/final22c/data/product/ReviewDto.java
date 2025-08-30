package com.ex.final22c.data.product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
public class ReviewDto {
    private Long reviewId;
    private String brandName;
    private String productName;
    private int rating;
    private String content;
    private String writerName;
    private String createDate;
}
