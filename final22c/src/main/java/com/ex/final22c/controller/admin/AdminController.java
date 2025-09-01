package com.ex.final22c.controller.admin;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.data.product.Brand;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.Review;
import com.ex.final22c.data.product.ReviewDto;
import com.ex.final22c.data.purchase.PurchaseRequest;
import com.ex.final22c.data.refund.Refund;
import com.ex.final22c.data.refund.RefundDetail;
import com.ex.final22c.data.refund.RefundDetailResponse;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.form.ProductForm;
import com.ex.final22c.service.admin.AdminService;
import com.ex.final22c.service.refund.RefundService;
import com.ex.final22c.service.stats.StatsService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/")
public class AdminController {
	private final AdminService adminService;
	private final RefundService refundService;
	private final StatsService statsService;

	// 회원목록
	@GetMapping("userList")
	public String userList(Model model,
	                       @RequestParam(value = "page", defaultValue = "0") int page,
	                       @RequestParam(value = "kw", defaultValue = "") String kw,
	                       @RequestParam(value = "filter", defaultValue = "") String filter) {
	    Page<Users> user = adminService.getList(page, kw, filter);
	    model.addAttribute("user", user);
	    model.addAttribute("kw", kw);
	    model.addAttribute("filter", filter);
	    return "admin/userList";
	}

	// 회원 정지
	@PostMapping("changeStatus")
	@ResponseBody
	public ResponseEntity<String> changeUserStatus(@RequestParam("username") String username,
			@RequestParam("status") String status) {
		this.adminService.banned(status, username);
		return ResponseEntity.ok("변경 성공");

	}

	// 상품 관리
	@GetMapping("productList")
	public String productList(
			Model model,
			@RequestParam(value = "kw", defaultValue = "") String kw,
			@RequestParam(value = "brand", required = false) List<Long> brandIds,
			@RequestParam(value = "isPicked", required = false) String isPicked,
			@RequestParam(value = "status", required = false) String status) {
		List<Product> paging = adminService.getItemList(kw, brandIds, isPicked, status);

		List<Brand> brands = adminService.getBrand();

		model.addAttribute("brands", brands);
		model.addAttribute("paging", paging);

		// 선택된 필터/정렬값 다시 모델에 전달
		model.addAttribute("kw", kw);
		model.addAttribute("brand", brandIds);
		model.addAttribute("isPicked", isPicked);
		model.addAttribute("status", status);

		return "admin/productList";
	}

	// 브랜드 선택/등록
	@GetMapping("selectBrand")
	public String selectBrand(Model model) {
		List<Brand> brands = this.adminService.getBrand();
		model.addAttribute("brands", brands);
		return "admin/selectBrand";
	}

	// 브랜드 등록
	@PostMapping("save")
	public String saveBrand(@RequestParam("brandName") String brandName,
			@RequestParam(value = "imgName", required = false) MultipartFile imgName,
			RedirectAttributes redirectAttributes) throws IOException {

		// 브랜드 저장
		Brand brand = adminService.saveBrand(brandName, imgName);

		// redirect 시 브랜드 ID 전달
		redirectAttributes.addAttribute("brandId", brand.getId());

		// 상품 등록 페이지로 이동
		return "redirect:/admin/newProduct";
	}

	// 상품 등록/수정
	@GetMapping("productForm")
	public String productForm(@RequestParam("brandId") Long brandId, Model model,
			@RequestParam(value = "id", required = false) Long id) {
		Brand brand = this.adminService.getBrand(brandId);
		if (id != null) {
			model.addAttribute("product", this.adminService.getProduct(id));
		}
		model.addAttribute("brand", brand);
		model.addAttribute("grades", this.adminService.getGrade());
		model.addAttribute("mainNotes", this.adminService.getMainNote());
		model.addAttribute("volumes", this.adminService.getVolume());
		return "admin/productForm"; // 상품 등록 페이지
	}

	// 상품 등록/수정
	@PostMapping("productForm")
	public String newProduct(@ModelAttribute ProductForm product) {
		this.adminService.register(product, product.getImgName());
		return "redirect:/admin/productList";
	}

	// 관리자 픽
	@PostMapping("updatePick")
	@ResponseBody
	public String updatePick(@RequestBody Map<String, Object> request) {
		Long id = Long.valueOf(request.get("id").toString());
		String isPicked = request.get("isPicked").toString();
		adminService.updatePick(id, isPicked);
		return "OK";
	}

