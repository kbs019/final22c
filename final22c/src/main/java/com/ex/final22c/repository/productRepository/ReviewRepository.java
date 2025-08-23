package com.ex.final22c.repository.productRepository;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // ✅ Product와 Sort를 함께 받아서 동적 정렬 가능
    List<Review> findByProduct(Product product, Sort sort);

    long countByProduct(Product product);

    @Query("select coalesce(avg(r.rating),0) from Review r where r.product = ?1")
    double avgRatingByProduct(Product product);
}
