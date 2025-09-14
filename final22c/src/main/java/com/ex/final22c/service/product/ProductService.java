package com.ex.final22c.service.product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

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

    // ===== 기존(브랜드 상세 등에서 사용) =====
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

    // ===== 리스트 페이지 전용: 정렬 + 페이징(24개 기본) =====
    public Map<String, Object> getProductsPaged(List<Long> brandIds,
                                                List<Long> gradeIds,
                                                List<Long> mainNoteIds,
                                                List<Long> volumeIds,
                                                String keyword,
                                                String sort,
                                                int page,
                                                int size) {
        if (size <= 0) size = 24;
        if (page < 1) page = 1;
        int offset = (page - 1) * size;

        long total = productMapper.countProducts(brandIds, gradeIds, mainNoteIds, volumeIds, keyword);
        List<Map<String, Object>> items = total == 0
                ? List.of()
                : productMapper.selectProductsPaged(brandIds, gradeIds, mainNoteIds, volumeIds, keyword, sort, offset, size);

        long totalPages = (total + size - 1) / size;

        Map<String, Object> res = new HashMap<>();
        res.put("total", total);
        res.put("items", items);
        res.put("totalPages", totalPages);
        res.put("page", page);
        res.put("size", size);
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

    // 이거 public 으로 바꿨는데 이상있으면 얘기해주세요
    public static <T> List<List<T>> chunk(List<T> list, int size) {
        List<List<T>> pages = new ArrayList<>();
        if (list == null || list.isEmpty() || size <= 0) return pages;
        for (int i = 0; i < list.size(); i += size) {
            pages.add(list.subList(i, Math.min(i  + size, list.size())));
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
                        String path = imgPath.trim();
                        if (!path.endsWith("/")) path += "/";
                        item.put("imageUrl", path + imgName.trim());  // DB의 imgPath 그대로 사용
                    } else {
                        item.put("imageUrl", "/img/noimg.png");
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

    // ===================== 추가: 구매자 통계(명수) 차트 데이터 =====================
    public Map<String, Object> getBuyerStatsForChart(Long productId) {
        List<ProductRepository.BuyerStatProjection> rows =
                productRepository.findBuyerStatsByProduct(productId);

        // UI 스케치(상단이 고연령) 기준 버킷 순서
        List<String> ageBuckets = Arrays.asList("50대","40대","30대","20대","10대","기타");

        Map<String, long[]> byGender = new LinkedHashMap<>();
        byGender.put("M", new long[ageBuckets.size()]);
        byGender.put("F", new long[ageBuckets.size()]);

        for (var r : rows) {
            String g = r.getGender() == null ? "" : r.getGender();
            String bucket = r.getAgeBucket() == null ? "기타" : r.getAgeBucket();
            // 50대 이상은 쿼리에서 "50대"로 묶였고, 나머지도 동일 라벨
            int idx = ageBuckets.indexOf(bucket);
            if (idx < 0) idx = ageBuckets.size() - 1;

            long cnt = r.getCnt() == null ? 0L : r.getCnt();

            if ("M".equalsIgnoreCase(g)) {
                byGender.get("M")[idx] += cnt;
            } else if ("F".equalsIgnoreCase(g)) {
                byGender.get("F")[idx] += cnt;
            } else {
                // 성별 미지정은 현재 제외
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("labels", ageBuckets);

        List<Map<String, Object>> datasets = new ArrayList<>();
        for (var entry : byGender.entrySet()) {
            Map<String, Object> ds = new LinkedHashMap<>();
            ds.put("label", "M".equals(entry.getKey()) ? "남성" : "여성");
            long[] arr = entry.getValue();
            List<Long> data = new ArrayList<>(arr.length);
            for (long v : arr) data.add(v);
            ds.put("data", data);
            ds.put("stack", "gender");
            datasets.add(ds);
        }
        out.put("datasets", datasets);
        return out;
    }
    
    /**
     * AI 추천용 상품 조회 (재고 있는 상품만)
     */
    public List<Map<String, Object>> getAvailableProductsForAI() {
        return productMapper.selectProducts(null, null, null, null, null)
            .stream()
            .filter(p -> {
                Integer count = (Integer) p.get("count");
                String status = (String) p.get("status");
                return count != null && count > 0 && 
                        (status == null || !"inactive".equals(status));
            })
            .collect(Collectors.toList());
    }

    // =================================== 상품 상세 페이지 상품 추천 캐러셀 ========================================
    /**
     * 같은 브랜드의 다른 상품 추천 (판매량 desc)
     * - 현재 상품으로부터 brandId를 내부 조회
     * - status=ACTIVE, count>0 조건은 쿼리에서 보장
     */
    public List<Object[]> getSameBrandRecommendations(Long productId, Integer limit) {
        int topN = normalizeLimit(limit, 8, 1, 20);

        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품: " + productId));

        Long brandId = p.getBrand().getId();

        return productRepository.findSameBrandSoldDesc(brandId, productId, topN);
    }

    /**
     * 이번 주 판매량 TOP N (4×2 캐러셀의 경우 N=8 권장)
     * - excludeId는 null 허용(현재 상품 제외 안함)
     * - status=ACTIVE, count>0 조건은 쿼리에서 보장
     */
    public List<Object[]> getRecentTopSold(Integer limit, Long excludeProductId) {
        int topN = normalizeLimit(limit, 8, 1, 20);
        return productRepository.findRecent7DaysTopSold(topN, excludeProductId);
    }

    public List<Object[]> getAllTimeTopSold(Integer limit, Long excludeProductId) {
        int topN = normalizeLimit(limit, 8, 1, 20);
        return productRepository.findAllTimeTopSold(topN, excludeProductId);
    }

    // ---- 내부 유틸 ----
    private int normalizeLimit(Integer input, int defaultVal, int min, int max) {
        int v = (input == null) ? defaultVal : input;
        if (v < min) v = min;
        if (v > max) v = max;
        return v;
    }
}
