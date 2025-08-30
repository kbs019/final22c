package com.ex.final22c.service.product;

import java.util.List;
import java.util.Iterator;

import org.springframework.security.access.AccessDeniedException;
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

    public Review get(Long reviewId) {
        return reviewRepository.findById(reviewId)
            .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));
    }

    @Transactional
    public boolean toggleLike(Long reviewId, Users actor) {
        if (actor == null) throw new AccessDeniedException("로그인이 필요합니다.");
        Review r = get(reviewId);

        // 이미 눌렀는지 사용자명으로 판별(동일 영속성 컨텍스트가 아닐 수도 있으므로)
        boolean already = r.getLikers().stream()
                .anyMatch(u -> actor.getUserName().equals(u.getUserName()));

        if (already) {
            // 안전 제거
            for (Iterator<Users> it = r.getLikers().iterator(); it.hasNext();) {
                Users u = it.next();
                if (actor.getUserName().equals(u.getUserName())) {
                    it.remove();
                    break;
                }
            }
            return false; // now unliked
        } else {
            r.getLikers().add(actor);
            return true;  // now liked
        }
    }

    public long getLikeCount(Long reviewId) {
        return get(reviewId).getLikers().size();
    }

    private void authorize(Review r, Users actor) {
        if (actor == null) throw new AccessDeniedException("로그인이 필요합니다.");
        boolean isOwner = r.getWriter() != null && actor.getUserName().equals(r.getWriter().getUserName());
        boolean isAdmin = actor.getRole() != null && actor.getRole().equalsIgnoreCase("ADMIN");
        if (!(isOwner || isAdmin)) throw new AccessDeniedException("권한이 없습니다.");
    }

    @Transactional
    public void update(Long reviewId, Users actor, int rating, String content) {
        Review r = get(reviewId);
        authorize(r, actor);
        r.setRating(Math.max(1, Math.min(5, rating)));
        r.setContent(content);
        // JPA dirty checking으로 자동 반영
    }

    @Transactional
    public Long delete(Long reviewId, Users actor) {
        Review r = get(reviewId);
        authorize(r, actor);
        Long productId = r.getProduct().getId();
        reviewRepository.delete(r);
        return productId;
    }
}
