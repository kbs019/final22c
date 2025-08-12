package com.ex.final22c.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/")
public class adminController {
	@GetMapping("userList")
	public String userList() {
		return "admin/userList";
	}
}
