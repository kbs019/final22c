package com.ex.final22c.repository.orderDetail; 

import com.ex.final22c.data.order.OrderDetail;
import com.ex.final22c.data.product.Product;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
	// 1) 결제 승인 직후: quantity = confirmQuantity
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE OrderDetail SET confirmQuantity = quantity WHERE orderId = :orderId",
	       nativeQuery = true)
	int fillConfirmQtyToQuantity(@Param("orderId") Long orderId);

	/** 집계 결과를 담을 Projection */
    interface ProductSalesView {
        Product getProduct();
        Long getSales();    // SUM(confirmQuantity)
    }

    /** 전체 베스트 (확정 수량 합계 기준) */
    @Query("""
           select d.product as product, sum(d.confirmQuantity) as sales
             from OrderDetail d
            group by d.product
            order by sum(d.confirmQuantity) desc
           """)
    List<ProductSalesView> findAllBest(Pageable pageable);

    /** 성별 베스트 (Users.gender = '남자' / '여자' 등) */
    @Query("""
           select d.product as product, sum(d.confirmQuantity) as sales
             from OrderDetail d
             join d.order o
             join o.user u
            where u.gender = :gender
            group by d.product
            order by sum(d.confirmQuantity) desc
           """)
    List<ProductSalesView> findBestByGender(@Param("gender") String gender, Pageable pageable);

    /** (옵션) 나이대 베스트 — DB별 함수 차이가 있어 native로 분리 권장
     *  예: H2/MySQL: timestampdiff(YEAR, u.birth, current_date) 사용 가능
     *      Oracle   : floor(months_between(sysdate, u.birth)/12)
     *  아래는 MySQL/H2 스타일 예시(nativeQuery).
     */
    @Query(value = """
           select od.id as orderDetailId, p.*, sum(od.confirm_quantity) as sales
             from order_detail od
             join orders o on o.order_id = od.order_id
             join users u on u.user_no = o.user_id
             join product p on p.id = od.id
            where timestampdiff(YEAR, u.birth, current_date) between :ageMin and :ageMax
            group by p.id
            order by sum(od.confirm_quantity) desc
            limit :limit
           """, nativeQuery = true)
    List<Object[]> findBestByAgeRangeNative(@Param("ageMin") int ageMin,
                                            @Param("ageMax") int ageMax,
                                            @Param("limit") int limit);
}

