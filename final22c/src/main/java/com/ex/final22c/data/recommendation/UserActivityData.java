package com.ex.final22c.data.recommendation;

import java.util.List;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.Review;
import com.ex.final22c.data.order.OrderDetail;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityData {
    private List<Product> zzimProducts;
    private List<Review> reviews;
    private List<OrderDetail> purchases;
}