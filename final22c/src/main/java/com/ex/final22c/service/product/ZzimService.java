package com.ex.final22c.service.product;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.product.BookMark;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.productRepository.BookMarkRepository;
import com.ex.final22c.repository.productRepository.ProductRepository;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ZzimService {

	 private final ProductRepository productRepository;
	    private final UserRepository userRepository;
	    private final BookMarkRepository bookMarkRepository;

	    @Transactional(readOnly = true)
	    public boolean isZzimed(String userName, Long productId) {
	        if (userName == null || productId == null) return false;
	        return bookMarkRepository.existsByUser_UserNameAndProduct_Id(userName, productId);
	    }

	    public void add(String userName, Long productId) {
	        Users user = userRepository.findByUserName(userName)
	                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));
	        Product product = productRepository.findById(productId)
	                .orElseThrow(() -> new IllegalArgumentException("상품 없음"));

	        // 이미 찜했는지 확인
	        if (bookMarkRepository.existsByUser_UserNameAndProduct_Id(userName, productId)) return;

	        BookMark bm = new BookMark();
	        bm.setUser(user);
	        bm.setProduct(product);

	        bookMarkRepository.save(bm);
	    }
	    public void remove(String userName, Long productId) {
	        bookMarkRepository.deleteByUser_UserNameAndProduct_Id(userName, productId);
	    }

	    @Transactional(readOnly = true)
	    public List<Product> listMyZzim(String userName) {
	        return bookMarkRepository.findByUser_UserNameOrderByCreateDateDesc(userName)
	                .stream()
	                .map(BookMark::getProduct)
	                .toList();
	    }
	
}
