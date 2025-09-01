package com.ex.final22c.data.product;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.ex.final22c.service.product.ProfanityFilter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
public class ReviewDto {
	private Long reviewId;
    private int rating;
    private String content;
    private String userName;
    private LocalDateTime createDate;
    private Set<String> likers; // 좋아요 누른 사용자 이름

    // Review -> ReviewDto 변환
    public static ReviewDto toDto(Review review, ProfanityFilter filter) {
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
}
