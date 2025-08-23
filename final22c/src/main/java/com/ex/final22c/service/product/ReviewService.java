package com.ex.final22c.service.product;

import java.util.Iterator;
import java.util.List;

import org.springframework.data.domain.Sort;
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

    /**
     * 리뷰 목록 조회 (정렬 지원)
     * sortKey = "recent" → 최신순
     * sortKey = "best"(기본) → 평점 높은 순, 동점 시 최신순
     */
    public List<Review> getReviews(Product product, String sortKey) {
        Sort sort;
        if ("recent".equalsIgnoreCase(sortKey)) {
            sort = Sort.by(Sort.Direction.DESC, "createDate"); // 최신순
        } else {
            sort = Sort.by(Sort.Direction.DESC, "rating")
                       .and(Sort.by(Sort.Direction.DESC, "createDate")); // 추천순
        }
        return reviewRepository.findByProduct(product, sort);
    }

    /** 리뷰 개수 */
    public long count(Product product) {
        return reviewRepository.countByProduct(product);
    }

    /** 평균 평점 */
    public double avg(Product product) {
        return reviewRepository.avgRatingByProduct(product);
    }

    /** 리뷰 작성 */
    @Transactional
    public Review write(Product product, Users writer, int rating, String content) {
        Review r = new Review();
        r.setProduct(product);
        r.setWriter(writer);
        r.setRating(Math.max(1, Math.min(5, rating))); // 1~5 사이 보정
        r.setContent(content);
        return reviewRepository.save(r);
    }

    /** 리뷰 단건 조회 */
    public Review get(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));
    }

    /** 좋아요 토글 (추가/제거) */
    @Transactional
    public boolean toggleLike(Long reviewId, Users actor) {
        if (actor == null) throw new AccessDeniedException("로그인이 필요합니다.");
        Review r = get(reviewId);

        // 이미 눌렀는지 사용자명으로 판별
        boolean already = r.getLikers().stream()
                .anyMatch(u -> actor.getUserName().equals(u.getUserName()));

        if (already) {
            // 안전하게 제거
            for (Iterator<Users> it = r.getLikers().iterator(); it.hasNext();) {
                Users u = it.next();
                if (actor.getUserName().equals(u.getUserName())) {
                    it.remove();
                    break;
                }
            }
            return false; // 좋아요 취소됨
        } else {
            r.getLikers().add(actor);
            return true; // 좋아요 추가됨
        }
    }

    /** 특정 리뷰의 좋아요 수 */
    public long getLikeCount(Long reviewId) {
        return get(reviewId).getLikers().size();
    }

    /** 권한 체크 (작성자 or 관리자) */
    private void authorize(Review r, Users actor) {
        if (actor == null) throw new AccessDeniedException("로그인이 필요합니다.");
        boolean isOwner = r.getWriter() != null && actor.getUserName().equals(r.getWriter().getUserName());
        boolean isAdmin = actor.getRole() != null && actor.getRole().equalsIgnoreCase("ADMIN");
        if (!(isOwner || isAdmin)) throw new AccessDeniedException("권한이 없습니다.");
    }

    /** 리뷰 수정 */
    @Transactional
    public void update(Long reviewId, Users actor, int rating, String content) {
        Review r = get(reviewId);
        authorize(r, actor);
        r.setRating(Math.max(1, Math.min(5, rating)));
        r.setContent(content);
        // JPA Dirty Checking 으로 자동 업데이트
    }

    /** 리뷰 삭제 */
    @Transactional
    public Long delete(Long reviewId, Users actor) {
        Review r = get(reviewId);
        authorize(r, actor);
        Long productId = r.getProduct().getId();
        reviewRepository.delete(r);
        return productId;
    }
}
