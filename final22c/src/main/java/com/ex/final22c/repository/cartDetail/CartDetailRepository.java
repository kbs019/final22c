package com.ex.final22c.repository.cartDetail;

import org.springframework.stereotype.Repository;

import com.ex.final22c.data.cart.CartDetail;

import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface CartDetailRepository extends JpaRepository<CartDetail, Long> {
    int countByCartUserUserName(String userName);
}