	// 상품 상태 변경
	@PostMapping("productStatus/{id}")
	public ResponseEntity<?> changeStatus(@PathVariable("id") Long id, @RequestParam("status") String status) {
		this.adminService.productStatus(id, status);
		return ResponseEntity.ok().build();
	}

	// 주문 관리
	@GetMapping("orderList")
	public String orderList(Model model) {

		model.addAttribute("orders", this.adminService.getOrders());
		return "admin/orderList";
	}

	// 발주 신청 페이지
	@GetMapping("purchaseOrder")
	public String purchaseOrde(Model model,
			@RequestParam(value = "kw", defaultValue = "") String kw,
			@RequestParam(value = "brand", required = false) List<Long> brandIds) {
		List<Product> list = adminService.getItemList(kw, brandIds);
		List<Brand> brands = adminService.getBrand();
		List<PurchaseRequest> pr = this.adminService.getPr();

		int totalAmount = pr.stream()
				.mapToInt(p -> p.getProduct().getCostPrice() * p.getQty())
				.sum();

		model.addAttribute("totalAmount", totalAmount);

		model.addAttribute("prList", pr);
		model.addAttribute("brands", brands);
		model.addAttribute("list", list);

		// 선택된 필터/정렬값 다시 모델에 전달
		model.addAttribute("kw", kw);
		model.addAttribute("brand", brandIds);
		return "admin/purchaseOrder";
	}

	// 챗봇
	@GetMapping("chat")
	public String chatAI() {
		return "admin/chat";
	}

	// 발주 신청 목록 추가
	@PostMapping("add")
	@ResponseBody
	public Map<String, Object> addToPurchaseRequest(@RequestBody Map<String, Object> payload) {
		return this.adminService.addToPurchaseRequest(payload);
	}

	// 발주 신청 목록 (모달용)
	@GetMapping("/getPurchaseRequest")
	@ResponseBody
	public List<Map<String, Object>> getPurchaseRequest() {
		return adminService.getPurchaseRequest();
	}

	// 발주 신청 목록 삭제
	@PostMapping("deletePurchaseRequest")
	@ResponseBody
	public Map<String, Object> deletePurchaseRequest(@RequestBody Map<String, Object> payload) {
		return this.adminService.deletePurchaseRequest(payload);
	}

	// 주문관리 -> 환불 내역 페이지로 이동
	@GetMapping("refundList")
	public String getRefundList(Model model) {

		model.addAttribute("refundList", adminService.getRefundList());

		return "admin/refundList";
	}

	//
	@GetMapping("/refunds/{refundId}")
	// @PreAuthorize("hasRole('ADMIN')") // 보안 적용 시 주석 해제
	public ResponseEntity<RefundDetailResponse> getRefundDetail(
			@PathVariable("refundId") Long refundId) {

		// 서비스에서 Refund + (Order, User, Payment, RefundDetail(Product)) 까지 fetch-join으로
		// 가져오도록 구현
		Refund refund = refundService.getRefundGraphForAdmin(refundId);

		// Header 구성
		RefundDetailResponse.Header header = RefundDetailResponse.Header.builder()
				.userName(refund.getUser().getUserName())
				.orderId(refund.getOrder().getOrderId())
				.createdAt(fmt(refund.getCreateDate()))
				.reason(refund.getRequestedReason())
				.status(refund.getStatus())
				.paymentTid(getTidSafe(refund)) // UI에는 안 쓰더라도 전달해둠
				.build();

		// Items 구성
		List<RefundDetailResponse.Item> items = refund.getDetails().stream()
				.sorted(Comparator.comparing(RefundDetail::getRefundDetailId))
				.map(d -> toItem(d))
				.collect(Collectors.toList());

		RefundDetailResponse body = RefundDetailResponse.builder()
				.header(header)
				.items(items)
				.build();

		return ResponseEntity.ok(body);
	}

	// ✅ 여기에 둔다(핸들러들 아래, DTO들 위)
	private RefundDetailResponse.Item toItem(RefundDetail d) {
		var od = d.getOrderDetail();
		var p = od.getProduct();

		return RefundDetailResponse.Item.builder()
				.refundDetailId(d.getRefundDetailId())
				.unitRefundAmount(d.getUnitRefundAmount())
				.quantity(d.getQuantity())
				.refundQty(d.getRefundQty())
				.product(RefundDetailResponse.ProductLite.builder()
						.id(p.getId())
						.name(p.getName())
						.imgPath(p.getImgPath())
						.imgName(p.getImgName())
						.build())
				.build();
	}

