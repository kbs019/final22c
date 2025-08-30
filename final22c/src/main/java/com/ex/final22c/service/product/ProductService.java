package com.ex.final22c.service.product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.DataNotFoundException;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.productMapper.ProductMapper;
import com.ex.final22c.repository.productRepository.ProductRepository;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final UserRepository userRepository;

    public List<Product> showList() { return productRepository.findAll(); }

    public Product getProduct(long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("해당하는 상품 정보를 찾을 수 없습니다."));
    }

    public List<Map<String, Object>> getBrandOptions()    { return productMapper.selectBrandOptions(); }
    public List<Map<String, Object>> getGradeOptions()    { return productMapper.selectGradeOptions(); }
    public List<Map<String, Object>> getMainNoteOptions() { return productMapper.selectMainNoteOptions(); }
    public List<Map<String, Object>> getVolumeOptions()   { return productMapper.selectVolumeOptions(); }

    // 필터 + 상품명 검색
    public Map<String, Object> getProducts(List<Long> brandIds,
                                           List<Long> gradeIds,
                                           List<Long> mainNoteIds,
                                           List<Long> volumeIds,
                                           String keyword) {
        long total = productMapper.countProducts(brandIds, gradeIds, mainNoteIds, volumeIds, keyword);
        List<Map<String, Object>> items = productMapper.selectProducts(brandIds, gradeIds, mainNoteIds, volumeIds, keyword);

        Map<String, Object> res = new HashMap<>();
        res.put("total", total);
        res.put("items", items);
        return res;
    }

    // ====== 브랜드 페이지용 ======
    public List<Map<String, Object>> getBrands() {
        return productMapper.selectBrands();
    }

    public Map<String, Object> getBrand(Long brandNo) {
        Map<String, Object> b = productMapper.selectBrandById(brandNo);
        if (b == null) throw new DataNotFoundException("브랜드를 찾을 수 없습니다. brandNo=" + brandNo);
        return b;
    }

    // ====== 재고 증감 (기존 유지) ======
    @Transactional
    public void decreaseStock(Long productId, int qty) {
        if (qty <= 0) return;
        int updated = productRepository.decreaseStock(productId, qty);
        if (updated != 1) throw new IllegalStateException("재고 부족 또는 상품 없음: id=" + productId);
    }

    @Transactional
    public void increaseStock(Long productId, int qty) {
        if (qty <= 0) return;
        int updated = productRepository.increaseStock(productId, qty);
        if (updated != 1) throw new IllegalStateException("재고 복구 실패 또는 상품 없음: id=" + productId);
    }

    // ==== DTO ====
    public record ProductRank(Product product, long sold, int rank) {}

    // PICK: 최신순 12개 -> 4개씩 슬라이드
    public List<List<Product>> getPickSlides(int perSlide) {
        int limit = Math.max(perSlide * 3, perSlide); // 최소 1슬라이드
        List<Product> picks = productRepository.findPicked(PageRequest.of(0, limit));
        return chunk(picks, perSlide);
    }

    // 전체 베스트 TOP N
    public List<ProductRank> getAllBest(int topN) {
        var list = productRepository.findTopAllBest(PageRequest.of(0, topN));
        return toRanks(list);
    }

    // 여성/남성 베스트 TOP N (여성= "2", 남성= "1")
    public List<ProductRank> getGenderBest(String gender, int topN) {
        var list = productRepository.findTopByGender(gender, PageRequest.of(0, topN));
        return toRanks(list);
    }

    // ==== helpers ====
    private List<ProductRank> toRanks(List<ProductRepository.ProductSalesProjection> src) {
        List<ProductRank> out = new ArrayList<>();
        int r = 1;
        for (var it : src) {
            out.add(new ProductRank(it.getProduct(), it.getSold() == null ? 0L : it.getSold(), r++));
        }
        return out;
    }

    private static <T> List<List<T>> chunk(List<T> list, int size) {
        List<List<T>> pages = new ArrayList<>();
        if (list == null || list.isEmpty() || size <= 0) return pages;
        for (int i = 0; i < list.size(); i += size) {
            pages.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return pages;
    }
}
