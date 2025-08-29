package com.ex.final22c.service.product;

import java.util.List;

import org.springframework.stereotype.Service;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewFilterService {
    private final OpenAiService openAiService;

    /**
     * 리뷰 텍스트를 GPT로 보내서 비속어 포함 여부 판별
     */
    public boolean containsBadWord(String reviewText) {
        ChatMessage systemMessage = new ChatMessage("system",
                "너는 리뷰 관리 AI야. 사용자가 작성한 리뷰가 비속어나 욕설을 포함하면 'BAD'를, 그렇지 않으면 'OK'만 출력해.");

        ChatMessage userMessage = new ChatMessage("user", reviewText);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")   // 가벼운 모델 추천
                .messages(List.of(systemMessage, userMessage))
                .maxTokens(5)
                .build();

        ChatCompletionResult result = openAiService.createChatCompletion(request);
        String answer = result.getChoices().get(0).getMessage().getContent().trim();

        return "BAD".equalsIgnoreCase(answer);
    }
}
