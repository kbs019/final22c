package com.ex.final22c.controller.myPage;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.qna.Answer;
import com.ex.final22c.data.qna.Question;
import com.ex.final22c.data.qna.QuestionDto;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.form.UsersAddressForm;
import com.ex.final22c.repository.mypage.UserAddressRepository;
import com.ex.final22c.repository.qna.QuestionRepository;
import com.ex.final22c.service.cart.CartService;
import com.ex.final22c.service.mypage.UserAddressService;
import com.ex.final22c.service.product.ZzimService;
import com.ex.final22c.service.user.UsersService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyPageController {

    private final UsersService usersService;
    private final UserAddressRepository userAddressRepository;
    private final QuestionRepository questionRepository;
    private final UserAddressService userAddressService;
    private final ZzimService zzimService;
    private final CartService cartService;

    // ====== DTO ======
    public record AddressDto(
            Long addressNo,
            String addressName,
            String recipient,
            String phone,
            String zonecode,
            String roadAddress,
            String detailAddress,
            String isDefault) {
        public static AddressDto from(com.ex.final22c.data.user.UserAddress ua) {
            return new AddressDto(
                    ua.getAddressNo(),
                    ua.getAddressName(),
                    ua.getRecipient(),
                    ua.getPhone(),
                    ua.getZonecode(),
                    ua.getRoadAddress(),
                    ua.getDetailAddress(),
                    ua.getIsDefault());
        }
    }

    // ====== 주소지 페이지 ======
    @GetMapping("/address")
    public String addressPage(Model model, Principal principal) {
        if (principal == null)
            return "redirect:/user/login";
        Users me = usersService.getLoginUser(principal);
        model.addAttribute("userAddresses",
                userAddressRepository.findByUser_UserNo(me.getUserNo()));
        model.addAttribute("usersAddressForm", new UsersAddressForm());
        model.addAttribute("section", "address");
        return "myPage/addressForm";
    }

    @PostMapping(value = "/address", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public String createAddress(
            @Valid @ModelAttribute("usersAddressForm") UsersAddressForm form,
            BindingResult bindingResult,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (principal == null) {
            return "redirect:/user/login";
        }

        if (bindingResult.hasErrors()) {
            Users me = usersService.getLoginUser(principal);
            model.addAttribute("userAddresses",
                    userAddressRepository.findByUser_UserNo(me.getUserNo()));
            model.addAttribute("section", "address"); // 사이드박스 하이라이트 유지용(있다면)
            return "myPage/addressForm";
        }

        Users me = usersService.getLoginUser(principal);
        userAddressService.insertUserAddress(me.getUserNo(), form); // 첫 주소 자동 기본지정은 서비스에서 처리

        redirectAttributes.addFlashAttribute("msg", "주소가 등록되었습니다.");
        return "redirect:/mypage/address";
    }

    @PostMapping("/address/default")
    @ResponseBody
    public ResponseEntity<Void> setDefault(@RequestBody Map<String, Long> payload,
            Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        Long addressNo = payload.get("addressNo");
        if (addressNo == null)
            return ResponseEntity.badRequest().build();
        Users me = usersService.getLoginUser(principal);
        userAddressService.setDefault(me.getUserNo(), addressNo);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/address/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addressList(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        Users me = usersService.getLoginUser(principal);
        var list = userAddressService.getUserAddressesList(me.getUserNo())
                .stream().map(AddressDto::from).toList();
        return ResponseEntity.ok(Map.of("addresses", list));
    }

    @PostMapping(value = "/address/default.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setDefaultJson(
            @RequestParam(value = "addressNo", required = false) Long addressNoForm,
            @RequestBody(required = false) Map<String, Long> body,
            Principal principal) {

        if (principal == null)
            return ResponseEntity.status(401).build();

        Long addressNo = addressNoForm != null ? addressNoForm
                : (body != null ? body.get("addressNo") : null);
        if (addressNo == null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "addressNo is required"));
        }

        Users me = usersService.getLoginUser(principal);
        userAddressService.setDefault(me.getUserNo(), addressNo);

        var selected = userAddressService.getUserAddressesList(me.getUserNo()).stream()
                .filter(a -> addressNo.equals(a.getAddressNo()))
                .findFirst().map(AddressDto::from).orElse(null);

        return ResponseEntity.ok(Map.of("ok", true, "address", selected));
    }

    // 수정
    @PutMapping(value = "/address/{addressNo}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateAddress(
            @PathVariable("addressNo") Long addressNo,
            @RequestBody UsersAddressForm form,
            Principal principal) {

        if (principal == null)
            return ResponseEntity.status(401).build();
        Users me = usersService.getLoginUser(principal);

        var updated = userAddressService.updateUserAddress(me.getUserNo(), addressNo, form);
        return ResponseEntity.ok(Map.of("ok", true, "address", MyPageController.AddressDto.from(updated)));
    }

    // 삭제
    @DeleteMapping(value = "/address/{addressNo}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteAddress(
            @PathVariable("addressNo") Long addressNo, Principal principal) {

        if (principal == null)
            return ResponseEntity.status(401).build();
        Users me = usersService.getLoginUser(principal);

        userAddressService.deleteUserAddress(me.getUserNo(), addressNo);
        return ResponseEntity.ok(Map.of("ok", true));
    }
    
    // ====== 찜목록 ======
    @GetMapping("/zzimList")
    public String wishlistPage(Model model, Principal principal) {
        if (principal == null)
            return "redirect:/user/login";
        List<Product> list = this.zzimService.listMyZzim(principal.getName());
        model.addAttribute("paging", list);
        model.addAttribute("section", "wishlist");
        return "mypage/zzimList";
    }
    
    @PostMapping("/zzimList/remove")
    @ResponseBody
    public ResponseEntity<?> removeZzim(Principal principal,
                                        @RequestBody Map<String, Long> body) {
        Long productId = body.get("productId");
        String user = principal.getName();
        zzimService.remove(user, productId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/addBatch")
    @ResponseBody
    public ResponseEntity<?> addBatchToCart(@RequestBody List<Map<String, Object>> items, Principal principal) {
        if (items == null || items.isEmpty()) {
            return ResponseEntity.badRequest().body("선택한 상품이 없습니다.");
        }
        // Map에서 productId와 quantity 추출
        cartService.addItemsToCart(principal.getName(), items);

        return ResponseEntity.ok("선택한 상품이 장바구니에 담겼습니다.");
    }
    
    // 결제 페이지에서 바로 주소등록
    @PostMapping(value = "/address/new", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createAddressJson(
            @RequestBody UsersAddressForm form,
            Principal principal) {
        
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "message", "로그인이 필요합니다."));
        }
        
        try {
            Users me = usersService.getLoginUser(principal);
            var saved = userAddressService.insertUserAddressReturn(me.getUserNo(), form);
            
            return ResponseEntity.ok(Map.of(
                "ok", true, 
                "address", AddressDto.from(saved)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false, 
                "message", "주소 등록에 실패했습니다: " + e.getMessage()
            ));
        }
    }

    // ====== 내 문의 ======
    @GetMapping("/myQuestion")
    public String myQuestions(Model model, Principal principal) {
        if (principal == null)
            return "redirect:/user/login";
        String userName = principal.getName();
        List<QuestionDto> questions = usersService.getUserQuestions(userName);
        model.addAttribute("questions", questions);
        model.addAttribute("section", "myQuestion");
        return "mypage/myQuestion"; // 내 문의 목록 페이지
    }

    @GetMapping("/questionDetail/{questionId}")
    @ResponseBody
    public QuestionDto getQuestionDetail(@PathVariable("questionId") Long questionId) {
        Question question = questionRepository.findByIdWithAnswer(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid question ID"));
        
        QuestionDto dto = new QuestionDto();
        dto.setQId(question.getQId());
        dto.setStatus(question.getStatus());
        dto.setTitle(question.getTitle());
        dto.setContent(question.getContent());
        dto.setQcId(question.getQc().getQcId());
        dto.setCreateDate(question.getCreateDate());

        Answer answer = question.getAnswer();
        if (answer != null) {
            dto.setAnswer(answer.getContent());
            dto.setAnswerCreateDate(answer.getCreateDate());
        }
        return dto;  // JSON 형태로 반환
    }
}
