package com.ex.final22c.controller.chat;

import com.ex.final22c.service.chat.ChatOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatOrchestratorService orchestrator;

    // /chat GET -> 템플릿 main/chat.html 렌더
    @GetMapping
    public String chatPage() {
        return "main/chat";
    }

    // /chat/ask POST -> 하이브리드(챗+SQL) 응답 JSON
    @PostMapping("/ask")
    @ResponseBody
    public ChatOrchestratorService.ChatAnswer ask(@RequestBody ChatReq req, Principal principal) {
        return orchestrator.handle(req.message(), principal);
    }

    public record ChatReq(String message) {}
}
