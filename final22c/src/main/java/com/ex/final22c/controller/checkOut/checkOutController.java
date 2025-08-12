package com.ex.final22c.controller.checkOut;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/checkout/*")
public class checkOutController {
	@GetMapping("test")
	public String test() {
		return "pay/checkout";
	}
}
