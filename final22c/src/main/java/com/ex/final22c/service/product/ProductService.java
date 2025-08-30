package com.ex.final22c.service.product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.DataNotFoundException;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.repository.productMapper.ProductMapper;
import com.ex.final22c.repository.productRepository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

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
    
 // ProductService.java에 추가할 메서드들

    /**
     * 설문 결과 기반 향수 추천 - 메인노트별 상품 조회 (상세 정보 포함)
     * @param mainNoteIds 추천할 메인노트 ID 리스트
     * @param limit 추천할 상품 개수 (기본 6개)
     * @return 추천 상품 상세 정보 Map
     */
    public Map<String, Object> getProductsByMainNotes(List<Long> mainNoteIds, int limit) {
        if (limit <= 0) limit = 6;
        
        // 메인노트별 상품 조회
        long total = productMapper.countProducts(null, null, mainNoteIds, null, null);
        
        List<Map<String, Object>> allItems = total == 0 
            ? List.of()
            : productMapper.selectProducts(null, null, mainNoteIds, null, null);
        
        // 수동으로 limit 적용 (selectProductsPaged가 없으므로)
        List<Map<String, Object>> items = allItems.stream()
            .limit(limit)
            .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("items", items);
        result.put("mainNoteIds", mainNoteIds);
        result.put("success", true);
        result.put("limit", limit);
        
        return result;
    }

    /*
     * 설문 추천용 - 기존 getProducts 메서드를 활용한 간단한 구현
     */
    public List<Map<String, Object>> getRecommendationsByMainNotes(List<Long> mainNoteIds, int limit) {
        if (mainNoteIds == null || mainNoteIds.isEmpty()) {
            return List.of();
        }
        
        try {
            // 기존 getProducts 메서드 활용 (이미 mainNoteIds 필터링 지원)
            Map<String, Object> result = this.getProducts(null, null, mainNoteIds, null, null);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allProducts = (List<Map<String, Object>>) result.get("items");
            
            if (allProducts == null || allProducts.isEmpty()) {
                return List.of();
            }
            
            // limit 적용하고 추천용 간단한 정보만 추출
            return allProducts.stream()
                .limit(limit > 0 ? limit : 6)
                .map(product -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", product.get("id"));
                    item.put("name", product.get("name"));
                    
                    // 브랜드명 처리 (실제 컬럼명에 맞춰 조정)
                    Object brand = product.get("brandName");
                    if (brand == null) brand = product.get("brand_name");
                    if (brand == null) brand = product.get("brand");
                    item.put("brand", brand != null ? brand.toString() : "");
                    
                    item.put("price", product.get("price"));
                    
                    // 이미지 URL 처리
                    String imgPath = (String) product.get("imgPath");
                    String imgName = (String) product.get("imgName");
                    if (imgPath != null && imgName != null && !imgPath.isEmpty() && !imgName.isEmpty()) {
                        item.put("imageUrl", "/upload/" + imgPath + "/" + imgName);
                    } else {
                        item.put("imageUrl", "/img/default-product.png");
                    }
                    
                    // 메인노트명 처리
                    Object mainNote = product.get("mainNoteName");
                    if (mainNote == null) mainNote = product.get("main_note_name");
                    if (mainNote == null) mainNote = product.get("mainNote");
                    item.put("mainNote", mainNote != null ? mainNote.toString() : "");
                    
                    item.put("description", product.get("description"));
                    item.put("count", product.get("count"));
                    
                    return item;
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            System.err.println("메인노트별 추천 상품 조회 실패: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }
    /**
     * 메인노트별 상품 개수만 조회 (설문 검증용)
     * @param mainNoteIds 메인노트 ID 리스트
     * @return 해당 메인노트들의 총 상품 개수
     */
    public long countProductsByMainNotes(List<Long> mainNoteIds) {
        if (mainNoteIds == null || mainNoteIds.isEmpty()) {
            return 0L;
        }
        
        try {
            return productMapper.countProducts(null, null, mainNoteIds, null, null);
        } catch (Exception e) {
            System.err.println("메인노트별 상품 개수 조회 실패: " + e.getMessage());
            return 0L;
        }
    }

    /**
     * 설문 결과 검증 - 추천할 수 있는 상품이 있는지 확인
     * @param mainNoteIds 메인노트 ID 리스트
     * @return 추천 가능 여부
     */
    public boolean hasRecommendableProducts(List<Long> mainNoteIds) {
        return countProductsByMainNotes(mainNoteIds) > 0;
    }
}
