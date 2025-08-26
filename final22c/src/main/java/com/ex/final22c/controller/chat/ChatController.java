package com.ex.final22c.controller.chat;

import com.ex.final22c.service.chat.ChatOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatOrchestratorService orchestrator;

    @GetMapping
    public String chatPage() {
        // templates/main/chat.html
        return "main/chat";
    }

    @PostMapping(
        value = "/ask",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public AiResult ask(@RequestBody ChatReq req, Principal principal) {
        return orchestrator.handle(req.message(), principal); // ★ 오케스트레이터가 AiResult 반환
    }

    public record ChatReq(String message) {}
}
