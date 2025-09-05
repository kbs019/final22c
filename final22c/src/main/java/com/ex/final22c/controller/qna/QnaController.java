package com.ex.final22c.controller.qna;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ex.final22c.data.qna.QuestionCategory;
import com.ex.final22c.data.qna.QuestionDto;
import com.ex.final22c.service.qna.QnaService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/qna/")
public class QnaController {
	private final QnaService qnaService;
	@GetMapping("question")
	public String questionForm(Model model) {
		 // ID 순으로 QuestionCategory 목록 가져오기
        List<QuestionCategory> categories = qnaService.getAllCategories();
        model.addAttribute("questionCategories", categories);
		return "main/questionForm";
	}
	
	@PostMapping("questionSave")
	public String questionSave(@ModelAttribute QuestionDto question, Principal principal) {
		qnaService.saveQuestion(question, principal.getName());
		return "redirect:/main";
	}
}
