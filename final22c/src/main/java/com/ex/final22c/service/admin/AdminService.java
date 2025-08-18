package com.ex.final22c.service.admin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ex.final22c.DataNotFoundException;
import com.ex.final22c.data.product.Brand;
import com.ex.final22c.data.product.Grade;
import com.ex.final22c.data.product.MainNote;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.Volume;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.form.ProductForm;
import com.ex.final22c.repository.productRepository.BrandRepository;
import com.ex.final22c.repository.productRepository.GradeRepository;
import com.ex.final22c.repository.productRepository.MainNoteRepository;
import com.ex.final22c.repository.productRepository.ProductRepository;
import com.ex.final22c.repository.productRepository.VolumeRepository;
import com.ex.final22c.repository.user.UserRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {
	private final UserRepository userRepository;
	private final ProductRepository productRepository;
	private final BrandRepository brandRepository;
	private final GradeRepository gradeRepository;
	private final MainNoteRepository mainNoteRepository;
	private final VolumeRepository volumeRepository;
	
	// 브랜드 이미지 경로 지정
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
    
    // 상품 추출
    public Product getProduct(Long id) {
    	Optional<Product> pro = this.productRepository.findById(id);
    	if(pro.isPresent()) {
    		return  pro.get();
    	}else {
    		throw new DataNotFoundException("상품을 찾을 수 없습니다.");
    	}
    	
    }
    
    // 브랜드 목록
    public List<Brand> getBrand(){
    	return this.brandRepository.findAll();
    } 
    
    // 브랜드 추출
    public Brand getBrand(Long id) {
    	Optional<Brand> brand = this.brandRepository.findById(id);
    	if(brand.isPresent()) {
    		return brand.get();
    	}else {
    		return null;
    	}
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
    	}else {
    		brand.setImgName("default.png");
    		brand.setImgPath("/img/");
    	}
    	return this.brandRepository.save(brand);
    }
    
    // 그레이드 목록
    public List<Grade> getGrade(){
    	return this.gradeRepository.findAll();
    }
    // 메인노트 목록
    public List<MainNote> getMainNote(){
    	return this.mainNoteRepository.findAll();
    }
    // 용량 목록
    public List<Volume> getVolume(){
    	return this.volumeRepository.findAll();
    }
    
    // 상품 등록
    public void register(ProductForm dto,MultipartFile imgName) {
    	 Product product;

    	    if (dto.getId() != null) {
    	        // 수정 모드: 기존 상품 가져오기
    	        product = productRepository.findById(dto.getId())
    	                  .orElseThrow(() -> new RuntimeException("상품 없음"));
    	    } else {
    	        // 등록 모드: 새 상품 생성
    	        product = new Product();
    	    }

    	    // 공통 필드 세팅
    	    product.setName(dto.getName());
    	    product.setCount(dto.getCount());
    	    product.setPrice(dto.getPrice());
    	    product.setDiscount(dto.getDiscount());
    	    product.setDescription(dto.getDescription());

    	    String brandName = null;

    	    // 브랜드 처리
    	    if (dto.getBrandNo() != null) {
    	        Brand brand = brandRepository.findById(dto.getBrandNo())
    	                        .orElseThrow(() -> new RuntimeException("브랜드 없음"));
    	        product.setBrand(brand);
    	        brandName = brand.getBrandName();
    	    }

    	    // 용량 처리
    	    if (dto.getVolumeNo() > 0) {
    	        Volume volume = volumeRepository.findById(dto.getVolumeNo())
    	                            .orElseThrow(() -> new RuntimeException("용량 없음"));
    	        product.setVolume(volume);
    	    }

    	    // 그레이드/메인노트/노트 처리 (기존 로직 그대로)
    	    if (dto.getGradeNo() > 0) {
    	        Grade grade = gradeRepository.findById(dto.getGradeNo())
    	                        .orElseThrow(() -> new RuntimeException("그레이드 없음"));
    	        product.setGrade(grade);
    	    }

    	    if (dto.getMainNoteNo() > 0) {
    	        MainNote mainNote = mainNoteRepository.findById(dto.getMainNoteNo())
    	                             .orElseThrow(() -> new RuntimeException("메인노트 없음"));
    	        product.setMainNote(mainNote);
    	    }

    	    if (dto.getSingleNote() != null && !dto.getSingleNote().isEmpty()) {
    	        product.setSingleNote(dto.getSingleNote());
    	        product.setBaseNote(null);
    	        product.setMiddleNote(null);
    	        product.setTopNote(null);
    	    } else {
    	        product.setSingleNote(null);
    	        product.setBaseNote(dto.getBaseNote());
    	        product.setMiddleNote(dto.getMiddleNote());
    	        product.setTopNote(dto.getTopNote());
    	    }

    	    // 이미지 처리
    	    String uploadDir2 = "src/main/resources/static/img/" + brandName + "/";
    	    if (imgName != null && !imgName.isEmpty()) {
    	        // 기존 이미지 삭제
    	        if (product.getImgName() != null && !"default.png".equals(product.getImgName())) {
    	            Path oldFilePath = Paths.get(uploadDir2, product.getImgName());
    	            try {
    	                Files.deleteIfExists(oldFilePath);
    	            } catch (IOException e) {
    	                e.printStackTrace();
    	                System.out.println("기존 이미지 삭제 실패: " + oldFilePath);
    	            }
    	        }

    	        // 새 이미지 저장
    	        String originalFilename = imgName.getOriginalFilename();
    	        String productName = dto.getName();
    	        String extension = (originalFilename != null && originalFilename.contains("."))
    	                           ? originalFilename.substring(originalFilename.lastIndexOf("."))
    	                           : "";
    	        String savedFileName = productName +  extension;
    	        Path savePath = Paths.get(uploadDir2, savedFileName);
    	        try {
					imgName.transferTo(savePath);
				} catch (Exception e) {
					e.printStackTrace();
				}
    	        product.setImgName(savedFileName);
    	        product.setImgPath("/img/" + brandName + "/");
    	    } else if (product.getId() == null) {
    	        // 신규 등록 시만 default 이미지
    	        product.setImgName("default.png");
    	        product.setImgPath("/img/");
    	    }
    	    // 수정 모드에서 파일 안 올리면 기존 이미지 그대로 유지

    	    productRepository.save(product); // JPA save: id 있으면 update, 없으면 insert
    }
    
    // 관리자 픽
    @Transactional
    public void updatePick(Long id,String isPicked) {
    	Optional<Product> pro = this.productRepository.findById(id);
    	if(pro.isPresent()) {
    		Product product = pro.get();
    		product.setIsPicked(isPicked);
    	}
    }
    
    // 상품 상태 변경
    public void productStatus(Long id,String status) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            product.setStatus(status); // 'active' 또는 'wait'
            productRepository.save(product);
        }
    }
    
    // 상품 필터
    public List<Product> filterProducts(List<Long> brandIds, List<String> isPickedList, List<String> statusList,
            Boolean sortStockAsc, Boolean sortStockDesc,
            Boolean sortPriceAsc, Boolean sortPriceDesc) {

		// JPA Specification 사용 예제
		return productRepository.findAll((root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			
			if(brandIds != null && !brandIds.isEmpty()) {
			predicates.add(root.get("brand").get("id").in(brandIds));
			}
			
			if(isPickedList != null && !isPickedList.isEmpty()) {
			predicates.add(root.get("isPicked").in(isPickedList));
			}
			
			if(statusList != null && !statusList.isEmpty()) {
			predicates.add(root.get("status").in(statusList));
			}
			
			query.where(predicates.toArray(new Predicate[0]));
			
			// 정렬
			List<Order> orders = new ArrayList<>();
			if(Boolean.TRUE.equals(sortStockAsc)) orders.add(cb.asc(root.get("stock")));
			if(Boolean.TRUE.equals(sortStockDesc)) orders.add(cb.desc(root.get("stock")));
			if(Boolean.TRUE.equals(sortPriceAsc)) orders.add(cb.asc(root.get("price")));
			if(Boolean.TRUE.equals(sortPriceDesc)) orders.add(cb.desc(root.get("price")));
			if(!orders.isEmpty()) query.orderBy(orders);
			
		return query.getRestriction();
		});
	}
}
