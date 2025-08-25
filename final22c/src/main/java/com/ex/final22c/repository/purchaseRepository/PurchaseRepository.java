package com.ex.final22c.repository.purchaseRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.purchase.Purchase;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase,Long>{

}
