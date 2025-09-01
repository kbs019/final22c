package com.ex.final22c.service.product;


import java.util.List;
import java.util.Collections;

import java.util.Iterator;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.Review;
import com.ex.final22c.data.product.ReviewDto;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.productRepository.ReviewRepository;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProfanityFilter profanityFilter;
    private final UserRepository userRepository;

    public List<ReviewDto> getReviews(Product product, String sort) {
        List<Review> reviews;


        if ("recent".equalsIgnoreCase(sort)) {
            reviews = reviewRepository.findByProductOrderByCreateDateDesc(product);
        } else {
            reviews = reviewRepository.findByProductOrderByRatingDescCreateDateDesc(product);
        }

        // DTO로 변환 + 필터 적용
        return reviews.stream()
                .<ReviewDto>map(rv -> toDto(rv, profanityFilter))
                .collect(Collectors.toList());
    }
    private ReviewDto toDto(Review review, ProfanityFilter filter) {
        return new ReviewDto(
                review.getReviewId(),
                review.getRating(),
                filter.maskProfanity(review.getContent()), // 욕설 * 처리
                review.getWriter().getUserName(),
                review.getCreateDate(), // 필요하면 포맷 적용
                review.getLikers().stream()
                        .map(u -> u.getUserName())
                        .collect(Collectors.toSet()) // 공감 사용자 이름 집합
        );
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
        if (actor == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        Review r = get(reviewId);

        // PK(userNo)로 비교해야 안전
        boolean already = r.getLikers().stream()
                .anyMatch(u -> u.getUserNo() != null && u.getUserNo().equals(actor.getUserNo()));

        if (already) {
            // 안전 제거(Iterator 사용)
            for (Iterator<Users> it = r.getLikers().iterator(); it.hasNext();) {
                Users u = it.next();
                if (u.getUserNo() != null && u.getUserNo().equals(actor.getUserNo())) {
                    it.remove();
                    break;
                }
            }
            return false; // now unliked
        } else {
            r.getLikers().add(actor);
            return true; // now liked
        }
    }

    public long getLikeCount(Long reviewId) {
        return get(reviewId).getLikers().size();
    }

    private void authorize(Review r, Users actor) {
        if (actor == null)
            throw new AccessDeniedException("로그인이 필요합니다.");
        boolean isOwner = r.getWriter() != null && actor.getUserName().equals(r.getWriter().getUserName());
        boolean isAdmin = actor.getRole() != null && actor.getRole().equalsIgnoreCase("ADMIN");
        if (!(isOwner || isAdmin))
            throw new AccessDeniedException("권한이 없습니다.");
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
    
    /**
     * 사용자별 리뷰 조회
     */
    public List<Review> getReviewsByUser(String userName) {
        Users user = userRepository.findByUserName(userName)
            .orElse(null);
        if (user == null) return Collections.emptyList();
        
        return reviewRepository.findByWriter(user);
    }
}
