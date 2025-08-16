package com.ex.final22c.data.cart;

import java.time.LocalDateTime;
import java.util.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.Users;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cart")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cart_seq_gen")
    @SequenceGenerator(name = "cart_seq_gen", sequenceName = "cart_seq", allocationSize = 1)
    @Column(name = "cartId")
    private long cartId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "userNo", nullable = false, unique = true)
    private Users user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartDetail> details = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "createDate")
    private LocalDateTime createDate;

    @UpdateTimestamp
    @Column(name = "updateDate")
    private LocalDateTime updateDate;

    /* 편의 메서드: 같은 상품이면 수량만 증가 */
    public CartDetail addItem(Product product, int qty) {
        CartDetail existing = details.stream()
                .filter(d -> d.getProduct().getId().equals(product.getId()))
                .findFirst().orElse(null);
        if (existing != null) {
            existing.setQuantity(Math.max(1, existing.getQuantity() + qty));
            return existing;
        }
        CartDetail d = new CartDetail();
        d.setCart(this);
        d.setProduct(product);
        d.setQuantity(Math.max(1, qty));
        details.add(d);
        return d;
    }

    public void removeItem(Long id) {
        details.removeIf(d -> d.getProduct().getId().equals(id));
    }
}
