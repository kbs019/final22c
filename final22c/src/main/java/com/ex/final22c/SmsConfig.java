package com.ex.final22c;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmsConfig {

    @Bean
    public DefaultMessageService messageService(
        @Value("${SOLAPI_API_KEY}") String apiKey,
        @Value("${SOLAPI_API_SECRET}") String apiSecret
    ) {
        // 공식 권장 엔드포인트
        return NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.solapi.com");
    }
}
