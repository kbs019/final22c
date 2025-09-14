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
import com.ex.final22c.data.user.Users;

public interface ReviewRepository extends JpaRepository<Review, Long> {

  @Query("""
        select r
        from Review r
        where r.product = :product
        and (r.status is null or r.status = 'ACTIVE')
        order by r.createDate desc, r.reviewId desc
      """)
  List<Review> findByProductOrderByCreateDateDesc(@Param("product") Product product); // 최신순

  @Query("""
        select r
        from Review r
        where r.product = :product
        and (r.status is null or r.status = 'ACTIVE')
        order by r.rating desc, r.createDate desc, r.reviewId desc
      """)
  List<Review> findByProductOrderByRatingDescCreateDateDesc(@Param("product") Product product); // 평점순(=평점 우선)

  // ✅ 좋아요순 (동점 시 최신, 그다음 id 내림차순으로 안정화)
  @Query("""
        select r
        from Review r
        left join r.likers lk
        where r.product = :product
        and (r.status is null or r.status = 'ACTIVE')
        group by r
        order by count(lk) desc, r.createDate desc, r.reviewId desc
      """)
  List<Review> findBestByProduct(@Param("product") Product product);

  @Query("""
        select count(*)
        from Review r
        where r.product = :product
        and (r.status is null or r.status = 'ACTIVE' )
      """)
  long countByProduct(@Param("product") Product product);

  @Query("""
        select coalesce(avg(r.rating),0)
        from Review r
        where r.product = :product
        and (r.status is null or r.status = 'ACTIVE')
      """)
  double avgRatingByProduct(@Param("product") Product product);

  // 내가 쓴 리뷰
  @EntityGraph(attributePaths = { "product", "product.brand" })
  Page<Review> findByWriter_UserNo(@Param("userNo") Long userNo, Pageable pageable);

  // 내가 공감한 리뷰(likers M:N)
  @EntityGraph(attributePaths = { "product", "product.brand" })
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

  // 리뷰 작성자
  List<Review> findByWriter(Users writer);

  List<Review> findByWriterAndRatingGreaterThanEqual(Users writer, int rating);

  // ============ 리뷰 평점 옆 막대 그래프 ========================
  // 점수별 개수 (존재하는 구간만 반환)
  @Query("""
    SELECT r.rating, COUNT(r)
    FROM Review r
    WHERE r.product.id = :productId
    AND (r.status IS NULL OR r.status = 'ACTIVE')
    GROUP BY r.rating
  """)
  List<Object[]> countByRating(@Param("productId") Long productId);

  // 평균 & 총개수
  @Query("""
    SELECT AVG(r.rating), COUNT(r)
    FROM Review r
    WHERE r.product.id = :productId
    AND (r.status IS NULL OR r.status = 'ACTIVE')
  """)
  Object[] avgAndTotal(@Param("productId") Long productId);
}
