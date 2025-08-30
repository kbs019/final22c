package com.ex.final22c.service.product;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.productRepository.ProductRepository;
import com.ex.final22c.repository.user.UserRepository;

import groovyjarjarantlr4.v4.parse.ANTLRParser.id_return;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ZzimService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * 관심등록 토글. true=현재 on(추가됨), false=현재 off(삭제됨)
     * DTO 없이 boolean만 반환한다.
     */
    /** 찜 토글: 등록/해제 후 (picked, count) 반환 */
    @Transactional
    public Map<String, Object> toggle(Long id, String userName) {
        Product p = productRepository.findByIdWithZzimers(id)
                .orElseThrow(() -> new IllegalArgumentException("상품이 존재하지 않습니다. id=" + id));

        Users u = userRepository.findByUserName(userName)
                .orElseThrow(() -> new IllegalArgumentException("사용자가 존재하지 않습니다. username=" + userName));

        if (p.getZzimers() == null) p.setZzimers(new LinkedHashSet<>());
        if (u.getZzimedProducts() == null) u.setZzimedProducts(new LinkedHashSet<>());

        boolean already = p.getZzimers().contains(u);
        boolean picked;

        if (already) {
            // 해제
            p.getZzimers().remove(u);          // owning side = Product.zzimers
            u.getZzimedProducts().remove(p);   // inverse side 동기화
            picked = false;
        } else {
            // 등록
            p.getZzimers().add(u);
            u.getZzimedProducts().add(p);
            picked = true;
        }

        productRepository.save(p);             // owning side 저장이 핵심
        int count = p.getZzimers().size();

        return Map.of("picked", picked, "count", count);
    }

    /** 특정 상품의 찜 개수 (쿼리 기반) */
    @Transactional(readOnly = true)
    public int count(Long id) {
        return Math.toIntExact(productRepository.countZzimers(id));
    }

    /** 사용자가 이 상품을 찜했는지 여부 (간단 확인) */
    @Transactional(readOnly = true)
    public boolean isZzimedBy(Long id, String userName) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품이 존재하지 않습니다. id=" + id));
        Users u = userRepository.findByUserName(userName)
                .orElseThrow(() -> new IllegalArgumentException("사용자가 존재하지 않습니다. username=" + userName));

        return p.getZzimers() != null && p.getZzimers().contains(u);
    }

    /** 내 관심목록(간단 리스트). 페이징 필요 시 Repository 쿼리로 대체 */
    @Transactional(readOnly = true)
    public List<Product> listMyZzim(String userName) {
        return productRepository.findZzimedProductsByUsername(userName);
    }

    /**
     * 뷰 초기값 바인딩용: (내가 찜했는지, 총 개수)
     * 반환값도 Map 사용: { "picked": boolean, "count": int }
     */
    @Transactional(readOnly = true)
    public Map<String, Object> initialState(Long id, String userName) {
        int count = Math.toIntExact(productRepository.countZzimers(id));
        boolean picked = false;

        if (userName != null) {
            picked = isZzimedBy(id, userName);
        }
        return Map.of("picked", picked, "count", count);
    }
}
