package com.ex.final22c.service.cart;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.cart.Cart;
import com.ex.final22c.data.cart.CartDetail;
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

    /** 장바구니 목록 조회 */ 
    @Transactional(readOnly = true)
    public List<CartLine> findMyCart(String userName){
        Cart cart = cartRepository.findByUser_UserName(userName)
                .orElseThrow(() -> new IllegalStateException("장바구니가 없습니다."));
        return cartDetailRepository.findAllByCart_CartIdOrderByCreateDateDesc(cart.getCartId())
                .stream()
                .map(CartLine::from) 
                .toList();
    }

    // ==================================================================================================================================================
    
    public record SelectionItem(Long cartDetailId, int quantity) {}  // ← 서비스가 받는 DTO

    @Transactional(readOnly = true)
    public CheckoutSummary prepareCheckout(String username, List<SelectionItem> items){
        int total = 0;
        for (SelectionItem it : items) {
            CartDetail d = cartDetailRepository.findById(it.cartDetailId())
                    .orElseThrow();
            if (!d.getCart().getUser().getUserName().equals(username))
                throw new SecurityException("본인 장바구니 아님");
            total += d.getUnitPrice() * it.quantity();
        }
        return new CheckoutSummary(items, total);
    }

    public record CheckoutSummary(List<SelectionItem> items, int total) {}

    // ================================================================================
    @Transactional
    public int removeAll(String username, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        Set<Long> targetIds = ids.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        long deleted = cartDetailRepository
            .deleteByCartDetailIdInAndCart_User_UserName(targetIds, username);
        return (int) deleted;
    }
    
    // 체크아웃 뷰
    // 선택 항목으로 체크아웃 화면에 뿌릴 라인/합계(배송비 3,000 포함) 구성
    @Transactional(readOnly = true)
    public CartView prepareCheckoutView(String username, List<SelectionItem> items){
        if (items == null || items.isEmpty()) return CartView.of(List.of());

        // 요청 수량 매핑
        var qtyMap = items.stream().collect(Collectors.toMap(
            SelectionItem::cartDetailId,
            it -> Math.max(1, it.quantity()),
            (a,b)->b,
            LinkedHashMap::new
        ));

        // 선택한 cartDetail 일괄 조회
        var details = cartDetailRepository.findAllById(qtyMap.keySet());

        // 소유권/수량 보정/단가 반영 → CartLine으로 변환
        List<CartLine> lines = details.stream().map(d -> {
            if (!d.getCart().getUser().getUserName().equals(username)) {
                throw new SecurityException("본인 장바구니 아님: " + d.getCartDetailId());
            }
            int reqQty   = qtyMap.getOrDefault(d.getCartDetailId(), 1);
            int stock    = Math.max(0, d.getProduct().getCount());
            int finalQty = Math.min(Math.max(1, reqQty), Math.max(1, stock));

            var p    = d.getProduct();
            int unit = d.getUnitPrice(); // 스냅샷 있으면 그 값, 아니면 Product.sellPrice

            return CartLine.builder()
                .cartDetailId(d.getCartDetailId())
                .id(p.getId())
                .name(p.getName())
                .brand(p.getBrand().getBrandName())
                .unitPrice(unit)
                .quantity(finalQty)
                .imgPath(p.getImgPath())
                .imgName(p.getImgName())
                .build();
        }).toList();

        // CartView.of()가 subtotal + (비어있지 않으면 3,000) 계산
        return CartView.of(lines);
    }
}