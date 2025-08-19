package com.ex.final22c.data.cart;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.ex.final22c.data.product.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cartDetail", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cart_detail_unique", columnNames = { "cartId", "id" }) })
public class CartDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cartDetail_seq_gen")
    @SequenceGenerator(name = "cartDetail_seq_gen", sequenceName = "cartDetail_seq", allocationSize = 1)
    @Column(name = "cartDetailId")
    private Long cartDetailId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cartId", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "sellPrice", nullable = false)
    private int sellPrice;

    @Column(name = "totalPrice", nullable = false)
    private int totalPrice;

    @CreationTimestamp
    @Column(name = "createDate", updatable = false)
    private LocalDateTime createDate;

    // Quantity 의 set() 변경
    public void setQuantity(int quantity) {
        this.quantity = Math.max(1, quantity);
    }

    // 현재 단가 = Product.sellPrice
    @Transient
    public int getUnitPrice() {
        if (sellPrice != 0) {
            return sellPrice;
        }
        return (product != null ? product.getSellPrice() : 0);
    }

    // INSERT/UPDATE 직전 스냅샷 계산
    @PrePersist
    @PreUpdate
    void computeLine() {
        if (quantity < 1) {
            quantity = 1;
        }

        // 스냅샷 NO: 항상 현재 Product의 sellPrice로 재계산
        int current = (product != null ? product.getSellPrice() : 0);
        this.sellPrice = current; // 저장하되 '현재가 기록' 용도로만
        this.totalPrice = current * quantity;
    }
}
