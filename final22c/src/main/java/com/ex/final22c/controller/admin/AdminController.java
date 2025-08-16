package com.ex.final22c.controller.admin;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ex.final22c.data.product.Brand;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.user.Users;
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

		model.addAttribute("pageSize", pageSize);
		model.addAttribute("currentBlock", currentBlock);
		model.addAttribute("paging",paging);
		return "admin/productList";
	}
	
	// 상품 등록
	@GetMapping("selectBrand")
	public String selectBrand(Model model) {
		List<Brand> brands = this.adminService.getBrand();
		model.addAttribute("brands",brands);
		return "admin/selectBrand";
	}
}
