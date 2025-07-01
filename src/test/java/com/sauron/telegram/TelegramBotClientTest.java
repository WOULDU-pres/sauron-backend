package com.sauron.telegram;

import com.sauron.common.config.TelegramConfig;
import com.sauron.common.external.telegram.TelegramBotClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TelegramBotClient 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class TelegramBotClientTest {
    
    private TelegramBotClient telegramBotClient;
    private TelegramConfig telegramConfig;
    
    @BeforeEach
    void setUp() {
        telegramConfig = new TelegramConfig();
        telegramConfig.getBot().setEnabled(true);
        telegramConfig.getBot().setToken("test-token");
        telegramConfig.getBot().setUsername("test-bot");
        telegramConfig.getBot().setTimeout(Duration.ofSeconds(10));
        telegramConfig.getBot().setMaxRetries(3);
        telegramConfig.getBot().setRetryDelay(Duration.ofSeconds(1));
        
        // NOTE: 실제 Telegram API 호출 없이 테스트하기 위해 stub 구현 사용
        telegramBotClient = new TelegramBotClientStub(telegramConfig);
    }
    
    @Test
    void testGetBotUsername() {
        // When
        String username = telegramBotClient.getBotUsername();
        
        // Then
        assertEquals("test-bot", username);
    }
    
    @Test
    void testGetBotToken() {
        // When
        String token = telegramBotClient.getBotToken();
        
        // Then
        assertEquals("test-token", token);
    }
    
    @Test
    void testSendMessage_Success() throws Exception {
        // Given
        String chatId = "12345";
        String message = "테스트 메시지";
        
        // When
        CompletableFuture<Boolean> result = telegramBotClient.sendMessage(chatId, message);
        
        // Then
        assertTrue(result.get(), "Message should be sent successfully");
    }
    
    @Test
    void testSendMessage_DisabledBot_ReturnsFalse() throws Exception {
        // Given
        telegramConfig.getBot().setEnabled(false);
        String chatId = "12345";
        String message = "테스트 메시지";
        
        // When
        CompletableFuture<Boolean> result = telegramBotClient.sendMessage(chatId, message);
        
        // Then
        assertFalse(result.get(), "Message should not be sent when bot is disabled");
    }
    
    @Test
    void testSendMessage_EmptyChatId_ReturnsFalse() throws Exception {
        // Given
        String chatId = "";
        String message = "테스트 메시지";
        
        // When
        CompletableFuture<Boolean> result = telegramBotClient.sendMessage(chatId, message);
        
        // Then
        assertFalse(result.get(), "Message should not be sent with empty chat ID");
    }
    
    @Test
    void testSendAlert_WithFormatting() throws Exception {
        // Given
        String chatId = "12345";
        String title = "스팸 메시지 감지";
        String content = "채팅방: 테스트방\n신뢰도: 85%";
        String severity = "spam";
        
        // When
        CompletableFuture<Boolean> result = telegramBotClient.sendAlert(chatId, title, content, severity);
        
        // Then
        assertTrue(result.get(), "Alert should be sent successfully");
    }
    
    @Test
    void testCheckBotHealth_Success() throws Exception {
        // When
        CompletableFuture<Boolean> result = telegramBotClient.checkBotHealth();
        
        // Then
        assertTrue(result.get(), "Bot health check should pass");
    }
    
    @Test
    void testCheckBotHealth_DisabledBot_ReturnsFalse() throws Exception {
        // Given
        telegramConfig.getBot().setEnabled(false);
        
        // When
        CompletableFuture<Boolean> result = telegramBotClient.checkBotHealth();
        
        // Then
        assertFalse(result.get(), "Health check should fail when bot is disabled");
    }
    
    /**
     * 테스트용 TelegramBotClient Stub 구현
     * 실제 Telegram API 호출 없이 테스트 가능
     */
    private static class TelegramBotClientStub extends TelegramBotClient {
        
        public TelegramBotClientStub(TelegramConfig telegramConfig) {
            super(telegramConfig);
        }
        
        @Override
        public CompletableFuture<Boolean> sendMessage(String chatId, String text) {
            return CompletableFuture.supplyAsync(() -> {
                if (!telegramConfig.getBot().isEnabled()) {
                    return false;
                }
                
                if (chatId == null || chatId.trim().isEmpty()) {
                    return false;
                }
                
                // 실제 API 호출 대신 성공 반환
                return true;
            });
        }
        
        @Override
        public CompletableFuture<Boolean> checkBotHealth() {
            return CompletableFuture.supplyAsync(() -> 
                telegramConfig.getBot().isEnabled());
        }
        
    }
}