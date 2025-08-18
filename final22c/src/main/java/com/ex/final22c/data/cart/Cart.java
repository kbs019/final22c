package com.ex.final22c.data.cart;

import java.beans.Transient;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cart")
@NoArgsConstructor
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
    public CartDetail addItem(Product product, int addQty) {
        if( addQty < 1 ){ addQty = 1; }
        int stock = Math.max(0, product.getCount());

        CartDetail existing = details.stream()
                .filter(d -> d.getProduct().getId().equals(product.getId()))
                .findFirst().orElse(null);
        if (existing != null) {
            existing.setQuantity(Math.min( stock, existing.getQuantity() + addQty));
            return existing;
        }
        CartDetail d = new CartDetail();
        d.setCart(this);
        d.setProduct(product);
        d.setQuantity(Math.max(1, addQty));
        details.add(d);
        return d;
    }

    // productId 로 제거 -- product 의 기본키
    public void removeItemByProductId(Long id) {
        details.removeIf(d -> d.getProduct().getId().equals(id));
    }

    // detailId 로 제거 -- cartDetail 의 기본키
    public void removeItemByDetailId( Long detailId ){
        details.removeIf( d -> d.getCartDetailId() == detailId );
    }

    // 장바구니 총액 계산
    @Transient
    public int getTotalAmount(){
        return details.stream().mapToInt(CartDetail :: getTotalPrice).sum();
    }

    public Cart(Users user) {
        this.user = user;
    }
}
