package com.ex.final22c.service.admin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.ex.final22c.DataNotFoundException;
import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.order.OrderDetail;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.data.product.Brand;
import com.ex.final22c.data.product.Grade;
import com.ex.final22c.data.product.MainNote;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.Review;
import com.ex.final22c.data.product.Volume;
import com.ex.final22c.data.purchase.Purchase;
import com.ex.final22c.data.purchase.PurchaseDetail;
import com.ex.final22c.data.purchase.PurchaseRequest;
import com.ex.final22c.data.qna.Answer;
import com.ex.final22c.data.qna.Question;
import com.ex.final22c.data.refund.Refund;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.form.ProductForm;
import com.ex.final22c.repository.order.OrderRepository;
import com.ex.final22c.repository.orderDetail.OrderDetailRepository;
import com.ex.final22c.repository.payment.PaymentRepository;
import com.ex.final22c.repository.productRepository.BrandRepository;
import com.ex.final22c.repository.productRepository.GradeRepository;
import com.ex.final22c.repository.productRepository.MainNoteRepository;
import com.ex.final22c.repository.productRepository.ProductRepository;
import com.ex.final22c.repository.productRepository.ReviewRepository;
import com.ex.final22c.repository.productRepository.VolumeRepository;
import com.ex.final22c.repository.purchaseRepository.PurchaseDetailRepository;
import com.ex.final22c.repository.purchaseRepository.PurchaseRepository;
import com.ex.final22c.repository.purchaseRepository.PurchaseRequestRepository;

import com.ex.final22c.repository.qna.AnswerRepository;

