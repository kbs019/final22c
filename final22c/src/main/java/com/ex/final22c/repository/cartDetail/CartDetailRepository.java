package com.ex.final22c.repository.cartDetail;

import org.springframework.stereotype.Repository;

import com.ex.final22c.data.cart.CartDetail;

import java.util.*;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface CartDetailRepository extends JpaRepository<CartDetail, Long> {

    // userName 에 해당하는 Users 객체가 가지는 장바구니 물품 갯수
    int countByCartUserUserName(String userName);

    // cartId 에 해당하는 CartDetail 타입의 모든 레코드 조회
    @EntityGraph(attributePaths = {"product","product.brand"})      // fetch.EAGER 방지
    List<CartDetail> findAllByCart_CartId(Long cartId);

    // userName 에 해당하는 Users 객체의 Cart 를 제거
    long deleteByCartDetailIdInAndCart_User_UserName(Collection<Long> ids, String userName);

}
