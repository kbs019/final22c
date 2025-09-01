package com.ex.final22c;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ex.final22c.service.product.ProfanityFilter;

@Configuration
public class FilterConfig {
    @Bean
    public ProfanityFilter profanityFilter() {
        return new ProfanityFilter();
    }
}
