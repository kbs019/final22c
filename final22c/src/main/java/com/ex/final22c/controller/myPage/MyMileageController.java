package com.ex.final22c.controller.myPage;

import java.security.Principal;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ex.final22c.data.user.MileageRowWithBalance;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.user.UserRepository;
import com.ex.final22c.service.mypage.MyMileageService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyMileageController {


}
