package com.ex.final22c.controller.admin;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

import com.ex.final22c.data.product.Brand;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.form.ProductForm;
import com.ex.final22c.service.admin.AdminService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/")
public class AdminController {
	private final AdminService adminService;
	
	// 회원목록
	@GetMapping("userList")
	public String userList(Model model,@RequestParam(value="page",defaultValue="0")int page,@RequestParam(value="kw" ,defaultValue="") String kw) {
		Page<Users> user = adminService.getList(page,kw);
		model.addAttribute("user",user);
		model.addAttribute("kw",kw);
		return "admin/userList";
	}
	
	// 회원 정지
	@PostMapping("changeStatus")
	@ResponseBody
	public ResponseEntity<String> changeUserStatus(@RequestParam("username") String username, @RequestParam("status") String status){
		this.adminService.banned(status, username);
		return ResponseEntity.ok("변경 성공");
		
	}
	
	// 상품 관리
	@GetMapping("productList")
	public String productList(Model model,@RequestParam(value="page",defaultValue="0") int page) {
		Page<Product> paging = adminService.getItemList(page);
		int pageSize = 10; // 한 블록에 보여줄 페이지 수
		int currentBlock = paging.getNumber() / pageSize; // 현재 블록
		
		List<Brand> brands = this.adminService.getBrand();
		
		model.addAttribute("brands",brands);
		model.addAttribute("pageSize", pageSize);
		model.addAttribute("currentBlock", currentBlock);
		model.addAttribute("paging",paging);
		return "admin/productList";
	}
	
	// 브랜드 선택/등록
	@GetMapping("selectBrand")
	public String selectBrand(Model model) {
		List<Brand> brands = this.adminService.getBrand();
		model.addAttribute("brands",brands);
		return "admin/selectBrand";
	}
	
	// 브랜드 등록
	@PostMapping("save")
	public String saveBrand(@RequestParam("brandName") String brandName,
	                        @RequestParam(value = "imgName",required = false) MultipartFile imgName,
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
	public String productForm(@RequestParam("brandId") Long  brandId,Model model,@RequestParam(value="id",required = false) Long  id) {
		Brand brand = this.adminService.getBrand(brandId);
		if(id != null) {
			model.addAttribute("product",this.adminService.getProduct(id));
		}
		model.addAttribute("brand",brand);
		model.addAttribute("grades",this.adminService.getGrade());
		model.addAttribute("mainNotes",this.adminService.getMainNote());
		model.addAttribute("volumes",this.adminService.getVolume());
	    return "admin/productForm"; // 상품 등록 페이지
	}
	
	// 상품 등록/수정
	@PostMapping("productForm")
	public String newProduct(@ModelAttribute ProductForm product) {
		this.adminService.register(product,product.getImgName());
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
	@PostMapping("/productStatus/{id}")
	public ResponseEntity<?> changeStatus(@PathVariable("id") Long id, @RequestParam("status") String status) {
	
	    this.adminService.productStatus(id, status);
	    return ResponseEntity.ok().build();
	}
	
	// 상품 정렬
    @GetMapping("profilter")
    public String filterProducts(
            @RequestParam(value = "brand", required = false) List<Long> brand,
            @RequestParam(value = "isPicked", required = false) List<String> isPicked,
            @RequestParam(value = "status", required = false) List<String> status,
            @RequestParam(value = "sortStockAsc", required = false) Boolean sortStockAsc,
            @RequestParam(value = "sortStockDesc", required = false) Boolean sortStockDesc,
            @RequestParam(value = "sortPriceAsc", required = false) Boolean sortPriceAsc,
            @RequestParam(value = "sortPriceDesc", required = false) Boolean sortPriceDesc,
            Model model) {

        List<Product> products = adminService.filterProducts(brand, isPicked, status, sortStockAsc, sortStockDesc, sortPriceAsc, sortPriceDesc);
        model.addAttribute("products", products);
        return "product/productList :: productListFragment"; // thymeleaf fragment
    }
}
