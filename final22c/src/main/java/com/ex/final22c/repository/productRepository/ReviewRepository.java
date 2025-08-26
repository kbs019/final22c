package com.ex.final22c.repository.productRepository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductOrderByCreateDateDesc(Product product);      // 최신순
    List<Review> findByProductOrderByRatingDescCreateDateDesc(Product product); // 추천순(=평점 우선)

    long countByProduct(Product product);

    @Query("select coalesce(avg(r.rating),0) from Review r where r.product = ?1")
    double avgRatingByProduct(Product product);

    // 내가 쓴 리뷰
     @EntityGraph(attributePaths = {"product", "product.brand"})
    Page<Review> findByWriter_UserNo(@Param("userNo") Long userNo, Pageable pageable);

    // 내가 공감한 리뷰(likers M:N)
    @EntityGraph(attributePaths = {"product", "product.brand"})
    Page<Review> findByLikers_UserNo(@Param("userNo") Long userNo, Pageable pageable);
    
    // 내가 쓴 리뷰, 공감
    Optional<Review> findByReviewIdAndLikers_UserNo(@Param("reviewId") Long reviewId, @Param("userNo") Long userNo);

     // 내가 쓴 리뷰 (product/brand 미리 로딩)
    @Query("""
      select r from Review r
      join fetch r.product p
      join fetch p.brand b
      where r.writer.userNo = :userNo
      order by r.reviewId desc
    """)
    Page<Review> findMyReviews(@Param("userNo") Long userNo, Pageable pageable);

    // 내가 공감한 리뷰 (product/brand 미리 로딩)
    @Query("""
      select r from Review r
      join r.likers lk
      join fetch r.product p
      join fetch p.brand b
      where lk.userNo = :userNo
      order by r.reviewId desc
    """)
    Page<Review> findMyLikedReviews(@Param("userNo") Long userNo, Pageable pageable);

    Optional<Review> findByReviewIdAndWriter_UserNo(@Param("reviewId") Long reviewId, @Param("userNo") Long userNo);
}
