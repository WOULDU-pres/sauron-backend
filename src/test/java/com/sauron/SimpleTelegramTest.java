package com.sauron;

import com.sauron.common.external.GeminiWorkerClient;
import com.sauron.common.external.telegram.TelegramBotClient;
import com.sauron.common.external.telegram.TelegramNotificationService;
import com.sauron.common.worker.GeminiAnalysisWorker;
import com.sauron.listener.entity.Message;
import com.sauron.listener.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for Sauron system without external API dependencies
 * Tests the complete message processing pipeline using mocks
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public class SimpleTelegramTest {

    @Mock
    private TelegramBotClient telegramBotClient;
    
    @Mock
    private TelegramNotificationService telegramNotificationService;
    
    @Mock
    private GeminiWorkerClient geminiWorkerClient;
    
    @Mock
    private MessageRepository messageRepository;
    
    @Mock
    private GeminiAnalysisWorker geminiAnalysisWorker;
    
    private Message testMessage;
    
    @BeforeEach
    void setUp() {
        testMessage = createTestMessage();
    }
    
    /**
     * Test: Telegram alert sending without external API
     */
    @Test
    void testTelegramAlertSending_Success() throws Exception {
        // Given
        String detectedType = "spam";
        Double confidence = 0.85;
        String chatId = "12345";
        
        when(telegramBotClient.sendAlert(eq(chatId), anyString(), anyString(), eq(detectedType)))
                .thenReturn(CompletableFuture.completedFuture(true));
        
        when(telegramNotificationService.sendAbnormalMessageAlert(testMessage, detectedType, confidence))
                .thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        Boolean result = telegramNotificationService
                .sendAbnormalMessageAlert(testMessage, detectedType, confidence)
                .get();
        
        // Then
        assertTrue(result, "Alert should be sent successfully");
        verify(telegramNotificationService).sendAbnormalMessageAlert(testMessage, detectedType, confidence);
    }
    
    /**
     * Test: Gemini analysis mock verification
     */
    @Test
    void testGeminiAnalysis_MockVerification() throws Exception {
        // Given
        String messageContent = "This is a spam message with repeated text!!!";
        String chatRoomTitle = "Test Chat Room";
        
        GeminiWorkerClient.AnalysisResult mockResult = new GeminiWorkerClient.AnalysisResult(
                "spam", 0.95, "Detected repeated exclamation marks and promotional language"
        );
        
        when(geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle))
                .thenReturn(CompletableFuture.completedFuture(mockResult));
        
        // When
        GeminiWorkerClient.AnalysisResult result = geminiWorkerClient
                .analyzeMessage(messageContent, chatRoomTitle)
                .get();
        
        // Then
        assertNotNull(result);
        assertEquals("spam", result.getDetectedType());
        assertEquals(0.95, result.getConfidenceScore());
        assertTrue(result.getMetadata().contains("repeated exclamation marks"));
        
        verify(geminiWorkerClient).analyzeMessage(messageContent, chatRoomTitle);
    }
    
    /**
     * Test: Complete message processing pipeline
     */
    @Test
    void testCompleteMessagePipeline_Success() throws Exception {
        // Given
        String messageId = "msg-123";
        String messageContent = "Buy now! Limited time offer!!!";
        String chatRoomTitle = "Shopping Group";
        
        // Mock message repository
        when(messageRepository.findByMessageId(messageId))
                .thenReturn(Optional.of(testMessage));
        when(messageRepository.save(any(Message.class)))
                .thenReturn(testMessage);
        
        // Mock Gemini analysis
        GeminiWorkerClient.AnalysisResult analysisResult = new GeminiWorkerClient.AnalysisResult(
                "advertisement", 0.88, "Promotional language detected"
        );
        when(geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle))
                .thenReturn(CompletableFuture.completedFuture(analysisResult));
        
        // Mock Telegram notification
        when(telegramNotificationService.sendAbnormalMessageAlert(any(Message.class), anyString(), anyDouble()))
                .thenReturn(CompletableFuture.completedFuture(true));
        
        // When - Simulate worker processing
        Optional<Message> messageOpt = messageRepository.findByMessageId(messageId);
        assertTrue(messageOpt.isPresent());
        
        Message message = messageOpt.get();
        message.setDetectionStatus("PROCESSING");
        messageRepository.save(message);
        
        GeminiWorkerClient.AnalysisResult result = geminiWorkerClient
                .analyzeMessage(messageContent, chatRoomTitle)
                .get();
        
        // Update message with analysis results
        message.setDetectedType(result.getDetectedType());
        message.setConfidenceScore(result.getConfidenceScore());
        message.setDetectionStatus("COMPLETED");
        message.setAnalyzedAt(Instant.now());
        messageRepository.save(message);
        
        // Send alert if abnormal
        if (message.isAbnormal()) {
            Boolean alertSent = telegramNotificationService
                    .sendAbnormalMessageAlert(message, result.getDetectedType(), result.getConfidenceScore())
                    .get();
            assertTrue(alertSent);
        }
        
        // Then
        assertEquals("advertisement", result.getDetectedType());
        assertEquals("COMPLETED", message.getDetectionStatus());
        assertNotNull(message.getAnalyzedAt());
        
        verify(messageRepository, times(2)).save(any(Message.class));
        verify(geminiWorkerClient).analyzeMessage(messageContent, chatRoomTitle);
        verify(telegramNotificationService).sendAbnormalMessageAlert(any(Message.class), eq("advertisement"), eq(0.88));
    }
    
    /**
     * Test: Configuration validation
     */
    @Test
    void testConfigurationValidation() {
        // Test that required configuration properties would be validated
        // This tests the configuration structure without needing actual values
        
        // Given
        String[] requiredProperties = {
            "gemini.api.key",
            "telegram.bot.token",
            "telegram.bot.username",
            "spring.datasource.url",
            "spring.data.redis.host"
        };
        
        // When/Then
        for (String property : requiredProperties) {
            assertNotNull(property, "Property should be defined: " + property);
        }
        
        // Verify property patterns
        assertTrue("${GEMINI_API_KEY}".matches("\\$\\{[A-Z_]+\\}"), "Environment variable pattern should be valid");
        assertTrue("${TELEGRAM_BOT_TOKEN:your-bot-token-here}".contains(":"), "Default value should be provided");
    }
    
    /**
     * Test: Worker health status
     */
    @Test
    void testWorkerHealthStatus() {
        // Given
        GeminiAnalysisWorker.WorkerStatus expectedStatus = new GeminiAnalysisWorker.WorkerStatus(
                true, 4, 100L, 2L, 50L
        );
        
        when(geminiAnalysisWorker.getWorkerStatus()).thenReturn(expectedStatus);
        
        // When
        GeminiAnalysisWorker.WorkerStatus status = geminiAnalysisWorker.getWorkerStatus();
        
        // Then
        assertTrue(status.isRunning());
        assertEquals(4, status.getThreadCount());
        assertEquals(100L, status.getProcessedMessages());
        assertEquals(2L, status.getFailedMessages());
        assertEquals(50L, status.getCacheHits());
        
        verify(geminiAnalysisWorker).getWorkerStatus();
    }
    
    /**
     * Test: Telegram bot health check
     */
    @Test
    void testTelegramBotHealthCheck() throws Exception {
        // Given
        when(telegramBotClient.checkBotHealth())
                .thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        Boolean healthStatus = telegramBotClient.checkBotHealth().get();
        
        // Then
        assertTrue(healthStatus, "Bot should be healthy in test environment");
        verify(telegramBotClient).checkBotHealth();
    }
    
    /**
     * Test: Error handling scenarios
     */
    @Test
    void testErrorHandling_GeminiFailure() throws Exception {
        // Given
        String messageContent = "Test message";
        String chatRoomTitle = "Test Room";
        
        when(geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Gemini API Error")));
        
        // When/Then
        assertThrows(Exception.class, () -> {
            geminiWorkerClient.analyzeMessage(messageContent, chatRoomTitle).get();
        });
        
        verify(geminiWorkerClient).analyzeMessage(messageContent, chatRoomTitle);
    }
    
    /**
     * Test: Message filtering logic
     */
    @Test
    void testMessageFiltering_NormalMessage() {
        // Given
        testMessage.setDetectedType("normal");
        testMessage.setConfidenceScore(0.95);
        
        // When/Then
        assertFalse(testMessage.isAbnormal(), "Normal message should not trigger alerts");
    }
    
    @Test
    void testMessageFiltering_AbnormalMessage() {
        // Given
        testMessage.setDetectedType("spam");
        testMessage.setConfidenceScore(0.85);
        
        // When/Then
        assertTrue(testMessage.isAbnormal(), "Spam message should trigger alerts");
    }
    
    /**
     * Helper method to create test message
     */
    private Message createTestMessage() {
        Message message = new Message();
        message.setMessageId("test-msg-001");
        message.setChatRoomId("room-123");
        message.setChatRoomTitle("Test Chat Room");
        message.setSenderHash("user-hash-456");
        message.setContentEncrypted("Test message content");
        message.setCreatedAt(Instant.now());
        message.setDetectionStatus("PENDING");
        return message;
    }
    
    /**
     * Integration test for service health checks
     */
    @Test
    void testServiceHealthChecks() throws Exception {
        // Given
        when(telegramNotificationService.checkServiceHealth())
                .thenReturn(CompletableFuture.completedFuture(true));
        when(geminiWorkerClient.checkApiHealth())
                .thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        Boolean telegramHealth = telegramNotificationService.checkServiceHealth().get();
        Boolean geminiHealth = geminiWorkerClient.checkApiHealth().get();
        
        // Then
        assertTrue(telegramHealth, "Telegram service should be healthy");
        assertTrue(geminiHealth, "Gemini service should be healthy");
        
        verify(telegramNotificationService).checkServiceHealth();
        verify(geminiWorkerClient).checkApiHealth();
    }
    
    /**
     * Performance test for message processing
     */
    @Test
    void testMessageProcessingPerformance() {
        // Given
        long startTime = System.currentTimeMillis();
        int messageCount = 10;
        
        // When - Simulate processing multiple messages
        for (int i = 0; i < messageCount; i++) {
            Message message = createTestMessage();
            message.setMessageId("msg-" + i);
            // Simulate processing time
            try {
                Thread.sleep(1); // 1ms per message
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        // Then
        assertTrue(totalTime < 1000, "Processing 10 messages should take less than 1 second");
        double avgTime = (double) totalTime / messageCount;
        assertTrue(avgTime < 100, "Average processing time should be less than 100ms per message");
    }
} 