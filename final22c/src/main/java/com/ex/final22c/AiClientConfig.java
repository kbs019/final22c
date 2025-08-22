package com.ex.final22c;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AiClientConfig {
  @Bean
  WebClient aiWebClient(
      @Value("${deepseek.api.base-url}") String baseUrl,
      @Value("${deepseek.api.key}")      String apiKey
  ){
    return WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("Authorization", "Bearer " + apiKey)
        .build();
  }
}