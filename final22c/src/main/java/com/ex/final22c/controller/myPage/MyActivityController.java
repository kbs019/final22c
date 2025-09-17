package com.ex.final22c.controller.myPage;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ex.final22c.data.user.Users;
import com.ex.final22c.service.mypage.MyActivityService;
import com.ex.final22c.service.user.UsersService;

import lombok.RequiredArgsConstructor;

@Controller  
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyActivityController {

    private final UsersService usersService;
    private final MyActivityService myActivityService;

    /** 활동내역 페이지 (뷰) */
    @GetMapping("/activity")
    public String activityPage(Model model, Principal principal){
        if (principal == null) return "redirect:/user/login";
        model.addAttribute("section", "activity");
        return "mypage/activity"; // templates/mypage/activity.html
    }

    @GetMapping("/reviews")
    public ResponseEntity<?> myReviews(Principal principal,
                                       @RequestParam(name = "page", defaultValue="0") int page,
                                       @RequestParam(name = "size", defaultValue="10") int size){
        Users me = usersService.getLoginUser(principal);
        Map<String,Object> body = myActivityService.getMyReviews(me.getUserNo(), page, size);
        return ResponseEntity.ok(body);
    }

    // 이미 @Controller + @RequestMapping("/mypage") 인 상태
    @GetMapping("/reviews/{reviewId}")
    @ResponseBody
    public ResponseEntity<?> getMyReview(@PathVariable("reviewId") Long reviewId, Principal principal){
        Users me = usersService.getLoginUser(principal); // 로그인 필수
        Map<String,Object> body = myActivityService.getMyReviewDetail(me.getUserNo(), reviewId);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/likes")
    public ResponseEntity<?> myLikes(Principal principal,
                                     @RequestParam(name="page", defaultValue="0") int page,
                                     @RequestParam(name="size", defaultValue="10") int size){
        Users me = usersService.getLoginUser(principal);
        Map<String,Object> body = myActivityService.getMyLikedReviews(me.getUserNo(), page, size);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/likes/{reviewId}")
    public ResponseEntity<?> unlike(@PathVariable("reviewId") Long reviewId, Principal principal){
        Users me = usersService.getLoginUser(principal);
        boolean ok = myActivityService.unlike(me.getUserNo(), reviewId);
        return ResponseEntity.ok(Map.of("ok", ok));
    }

    // 내용 수정(필요 시 rating도 같이 받기)
    @PutMapping("/reviews/{reviewId}")
    @ResponseBody
    public ResponseEntity<?> updateMyReview(@PathVariable("reviewId") Long reviewId,
                                            @RequestBody Map<String, Object> req,
                                            Principal principal){
        Users me = usersService.getLoginUser(principal);
        String content = String.valueOf(req.getOrDefault("content","")).trim();
        int rating = 0;
        if (req.get("rating") != null) {
            try { rating = Integer.parseInt(String.valueOf(req.get("rating"))); } catch (Exception ignored){}
        }
        Map<String,Object> body = myActivityService.updateMyReview(me.getUserNo(), reviewId, content, rating);
        return ResponseEntity.ok(body);
    }

    // 삭제
    @DeleteMapping("/reviews/{reviewId}")
    @ResponseBody
    public ResponseEntity<?> deleteMyReview(@PathVariable("reviewId") Long reviewId, Principal principal){
        Users me = usersService.getLoginUser(principal);
        boolean ok = myActivityService.deleteMyReview(me.getUserNo(), reviewId);
        return ResponseEntity.ok(Map.of("ok", ok));
    }

    /** 내 관심목록 페이지 (Thymeleaf) */
    // @GetMapping("/zzim")
    // @PreAuthorize("isAuthenticated()")
    // public String myZzimPage(@RequestParam(defaultValue = "0") int page,
    //                          @RequestParam(defaultValue = "12") int size,
    //                          Principal principal,
    //                          Model model) {
    //     Pageable pageable = PageRequest.of(page, size);
    //     Page<Product> paging = zzimService.listMyZzim(principal.getName(), pageable);

    //     model.addAttribute("paging", paging);
    //     model.addAttribute("totalElements", paging.getTotalElements());
    //     model.addAttribute("totalPages", paging.getTotalPages());
    //     model.addAttribute("page", page);
    //     model.addAttribute("size", size);

    //     return "user/zzimList"; // ← 뷰 이름(원하는 템플릿 경로로 바꿔도 됨)
    // }
}