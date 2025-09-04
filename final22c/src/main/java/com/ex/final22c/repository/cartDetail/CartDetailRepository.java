package com.ex.final22c.repository.cartDetail;

import org.springframework.stereotype.Repository;

import com.ex.final22c.data.cart.Cart;
import com.ex.final22c.data.cart.CartDetail;
import com.ex.final22c.data.product.Product;

import java.util.*;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface CartDetailRepository extends JpaRepository<CartDetail, Long> {

    // userName 에 해당하는 Users 객체가 가지는 장바구니 물품 갯수
    int countByCartUserUserName(String userName);

    // 
    boolean existsByCartUserUserNameAndProductId(@Param("userName") String userName, @Param("id") Long id);

    // cartId 에 해당하는 CartDetail 타입의 모든 레코드 조회
    @EntityGraph(attributePaths = { "product", "product.brand" }) // fetch.EAGER 방지
    List<CartDetail> findAllByCart_CartIdOrderByCreateDateDesc(Long cartId);

    // userName 에 해당하는 Users 객체의 Cart 를 제거
    long deleteByCartDetailIdInAndCart_User_UserName(Collection<Long> ids, String userName);

    // 단건 조회: 소유자 일치 + product/cart/user fetch
    @EntityGraph(attributePaths = { "product", "cart", "cart.user" })
    @Query("""
			select cd
			from CartDetail cd 
			where cd.cartDetailId = :id
			and cd.cart.user.userName = :userName
		""")
    Optional<CartDetail> findByIdAndOwnerFetch(@Param("id") Long id, @Param("userName") String userName);

    // 다건 조회: 체크아웃/일괄 처리 등에 사용
    @EntityGraph(attributePaths = { "product", "cart", "cart.user" })
    @Query("""
            select cd
            from CartDetail cd
            where cd.cart.user.userName = :userName
            and cd.cartDetailId in :ids
        """)
    List<CartDetail> findAllByIdsAndOwnerFetch(@Param("userName") String userName, @Param("ids") List<Long> ids);

    // 선택 삭제
    @Modifying
    @Query("""
            delete from CartDetail cd
            where cd.cart.user.userName = :userName
            and cd.cartDetailId in :ids
        """)
    int deleteByIdsAndOwner(@Param("userName") String userName, @Param("ids") List<Long> ids);
    
    // 선택 라인 결제시 삭제
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    		delete from CartDetail cd
    		where cd.cartDetailId in :ids
    		and cd.cart.user.userNo = :userNo
    	""")
    int deleteByUserNoAndIds(@Param("userNo") Long userNo, @Param("ids") List<Long> ids);
    		
    Optional<CartDetail> findByCartAndProduct(Cart cart, Product product);
    
    
    
    
}