	private String fmt(java.time.LocalDateTime t) {
		return (t == null) ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
	}

	private String getTidSafe(Refund refund) {
		Payment p = refund.getPayment();
		return (p == null) ? null : p.getTid(); // 네 Payment 엔티티에 tid 게터가 있다고 가정
	}

	// 발주 목록
	@GetMapping("purchaseList")
	public String purchaseList(Model model) {

		model.addAttribute("purList", this.adminService.getPurchase());
		return "admin/purchaseList";
	}

	// 발주 신청
	@PostMapping("confirmPurchase")
	@ResponseBody
	public Map<String, Object> confirmPurchase(@RequestBody Map<String, List<Map<String, Object>>> payload) {
		List<Map<String, Object>> items = payload.get("items");
		Map<String, Object> result = new HashMap<>();
		try {
			adminService.confirmPurchase(items);
			result.put("success", true);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 발주 상세 내역
	@GetMapping("getPurchaseDetail/{purchaseId}")
	@ResponseBody
	public Map<String, Object> getPurchaseDetail(@PathVariable("purchaseId") Long purchaseId) {
		return this.adminService.getPurchaseDetail(purchaseId);
	}

	// 주문 내역 상세
	@GetMapping("orderList/{id}/fragment")
	public String orderItemsFragment(@PathVariable("id") Long id, Model model) {

		Order order = adminService.findMyOrderWithDetails(id);
		List<Payment> payments = adminService.findPaymentsofOrder(id);
		model.addAttribute("order", order);
		model.addAttribute("payments", payments);
		return "admin/orderDetail :: items";
	}


	// 상품 통계
	@GetMapping("stats")
	public String statsList(Model model, @RequestParam(value = "kw", defaultValue = "") String kw,
			@RequestParam(value = "brand", required = false) List<Long> brandIds) {

		// kw 와 brandIds 를 사용한 상품 리스트
		List<Product> paging = adminService.getItemList(kw, brandIds);

		List<Brand> brands = adminService.getBrand();

		// ★ paging에서 id 추출
		List<Long> productIds = paging.stream()
				.map(Product::getId)
				.toList();

		// ★ 한 방에 합계 맵
		Map<Long, Long> confirmedSumMap = adminService.getConfirmedQtySumMap(productIds);

		model.addAttribute("brands", brands);
		model.addAttribute("paging", paging);
		model.addAttribute("confirmedSumMap", confirmedSumMap);

		return "admin/stats";
	}

	// {id} 상품의 통계 화면
	@GetMapping("stats/{id}")
	public String productStatsShow(@PathVariable("id") Long id, Model model) {
		Product p = adminService.findProduct(id);
		model.addAttribute("product", p);
		return "admin/stat";
	}

	// Ajax: 구매자 통계
	@GetMapping("stats/{id}/buyers")
	@ResponseBody
	public Map<String, Object> productBuyerStats(@PathVariable("id") Long id) {
		return adminService.buildBuyerStats(id);
	}

	// // (미사용) Ajax: 매출 통계 훅 - 비워둠
	// @GetMapping("stats/{id}/sales")
	// @ResponseBody
	// public Map<String, Object> productSalesStats(@PathVariable("id") Long id) {
	// 	return Map.of();
	// }

    // GET /admin/stats/{productId}/sales?unit=DAY&date=2025-08-28
    // GET /admin/stats/{productId}/sales?unit=WEEK&date=2025-08 (YYYY-MM)
    // GET /admin/stats/{productId}/sales?unit=MONTH&year=2025
    // GET /admin/stats/{productId}/sales?unit=YEAR
    @GetMapping("/stats/{id}/sales")
	@ResponseBody
    public Map<String, Object> salesSeries(
            @PathVariable("id") Long id,
            @RequestParam("unit") String unit,
            @RequestParam(name="date", required = false) String date,
            @RequestParam(name="year", required = false) Integer year) {
        return statsService.buildSeries(id, unit, date, year);
    }

	
	// 리뷰 관리
	@GetMapping("reviewList")
	public String reviewList(Model model) {
		List<Review> re = this.adminService.getReview();
		model.addAttribute("re",re);
		return "admin/reviewList";
	}
    @GetMapping("reviews/badwords")
    public List<Review> badWordReviews() {
        return adminService.getFilteredReviews();
    }
}
