package com.sauron.telegram;

import com.sauron.common.config.TelegramConfig;
import com.sauron.common.external.telegram.TelegramBotClient;
import com.sauron.common.external.telegram.TelegramNotificationService;
import com.sauron.listener.entity.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T-006 텔레그램 알림 통합 테스트
 * 이상 메시지 감지 시 텔레그램 알림 전송 기능 검증
 */
@SpringBootTest
@ActiveProfiles("test")
class TelegramNotificationIntegrationTest {
    
    @Autowired
    private TelegramNotificationService telegramNotificationService;
    
    @MockBean
    private TelegramBotClient telegramBotClient;
    
    @MockBean
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private TelegramConfig telegramConfig;
    
    @BeforeEach
    void setUp() {
        // 텔레그램 설정 활성화
        telegramConfig.getBot().setEnabled(true);
        telegramConfig.getAlerts().setEnabled(true);
        telegramConfig.getChannels().setDefaultChatId("12345");
        
        // Redis 모킹 - 스로틀링 및 카운트 체크
        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
    }
    
    @Test
    void testSendAbnormalMessageAlert_Success() throws Exception {
        // Given
        Message abnormalMessage = createAbnormalMessage();
        String detectedType = "spam";
        Double confidence = 0.85;
        
        when(telegramBotClient.sendAlert(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        long startTime = System.currentTimeMillis();
        Boolean result = telegramNotificationService
                .sendAbnormalMessageAlert(abnormalMessage, detectedType, confidence)
                .get();
        long endTime = System.currentTimeMillis();
        
        // Then
        assertTrue(result, "Alert should be sent successfully");
        assertTrue((endTime - startTime) < 5000, 
                  "Alert should be sent within 5 seconds");
        
        verify(telegramBotClient).sendAlert(
                eq("12345"),
                contains("스팸 메시지"),
                contains("신뢰도: 85.0%"),
                eq("spam")
        );
    }
    
    @Test
    void testSendAlert_LowConfidence_NotSent() throws Exception {
        // Given
        Message abnormalMessage = createAbnormalMessage();
        String detectedType = "spam";
        Double confidence = 0.6; // 임계값(0.7) 미만
        
        // When
        Boolean result = telegramNotificationService
                .sendAbnormalMessageAlert(abnormalMessage, detectedType, confidence)
                .get();
        
        // Then
        assertFalse(result, "Alert should not be sent for low confidence");
        verify(telegramBotClient, never()).sendAlert(anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void testSendAlert_DisabledType_NotSent() throws Exception {
        // Given
        Message abnormalMessage = createAbnormalMessage();
        String detectedType = "unknown"; // 알림 대상이 아닌 타입
        Double confidence = 0.9;
        
        // When
        Boolean result = telegramNotificationService
                .sendAbnormalMessageAlert(abnormalMessage, detectedType, confidence)
                .get();
        
        // Then
        assertFalse(result, "Alert should not be sent for disabled type");
        verify(telegramBotClient, never()).sendAlert(anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void testSendAlert_TelegramDisabled_NotSent() throws Exception {
        // Given
        telegramConfig.getBot().setEnabled(false);
        Message abnormalMessage = createAbnormalMessage();
        String detectedType = "spam";
        Double confidence = 0.85;
        
        // When
        Boolean result = telegramNotificationService
                .sendAbnormalMessageAlert(abnormalMessage, detectedType, confidence)
                .get();
        
        // Then
        assertFalse(result, "Alert should not be sent when Telegram is disabled");
        verify(telegramBotClient, never()).sendAlert(anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void testSendAlert_TelegramFailure_ReturnsFalse() throws Exception {
        // Given
        Message abnormalMessage = createAbnormalMessage();
        String detectedType = "spam";
        Double confidence = 0.85;
        
        when(telegramBotClient.sendAlert(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(false));
        
        // When
        Boolean result = telegramNotificationService
                .sendAbnormalMessageAlert(abnormalMessage, detectedType, confidence)
                .get();
        
        // Then
        assertFalse(result, "Should return false when Telegram sending fails");
    }
    
    @Test
    void testSendAlert_MultipleRecipients_Success() throws Exception {
        // Given
        telegramConfig.getChannels().setAdminChatIds("12345,67890");
        Message abnormalMessage = createAbnormalMessage();
        String detectedType = "abuse";
        Double confidence = 0.95;
        
        when(telegramBotClient.sendAlert(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        Boolean result = telegramNotificationService
                .sendAbnormalMessageAlert(abnormalMessage, detectedType, confidence)
                .get();
        
        // Then
        assertTrue(result, "Alert should be sent to all recipients");
        
        verify(telegramBotClient).sendAlert(
                eq("12345"),
                anyString(),
                anyString(),
                eq("abuse")
        );
        verify(telegramBotClient).sendAlert(
                eq("67890"),
                anyString(),
                anyString(),
                eq("abuse")
        );
    }
    
    @Test
    void testAlertThrottling_PreventsSpam() throws Exception {
        // Given
        Message abnormalMessage = createAbnormalMessage();
        String detectedType = "spam";
        Double confidence = 0.85;
        
        // 이미 스로틀링된 상태로 설정
        when(redisTemplate.opsForValue().get("telegram:alert:throttle:" + abnormalMessage.getChatRoomId()))
                .thenReturn("2025-01-01T12:00:00Z");
        
        // When
        Boolean result = telegramNotificationService
                .sendAbnormalMessageAlert(abnormalMessage, detectedType, confidence)
                .get();
        
        // Then
        assertFalse(result, "Alert should be throttled");
        verify(telegramBotClient, never()).sendAlert(anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void testServiceHealthCheck() throws Exception {
        // Given
        when(telegramBotClient.checkBotHealth())
                .thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        Boolean healthy = telegramNotificationService.checkServiceHealth().get();
        
        // Then
        assertTrue(healthy, "Service should be healthy");
        verify(telegramBotClient).checkBotHealth();
    }
    
    @Test
    void testSendTestAlert() throws Exception {
        // Given
        String testChatId = "test123";
        when(telegramBotClient.sendAlert(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        Boolean result = telegramNotificationService.sendTestAlert(testChatId).get();
        
        // Then
        assertTrue(result, "Test alert should be sent successfully");
        verify(telegramBotClient).sendAlert(
                eq(testChatId),
                eq("Sauron 시스템 테스트"),
                contains("정상적으로 작동"),
                eq("test")
        );
    }
    
    @Test
    void testPerformance_AlertSentWithin5Seconds() throws Exception {
        // Given
        Message abnormalMessage = createAbnormalMessage();
        String detectedType = "inappropriate";
        Double confidence = 0.8;
        
        when(telegramBotClient.sendAlert(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        long startTime = System.currentTimeMillis();
        
        Boolean result = telegramNotificationService
                .sendAbnormalMessageAlert(abnormalMessage, detectedType, confidence)
                .get();
        
        long endTime = System.currentTimeMillis();
        long processingTime = endTime - startTime;
        
        // Then
        assertTrue(result, "Alert should be sent successfully");
        assertTrue(processingTime < 5000, 
                  "Alert should be sent within 5 seconds, but took: " + processingTime + "ms");
        
        System.out.println("Telegram alert processing time: " + processingTime + "ms");
    }
    
    /**
     * 테스트용 이상 메시지 생성
     */
    private Message createAbnormalMessage() {
        return Message.builder()
                .messageId("test-msg-001")
                .deviceId("test-device")
                .chatRoomId("test-room-001")
                .chatRoomTitle("테스트 채팅방")
                .senderHash("sender-hash-001")
                .contentEncrypted("encrypted-spam-content")
                .contentHash("content-hash-001")
                .detectedType("spam")
                .confidenceScore(0.85)
                .detectionStatus("COMPLETED")
                .createdAt(Instant.now())
                .analyzedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}