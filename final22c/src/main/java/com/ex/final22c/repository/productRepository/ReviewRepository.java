package com.ex.final22c.repository.productRepository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductOrderByCreateDateDesc(Product product);      // 최신순
    List<Review> findByProductOrderByRatingDescCreateDateDesc(Product product); // 추천순(=평점 우선)

    long countByProduct(Product product);

    @Query("select coalesce(avg(r.rating),0) from Review r where r.product = ?1")
    double avgRatingByProduct(Product product);
}
