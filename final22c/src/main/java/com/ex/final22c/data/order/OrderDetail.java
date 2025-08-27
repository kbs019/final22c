package com.ex.final22c.data.order;

import com.ex.final22c.data.product.Product;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "orderDetail",
    uniqueConstraints = {
        @UniqueConstraint(name = "UK_OD_ORDER_PRODUCT", columnNames = {"orderId","id"})
    }
)
@Getter @Setter @NoArgsConstructor
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="orderDetail_seq_gen")
    @SequenceGenerator(name="orderDetail_seq_gen", sequenceName="orderDetail_seq", allocationSize=1)
    @Column(name = "orderDetailId")
    private long orderDetailId;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orderId")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id")
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity; // 사용자 입력 수량(1 이상, 재고 초과 불가)

    // 가격 스냅샷(주문 생성 시 고정)
    @Column(name = "sellPrice", nullable = false, updatable = false)
    private Integer sellPrice; // 결제 시점 단가 스냅샷

    @Column(name = "totalPrice", nullable = false, updatable = false)
    private Integer totalPrice; // quantity * sellPrice 스냅샷

    @Column(name = "confirmQuantity", nullable = false)
    private Integer confirmQuantity;    // 확정 수량

    @PrePersist
    public void prePersist() {
        if (quantity == null || quantity < 1)
            quantity = 1;
        if (sellPrice == null)
            sellPrice = 0;
        if (totalPrice == null)
            totalPrice = quantity * sellPrice;
        if (confirmQuantity == null)
            confirmQuantity = 0;
    }

    // 편의 생성자 (가격 계산 포함)
    // 주문 생성 시점의 "스냅샷" 가격을 고정 저장
    public static OrderDetail of(Product product, int qty) {
        OrderDetail d = new OrderDetail();
        d.setProduct(product);
        d.setQuantity(Math.max(1, qty));
        d.setSellPrice(product.getSellPrice());      // 스냅샷
        d.setTotalPrice(d.getQuantity() * d.getSellPrice()); // 스냅샷
        return d;
    }
}
