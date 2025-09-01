package com.ex.final22c.data.recommendation;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class SituationalRecommendation {
    private String situation;
    private List<RecommendedProduct> products;
    private String analysis;
}