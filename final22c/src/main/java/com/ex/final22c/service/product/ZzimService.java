package com.ex.final22c.service.product;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.productRepository.ProductRepository;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ZzimService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public boolean isZzimed(String userName, Long productId) {
        if (userName == null || productId == null) return false;
        return productRepository.countZzimByUserAndProduct(userName, productId) > 0;
    }

    public void add(String userName, Long productId) {
        Users user = userRepository.findByUserName(userName)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));
        Product product = productRepository.findByIdWithZzimers(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품 없음"));

        boolean already = product.getZzimers().stream()
                .anyMatch(u -> Objects.equals(u.getUserNo(), user.getUserNo()));
        if (already) return; // 멱등

        product.getZzimers().add(user);           // 소유측 수정
        user.getZzimedProducts().add(product);    // 반대편 컬렉션 동기화(메모리 일관성)
    }

    public void remove(String userName, Long productId) {
        Users user = userRepository.findByUserName(userName)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));
        Product product = productRepository.findByIdWithZzimers(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품 없음"));

        boolean removed = product.getZzimers()
                .removeIf(u -> Objects.equals(u.getUserNo(), user.getUserNo()));
        if (removed) {
            user.getZzimedProducts().removeIf(p -> Objects.equals(p.getId(), product.getId()));
        }
    }
    @Transactional(readOnly = true)
    public List<Product> listMyZzim(String userName) {
        Users user = userRepository.findByUserName(userName)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        return List.copyOf(user.getZzimedProducts());
    }
}
