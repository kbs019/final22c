package com.ex.final22c.service.main;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.repository.orderDetail.OrderDetailRepository;
import com.ex.final22c.repository.orderDetail.OrderDetailRepository.ProductSalesView;
import com.ex.final22c.repository.productRepository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MainPageService {

    private final ProductRepository productRepository;
    private final OrderDetailRepository orderDetailRepository;

    /** Pick 섹션: isPicked='1' 상품 n개 (캐러셀은 프론트에서 4개씩 슬라이드) */
    public List<Product> getPickedProducts(int limit) {
        return productRepository.findByIsPickedOrderByIdDesc("1", PageRequest.of(0, limit));
        // isPicked 값이 'Y'인 프로젝트라면 "Y"로 바꾸세요.
    }

    /** ALL BEST: 판매 확정 수량 기준 상위 n개 */
    public List<ProductSalesView> getAllBest(int limit) {
        return orderDetailRepository.findAllBest(PageRequest.of(0, limit));
    }

    /** WOMAN / MAN BEST */
    public List<ProductSalesView> getBestByGender(String gender, int limit) {
        return orderDetailRepository.findBestByGender(gender, PageRequest.of(0, limit));
    }

    /** (옵션) 나이대 BEST — 필요 시 활성화 */
    // public List<Object[]> getBestByAgeRange(int ageMin, int ageMax, int limit) {
    //     return orderDetailRepository.findBestByAgeRangeNative(ageMin, ageMax, limit);
    // }
}
