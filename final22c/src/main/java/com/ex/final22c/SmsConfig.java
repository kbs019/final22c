package com.ex.final22c;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.service.DefaultMessageService;

@Configuration
public class SmsConfig {

    @Bean
    public DefaultMessageService messageService(
            @Value("${SOLAPI_API_KEY1}") String apiKey,
            @Value("${SOLAPI_API_SECRET1}") String apiSecret,
            @Value("${sms.domain:https://api.solapi.com}") String domain // 기본값
    ) {
        return NurigoApp.INSTANCE.initialize(apiKey, apiSecret, domain);
    }
}
