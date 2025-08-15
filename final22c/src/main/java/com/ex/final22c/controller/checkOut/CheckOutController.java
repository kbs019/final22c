/*
 * package com.ex.final22c.controller.checkOut; import java.security.Principal;
 * 
 * import org.springframework.stereotype.Controller; import
 * org.springframework.ui.Model; import
 * org.springframework.web.bind.annotation.GetMapping; import
 * org.springframework.web.bind.annotation.PathVariable; import
 * org.springframework.web.bind.annotation.PostMapping; import
 * org.springframework.web.bind.annotation.RequestMapping; import
 * org.springframework.web.bind.annotation.RequestParam;
 * 
 * import com.ex.final22c.data.product.Product; import
 * com.ex.final22c.data.user.Users; import
 * com.ex.final22c.service.product.ProductService; import
 * com.ex.final22c.service.user.UsersService;
 * 
 * import lombok.RequiredArgsConstructor;
 * 
 * @Controller
 * 
 * @RequiredArgsConstructor
 * 
 * @RequestMapping("/checkout") public class CheckOutController { private final
 * UsersService userService; private final ProductService productService;
 * 
 * @PostMapping("/order") public String orderOne(@RequestParam("id") long
 * id, @RequestParam("quantity") int quantity) { return
 * "redirect:/checkout/order/"+id+"?qty="+quantity; }
 * 
 * 
 * // 예: /checkout/order/5?qty=2 로 호출~
 * 
 * @GetMapping("/order/{perfumeNo}") public String orderOne(@PathVariable("id")
 * long id,
 * 
 * @RequestParam(name="qty", defaultValue="1") int qty, Principal principal,
 * Model model) {
 * 
 * Product product = productService.getProduct(id); int unitPrice = (int)
 * (product.getPrice() * 0.7); int lineTotal = unitPrice * qty; Users user =
 * this.userService.getUser(principal.getName());
 * 
 * // 뷰에서 바로 쓰도록 기본 값들 세팅, 주문페이지에선 order에 넣을필요없음 model.addAttribute("product",
 * product); // 기존 템플릿이 쓰는 객체 model.addAttribute("qty", qty);
 * model.addAttribute("unitPrice", unitPrice); model.addAttribute("lineTotal",
 * lineTotal); model.addAttribute("user",user);
 * 
 * 
 * // (선택) 카카오 Ready에 쓸 대표값 model.addAttribute("pgItemName",
 * perfume.getPerfumeName()); model.addAttribute("pgTotalQty", qty);
 * model.addAttribute("pgTotalAmount", lineTotal);
 * 
 * 
 * return "pay/checkout"; }
 * 
 * 
 * }
 */