package com.ex.final22c.service.product;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ProfanityFilter {
	private Set<String> profanitySet = new HashSet<>();

    @PostConstruct
    public void init() throws Exception {
        InputStream is = getClass().getResourceAsStream("/static/profanity.txt"); 
        if (is == null) throw new RuntimeException("profanity.txt 파일을 찾을 수 없습니다!");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) profanitySet.add(line);
            }
        }
    }

    public boolean containsProfanity(String text) {
        return profanitySet.stream().anyMatch(text::contains);
    }

    public String maskProfanity(String text) {
        String maskedText = text;
        for (String word : profanitySet) {
            String stars = "*".repeat(word.length());
            maskedText = maskedText.replace(word, stars); // ← 정규식 말고 그냥 replace
        }
        return maskedText;
    }
}
