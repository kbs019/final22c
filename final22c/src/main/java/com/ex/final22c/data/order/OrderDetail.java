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
    private int orderDetailId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orderId")
    private Order order;

    // FK 컬럼명이 실제 DB에서 'id'라면 유지
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id")
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "sellPrice", nullable = false)
    private Integer sellPrice;

    @Column(name = "totalPrice", nullable = false)
    private Integer totalPrice;

    @PrePersist
    public void prePersist() {
        if (quantity == null || quantity < 1) quantity = 1;
        if (sellPrice == null) sellPrice = 0;
        if (totalPrice == null) totalPrice = quantity * sellPrice;
    }

    // 편의 생성자 (가격 계산 포함)
    public static OrderDetail of(Order order, Product product, int qty) {
        OrderDetail d = new OrderDetail();
        d.setOrder(order);
        d.setProduct(product);
        d.setQuantity(Math.max(1, qty));
        d.setSellPrice((int) Math.round(product.getPrice() * 0.7)); // 반올림
        d.setTotalPrice(d.getQuantity() * d.getSellPrice());
        return d;
    }
}
