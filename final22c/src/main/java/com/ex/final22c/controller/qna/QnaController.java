package com.ex.final22c.controller.qna;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ex.final22c.data.qna.QuestionCategory;
import com.ex.final22c.data.qna.QuestionDto;
import com.ex.final22c.service.qna.QnaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/qna") // 끝에 슬래시 없음
public class QnaController {

    private final QnaService qnaService;

    /** 문의 작성 폼 */
    @GetMapping("/question")
    public String questionForm(Model model) {
        if (!model.containsAttribute("question")) {
            model.addAttribute("question", new QuestionDto());
        }
        List<QuestionCategory> categories = qnaService.getAllCategories();
        model.addAttribute("questionCategories", categories);
        return "main/questionForm";
    }

    /** 문의 저장 */
    @PostMapping("/questionSave")
    public String questionSave(@Valid @ModelAttribute("question") QuestionDto question,
                               BindingResult bindingResult,
                               Principal principal,
                               Model model,
                               RedirectAttributes redirectAttributes) {

        // 로그인 필요
        if (principal == null) {
            bindingResult.addError(new FieldError("question", "title", "로그인이 필요합니다."));
        }

        // Summernote HTML → 텍스트 검증
        String plain = stripHtmlToText(question.getContent());
        if (plain.isBlank()) {
            bindingResult.addError(new FieldError("question", "content", "내용을 입력해 주세요."));
        }

        if (bindingResult.hasErrors()) {
            // 에러 시 카테고리 재주입 후 동일 뷰 렌더
            model.addAttribute("questionCategories", qnaService.getAllCategories());
            return "main/questionForm";
        }

        // 저장
        qnaService.saveQuestion(question, principal.getName());

        // PRG + 메시지
        redirectAttributes.addFlashAttribute("msg", "문의가 등록되었습니다.");
        return "redirect:/mypage/myQuestion";
    }

    /** 태그/nbsp 제거 후 공백 정리 */
    private static String stripHtmlToText(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", " ")
                   .replace("&nbsp;", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
    }
}
