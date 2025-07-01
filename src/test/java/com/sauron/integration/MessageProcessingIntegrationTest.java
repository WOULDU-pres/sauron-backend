package com.sauron.integration;

import com.sauron.common.service.LogStorageService;
import com.sauron.common.utils.EncryptionUtils;
import com.sauron.listener.dto.MessageRequest;
import com.sauron.listener.dto.MessageResponse;
import com.sauron.listener.entity.Message;
import com.sauron.listener.repository.MessageRepository;
import com.sauron.listener.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 메시지 처리 통합 테스트
 * MessageService -> LogStorageService -> EncryptionUtils 통합 플로우 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "sauron.encryption.key=dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcw==", // 32 bytes base64
    "sauron.encryption.salt=test-salt-2024",
    "gemini.worker.enabled=false",  // 자동 워커 시작 방지
    "gemini.cache.enabled=true"
})
@Transactional
class MessageProcessingIntegrationTest {
    
    @Autowired
    private MessageService messageService;
    
    @Autowired
    private LogStorageService logStorageService;
    
    @Autowired
    private EncryptionUtils encryptionUtils;
    
    @Autowired
    private MessageRepository messageRepository;
    
    @Test
    void testCompleteMessageProcessingFlow() {
        // Given
        String messageId = "msg-" + UUID.randomUUID().toString();
        String deviceId = "device-test-001";
        String chatRoomTitle = "테스트 채팅방";
        String chatRoomId = "room-001";
        String messageContent = "안녕하세요! 이것은 테스트 메시지입니다.";
        
        MessageRequest request = new MessageRequest(
                messageId,
                chatRoomTitle,
                deviceId, // senderHash
                messageContent,
                Instant.now(), // receivedAt
                "com.kakao.talk", // packageName
                deviceId,
                "normal" // priority
        );
        
        // When
        MessageResponse response = messageService.processMessage(request);
        
        // Then
        assertNotNull(response);
        assertEquals("RECEIVED", response.getStatus());
        assertEquals(messageId, response.getMessageId());
        assertNotNull(response.getId());
        
        // 데이터베이스에서 저장된 메시지 확인
        Optional<Message> savedMessageOpt = messageRepository.findByMessageId(messageId);
        assertTrue(savedMessageOpt.isPresent());
        
        Message savedMessage = savedMessageOpt.get();
        assertEquals(messageId, savedMessage.getMessageId());
        assertEquals(deviceId, savedMessage.getDeviceId());
        assertEquals("PENDING", savedMessage.getDetectionStatus());
        
        // 암호화 검증
        assertNotNull(savedMessage.getContentEncrypted());
        assertNotEquals(messageContent, savedMessage.getContentEncrypted());
        
        // 복호화 검증
        String decryptedContent = encryptionUtils.decrypt(savedMessage.getContentEncrypted());
        assertEquals(messageContent, decryptedContent);
        
        // 해시 검증
        assertNotNull(savedMessage.getContentHash());
        assertNotNull(savedMessage.getSenderHash());
        
        String expectedContentHash = encryptionUtils.hashContent(messageContent);
        assertEquals(expectedContentHash, savedMessage.getContentHash());
        
        String expectedSenderHash = encryptionUtils.hashSender(deviceId);
        assertEquals(expectedSenderHash, savedMessage.getSenderHash());
        
        // 채팅방 정보 익명화 검증
        assertNotEquals(chatRoomTitle, savedMessage.getChatRoomTitle());
        assertNotNull(savedMessage.getChatRoomId());
    }
    
    @Test
    void testMessageProcessingWithEncryption() {
        // Given
        String messageContent = "민감한 개인정보가 포함된 메시지입니다.";
        String deviceId = "secure-device-001";
        
        MessageRequest request = new MessageRequest(
                "secure-msg-" + UUID.randomUUID().toString(),
                "보안 테스트방",
                deviceId, // senderHash
                messageContent,
                Instant.now(), // receivedAt
                "com.kakao.talk", // packageName
                deviceId,
                "normal" // priority
        );
        
        // When
        MessageResponse response = messageService.processMessage(request);
        
        // Then
        assertEquals("RECEIVED", response.getStatus());
        
        Optional<Message> savedMessage = messageRepository.findByMessageId(request.getMessageId());
        assertTrue(savedMessage.isPresent());
        
        Message message = savedMessage.get();
        
        // 원본 내용이 암호화되어 저장되었는지 확인
        assertNotEquals(messageContent, message.getContentEncrypted());
        assertTrue(message.getContentEncrypted().length() > messageContent.length());
        
        // 복호화 시 원본과 일치하는지 확인
        String decrypted = encryptionUtils.decrypt(message.getContentEncrypted());
        assertEquals(messageContent, decrypted);
        
        // 발신자 정보가 해시화되었는지 확인
        assertNotEquals(deviceId, message.getSenderHash());
        assertEquals(64, message.getSenderHash().length()); // SHA-256 = 64 hex chars
    }
    
