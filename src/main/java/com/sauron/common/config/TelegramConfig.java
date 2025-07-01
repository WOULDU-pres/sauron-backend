package com.sauron.common.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

/**
 * Telegram Bot 설정 및 초기화
 */
@Slf4j
@Getter
@Configuration
@ConfigurationProperties(prefix = "telegram")
public class TelegramConfig {
    
    private Bot bot = new Bot();
    private Alerts alerts = new Alerts();
    private Channels channels = new Channels();
    
    @PostConstruct
    public void initTelegramApi() {
        if (!bot.enabled) {
            log.info("Telegram bot is disabled");
            return;
        }
        
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            log.info("Telegram Bots API initialized successfully");
        } catch (TelegramApiException e) {
            log.error("Failed to initialize Telegram Bots API", e);
        }
    }
    
    @Getter
    public static class Bot {
        private String token = "your-bot-token-here";
        private String username = "sauron_alert_bot";
        private boolean enabled = true;
        private Duration timeout = Duration.ofSeconds(10);
        private int maxRetries = 3;
        private Duration retryDelay = Duration.ofSeconds(1);
        
        public void setToken(String token) {
            this.token = token;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
        
        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
        
        public void setRetryDelay(Duration retryDelay) {
            this.retryDelay = retryDelay;
        }
    }
    
    @Getter
    public static class Alerts {
        private boolean enabled = true;
        private double minConfidence = 0.7;
        private List<String> alertTypes = List.of("spam", "advertisement", "abuse", "inappropriate", "conflict");
        private int throttleMinutes = 5;
        private int maxAlertsPerHour = 100;
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public void setMinConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
        }
        
        public void setAlertTypes(List<String> alertTypes) {
            this.alertTypes = alertTypes;
        }
        
        public void setThrottleMinutes(int throttleMinutes) {
            this.throttleMinutes = throttleMinutes;
        }
        
        public void setMaxAlertsPerHour(int maxAlertsPerHour) {
            this.maxAlertsPerHour = maxAlertsPerHour;
        }
    }
    
    @Getter
    public static class Channels {
        private String defaultChatId = "";
        private String adminChatIds = "";
        
        public void setDefaultChatId(String defaultChatId) {
            this.defaultChatId = defaultChatId;
        }
        
        public void setAdminChatIds(String adminChatIds) {
            this.adminChatIds = adminChatIds;
        }
        
        public List<String> getAdminChatIdList() {
            if (adminChatIds == null || adminChatIds.trim().isEmpty()) {
                return List.of();
            }
            return List.of(adminChatIds.split(","))
                    .stream()
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .toList();
        }
    }
}