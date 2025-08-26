package com.ex.final22c.service.mypage;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.Review;
import com.ex.final22c.repository.productRepository.ReviewRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MyActivityService {

    private final ReviewRepository reviewRepository;
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    public Map<String, Object> getMyReviews(Long userNo, int page, int size) {
        Page<Review> p = reviewRepository.findByWriter_UserNo(
                userNo, PageRequest.of(page, size, Sort.by("createDate").descending()));
        return pageWrap(p, userNo, true); // 내 리뷰이므로 mine=true
    }

    public Map<String, Object> getMyLikedReviews(Long userNo, int page, int size) {
        Page<Review> p = reviewRepository.findByLikers_UserNo(
                userNo, PageRequest.of(page, size, Sort.by("createDate").descending()));
        return pageWrap(p, userNo, false);
    }

    public boolean unlike(Long userNo, Long reviewId) {
        Review r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));
        boolean removed = r.getLikers().removeIf(u -> Objects.equals(u.getUserNo(), userNo));
        if (removed) reviewRepository.save(r);
        return removed;
    }

    /* ---------- 내부 변환 유틸 ---------- */

    private Map<String, Object> pageWrap(Page<Review> page, Long meUserNo, boolean mineAlways) {
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Review r : page.getContent()) {
            boolean mine = mineAlways || (r.getWriter()!=null && Objects.equals(r.getWriter().getUserNo(), meUserNo));
            cards.add(toCard(r, mine));
        }
        return Map.of(
                "content", cards,
                "page", page.getNumber(),
                "size", page.getSize(),
                "totalPages", page.getTotalPages(),
                "totalElements", page.getTotalElements()
        );
    }

    private Map<String, Object> toCard(Review r, boolean mine) {
        Product p = r.getProduct();
        Long productId     = (p!=null) ? p.getId() : null;
        String productName = (p!=null) ? p.getName() : null;

        String brandName = null;
        if (p != null && p.getBrand() != null) brandName = p.getBrand().getBrandName();

        // 이미지 경로 안전하게 합치기
        String imageUrl = "/img/noimg.png";
        if (p != null) {
            String path = Optional.ofNullable(p.getImgPath()).orElse("");
            if (!path.isBlank() && !path.endsWith("/")) path += "/";
            String name = Optional.ofNullable(p.getImgName()).orElse("");
            if (!name.isBlank()) imageUrl = path + name;
        }

        return Map.of(
            "reviewId", r.getReviewId(),
            "productId", productId,
            "productName", productName,
            "brandName", brandName,
            "imageUrl", imageUrl,
            "rating", r.getRating(),
            "createDate", r.getCreateDate()==null ? "" : r.getCreateDate().format(DTF),
            "content", r.getContent(),
            "mine", mine
        );
    }

    @Transactional(readOnly = true)
    public Map<String,Object> getMyReviewDetail(Long userNo, Long reviewId){
        Review r = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new IllegalArgumentException("리뷰가 존재하지 않습니다."));
        Product p = r.getProduct();

        Map<String,Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("reviewId", r.getReviewId());
        m.put("content", r.getContent());
        m.put("rating", r.getRating());
        m.put("createDate", r.getCreateDate()==null ? "" : r.getCreateDate().format(DTF));

        if (p != null){
            m.put("productId", p.getId());
            m.put("productName", p.getName());
            m.put("brandName", p.getBrand() != null ? p.getBrand().getBrandName() : null);
            String path = Optional.ofNullable(p.getImgPath()).orElse("");
            if (!path.isBlank() && !path.endsWith("/")) path += "/";
            String name = Optional.ofNullable(p.getImgName()).orElse("");
            m.put("imageUrl", name.isBlank() ? "/img/noimg.png" : path + name);
        }
        return m;
    }

    @Transactional
    public Map<String,Object> updateMyReview(Long userNo, Long reviewId, String content, Integer rating){
        Review r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰가 존재하지 않습니다."));
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("내용을 입력해 주세요.");
        }
        r.setContent(content);
        if (rating != null) r.setRating(rating);
        // flush는 @Transactional로 커밋 시 반영

        Map<String,Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("reviewId", r.getReviewId());
        res.put("content", r.getContent());
        res.put("rating", r.getRating());
        return res;
    }

    @Transactional
    public boolean deleteMyReview(Long userNo, Long reviewId){
        Review r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰가 존재하지 않습니다."));
        reviewRepository.delete(r);
        return true;
    }
}