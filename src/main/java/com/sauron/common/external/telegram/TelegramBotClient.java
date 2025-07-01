package com.sauron.common.external.telegram;

import com.sauron.common.config.TelegramConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Telegram Bot API 클라이언트
 * 메시지 전송 및 Bot 관리 기능
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotClient extends TelegramLongPollingBot {
    
    protected final TelegramConfig telegramConfig;
    
    @Override
    public String getBotUsername() {
        return telegramConfig.getBot().getUsername();
    }
    
    @Override
    public String getBotToken() {
        return telegramConfig.getBot().getToken();
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        // 현재는 알림 전송만 필요하므로 수신 메시지 처리는 기본 로깅만
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            String chatId = update.getMessage().getChatId().toString();
            String userName = update.getMessage().getFrom().getUserName();
            
            log.debug("Received message from {}: {} in chat {}", userName, messageText, chatId);
        }
    }
    
    /**
     * 텔레그램 메시지 전송
     */
    public CompletableFuture<Boolean> sendMessage(String chatId, String text) {
        return CompletableFuture.supplyAsync(() -> {
            if (!telegramConfig.getBot().isEnabled()) {
                log.debug("Telegram bot is disabled, skipping message send to chat: {}", chatId);
                return false;
            }
            
            if (chatId == null || chatId.trim().isEmpty()) {
                log.warn("Chat ID is empty, cannot send message");
                return false;
            }
            
            return sendMessageWithRetry(chatId, text, telegramConfig.getBot().getMaxRetries());
        });
    }
    
    /**
     * 재시도 로직을 포함한 메시지 전송
     */
    private boolean sendMessageWithRetry(String chatId, String text, int maxRetries) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                SendMessage message = SendMessage.builder()
                        .chatId(chatId)
                        .text(text)
                        .parseMode("HTML")
                        .disableWebPagePreview(true)
                        .build();
                
                execute(message);
                
                log.debug("Message sent successfully to chat {} on attempt {}", chatId, attempt);
                return true;
                
            } catch (TelegramApiException e) {
                lastException = e;
                log.warn("Failed to send message to chat {} on attempt {}: {}", 
                        chatId, attempt, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        Duration delay = telegramConfig.getBot().getRetryDelay()
                                .multipliedBy(attempt); // 지수적 백오프
                        Thread.sleep(delay.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry delay interrupted", ie);
                        break;
                    }
                }
            }
        }
        
        log.error("Failed to send message to chat {} after {} attempts", chatId, maxRetries, lastException);
        return false;
    }
    
    /**
     * Bot 상태 확인
     */
    public CompletableFuture<Boolean> checkBotHealth() {
        return CompletableFuture.supplyAsync(() -> {
            if (!telegramConfig.getBot().isEnabled()) {
                return false;
            }
            
            try {
                // Bot 정보 조회로 API 상태 확인
                getMe();
                return true;
            } catch (Exception e) {
                log.warn("Telegram bot health check failed", e);
                return false;
            }
        });
    }
    
    /**
     * 포맷팅된 알림 메시지 전송
     */
    public CompletableFuture<Boolean> sendAlert(String chatId, String title, String content, String severity) {
        String formattedMessage = formatAlertMessage(title, content, severity);
        return sendMessage(chatId, formattedMessage);
    }
    
    /**
     * 알림 메시지 포맷팅
     */
    private String formatAlertMessage(String title, String content, String severity) {
        String emoji = getEmojiForSeverity(severity);
        
        return String.format(
                "%s <b>%s</b>\n\n" +
                "%s\n\n" +
                "<i>시간:</i> %s\n" +
                "<i>심각도:</i> %s",
                emoji,
                title,
                content,
                java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                ),
                severity.toUpperCase()
        );
    }
    
    /**
     * 심각도별 이모지 반환
     */
    private String getEmojiForSeverity(String severity) {
        return switch (severity.toLowerCase()) {
            case "spam" -> "🚫";
            case "advertisement" -> "📢";
            case "abuse" -> "⚠️";
            case "inappropriate" -> "🔞";
            case "conflict" -> "💥";
            default -> "🔔";
        };
    }
}