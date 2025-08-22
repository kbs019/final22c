package com.ex.final22c.service.product;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.Review;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.productRepository.ReviewRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public List<Review> getReviews(Product product, String sort) {
        if ("recent".equalsIgnoreCase(sort)) {
            return reviewRepository.findByProductOrderByCreateDateDesc(product);
        }
        // default: "best"(추천순 = 평점 높은 순)
        return reviewRepository.findByProductOrderByRatingDescCreateDateDesc(product);
    }

    public long count(Product product) {
        return reviewRepository.countByProduct(product);
    }

    public double avg(Product product) {
        return reviewRepository.avgRatingByProduct(product);
    }

    @Transactional
    public Review write(Product product, Users writer, int rating, String content) {
        Review r = new Review();
        r.setProduct(product);
        r.setWriter(writer);
        r.setRating(Math.max(1, Math.min(5, rating)));
        r.setContent(content);
        return reviewRepository.save(r);
    }
}
