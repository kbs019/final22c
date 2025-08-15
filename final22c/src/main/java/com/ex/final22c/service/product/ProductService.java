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

    public List<Product> showList() {
        return productRepository.findAll();
    }

    public Product getProduct(long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("해당하는 상품 정보를 찾을 수 없습니다."));
    }

    public List<Map<String, Object>> getBrandOptions()    { return productMapper.selectBrandOptions(); }
    public List<Map<String, Object>> getGradeOptions()    { return productMapper.selectGradeOptions(); }
    public List<Map<String, Object>> getMainNoteOptions() { return productMapper.selectMainNoteOptions(); }
    public List<Map<String, Object>> getVolumeOptions()   { return productMapper.selectVolumeOptions(); }

    public Map<String, Object> getProducts(List<Long> brandIds,
                                           List<Long> gradeIds,
                                           List<Long> mainNoteIds,
                                           List<Long> volumeIds) {
        long total = productMapper.countProducts(brandIds, gradeIds, mainNoteIds, volumeIds);
        List<Map<String, Object>> items = productMapper.selectProducts(brandIds, gradeIds, mainNoteIds, volumeIds);

        Map<String, Object> res = new HashMap<>();
        res.put("total", total);
        res.put("items", items);
        return res;
    }
}
