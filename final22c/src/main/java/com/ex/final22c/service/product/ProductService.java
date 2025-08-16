package com.ex.final22c.service.product;

import java.util.List;

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

    // 해당 id 에 대한 상품 정보 조회
    public Product getProduct(long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("해당하는 상품 정보를 찾을 수 없습니다."));
    }

    public List<Product> search(String q,
                                List<String> grades,
                                List<String> accords,
                                List<String> brands,
                                List<String> volumes) {

        String qq = (q == null || q.isBlank()) ? null : q.trim();
        List<String> gs = (grades == null || grades.isEmpty()) ? null : grades;
        List<String> ac = (accords == null || accords.isEmpty()) ? null : accords;
        List<String> bs = (brands == null || brands.isEmpty()) ? null : brands;
        List<String> vs = (volumes == null || volumes.isEmpty()) ? null : volumes;

        return productMapper.search(qq, gs, ac, bs, vs);
    }
}
