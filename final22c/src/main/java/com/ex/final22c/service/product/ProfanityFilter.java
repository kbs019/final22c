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
	        InputStream is = getClass().getResourceAsStream("/profanity.txt");
	        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
	        String line;
	        while ((line = reader.readLine()) != null) {
	            line = line.trim();
	            if (!line.isEmpty()) {
	                profanitySet.add(line);
	            }
	        }
	        reader.close();
	    }

	    // 욕설 포함 여부 체크
	    public boolean containsProfanity(String text) {
	        for (String word : profanitySet) {
	            if (text.contains(word)) {
	                return true;
	            }
	        }
	        return false;
	    }

	    // 욕설을 '*' 처리
	    public String maskProfanity(String text) {
	        String maskedText = text;
	        for (String word : profanitySet) {
	            if (maskedText.contains(word)) {
	                String stars = "*".repeat(word.length());
	                maskedText = maskedText.replaceAll(word, stars);
	            }
	        }
	        return maskedText;
	    }
}
