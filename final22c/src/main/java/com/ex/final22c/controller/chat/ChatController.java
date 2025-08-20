package com.ex.final22c.controller.chat;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/chat")
public class ChatController {

    // /chat GET 요청 -> 템플릿 main/chat.html 렌더
    @GetMapping
    public String chatPage() {
        return "main/chat";
    }
}
