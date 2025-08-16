package com.ex.final22c.service.admin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ex.final22c.DataNotFoundException;
import com.ex.final22c.data.product.Brand;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.productRepository.BrandRepository;
import com.ex.final22c.repository.productRepository.ProductRepository;
import com.ex.final22c.repository.user.UserRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {
	private final UserRepository userRepository;
	private final ProductRepository productRepository;
	private final BrandRepository brandRepository;
	
	// static 폴더 안에 경로 지정
    private final String uploadDir = "src/main/resources/static/img/brand/";
	
    // 아이디 검색
    private Specification<Users> search(String kw){
    	return new Specification<>() {

			@Override
			public Predicate toPredicate(Root<Users> root, CriteriaQuery<?> query,
					CriteriaBuilder cb) {
				return cb.like(root.get("userName"), "%" + kw + "%");
			}
    		
    	};
    }
    
    // 전체 회원목록
    public Page<Users> getList(int page,String kw){
    	List<Sort.Order> sorts = new ArrayList<>();
    	sorts.add(Sort.Order.desc("reg"));
    	PageRequest pageable = PageRequest.of(page,10,Sort.by(sorts));
    	Specification<Users> spec = search(kw);
    	return this.userRepository.findAll(spec,pageable);
    }
    
    // 회원 정보
    public Users getUser(String userName) {
    	Optional<Users> _user = this.userRepository.findByUserName(userName);
    	if(_user.isPresent()) {
    		return _user.get();
    	}else {
    		throw new DataNotFoundException("사용자를 찾을 수 없습니다.");
    	}
    }
    
    // 회원 정지
    public void banned(String statusType,String userName) {
    		Users user = getUser(userName);
	    	LocalDate now = LocalDate.now();
	
	        switch (statusType) {
	            case "normal":
	                user.setStatus("active");
	                user.setBanReg(null);
	                break;
	            case "7d":
	                user.setStatus("suspended");
	                user.setBanReg(now.plusDays(7));
	                break;
	            case "30d":
	                user.setStatus("suspended");
	                user.setBanReg(now.plusDays(30));
	                break;
	            case "permanent":
	                user.setStatus("banned");
	                user.setBanReg(null);
	                break;
	        }
	        this.userRepository.save(user);
     }
    
    // 상품 목록
    public Page<Product> getItemList(int page){
    	List<Sort.Order> sorts = new ArrayList<>();
    	sorts.add(Sort.Order.desc("id"));
    	PageRequest pageable = PageRequest.of(page,10,Sort.by(sorts));
    	return this.productRepository.findAll(pageable);
    }
    
    // 브랜드 목록
    public List<Brand> getBrand(){
    	return this.brandRepository.findAll();
    } 
    
    // 새 브랜드 등록
    public Brand saveBrand(String brandName, MultipartFile imgName) throws IOException {
    	Brand brand = new Brand();
    	brand.setBrandName(brandName);
    	
    	if(imgName!=null && !imgName.isEmpty()) {
    		File dir = new File(uploadDir);
    		
    		String originalFilename = imgName.getOriginalFilename();
    		String extension = "";
    		
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            
            // 브랜드명 파일명
            String savedFileName = brandName + extension;
            
            Path savePath = Paths.get(uploadDir,savedFileName);
            imgName.transferTo(savePath);	// 실제 저장
            
            // db
            brand.setImgName(savedFileName);
            brand.setImgPath("/img/brand/");
    	}
    	return this.brandRepository.save(brand);
    }
}