    @Test
    void testLogStorageServiceIntegration() {
        // Given
        String messageId = "log-test-" + UUID.randomUUID().toString();
        String messageContent = "로그 저장 테스트 메시지";
        
        MessageRequest request = new MessageRequest(
                messageId,
                "로그 테스트방",
                "log-device-001", // senderHash
                messageContent,
                Instant.now(), // receivedAt
                "com.kakao.talk", // packageName
                "log-device-001", // deviceId
                "normal" // priority
        );
        
        // When
        MessageResponse response = messageService.processMessage(request);
        
        // Then
        assertEquals("RECEIVED", response.getStatus());
        
        // LogStorageService를 통한 조회 테스트
        Optional<Message> foundMessage = logStorageService.findMessageByMessageId(messageId);
        assertTrue(foundMessage.isPresent());
        
        Message message = foundMessage.get();
        assertEquals(messageId, message.getMessageId());
        assertEquals("PENDING", message.getDetectionStatus());
        
        // 내용 해시가 올바르게 생성되었는지 확인
        String expectedHash = encryptionUtils.hashContent(messageContent);
        assertEquals(expectedHash, message.getContentHash());
    }
    
    @Test
    void testDuplicateMessageHandling() {
        // Given
        String messageId = "duplicate-test-" + UUID.randomUUID().toString();
        MessageRequest request = new MessageRequest(
                messageId,
                "중복 테스트방",
                "duplicate-device-001", // senderHash
                "중복 메시지 테스트",
                Instant.now(), // receivedAt
                "com.kakao.talk", // packageName
                "duplicate-device-001", // deviceId
                "normal" // priority
        );
        
        // When - 첫 번째 메시지 처리
        MessageResponse firstResponse = messageService.processMessage(request);
        assertEquals("RECEIVED", firstResponse.getStatus());
        
        // Then - 두 번째 동일한 메시지 처리 시 예외 발생
        assertThrows(Exception.class, () -> {
            messageService.processMessage(request);
        });
        
        // 데이터베이스에는 하나의 메시지만 존재해야 함
        Optional<Message> savedMessage = messageRepository.findByMessageId(messageId);
        assertTrue(savedMessage.isPresent());
    }
    
    @Test
    void testEncryptionUtilsValidation() {
        // Given
        String testContent = "암호화 테스트 내용";
        String testSender = "test-sender-123";
        String testChatRoom = "테스트 채팅방";
        
        // When & Then
        // 암호화/복호화 검증
        String encrypted = encryptionUtils.encrypt(testContent);
        assertNotNull(encrypted);
        assertNotEquals(testContent, encrypted);
        
        String decrypted = encryptionUtils.decrypt(encrypted);
        assertEquals(testContent, decrypted);
        
        // 해시 검증
        String contentHash = encryptionUtils.hashContent(testContent);
        assertNotNull(contentHash);
        assertEquals(64, contentHash.length()); // SHA-256 hex length
        
        String senderHash = encryptionUtils.hashSender(testSender);
        assertNotNull(senderHash);
        assertEquals(64, senderHash.length());
        
        String chatRoomHash = encryptionUtils.hashChatRoom(testChatRoom);
        assertNotNull(chatRoomHash);
        assertEquals(64, chatRoomHash.length());
        
        // 같은 입력에 대해 같은 결과가 나오는지 확인 (일관성)
        assertEquals(contentHash, encryptionUtils.hashContent(testContent));
        assertEquals(senderHash, encryptionUtils.hashSender(testSender));
        assertEquals(chatRoomHash, encryptionUtils.hashChatRoom(testChatRoom));
    }
} 