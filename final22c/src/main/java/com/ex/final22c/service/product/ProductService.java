package com.ex.final22c.service.product;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // 해당 id 에 대한 상품 정보 조회
    public Product getProduct(long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("해당하는 상품 정보를 찾을 수 없습니다."));
    }

    public List<Map<String, Object>> getBrandOptions()    { return productMapper.selectBrandOptions(); }
    public List<Map<String, Object>> getGradeOptions()    { return productMapper.selectGradeOptions(); }
    public List<Map<String, Object>> getMainNoteOptions() { return productMapper.selectMainNoteOptions(); }
    public List<Map<String, Object>> getVolumeOptions()   { return productMapper.selectVolumeOptions(); }

    // 필터 + 상품명 검색 동시 지원
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
    
    // 결제 승인시 재고 차감
    @Transactional
    public void decreaseStock(Long productId, int qty) {
    	if (qty <=0) return;
    	int updated = productRepository.decreaseStock(productId, qty);
    	if (updated != 1) {
    		throw new IllegalStateException("재고 부족 또는 상품 없음: id=" + productId);
        }
    }
    
    // 추후 취소/환불 구현시 재고 복구
    @Transactional
    public void increaseStock(Long productId, int qty) {
        if (qty <= 0) return;
        int updated = productRepository.increaseStock(productId, qty);
        if (updated != 1) {
            throw new IllegalStateException("재고 복구 실패 또는 상품 없음: id=" + productId);
        }
    }

    
}
