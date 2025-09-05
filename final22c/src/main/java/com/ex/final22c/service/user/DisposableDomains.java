package com.ex.final22c.service.user;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class DisposableDomains {
    private final Set<String> domains;

    public DisposableDomains() {
        Set<String> list = new LinkedHashSet<>();
        try {
            var res = new ClassPathResource("disposable-domains.txt");
            if (res.exists()) {
                try (var br = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                    list.addAll(br.lines()
                            .map(String::trim)
                            .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                            .map(String::toLowerCase)
                            .collect(Collectors.toList()));
                }
            }
        } catch (Exception ignored) {}

        // 파일이 없거나 비어있으면 최소 기본 셋
        if (list.isEmpty()) {
            list.add("mailinator.com");
            list.add("10minutemail.com");
            list.add("tempmail.dev");
            list.add("guerrillamail.com");
        }
        this.domains = Set.copyOf(list);
    }

    public Set<String> domains() {
        return domains;
    }
}