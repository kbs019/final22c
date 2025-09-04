package com.ex.final22c.repository.purchaseRepository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.purchase.Purchase;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase,Long>{
	 List<Purchase> findByRegBetweenOrderByRegDesc(LocalDateTime start, LocalDateTime end);
}
