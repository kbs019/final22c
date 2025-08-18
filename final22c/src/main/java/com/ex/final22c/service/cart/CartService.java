package com.ex.final22c.service.cart;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.cart.Cart;
import com.ex.final22c.data.cart.CartLine;
import com.ex.final22c.data.cart.CartView;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.cart.CartRepository;
import com.ex.final22c.repository.cartDetail.CartDetailRepository;
import com.ex.final22c.repository.productRepository.ProductRepository;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CartDetailRepository cartDetailRepository;
    private final UserRepository usersRepository;

    @Transactional
    public void addItem(String username, Long productId, int qty) {
        // 1) 수량 보정
        int toAdd = Math.max(1, qty);

        // 2) 상품/재고 확인
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품 없음"));
        int stock = product.getCount();
        if (stock <= 0)
            throw new IllegalStateException("품절");

        // 3) 사용자 조회
        Users user = usersRepository.findByUserName(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        // 4) 장바구니 조회/생성
        Cart cart = cartRepository.findByUser(user).orElseGet(() -> {
            try {
                return cartRepository.save(new Cart(user));
            } catch (DataIntegrityViolationException e) {
                // 유니크 제약 충돌 → 다른 트랜잭션이 먼저 생성
                return cartRepository.findByUser(user).orElseThrow(() -> e);
            }
        });

        // 5) 아이템 추가(재고 상한 클램프는 Cart.addItem에서 처리하도록)
        cart.addItem(product, toAdd);

        // 6) 저장(변경감지로도 반영되지만 명시 저장해도 무방)
        cartRepository.save(cart);
    }

    @Transactional(readOnly = true)
    public int countItems(String userName) {
        return cartDetailRepository.countByCartUserUserName(userName);
    }

    @Transactional(readOnly = true)
    public CartView getCartView(String userName) {
        Users user = usersRepository.findByUserName(userName)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        Cart cart = cartRepository.findByUserWithDetails(user).orElse(null);
        if (cart == null || cart.getDetails().isEmpty()) {
            return CartView.of(Collections.emptyList());
        }

        List<CartLine> lines = cart.getDetails().stream()
                .map(d -> {
                    Product p = d.getProduct();
                    return CartLine.builder()
                            .cartDetailId(d.getCartDetailId())
                            .id(p.getId())
                            .name(p.getName())
                            .unitPrice(p.getSellPrice()) // 스냅샷 미사용
                            .quantity(d.getQuantity())
                            .imgPath(p.getImgPath())
                            .imgName(p.getImgName())
                            .build();
                })
                .collect(Collectors.toList());

        return CartView.of(lines); // subtotal, grandTotal(= subtotal + 3000 or 0) 계산됨
    }
}