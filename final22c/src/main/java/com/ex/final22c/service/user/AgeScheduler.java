package com.ex.final22c.service.user;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgeScheduler {
    private final UserRepository userRepository;

    // 매년 1/1 00:00:00 KST
    @Scheduled(cron = "0 0 0 1 1 *", zone = "Asia/Seoul")
    public void bumpAllAgesAtNewYear() {
        int updated = userRepository.refreshAgesForNewYear();
        log.info("Ages refreshed for {} users", updated);
    }
}
