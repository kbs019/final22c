package com.ex.final22c;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.service.DefaultMessageService;

@Configuration
public class SmsConfig {

    private static void assertNotBlank(String name, String v) {
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("SMS 설정 누락: " + name + " 가 비어있습니다.");
        }
    }

    @Bean
    public DefaultMessageService solapi(
            @Value("${sms.api-key:}") String key,
            @Value("${sms.api-secret-key:}") String secret,
            @Value("${sms.domain:https://api.solapi.com}") String domain) {

        assertNotBlank("sms.api-key", key);
        assertNotBlank("sms.api-secret-key", secret);

        return NurigoApp.INSTANCE.initialize(key, secret, domain);
    }
}
