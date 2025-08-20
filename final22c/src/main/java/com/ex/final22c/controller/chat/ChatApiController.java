package com.ex.final22c.controller.chat;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import com.ex.final22c.service.chat.ChatService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatApiController {

    private final ChatService chatService;

    // 프론트에서 { "message": "..." } 로 보내면, DeepSeek 응답 텍스트만 반환
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req) {
        String answer = chatService.ask(req.message());
        return ResponseEntity.ok(new ChatResponse(answer));
    }

    // 간단한 DTO (record 사용)
    public record ChatRequest(String message) {}
    public record ChatResponse(String answer) {}
}