import com.ex.final22c.repository.qna.QuestionRepository;
import com.ex.final22c.repository.refund.RefundRepository;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.product.RestockNotifyService;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
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
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final RefundRepository refundRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseDetailRepository purchaseDetailRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final RestockNotifyService restockNotifyService;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    public Map<String, Object> buildDashboardKpis() {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to   = today.atTime(LocalTime.MAX);

        // 오늘 매출
        long todayRevenue = 0L;
        var rows = orderDetailRepository.revenueSeriesByDay(from, to);
        for (Object[] r : rows) {
            Number rev = (Number) r[1];
            if (rev != null) todayRevenue += rev.longValue();
        }

        // 오늘 주문건수: PAID/CONFIRMED/REFUNDED
        List<String> okStatuses = List.of("PAID","CONFIRMED","REFUNDED");
        long ordersToday = orderRepository.countByRegDateBetweenAndStatusIn(from, to, okStatuses);

        // 전체 회원수, 최근 7일 신규
        long totalUsers = userRepository.count();
        LocalDate since = today.minusDays(7);
        long newUsers7d = userRepository.findAll().stream()
                .filter(u -> u.getReg()!=null && (!u.getReg().isBefore(since)))
                .count();

        // 처리 대기 환불
        long pendingRefunds = refundRepository.countByStatus("REQUESTED");

        // 처리 대기 문의
        long pendingQna = questionRepository.countByStatus("wait");

        return Map.of(
                "todayRevenue", todayRevenue,
                "ordersToday", ordersToday,
                "totalUsers", totalUsers,
                "newUsers7d", newUsers7d,
                "pendingRefunds", pendingRefunds,
                "pendingQna", pendingQna
        );
    }

    // ===== 품절임박 Top5 (재고 0 제외, count 오름차순) =====
    public List<Map<String, Object>> findLowStockTop5() {
        List<Product> all = productRepository.findAll(Sort.by(Sort.Direction.ASC, "count"));
        return all.stream()
                .filter(p -> p.getCount() > 0)
                .limit(5)
                .map(p -> {
                    String brandName = (p.getBrand()!=null) ? p.getBrand().getBrandName() : null;
                    return Map.<String,Object>of(
                            "id", p.getId(),
                            "name", p.getName(),
                            "brand", brandName,
                            "imgPath", p.getImgPath(),
                            "imgName", p.getImgName(),
                            "count", p.getCount(),
                            "sellPrice", p.getSellPrice()
                    );
                })
                .collect(Collectors.toList());
    }

    // 품절(재고 0) Top5: 최근 등록(id 내림차순) 기준
    public List<Map<String, Object>> findSoldOutTop5() {
        // id DESC로 정렬 후 count==0만 필터링하여 상위 5개
        List<Product> all = productRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
        return all.stream()
                .filter(p -> p.getCount() == 0)
                .limit(5)
                .map(p -> {
                    String brandName = (p.getBrand()!=null) ? p.getBrand().getBrandName() : null;
                    return Map.<String,Object>of(
                            "id", p.getId(),
                            "name", p.getName(),
                            "brand", brandName,
                            "imgPath", p.getImgPath(),
                            "imgName", p.getImgName(),
                            "count", p.getCount(),
                            "sellPrice", p.getSellPrice()
                    );
                })
                .collect(java.util.stream.Collectors.toList());
    }


    // ===== 신규회원 일간 시리즈 =====
    public List<Map<String, Object>> buildNewUserSeries(LocalDate from, LocalDate to) {
        // users 전체 가져와 reg 기준 그룹핑
        Map<LocalDate, Long> grouped = userRepository.findAll().stream()
                .filter(u -> u.getReg()!=null && !u.getReg().isBefore(from) && !u.getReg().isAfter(to))
                .collect(Collectors.groupingBy(Users::getReg, Collectors.counting()));

        long days = ChronoUnit.DAYS.between(from, to);
        List<Map<String,Object>> out = new ArrayList<>();
        for (int i=0; i<=days; i++){
            LocalDate d = from.plusDays(i);
            long c = grouped.getOrDefault(d, 0L);
            out.add(Map.of("date", d.toString(), "count", c));
        }
        return out;
    }

    
    // 브랜드 이미지 경로 지정
    private final String uploadDir = "src/main/resources/static/img/brand/";

    // 아이디 검색
    private Specification<Users> search(String kw) {
        return new Specification<>() {

            @Override
            public Predicate toPredicate(Root<Users> root, CriteriaQuery<?> query,
                    CriteriaBuilder cb) {
                return cb.like(root.get("userName"), "%" + kw + "%");
            }

        };
    }

    // 정지 시간 갱신
    public void updateUserStatusIfExpired(Users user) {
        if ("suspended".equals(user.getStatus()) && user.getBanReg() != null) {
            // banReg가 지났으면 정지 해제
            if (user.getBanReg().isBefore(LocalDate.now())) {
                user.setStatus("active");
                user.setBanReg(null);
                userRepository.save(user);
            }
        }
    }
    // 회원목록
    public Page<Users> getList(int page, String kw, String filter) {
        // 최신순(userNo 기준) 정렬
        List<Sort.Order> sorts = new ArrayList<>();
        sorts.add(Sort.Order.desc("userNo"));  // userNo
        PageRequest pageable = PageRequest.of(page, 10, Sort.by(sorts));

        // 기존 검색 조건
        Specification<Users> spec = search(kw);

        // filter 조건
        if ("suspended".equals(filter)) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.equal(root.get("status"), "suspended"),
                    cb.equal(root.get("status"), "banned")));
        }

        Page<Users> result = this.userRepository.findAll(spec, pageable);

        // 상태 만료 체크 후 업데이트
        result.forEach(this::updateUserStatusIfExpired);
        
        return result;
    }

    // 회원 정보
    public Users getUser(String userName) {
        Optional<Users> _user = this.userRepository.findByUserName(userName);
        if (_user.isPresent()) {
            return _user.get();
        } else {
            throw new DataNotFoundException("사용자를 찾을 수 없습니다.");
        }
    }

    // 회원 정지
    public void banned(String statusType, String userName) {
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

    // 상품명 검색
    private Specification<Product> proSearch(String kw) {
        return new Specification<>() {

            @Override
            public Predicate toPredicate(Root<Product> root, CriteriaQuery<?> query,
                    CriteriaBuilder cb) {
                return cb.like(root.get("name"), "%" + kw + "%");
            }

        };
    }

    // 상품 목록
    public List<Product> getItemList(String kw) {

        Specification<Product> spec = proSearch(kw);
        return this.productRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id"));
    }

    // 상품 추출
    public Product getProduct(Long id) {
        Optional<Product> pro = this.productRepository.findById(id);
        if (pro.isPresent()) {
            return pro.get();
        } else {
            throw new DataNotFoundException("상품을 찾을 수 없습니다.");
        }

    }

    // 브랜드 목록
    public List<Brand> getBrand() {
        return this.brandRepository.findAll();
    }

    // 브랜드 추출
    public Brand getBrand(Long id) {
        Optional<Brand> brand = this.brandRepository.findById(id);
        if (brand.isPresent()) {
            return brand.get();
        } else {
            return null;
        }
    }

 // 새 브랜드 등록
    public Brand saveBrand(String brandName, MultipartFile imgName) throws IOException {
        Brand brand = new Brand();
        brand.setBrandName(brandName);

        if (imgName != null && !imgName.isEmpty()) {
            File dir = new File(uploadDir);

            String originalFilename = imgName.getOriginalFilename();
            String extension = "";

            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // 브랜드명 파일명
            String savedFileName = brandName + extension;

            Path savePath = Paths.get(uploadDir, savedFileName);
            imgName.transferTo(savePath); // 실제 저장

            // db
            brand.setImgName(savedFileName);
            brand.setImgPath("/img/brand/");
        } else {
            brand.setImgName("default.png");
            brand.setImgPath("/img/");
        }

        return this.brandRepository.save(brand);
    }

    public boolean existsByBrandName(String brandName) {
        return brandRepository.existsByBrandName(brandName);
    }
    
    // 그레이드 목록
    public List<Grade> getGrade() {
        return this.gradeRepository.findAll();
    }

    // 메인노트 목록
    public List<MainNote> getMainNote() {
        return this.mainNoteRepository.findAll();
    }

    // 용량 목록
    public List<Volume> getVolume() {
        return this.volumeRepository.findAll();
    }

    // 상품 등록
    public Product register(ProductForm dto, MultipartFile imgName) {
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
     // 이미지 처리
        String uploadDir2 = "src/main/resources/static/img/" + brandName + "/";
        Path uploadPath = Paths.get(uploadDir2);

        // 폴더가 없으면 생성
        if (!Files.exists(uploadPath)) {
            try {
                Files.createDirectories(uploadPath); // 상위 폴더까지 포함해서 생성
                System.out.println("폴더 생성됨: " + uploadPath.toAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("폴더 생성 실패: " + uploadPath.toAbsolutePath());
            }
        }

        if (imgName != null && !imgName.isEmpty()) {
            // 기존 이미지 삭제
            if (product.getImgName() != null && !"default.png".equals(product.getImgName())) {
                Path oldFilePath = uploadPath.resolve(product.getImgName());
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
            String savedFileName = productName + extension;
            Path savePath = uploadPath.resolve(savedFileName);
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

        return productRepository.save(product);
    }

    // 관리자 픽
    @Transactional
    public void updatePick(Long id, String isPicked) {
        Optional<Product> pro = this.productRepository.findById(id);
        if (pro.isPresent()) {
            Product product = pro.get();
            product.setIsPicked(isPicked);
        }
    }

    // 상품 상태 변경
    public void productStatus(Long id, String status) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            product.setStatus(status); // 'active' 또는 'wait'
            productRepository.save(product);
        }
    }

    // 동적 필터
    private Specification<Product> proFilter(
            String kw,
            List<Long> brandIds,
            List<String> isPicked,
            List<String> status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 검색 (상품명 LIKE)
            if (kw != null && !kw.isBlank()) {
                predicates.add(cb.like(root.get("name"), "%" + kw + "%"));
            }

            // 브랜드 필터
            if (brandIds != null && !brandIds.isEmpty()) {
                Join<Product, Brand> brandJoin = root.join("brand", JoinType.LEFT);
                predicates.add(brandJoin.get("id").in(brandIds));
            }

            // 관리자픽 필터
            if (isPicked != null && !isPicked.isEmpty()) {
                predicates.add(root.get("isPicked").in(isPicked));
            }

            // 상태 필터
            if (status != null && !status.isEmpty()) {
                predicates.add(root.get("status").in(status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // 상품 필터
    public List<Product> getItemList(
            String kw,
            List<Long> brandIds,
            String isPicked, // radio로 하나만 선택
            String status // radio로 하나만 선택
    ) {
        List<Sort.Order> sorts = new ArrayList<>();

        // 정렬이 없으면 기본 id DESC
        if (sorts.isEmpty())
            sorts.add(Sort.Order.desc("id"));

        // 동적 필터
        List<String> isPickedList = isPicked == null || isPicked.isEmpty() ? null : List.of(isPicked);
        List<String> statusList = status == null || status.isEmpty() ? null : List.of(status);

        Specification<Product> spec = proFilter(kw, brandIds, isPickedList, statusList);

        return this.productRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id"));
    }

    // 동적 필터
    private Specification<Product> proFilter(
            String kw,
            List<Long> brandIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 검색 (상품명 LIKE)
            if (kw != null && !kw.isBlank()) {
                predicates.add(cb.like(root.get("name"), "%" + kw + "%"));
            }

            // 브랜드 필터
            if (brandIds != null && !brandIds.isEmpty()) {
                Join<Product, Brand> brandJoin = root.join("brand", JoinType.LEFT);
                predicates.add(brandJoin.get("id").in(brandIds));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // 상품 필터
    public List<Product> getItemList(
            String kw,
            List<Long> brandIds) {
        List<Sort.Order> sorts = new ArrayList<>();

        // 정렬이 없으면 기본 id DESC
        if (sorts.isEmpty())
            sorts.add(Sort.Order.desc("id"));

        // 동적 필터
        Specification<Product> spec = proFilter(kw, brandIds);

        return productRepository.findAll(spec, Sort.by(sorts));
    }
    
    public List<Product> getItemList2(
            String kw,
            List<Long> brandIds) {
        List<Sort.Order> sorts = new ArrayList<>();

        // ✅ 재고 낮은 순 정렬 (count ASC)
        sorts.add(Sort.Order.asc("count"));

        // 동적 필터
        Specification<Product> spec = proFilter(kw, brandIds);

        return productRepository.findAll(spec, Sort.by(sorts));
    }
    
    // 발주 신청 목록 추가
    public Map<String, Object> addToPurchaseRequest(Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long productId = Long.valueOf(payload.get("productId").toString());
            int qty = Integer.parseInt(payload.get("qty").toString());

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("상품이 존재하지 않습니다"));

            Optional<PurchaseRequest> existingPr = purchaseRequestRepository.findByProduct(product);
            if (existingPr.isPresent()) {
                PurchaseRequest pr = existingPr.get();
                pr.setQty(pr.getQty() + qty);
                purchaseRequestRepository.save(pr);
            } else {
                PurchaseRequest pr = new PurchaseRequest();
                pr.setProduct(product); // Product 객체(FK) 바인딩
                pr.setQty(qty);
                purchaseRequestRepository.save(pr);
            }

            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    // 발주 신청 목록
    public List<PurchaseRequest> getPr() {
        return this.purchaseRequestRepository.findAll();
    }

    // 환불 내역 목록
    public List<Refund> getRefundList() {
        return this.refundRepository.findAllByOrderByCreateDateDesc();
    }

    // 모달용 발주 신청 목록 DTO 생성
    public List<Map<String, Object>> getPurchaseRequest() {
        List<PurchaseRequest> prList = purchaseRequestRepository.findAll(); // 필요한 조건이 있으면 추가

        List<Map<String, Object>> result = new ArrayList<>();

        for (PurchaseRequest pr : prList) {
            Map<String, Object> prMap = new HashMap<>();
            prMap.put("qty", pr.getQty());
            prMap.put("prId", pr.getPrId());

            Map<String, Object> productMap = new HashMap<>();
            productMap.put("id", pr.getProduct().getId());
            productMap.put("name", pr.getProduct().getName());
            productMap.put("costPrice", pr.getProduct().getCostPrice());

            Map<String, Object> brandMap = new HashMap<>();
            brandMap.put("brandName", pr.getProduct().getBrand().getBrandName());

            productMap.put("brand", brandMap);
            prMap.put("product", productMap);

            result.add(prMap);
        }

        return result;
    }

    // 발주 신청 목록 삭제
    public Map<String, Object> deletePurchaseRequest(Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long prId = Long.valueOf(payload.get("prId").toString()); // prId 키 확인
            purchaseRequestRepository.deleteById(prId); // PK로 삭제
            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    // ==================== 기존 발주 신청 ========================
    // // 발주 신청
    // @Transactional
    // public void confirmPurchase(List<Map<String, Object>> items) {
    // // 1. 발주 마스터 생성
    // Purchase purchase = new Purchase();
    // purchase.setReg(LocalDateTime.now());
    // purchaseRepository.save(purchase);

    // int totalPrice = 0;
    // // 2. 발주 상세 생성
    // for (Map<String, Object> item : items) {
    // Object productIdObj = item.get("productId");
    // Object qtyObj = item.get("qty");

    // if (productIdObj == null || qtyObj == null) {
    // throw new RuntimeException("productId 또는 qty 값이 없습니다.");
    // }
    // Long productId = Long.valueOf(item.get("productId").toString());
    // int qty = Integer.valueOf(item.get("qty").toString());

    // Product product = productRepository.findById(productId)
    // .orElseThrow(() -> new RuntimeException("상품 없음"));

    // PurchaseDetail detail = new PurchaseDetail();
    // detail.setProduct(product);
    // detail.setQty(qty);
    // detail.setPurchase(purchase);
    // detail.setTotalPrice(product.getCostPrice()*qty);
    // purchaseDetailRepository.save(detail);

    // // 재고 업데이트
    // product.setCount(product.getCount() + qty);
    // productRepository.save(product); // 변경된 재고 저장

    // // 총금액 계산
    // totalPrice += product.getCostPrice() * qty;
    // }
    // // 발주 품목 개수 계산
    // purchase.setCount(items.size());
    // purchase.setTotalPrice(totalPrice);
    // purchaseRepository.save(purchase);

    // // 3. 발주신청목록 전체 삭제
    // purchaseRequestRepository.deleteAll();
    // }

    @Transactional
    public void confirmPurchase(List<Map<String, Object>> items) {
        // ===== 1) 발주 마스터 생성 =====
        Purchase purchase = new Purchase();
        purchase.setReg(LocalDateTime.now());
        purchaseRepository.save(purchase);

        int totalPrice = 0;
        int itemCount = 0;

        // 알림 대상(0→양수 전환된 상품)
        Set<Long> toNotify = new LinkedHashSet<>();

        // ===== 2) 발주 상세 생성 + 재고 증가 =====
        for (Map<String, Object> item : items) {
            Object productIdObj = item.get("productId");
            Object qtyObj = item.get("qty");

            if (productIdObj == null || qtyObj == null) {
                throw new RuntimeException("productId 또는 qty 값이 없습니다.");
            }

            Long productId = Long.valueOf(productIdObj.toString());
            int qty = Integer.parseInt(qtyObj.toString());

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("상품 없음"));

            // 상세 생성/저장
            PurchaseDetail detail = new PurchaseDetail();
            detail.setProduct(product);
            detail.setQty(qty);
            detail.setPurchase(purchase);
            detail.setTotalPrice(product.getCostPrice() * qty);
            purchaseDetailRepository.save(detail);

            // 재고 업데이트 (필드명: count 사용 중)
            int prev = product.getCount();
            int now = prev + qty;
            product.setCount(now);
            productRepository.save(product);

            // 총금액/품목수 집계
            totalPrice += product.getCostPrice() * qty;
            itemCount += 1;

            // 0 → 양수 전환 시 알림 후보
            if (prev == 0 && now > 0) {
                toNotify.add(productId);
            }
        }

        // ===== 3) 발주 요약값 업데이트 =====
        purchase.setCount(itemCount);
        purchase.setTotalPrice(totalPrice);
        purchaseRepository.save(purchase);

        // ===== 4) 발주신청목록 전체 삭제(기존 동작 유지) =====
        purchaseRequestRepository.deleteAll();

        // ===== 5) 커밋 후(afterCommit) 알림 예약 =====
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Long pid : toNotify) {
                        restockNotifyService.notifyForProduct(pid);
                    }
                }
            }
        );
    }

    // 발주 목록

    public List<Purchase> getPurchase(){
    	 return this.purchaseRepository.findAll(Sort.by(Sort.Direction.DESC, "reg"));
    }

    // 기간별 발주 조회
    public List<Purchase> getPurchasesByPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return getPurchase();
        }
        return purchaseRepository.findByRegBetweenOrderByRegDesc(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        );
    }
    
    // 발주 상세 내역
    public Map<String, Object> getPurchaseDetail(Long purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new RuntimeException("발주 없음"));

        List<Map<String, Object>> items = new ArrayList<>();
        for (PurchaseDetail d : purchase.getPurchaseDetail()) {
            Map<String, Object> item = new HashMap<>();
            item.put("productId", d.getProduct().getId()); // 오타 주의
            item.put("productName", d.getProduct().getName());
            item.put("qty", d.getQty());
            item.put("price", d.getProduct().getCostPrice()); // 단가
            item.put("total", d.getTotalPrice()); // 합계
            items.add(item);
        }

        return Map.of(
                "reg", purchase.getReg().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                "totalPrice", purchase.getTotalPrice(),
                "items", items);
    }

    // 주문 내역
    public List<Order> getOrders() {
        return this.orderRepository.findAll(Sort.by(Sort.Direction.DESC, "regDate"));
    }

    @Transactional(readOnly = true)
    public Order findMyOrderWithDetails(Long orderId) {
        return orderRepository.findOneWithDetails(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Payment> findPaymentsofOrder(Long orderId) {
        return paymentRepository.findByOrder_OrderId(orderId);
    }

    private static final List<String> OK = List.of("PAID", "CONFIRMED", "REFUNDED");

    // 판매량 조회
    public Map<Long, Long> getConfirmedQtySumMap(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty())
            return Collections.emptyMap();

        List<Object[]> rows = orderDetailRepository.sumConfirmQuantityByProductIds(productIds, OK);
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            Long pid = (Long) r[0];
            Number sum = (Number) r[1];
            map.put(pid, sum != null ? sum.longValue() : 0L);
        }
        // 주문내역이 없던 상품은 0으로 채움
        for (Long pid : productIds) {
            map.putIfAbsent(pid, 0L);
        }
        return map;
    }

    public Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("상품을 찾을 수 없습니다. id=" + id));
    }

    /** 구매자 통계(고유 구매자 기준) */
    public Map<String, Object> buildBuyerStats(Long productId) {
        // confirmQuantity > 0 인 주문 상세만 집계
        List<OrderDetail> details = orderDetailRepository.findConfirmedByProductId(productId);

        // 고유 구매자
        Map<Long, Users> uniqueBuyers = new LinkedHashMap<>();
        for (OrderDetail d : details) {
            if (d.getOrder() == null || d.getOrder().getUser() == null)
                continue;
            Users u = d.getOrder().getUser();
            uniqueBuyers.put(u.getUserNo(), u);
        }

        int male = 0, female = 0, unknown = 0;
        int a10 = 0, a20 = 0, a30 = 0, a40 = 0, a50p = 0;

        for (Users u : uniqueBuyers.values()) {
            // 성별
            String g = Optional.ofNullable(u.getGender()).orElse("").trim().toUpperCase();
            if (g.equals("F") || g.equals("FEMALE") || g.equals("여") || g.equals("여자"))
                female++;
            else if (g.equals("M") || g.equals("MALE") || g.equals("남") || g.equals("남자"))
                male++;
            else
                unknown++;

            // 연령대 (10~19: 10대, 20~29: 20대, 30~39: 30대, 40~49: 40대, 50+: 50+)
            Integer age = u.getAge(); // Users.prePersist/Update에서 계산됨
            if (age != null) {
                if (age >= 10 && age < 20)
                    a10++;
                else if (age < 30)
                    a20++;
                else if (age < 40)
                    a30++;
                else if (age < 50)
                    a40++;
                else
                    a50p++;
            }
        }

        Map<String, Object> gender = new LinkedHashMap<>();
        gender.put("female", female);
        gender.put("male", male);
        gender.put("unknown", unknown);

        Map<String, Object> age = new LinkedHashMap<>();
        age.put("t10", a10);
        age.put("t20", a20);
        age.put("t30", a30);
        age.put("t40", a40);
        age.put("t50p", a50p);

        return Map.of(
                "productId", productId,
                "gender", gender,
                "age", age,
                "uniqueBuyerCount", uniqueBuyers.size());
    }

    // 리뷰 목록
    public List<Review> getReview() {

        return reviewRepository.findAll(Sort.by(Sort.Direction.DESC, "createDate"));

    }


    public boolean applySanction(String username, String sanction) {
        Optional<Users> optionalUser = userRepository.findByUserName(username);

        if (!optionalUser.isPresent()) {
            return false; // 사용자 없으면 실패
        }

        Users user = optionalUser.get();
        LocalDate today = LocalDate.now();

        switch (sanction) {
            case "7d":
                user.setStatus("suspended");
                user.setBanReg(today.plusDays(7));
                break;
            case "30d":
                user.setStatus("suspended");
                user.setBanReg(today.plusDays(30));
                break;
            case "permanent":
                user.setStatus("banned");
                user.setBanReg(null);
                break;
            default:
                return false;
        }

        userRepository.save(user);
        return true;
    }

    @Transactional
    public void changeStatus(Long reviewId, String status) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰 없음"));

        review.setStatus(status);
        // JPA에서는 save() 안 해도 @Transactional 걸려 있으면 flush 때 반영됨
    }

    public Map<String, Object> buildAllUserStats() {
        List<Users> all = userRepository.findAll();

        int male = 0, female = 0, unknown = 0;
        int a10 = 0, a20 = 0, a30 = 0, a40 = 0, a50p = 0;

        // 신규 가입(최근 7일)
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate since = today.minusDays(7);
        long newUsers7d = 0;

        for (Users u : all) {
            // 성별 집계
            String g = java.util.Optional.ofNullable(u.getGender()).orElse("").trim().toUpperCase();
            if (g.equals("F") || g.equals("FEMALE") || g.equals("여") || g.equals("여자"))
                female++;
            else if (g.equals("M") || g.equals("MALE") || g.equals("남") || g.equals("남자"))
                male++;
            else
                unknown++;

            // 연령대 집계
            Integer age = u.getAge();
            if (age != null) {
                if (age >= 10 && age < 20)
                    a10++;
                else if (age < 30)
                    a20++;
                else if (age < 40)
                    a30++;
                else if (age < 50)
                    a40++;
                else
                    a50p++;
            }

            // 최근 7일 신규 (reg가 LocalDate 기준, since 이상이면 포함)
            java.time.LocalDate reg = u.getReg();
            if (reg != null && (reg.isAfter(since) || reg.isEqual(since))) {
                newUsers7d++;
            }
        }

        Map<String, Object> gender = new java.util.LinkedHashMap<>();
        gender.put("female", female);
        gender.put("male", male);
        gender.put("unknown", unknown);

        Map<String, Object> age = new java.util.LinkedHashMap<>();
        age.put("t10", a10);
        age.put("t20", a20);
        age.put("t30", a30);
        age.put("t40", a40);
        age.put("t50p", a50p);

        return java.util.Map.of(
                "gender", gender,
                "age", age,
                "totalUserCount", all.size(),
                "newUserCount7d", newUsers7d);
    }


    public Map<String, Object> findLowStockPaged(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "count").and(Sort.by(Sort.Direction.ASC, "id")));
        var slice = productRepository.findByCountBetween(1, 20, pageable);
        long total = productRepository.countByCountBetween(1, 20);
        var items = slice.getContent().stream().map(p -> Map.<String,Object>of(
                "id", p.getId(),
                "name", p.getName(),
                "brand", p.getBrand()!=null? p.getBrand().getBrandName(): null,
                "imgPath", p.getImgPath(),
                "imgName", p.getImgName(),
                "count", p.getCount(),
                "sellPrice", p.getSellPrice()
        )).toList();
        return Map.of("total", total, "items", items);
    }

    public Map<String, Object> findSoldOutPaged(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")); // 최근 등록순
        var slice = productRepository.findByCount(0, pageable);
        long total = productRepository.countByCount(0);
        var items = slice.getContent().stream().map(p -> Map.<String,Object>of(
                "id", p.getId(),
                "name", p.getName(),
                "brand", p.getBrand()!=null? p.getBrand().getBrandName(): null,
                "imgPath", p.getImgPath(),
                "imgName", p.getImgName(),
                "count", p.getCount(),
                "sellPrice", p.getSellPrice()
        )).toList();
        return Map.of("total", total, "items", items);
    }
    
    // 문의 내역 출력
    public List<Question> getAllQuestions(){
    	return questionRepository.findAllByOrderByCreateDateDesc();
    }
    
 // 답변 작성
    @Transactional
    public void saveAnswer(Long qId, String content, Users admin) {
        Question question = questionRepository.findById(qId)
                .orElseThrow(() -> new IllegalArgumentException("문의 없음"));

        // 1. Answer 저장
        Answer answer = new Answer();
        answer.setQuestion(question);
        answer.setWriter(admin);
        answer.setContent(content);
        answerRepository.save(answer);

        // 2. Question에 answer 연결
        question.setAnswer(answer);

        // 3. Question 상태 변경
        question.setStatus("done");
        questionRepository.save(question);
    }
    
    // 질문 ID로 질문 가져오기
    public Question getQuestion(Long qId) {
        return questionRepository.findById(qId)
                .orElseThrow(() -> new IllegalArgumentException("질문이 없습니다. qId=" + qId));
    }

    // 질문 객체로 답변 가져오기
    public Answer getAnswersByQuestion(Question question) {
        return answerRepository.findByQuestion(question);

    }
    // 상품 등록 후 DB(product.aiGuide)에 넣을 메서드
    @Transactional
    public void updateAiGuide(Long productId, String aiGuide) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new RuntimeException("상품을 찾을 수 없습니다."));
        product.setAiGuide(aiGuide);
        // JPA dirty checking으로 자동 업데이트
    }
}
