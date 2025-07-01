package com.sauron.listener.service;

import com.sauron.common.dto.BusinessException;
import com.sauron.common.dto.ErrorCode;
import com.sauron.common.external.GeminiWorkerClient;
import com.sauron.common.queue.MessageQueueService;
import com.sauron.common.ratelimit.RateLimitService;
import com.sauron.common.validation.MessageValidator;
import com.sauron.listener.dto.MessageRequest;
import com.sauron.listener.dto.MessageResponse;
import com.sauron.listener.entity.Message;
import com.sauron.listener.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MessageService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    
    @Mock
    private MessageValidator messageValidator;
    
    @Mock
    private RateLimitService rateLimitService;
    
    @Mock
    private MessageQueueService messageQueueService;
    
    @Mock
    private MessageRepository messageRepository;
    
    @Mock
    private GeminiWorkerClient geminiWorkerClient;
    
    @InjectMocks
    private MessageService messageService;
    
    private MessageRequest testRequest;
    private Message testMessage;
    
    @BeforeEach
    void setUp() {
        testRequest = new MessageRequest();
        testRequest.setMessageId("test-message-001");
        testRequest.setDeviceId("test-device-001");
        testRequest.setChatRoomTitle("Test Chat Room");
        testRequest.setSenderHash("test-sender-hash");
        testRequest.setMessageContent("안녕하세요 테스트 메시지입니다");
        testRequest.setPackageName("com.kakao.talk");
        testRequest.setPriority("normal");
        testRequest.setReceivedAt(Instant.now());
        
        testMessage = Message.builder()
                .id(1L)
                .messageId("test-message-001")
                .deviceId("test-device-001")
                .chatRoomTitle("Test Chat Room")
                .senderHash("test-sender-hash")
                .contentEncrypted("encrypted-content")
                .detectionStatus("PENDING")
                .createdAt(Instant.now())
                .build();
    }
    
    @Test
    void processMessage_성공_시나리오() {
        // Given
        when(rateLimitService.isAllowedForDevice(anyString())).thenReturn(true);
        when(messageValidator.validate(any(MessageRequest.class)))
                .thenReturn(new MessageValidator.ValidationResult(true, new ArrayList<>()));
        when(messageRepository.existsByMessageId(anyString())).thenReturn(false);
        when(messageRepository.existsByContentHash(anyString())).thenReturn(false);
        when(messageRepository.save(any(Message.class))).thenReturn(testMessage);
        when(messageQueueService.enqueueForAnalysis(any(MessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        MessageResponse response = messageService.processMessage(testRequest);
        
        // Then
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("test-message-001", response.getMessageId());
        assertTrue(response.isQueuedForAnalysis());
        assertEquals("RECEIVED", response.getStatus());
        
        verify(messageRepository).save(any(Message.class));
        verify(messageQueueService).enqueueForAnalysis(any(MessageRequest.class));
    }
    
    @Test
    void processMessage_중복_메시지_예외() {
        // Given
        when(rateLimitService.isAllowedForDevice(anyString())).thenReturn(true);
        when(messageValidator.validate(any(MessageRequest.class)))
                .thenReturn(new MessageValidator.ValidationResult(true, new ArrayList<>()));
        when(messageRepository.existsByMessageId(anyString())).thenReturn(true);
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> messageService.processMessage(testRequest));
        
        assertEquals(ErrorCode.DUPLICATE_MESSAGE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("already exists"));
        
        verify(messageRepository, never()).save(any(Message.class));
    }
    
    @Test
    void processMessage_검증_실패_예외() {
        // Given
        when(rateLimitService.isAllowedForDevice(anyString())).thenReturn(true);
        when(messageValidator.validate(any(MessageRequest.class)))
                .thenReturn(new MessageValidator.ValidationResult(false, Arrays.asList("Invalid message format")));
        
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> messageService.processMessage(testRequest));
        
        assertTrue(exception.getMessage().contains("Message validation failed"));
        
        verify(messageRepository, never()).save(any(Message.class));
    }
    
    @Test
    void isHealthy_모든_서비스_정상() {
        // Given
        MessageQueueService.QueueStatus queueStatus = 
                new MessageQueueService.QueueStatus(10, 0, true);
        when(messageQueueService.getQueueStatus()).thenReturn(queueStatus);
        when(messageRepository.count()).thenReturn(100L);
        when(geminiWorkerClient.checkApiHealth()).thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        boolean healthy = messageService.isHealthy();
        
        // Then
        assertTrue(healthy);
        verify(messageQueueService).getQueueStatus();
        verify(messageRepository).count();
    }
    
    @Test
    void isHealthy_데이터베이스_장애() {
        // Given
        MessageQueueService.QueueStatus queueStatus = 
                new MessageQueueService.QueueStatus(10, 0, true);
        when(messageQueueService.getQueueStatus()).thenReturn(queueStatus);
        when(messageRepository.count()).thenThrow(new RuntimeException("Database connection failed"));
        
        // When
        boolean healthy = messageService.isHealthy();
        
        // Then
        assertFalse(healthy);
    }
    
    @Test
    void isHealthy_큐_서비스_장애() {
        // Given
        MessageQueueService.QueueStatus queueStatus = 
                new MessageQueueService.QueueStatus(10, 0, false);
        when(messageQueueService.getQueueStatus()).thenReturn(queueStatus);
        when(messageRepository.count()).thenReturn(100L);
        
        // When
        boolean healthy = messageService.isHealthy();
        
        // Then
        assertFalse(healthy);
    }
} 