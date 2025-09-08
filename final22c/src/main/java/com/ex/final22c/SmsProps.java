package com.ex.final22c;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "sms")
public class SmsProps {
    public static class Channel {
        @NotBlank public String apiKey;
        @NotBlank public String apiSecretKey;
        @NotBlank public String domain;
        @NotBlank public String fromNumber;
    }
    public Channel a = new Channel();
    public Channel b = new Channel();
}
