package com.ex.final22c;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

// 날씨 전용
@Configuration
public class WeatherClientConfig {
    @Bean
    WebClient wxWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.open-meteo.com/v1")
                .build();
    }
}
