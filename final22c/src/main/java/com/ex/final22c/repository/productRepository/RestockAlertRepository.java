package com.ex.final22c.repository.productRepository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.product.RestockAlert;

@Repository
public interface RestockAlertRepository extends JpaRepository<RestockAlert, Long> {

    boolean existsByProduct_IdAndUser_UserNoAndStatus(@Param("productId") Long productId, @Param("userNo") Long userNo, @Param("status") String status);

    RestockAlert findTopByProduct_IdAndUser_UserNoAndStatus(@Param("productId") Long productId, @Param("userNo") Long userNo, @Param("status") String status);

    List<RestockAlert> findTop500ByProduct_IdAndStatusOrderByRequestedRegAsc(@Param("productId") Long productId, @Param("status") String status);
}
