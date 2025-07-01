package com.sauron.telegram;

import com.sauron.common.config.TelegramConfig;
import com.sauron.common.external.telegram.TelegramNotificationService;
import com.sauron.listener.entity.Message;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 간단한 텔레그램 기능 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
class SimpleTelegramTest {
    
    @Test
    void testTelegramConfigCreation() {
        TelegramConfig config = new TelegramConfig();
        config.getBot().setEnabled(true);
        config.getBot().setToken("test-token");
        config.getBot().setUsername("test-bot");
        
        assertTrue(config.getBot().isEnabled());
        assertEquals("test-token", config.getBot().getToken());
        assertEquals("test-bot", config.getBot().getUsername());
        
        System.out.println("✅ 텔레그램 설정 클래스 생성 및 설정 테스트 통과");
    }
    
    @Test
    void testMessageAbnormalDetection() {
        Message normalMessage = Message.builder()
                .messageId("test-001")
                .detectedType("normal")
                .confidenceScore(0.9)
                .build();
        
        Message abnormalMessage = Message.builder()
                .messageId("test-002")
                .detectedType("spam")
                .confidenceScore(0.8)
                .build();
        
        assertFalse(normalMessage.isAbnormal());
        assertTrue(abnormalMessage.isAbnormal());
        
        System.out.println("✅ 메시지 이상 감지 로직 테스트 통과");
    }
    
    @Test
    void testMessageEntityCreation() {
        Message message = Message.builder()
                .messageId("test-msg-001")
                .deviceId("test-device")
                .chatRoomId("test-room")
                .chatRoomTitle("테스트 채팅방")
                .detectedType("advertisement")
                .confidenceScore(0.85)
                .detectionStatus("COMPLETED")
                .createdAt(Instant.now())
                .analyzedAt(Instant.now())
                .build();
        
        assertNotNull(message.getMessageId());
        assertEquals("advertisement", message.getDetectedType());
        assertEquals(0.85, message.getConfidenceScore());
        assertTrue(message.isAbnormal());
        assertTrue(message.isAnalysisCompleted());
        
        System.out.println("✅ 메시지 엔티티 생성 및 속성 테스트 통과");
    }
    
    @Test
    void testSystemIntegrationReadiness() {
        // 시스템 통합을 위한 기본 구성 요소들이 정상적으로 로드되는지 확인
        
        // 1. 텔레그램 설정
        TelegramConfig telegramConfig = new TelegramConfig();
        assertNotNull(telegramConfig);
        
        // 2. 기본 설정값 확인
        assertEquals(true, telegramConfig.getAlerts().isEnabled());
        assertEquals(0.7, telegramConfig.getAlerts().getMinConfidence());
        assertTrue(telegramConfig.getAlerts().getAlertTypes().contains("spam"));
        assertTrue(telegramConfig.getAlerts().getAlertTypes().contains("advertisement"));
        
        System.out.println("✅ 시스템 통합 준비 상태 확인 완료");
        System.out.println("  - 텔레그램 설정: ✓");
        System.out.println("  - 알림 설정: ✓");
        System.out.println("  - 지원 알림 유형: " + telegramConfig.getAlerts().getAlertTypes());
    }
}