package com.ex.final22c.controller.qna;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/qna/")
public class qnaController {
	@GetMapping("question")
	public String questionForm() {
		return "main/questionForm";
	}
	
	@PostMapping("questionSave")
	public String questionSave() {
		return "";
	}
}
