package com.ex.final22c.repository.cart;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ex.final22c.data.cart.Cart;
import com.ex.final22c.data.user.Users;

public interface CartRepository extends JpaRepository<Cart, Long> {
    // Optional<Cart> findByUserId(Long userId);
    Optional<Cart> findByUserUserName(String userName);

    Optional<Cart> findByUser(Users user);

    @Query("""
        select distinct c
        from Cart c
        left join fetch c.details d
        left join fetch d.product p
        where c.user = :user
    """)
    Optional<Cart> findByUserWithDetails(@Param("user") Users user);
}