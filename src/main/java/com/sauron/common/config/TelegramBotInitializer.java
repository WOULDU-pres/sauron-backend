package com.sauron.common.config;

import com.sauron.common.external.telegram.TelegramBotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Telegram Bot 초기화 및 등록
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotInitializer {
    
    private final TelegramBotClient telegramBotClient;
    private final TelegramConfig telegramConfig;
    
    /**
     * 애플리케이션 시작 후 Telegram Bot 등록
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerTelegramBot() {
        if (!telegramConfig.getBot().isEnabled()) {
            log.info("Telegram bot is disabled, skipping registration");
            return;
        }
        
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramBotClient);
            
            log.info("Telegram bot '{}' registered successfully", 
                    telegramConfig.getBot().getUsername());
            
            // Bot 연결 테스트
            telegramBotClient.checkBotHealth()
                    .thenAccept(healthy -> {
                        if (healthy) {
                            log.info("Telegram bot health check passed");
                        } else {
                            log.warn("Telegram bot health check failed");
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Telegram bot health check error", throwable);
                        return null;
                    });
                    
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot", e);
        }
    }
}