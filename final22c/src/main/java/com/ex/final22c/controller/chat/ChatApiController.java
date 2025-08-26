package com.ex.final22c.controller.chat;

import com.ex.final22c.service.chat.ChatOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatApiController {

    private final ChatOrchestratorService orchestrator;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatOrchestratorService.ChatAnswer chat(@RequestBody ChatRequest req, Principal principal) {
        // 오케스트레이터에서 SQL 생성/가드/실행/요약까지 모두 처리
        return orchestrator.handle(req.message(), principal);
    }

    // 요청 DTO (필요하면 분리 가능)
    public record ChatRequest(String message) {}
}
