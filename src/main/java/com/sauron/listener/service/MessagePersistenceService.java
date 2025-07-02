package com.sauron.listener.service;

import com.sauron.common.core.async.AsyncExecutor;
import com.sauron.common.dto.BusinessException;
import com.sauron.common.dto.ErrorCode;
import com.sauron.common.service.LogStorageService;
import com.sauron.common.utils.EncryptionUtils;
import com.sauron.listener.dto.MessageRequest;
import com.sauron.listener.entity.Message;
import com.sauron.listener.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * 메시지 저장 전용 서비스
 * 메시지 암호화, 데이터베이스 저장, 로그 저장을 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagePersistenceService {
    
    private final MessageRepository messageRepository;
    private final LogStorageService logStorageService;
    private final EncryptionUtils encryptionUtils;
    private final AsyncExecutor asyncExecutor;
    
    /**
     * 저장 결과 DTO
     */
    public static class PersistenceResult {
        private final boolean success;
        private final Message savedMessage;
        private final String errorMessage;
        
        private PersistenceResult(boolean success, Message savedMessage, String errorMessage) {
            this.success = success;
            this.savedMessage = savedMessage;
            this.errorMessage = errorMessage;
        }
        
        public static PersistenceResult success(Message savedMessage) {
            return new PersistenceResult(true, savedMessage, null);
        }
        
        public static PersistenceResult failure(String errorMessage) {
            return new PersistenceResult(false, null, errorMessage);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public Message getSavedMessage() { return savedMessage; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * 메시지 저장 (동기)
     * 
     * @param request 메시지 요청
     * @return 저장 결과
     */
    @Transactional
    public PersistenceResult saveMessage(MessageRequest request) {
        String messageId = request.getMessageId();
        
        try {
            log.debug("Starting message persistence for ID: {}", messageId);
            
            // 1. 메시지 암호화 및 해싱
            EncryptedMessageData encryptedData = encryptMessageData(request);
            
            // 2. 메시지 엔티티 생성
            Message message = createMessageEntity(request, encryptedData);
            
            // 3. 데이터베이스 저장
            Message savedMessage = saveToDatabase(message);
            
            // 4. 로그 저장
            saveToLogStorage(savedMessage);
            
            log.info("Message persistence completed successfully - DB ID: {}, Message ID: {}", 
                     savedMessage.getId(), savedMessage.getMessageId());
            
            return PersistenceResult.success(savedMessage);
            
        } catch (EncryptionUtils.EncryptionException e) {
            log.error("Encryption error during message persistence: {}", messageId, e);
            return PersistenceResult.failure("Failed to encrypt message content: " + e.getMessage());
            
        } catch (DataAccessException e) {
            log.error("Database error during message persistence: {}", messageId, e);
            return PersistenceResult.failure("Failed to save message to database: " + e.getMessage());
            
        } catch (LogStorageService.LogStorageException e) {
            log.error("Log storage error during message persistence: {}", messageId, e);
            return PersistenceResult.failure("Failed to save message log: " + e.getMessage());
            
        } catch (Exception e) {
            log.error("Unexpected error during message persistence: {}", messageId, e);
            return PersistenceResult.failure("Failed to save message: " + e.getMessage());
        }
    }
    
    /**
     * 메시지 저장 (비동기)
     * 
     * @param request 메시지 요청
     * @return CompletableFuture<PersistenceResult>
     */
    @Transactional
    public CompletableFuture<PersistenceResult> saveMessageAsync(MessageRequest request) {
        return asyncExecutor.executeWithRetry(
            () -> saveMessage(request),
            "message-persistence-" + request.getMessageId(),
            2  // 최대 2회 재시도
        );
    }
    
    /**
     * 메시지 데이터 암호화
     */
    private EncryptedMessageData encryptMessageData(MessageRequest request) {
        try {
            String encryptedContent = null;
            String contentHash = null;
            
            // 메시지 내용이 있는 경우에만 암호화
            if (request.getMessageContent() != null && !request.getMessageContent().trim().isEmpty()) {
                encryptedContent = encryptionUtils.encrypt(request.getMessageContent());
                contentHash = encryptionUtils.hashContent(request.getMessageContent());
            }
            
            String senderHash = encryptionUtils.hashSender(
                request.getSenderHash() != null ? request.getSenderHash() : request.getDeviceId()
            );
            
            String chatRoomHash = encryptionUtils.hashChatRoom(request.getChatRoomTitle());
            String anonymizedTitle = encryptionUtils.anonymizeChatRoomTitle(request.getChatRoomTitle());
            
            log.debug("Message data encryption completed for ID: {}", request.getMessageId());
            
            return EncryptedMessageData.builder()
                .encryptedContent(encryptedContent)
                .contentHash(contentHash)
                .senderHash(senderHash)
                .chatRoomHash(chatRoomHash)
                .anonymizedTitle(anonymizedTitle)
                .build();
                
        } catch (Exception e) {
            throw new EncryptionUtils.EncryptionException("Failed to encrypt message data", e);
        }
    }
    
    /**
     * 메시지 엔티티 생성
     */
    private Message createMessageEntity(MessageRequest request, EncryptedMessageData encryptedData) {
        Instant now = Instant.now();
        
        return Message.builder()
            .messageId(request.getMessageId())
            .deviceId(request.getDeviceId())
            .chatRoomId(encryptedData.getChatRoomHash())
            .chatRoomTitle(encryptedData.getAnonymizedTitle())
            .senderHash(encryptedData.getSenderHash())
            .contentEncrypted(encryptedData.getEncryptedContent())
            .contentHash(encryptedData.getContentHash())
            .priority(request.getPriority() != null ? request.getPriority() : "normal")
            .detectionStatus("PENDING")
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
    
    /**
     * 데이터베이스 저장
     */
    private Message saveToDatabase(Message message) {
        try {
            return messageRepository.save(message);
        } catch (DataAccessException e) {
            log.error("Failed to save message to database: {}", message.getMessageId(), e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "Database save failed", e);
        }
    }
    
    /**
     * 로그 저장
     */
    private void saveToLogStorage(Message savedMessage) {
        try {
            logStorageService.saveMessageLog(savedMessage);
        } catch (LogStorageService.LogStorageException e) {
            log.error("Failed to save message log: {}", savedMessage.getMessageId(), e);
            // 로그 저장 실패는 치명적이지 않으므로 예외를 다시 던지지 않음
            // 대신 메트릭이나 별도 알림으로 처리할 수 있음
        }
    }
    
    /**
     * 메시지 상태 업데이트
     * 
     * @param messageId 메시지 ID
     * @param status 새로운 상태
     * @return 업데이트 성공 여부
     */
    @Transactional
    public boolean updateMessageStatus(String messageId, String status) {
        try {
            Instant now = Instant.now();
            int updated = messageRepository.updateMessageStatus(messageId, status, now);
            
            if (updated == 0) {
                log.warn("No message found to update status for ID: {}", messageId);
                return false;
            }
            
            log.debug("Message status updated - ID: {}, Status: {}", messageId, status);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to update message status for ID: {}", messageId, e);
            return false;
        }
    }
    
    /**
     * 분석 결과 업데이트
     * 
     * @param messageId 메시지 ID
     * @param detectedType 감지된 타입
     * @param confidence 신뢰도
     * @return 업데이트 성공 여부
     */
    @Transactional
    public boolean updateAnalysisResult(String messageId, String detectedType, Double confidence) {
        try {
            Instant now = Instant.now();
            int updated = messageRepository.updateAnalysisResult(
                messageId, detectedType, confidence, "COMPLETED", now, now
            );
            
            if (updated == 0) {
                log.warn("No message found to update analysis result for ID: {}", messageId);
                return false;
            }
            
            log.debug("Analysis result updated - ID: {}, Type: {}, Confidence: {}", 
                     messageId, detectedType, confidence);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to update analysis result for ID: {}", messageId, e);
            return false;
        }
    }
    
    /**
     * 저장 서비스 상태 확인
     */
    public boolean isHealthy() {
        try {
            // 데이터베이스 연결 확인
            boolean dbHealthy = checkDatabaseHealth();
            
            // 암호화 서비스 상태 확인
            boolean encryptionHealthy = checkEncryptionHealth();
            
            if (!dbHealthy) {
                log.warn("Database connection is unhealthy");
            }
            
            if (!encryptionHealthy) {
                log.warn("Encryption service is unhealthy");
            }
            
            return dbHealthy && encryptionHealthy;
            
        } catch (Exception e) {
            log.error("Persistence service health check failed", e);
            return false;
        }
    }
    
    /**
     * 데이터베이스 연결 상태 확인
     */
    private boolean checkDatabaseHealth() {
        try {
            messageRepository.count();
            return true;
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return false;
        }
    }
    
    /**
     * 암호화 서비스 상태 확인
     */
    private boolean checkEncryptionHealth() {
        try {
            // 간단한 암호화/복호화 테스트
            String testData = "health_check";
            String encrypted = encryptionUtils.encrypt(testData);
            String decrypted = encryptionUtils.decrypt(encrypted);
            return testData.equals(decrypted);
        } catch (Exception e) {
            log.error("Encryption health check failed", e);
            return false;
        }
    }
    
    /**
     * 암호화된 메시지 데이터 DTO
     */
    public static class EncryptedMessageData {
        private final String encryptedContent;
        private final String contentHash;
        private final String senderHash;
        private final String chatRoomHash;
        private final String anonymizedTitle;
        
        private EncryptedMessageData(String encryptedContent, String contentHash, String senderHash,
                                   String chatRoomHash, String anonymizedTitle) {
            this.encryptedContent = encryptedContent;
            this.contentHash = contentHash;
            this.senderHash = senderHash;
            this.chatRoomHash = chatRoomHash;
            this.anonymizedTitle = anonymizedTitle;
        }
        
        public static EncryptedMessageDataBuilder builder() {
            return new EncryptedMessageDataBuilder();
        }
        
        // Getters
        public String getEncryptedContent() { return encryptedContent; }
        public String getContentHash() { return contentHash; }
        public String getSenderHash() { return senderHash; }
        public String getChatRoomHash() { return chatRoomHash; }
        public String getAnonymizedTitle() { return anonymizedTitle; }
        
        public static class EncryptedMessageDataBuilder {
            private String encryptedContent;
            private String contentHash;
            private String senderHash;
            private String chatRoomHash;
            private String anonymizedTitle;
            
            public EncryptedMessageDataBuilder encryptedContent(String encryptedContent) {
                this.encryptedContent = encryptedContent;
                return this;
            }
            
            public EncryptedMessageDataBuilder contentHash(String contentHash) {
                this.contentHash = contentHash;
                return this;
            }
            
            public EncryptedMessageDataBuilder senderHash(String senderHash) {
                this.senderHash = senderHash;
                return this;
            }
            
            public EncryptedMessageDataBuilder chatRoomHash(String chatRoomHash) {
                this.chatRoomHash = chatRoomHash;
                return this;
            }
            
            public EncryptedMessageDataBuilder anonymizedTitle(String anonymizedTitle) {
                this.anonymizedTitle = anonymizedTitle;
                return this;
            }
            
            public EncryptedMessageData build() {
                return new EncryptedMessageData(encryptedContent, contentHash, senderHash,
                                              chatRoomHash, anonymizedTitle);
            }
        }
    }
}