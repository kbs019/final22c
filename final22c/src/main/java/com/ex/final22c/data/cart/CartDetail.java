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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table( name = "cartDetail", uniqueConstraints = { @UniqueConstraint(name = "uk_cart_detail_unique", columnNames = {"cartId", "id"}) } )
public class CartDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cartDetail_seq_gen")
    @SequenceGenerator(name = "cartDetail_seq_gen", sequenceName = "cartDetail_seq", allocationSize = 1)
    @Column(name = "cartDetailId")
    private long cartDetailId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cartId", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @CreationTimestamp
    @Column(name = "createDate", updatable = false)
    private LocalDateTime createDate;

    @Column(name = "sellPrice", nullable = false)
    private int sellPrice;

    @Column(name = "totalPrice", nullable = false)
    private int totalPrice;

    // Quantity 의 set() 변경
    public void setQuantity(int q) {
        this.quantity = Math.max(1, q);
        recalc();
    }

    // sellPrice 의 set() 변경
    public void setSellPrice(int p) {
        this.sellPrice = Math.max(0, p);
        recalc();
    }
    // totalPrice 는 sellPrice * quantity
    public void recalc() { 
        this.totalPrice = this.sellPrice * this.quantity; 
    }

    @PrePersist
    public void prePersist(){
        if (this.sellPrice == 0) {
            // 정책: 예) 판매가=정가*0.9 (프로젝트 정책에 맞게 치환)
            this.sellPrice = (int)Math.floor(this.product.getPrice() * 0.7);
        }
        recalc();
    }
}
