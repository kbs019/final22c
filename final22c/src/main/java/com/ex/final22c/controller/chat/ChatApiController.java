package com.ex.final22c.controller.chat;

import com.ex.final22c.service.chat.ChatOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api") // 공통 prefix
public class ChatApiController {

    private final ChatOrchestratorService orchestrator;

    @PostMapping(
        path = {"/chat", "/ai/query"},         // ← 두 경로 모두 허용
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public AiResult ask(@RequestBody ChatRequest req, Principal principal) {
        return orchestrator.handle(req.message(), principal); // 내부 라우팅 그대로
    }

    public record ChatRequest(String message) {}
}