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

import com.ex.final22c.data.qna.QuestionCategory;
import com.ex.final22c.data.qna.QuestionDto;
import com.ex.final22c.service.qna.QnaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/qna/")
public class QnaController {

    private final QnaService qnaService;

    @GetMapping("question")
    public String questionForm(Model model) {
        List<QuestionCategory> categories = qnaService.getAllCategories();
        if (!model.containsAttribute("question")) {
            model.addAttribute("question", new QuestionDto());
        }
        model.addAttribute("questionCategories", categories);
        return "main/questionForm";
    }

    @PostMapping("questionSave")
    public String questionSave(@Valid @ModelAttribute("question") QuestionDto question,
                               BindingResult bindingResult,
                               Principal principal,
                               Model model) {
        if (principal == null) {
            // 로그인 요구
            bindingResult.addError(new FieldError("question", "title", "로그인이 필요합니다."));
        }

        // Summernote 내용은 HTML일 수 있어 텍스트 검증을 별도로 수행
        String plain = stripHtmlToText(question.getContent());
        if (plain.isBlank()) {
            bindingResult.addError(new FieldError("question", "content", "내용을 입력해 주세요."));
        }

        if (bindingResult.hasErrors()) {
            // 오류시 카테고리 다시 제공
            List<QuestionCategory> categories = qnaService.getAllCategories();
            model.addAttribute("questionCategories", categories);
            return "main/questionForm";
        }

        qnaService.saveQuestion(question, principal.getName());
        // PRG 패턴: 중복 제출 방지
        return "redirect:/main";
    }

    /** 간단한 HTML 제거 유틸 (Jsoup 미사용, 태그/nbsp 제거 후 공백 정리) */
    private static String stripHtmlToText(String html) {
        if (html == null) return "";
        String text = html.replaceAll("<[^>]*>", " ")   // 태그 제거
                          .replace("&nbsp;", " ")       // nbsp 제거
                          .replaceAll("\\s+", " ")      // 다중 공백 정리
                          .trim();
        return text;
    }
}
