package com.sauron.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauron.common.queue.MessageQueueService;
import com.sauron.listener.dto.MessageRequest;
import com.sauron.listener.dto.MessageResponse;
import com.sauron.listener.entity.Message;
import com.sauron.listener.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureTestMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T-004 메시지 수신 API 통합 테스트
 * 전체 플로우 검증: 인증 → 유효성 검증 → Rate Limit → Redis Queue → DB 저장
 */
@SpringBootTest
@AutoConfigureTestMvc
@ActiveProfiles("test")
@Transactional
class MessageApiIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private MessageRepository messageRepository;
    
    @MockBean
    private MessageQueueService messageQueueService;
    
    private static final String API_BASE_URL = "/api/v1/messages";
    
    @BeforeEach
    void setUp() {
        // 큐 서비스 Mock 설정
        when(messageQueueService.enqueueForAnalysis(any(MessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(true));
    }
    
    @Test
    @WithMockUser
    void testMessageSubmission_FullWorkflow_Success() throws Exception {
        // Given
        MessageRequest request = createValidMessageRequest();
        
        // When
        MvcResult result = mockMvc.perform(post(API_BASE_URL)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").value(request.getMessageId()))
                .andExpect(jsonPath("$.status").value("received"))
                .andReturn();
        
        // Then
        MessageResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), 
                MessageResponse.class
        );
        
        // 1. API 응답 검증
        assertNotNull(response);
        assertEquals(request.getMessageId(), response.getMessageId());
        assertEquals("received", response.getStatus());
        
        // 2. DB 저장 검증
        Message savedMessage = messageRepository.findByMessageId(request.getMessageId());
        assertNotNull(savedMessage);
        assertEquals(request.getDeviceId(), savedMessage.getDeviceId());
        assertEquals(request.getChatRoomTitle(), savedMessage.getChatRoomTitle());
        assertEquals("PENDING", savedMessage.getDetectionStatus());
        assertNotNull(savedMessage.getCreatedAt());
    }
    
    @Test
    void testMessageSubmission_WithoutAuthentication_Returns401() throws Exception {
        // Given
        MessageRequest request = createValidMessageRequest();
        
        // When & Then
        mockMvc.perform(post(API_BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser
    void testMessageSubmission_InvalidRequest_Returns400() throws Exception {
        // Given - 필수 필드 누락
        MessageRequest invalidRequest = new MessageRequest();
        invalidRequest.setMessageId(""); // 빈 값
        // deviceId, content 누락
        
        // When & Then
        mockMvc.perform(post(API_BASE_URL)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }
    
    @Test
    @WithMockUser
    void testMessageSubmission_TooLongContent_Returns400() throws Exception {
        // Given - 너무 긴 메시지
        MessageRequest request = createValidMessageRequest();
        request.setContent("x".repeat(2001)); // 2000자 초과
        
        // When & Then
        mockMvc.perform(post(API_BASE_URL)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
    
    @Test
    @WithMockUser
    void testMessageSubmission_QueueFailure_Returns500() throws Exception {
        // Given
        MessageRequest request = createValidMessageRequest();
        
        // Queue 실패 시뮬레이션
        when(messageQueueService.enqueueForAnalysis(any(MessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(false));
        
        // When & Then
        mockMvc.perform(post(API_BASE_URL)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("QUEUE_ERROR"));
    }
    
    @Test
    @WithMockUser
    void testMessageRetrieval_ExistingMessage_Success() throws Exception {
        // Given - 메시지를 먼저 저장
        Message savedMessage = createAndSaveMessage();
        
        // When & Then
        mockMvc.perform(get(API_BASE_URL + "/" + savedMessage.getMessageId())
                .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(savedMessage.getMessageId()))
                .andExpect(jsonPath("$.deviceId").value(savedMessage.getDeviceId()))
                .andExpect(jsonPath("$.detectionStatus").value(savedMessage.getDetectionStatus()));
    }
    
    @Test
    @WithMockUser
    void testMessageRetrieval_NonExistentMessage_Returns404() throws Exception {
        // Given
        String nonExistentMessageId = "non-existent-id";
        
        // When & Then
        mockMvc.perform(get(API_BASE_URL + "/" + nonExistentMessageId)
                .with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MESSAGE_NOT_FOUND"));
    }
    
    @Test
    @WithMockUser
    void testMessageSubmission_Performance_Under1Second() throws Exception {
        // Given
        MessageRequest request = createValidMessageRequest();
        
        // When
        long startTime = System.currentTimeMillis();
        
        mockMvc.perform(post(API_BASE_URL)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        
        long endTime = System.currentTimeMillis();
        long processingTime = endTime - startTime;
        
        // Then - 1초 이내 처리 검증
        assertTrue(processingTime < 1000, 
                   "Processing time should be under 1 second, but was: " + processingTime + "ms");
    }
    
    @Test
    @WithMockUser
    void testMessageSubmission_DuplicateMessageId_Returns409() throws Exception {
        // Given - 중복 메시지 ID
        MessageRequest request1 = createValidMessageRequest();
        MessageRequest request2 = createValidMessageRequest();
        request2.setMessageId(request1.getMessageId()); // 동일한 ID 사용
        
        // When - 첫 번째 메시지 전송
        mockMvc.perform(post(API_BASE_URL)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());
        
        // Then - 두 번째 메시지 전송 시 충돌 에러
        mockMvc.perform(post(API_BASE_URL)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_MESSAGE_ID"));
    }
    
    @Test
    @WithMockUser
    void testMessageSubmission_SpecialCharacters_Success() throws Exception {
        // Given - 특수 문자 포함 메시지
        MessageRequest request = createValidMessageRequest();
        request.setContent("🔥특가 이벤트🔥 광고입니다! @#$%^&*()_+");
        request.setChatRoomTitle("테스트방 (특수문자)");
        
        // When & Then
        mockMvc.perform(post(API_BASE_URL)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").value(request.getMessageId()));
    }
    
    @Test
    @WithMockUser
    void testMessageSubmission_EmptyOptionalFields_Success() throws Exception {
        // Given - 선택적 필드들을 비운 요청
        MessageRequest request = createValidMessageRequest();
        request.setChatRoomTitle(null);
        request.setPriority(null);
        
        // When & Then
        mockMvc.perform(post(API_BASE_URL)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").value(request.getMessageId()));
        
        // DB에서 기본값 확인
        Message savedMessage = messageRepository.findByMessageId(request.getMessageId());
        assertEquals("normal", savedMessage.getPriority()); // 기본값
    }
    
    @Test
    @WithMockUser 
    void testConcurrentMessageSubmission_Success() throws Exception {
        // Given - 동시 요청용 메시지들
        int concurrentRequests = 5;
        MessageRequest[] requests = new MessageRequest[concurrentRequests];
        
        for (int i = 0; i < concurrentRequests; i++) {
            requests[i] = createValidMessageRequest();
            requests[i].setMessageId("concurrent-msg-" + i);
        }
        
        // When - 동시 요청 수행
        CompletableFuture<Void>[] futures = new CompletableFuture[concurrentRequests];
        
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    mockMvc.perform(post(API_BASE_URL)
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requests[index])))
                            .andExpect(status().isCreated());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        
        // Then - 모든 요청 완료 대기
        CompletableFuture.allOf(futures).join();
        
        // 모든 메시지가 DB에 저장되었는지 확인
        for (MessageRequest request : requests) {
            Message savedMessage = messageRepository.findByMessageId(request.getMessageId());
            assertNotNull(savedMessage, "Message should be saved: " + request.getMessageId());
        }
    }
    
    /**
     * 유효한 메시지 요청 생성 헬퍼
     */
    private MessageRequest createValidMessageRequest() {
        MessageRequest request = new MessageRequest();
        request.setMessageId(UUID.randomUUID().toString());
        request.setDeviceId("test-device-" + System.currentTimeMillis());
        request.setChatRoomTitle("테스트 채팅방");
        request.setContent("안녕하세요! 테스트 메시지입니다.");
        request.setPriority("normal");
        return request;
    }
    
    /**
     * 테스트용 메시지 생성 및 저장 헬퍼
     */
    private Message createAndSaveMessage() {
        Message message = Message.builder()
                .messageId("test-msg-" + System.currentTimeMillis())
                .deviceId("test-device")
                .chatRoomTitle("테스트방")
                .contentEncrypted("테스트 메시지 내용")
                .contentHash("test-hash")
                .priority("normal")
                .detectionStatus("PENDING")
                .createdAt(Instant.now())
                .build();
        
        return messageRepository.save(message);
    }
}