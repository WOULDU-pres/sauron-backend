package com.sauron.listener.service;

import com.sauron.common.dto.BusinessException;
import com.sauron.common.dto.ErrorCode;
import com.sauron.common.external.GeminiWorkerClient;
import com.sauron.common.queue.MessageQueueException;
import com.sauron.common.queue.MessageQueueService;
import com.sauron.common.ratelimit.RateLimitException;
import com.sauron.common.ratelimit.RateLimitService;
import com.sauron.common.service.LogStorageService;
import com.sauron.common.utils.EncryptionUtils;
import com.sauron.common.validation.MessageValidator;
import com.sauron.filter.service.MessageFilterService;
import com.sauron.listener.dto.MessageRequest;
import com.sauron.listener.dto.MessageResponse;
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
 * 메시지 처리 서비스
 * 메시지 검증, Rate Limiting, 저장, 큐잉, AI 분석을 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {
    
    private final MessageValidator messageValidator;
    private final RateLimitService rateLimitService;
    private final MessageQueueService messageQueueService;
    private final MessageRepository messageRepository;
    private final GeminiWorkerClient geminiWorkerClient;
    private final LogStorageService logStorageService;
    private final EncryptionUtils encryptionUtils;
    private final MessageFilterService messageFilterService;
    
    /**
     * 메시지 처리 메인 로직
     * 
     * @param request 메시지 요청
     * @return 처리 결과
     */
    @Transactional
    public MessageResponse processMessage(MessageRequest request) {
        log.debug("Processing message - ID: {}", request.getMessageId());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. Rate Limiting 확인
            checkRateLimit(request);
            
            // 2. 메시지 검증
            validateMessage(request);
            
            // 3. 중복 메시지 확인
            checkDuplicateMessage(request);
            
            // 4. 메시지 데이터베이스 저장
            Message savedMessage = saveMessage(request);
            
            // 5. 분석 큐에 메시지 전송 (비동기)
            boolean queued = queueForAnalysis(request);
            
            // 6. AI 분석 시작 (비동기, 별도 처리)
            if (queued) {
                startAsyncAnalysis(savedMessage);
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Message processed successfully - ID: {}, DB ID: {}, Time: {}ms, Queued: {}", 
                    request.getMessageId(), savedMessage.getId(), processingTime, queued);
            
            return MessageResponse.success(savedMessage.getId(), request.getMessageId(), queued);
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Failed to process message - ID: {}, Time: {}ms, Error: {}", 
                     request.getMessageId(), processingTime, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Rate Limiting 확인
     */
    private void checkRateLimit(MessageRequest request) {
        // 디바이스별 Rate Limit 확인
        if (!rateLimitService.isAllowedForDevice(request.getDeviceId())) {
            int remaining = rateLimitService.getRemainingRequests("device:" + request.getDeviceId());
            throw new RateLimitException(
                "device:" + request.getDeviceId(), 
                remaining,
                "Device rate limit exceeded. Please slow down your requests."
            );
        }
        
        log.debug("Rate limit check passed for device: {}", request.getDeviceId());
    }
    
    /**
     * 메시지 검증
     */
    private void validateMessage(MessageRequest request) {
        MessageValidator.ValidationResult result = messageValidator.validate(request);
        
        if (!result.isValid()) {
            String errorMessage = "Message validation failed: " + result.getErrorMessage();
            log.warn("Validation failed for message {}: {}", request.getMessageId(), errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        
        log.debug("Message validation passed for ID: {}", request.getMessageId());
    }
    
    /**
     * 중복 메시지 확인
     */
    private void checkDuplicateMessage(MessageRequest request) {
        // 메시지 ID 중복 확인
        if (messageRepository.existsByMessageId(request.getMessageId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_MESSAGE, 
                "Message with ID " + request.getMessageId() + " already exists");
        }
        
        // 내용 해시 기반 중복 확인 (선택적)
        if (request.getMessageContent() != null) {
            String contentHash = encryptionUtils.hashContent(request.getMessageContent());
            if (messageRepository.existsByContentHash(contentHash)) {
                log.warn("Duplicate content detected for message: {}", request.getMessageId());
                // 중복 내용이지만 처리는 계속 진행 (로깅만)
            }
        }
        
        log.debug("Duplicate check passed for message: {}", request.getMessageId());
    }
    
    /**
     * 메시지 데이터베이스 저장 (암호화 및 로그 저장 통합)
     */
    private Message saveMessage(MessageRequest request) {
        try {
            log.debug("Saving message to database with encryption - ID: {}", request.getMessageId());
            
            // EncryptionUtils를 사용한 암호화 및 해싱
            String encryptedContent = encryptionUtils.encrypt(request.getMessageContent());
            String contentHash = encryptionUtils.hashContent(request.getMessageContent());
            String senderHash = encryptionUtils.hashSender(request.getSenderHash() != null ? 
                    request.getSenderHash() : request.getDeviceId());
            String chatRoomHash = encryptionUtils.hashChatRoom(request.getChatRoomTitle());
            String anonymizedTitle = encryptionUtils.anonymizeChatRoomTitle(request.getChatRoomTitle());
            
            Message message = Message.builder()
                    .messageId(request.getMessageId())
                    .deviceId(request.getDeviceId())
                    .chatRoomId(chatRoomHash)
                    .chatRoomTitle(anonymizedTitle)
                    .senderHash(senderHash)
                    .contentEncrypted(encryptedContent)
                    .contentHash(contentHash)
                    .priority(request.getPriority() != null ? request.getPriority() : "normal")
                    .detectionStatus("PENDING")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            
            // LogStorageService를 통한 통합 로그 저장
            Message saved = logStorageService.saveMessageLog(message);
            
            log.info("Message saved with encryption - DB ID: {}, Message ID: {}, Content encrypted: {}", 
                     saved.getId(), saved.getMessageId(), encryptedContent != null);
            
            return saved;
            
        } catch (LogStorageService.LogStorageException e) {
            log.error("Log storage error saving message: {}", request.getMessageId(), e);
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "Failed to save message log", e);
                 } catch (EncryptionUtils.EncryptionException e) {
             log.error("Encryption error processing message: {}", request.getMessageId(), e);
             throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to encrypt message content", e);
         } catch (DataAccessException e) {
             log.error("Database error saving message: {}", request.getMessageId(), e);
             throw new BusinessException(ErrorCode.DATABASE_ERROR, "Failed to save message to database", e);
         } catch (Exception e) {
             log.error("Unexpected error saving message: {}", request.getMessageId(), e);
             throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to save message", e);
        }
    }
    
    /**
     * 분석을 위해 메시지를 큐에 전송
     */
    private boolean queueForAnalysis(MessageRequest request) {
        try {
            // 비동기로 큐에 전송 (성능 향상)
            CompletableFuture<Boolean> queueResult = messageQueueService.enqueueForAnalysis(request);
            
            // 큐 전송은 비동기이지만, 빠른 응답을 위해 즉시 true 반환
            // 실제 전송 결과는 별도로 로깅됨
            queueResult.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Async queue enqueue failed for message: {}", 
                            request.getMessageId(), throwable);
                    
                    // 비동기 실패 시 동기 방식으로 재시도
                    try {
                        boolean syncResult = messageQueueService.enqueueForAnalysisSync(request);
                        log.info("Sync queue fallback {} for message: {}", 
                                syncResult ? "succeeded" : "failed", request.getMessageId());
                    } catch (Exception e) {
                        log.error("Sync queue fallback failed for message: {}", 
                                request.getMessageId(), e);
                    }
                } else {
                    log.debug("Async queue enqueue {} for message: {}", 
                            result ? "succeeded" : "failed", request.getMessageId());
                }
            });
            
            log.info("Message queued for analysis - ID: {}, Content length: {}", 
                    request.getMessageId(), 
                    request.getMessageContent() != null ? request.getMessageContent().length() : 0);
            
            return true;
            
        } catch (MessageQueueException e) {
            log.error("Failed to queue message for analysis: {}", request.getMessageId(), e);
            // 큐 실패는 치명적이지 않으므로 예외를 던지지 않고 false 반환
            return false;
        } catch (Exception e) {
            log.error("Unexpected error queuing message: {}", request.getMessageId(), e);
            return false;
        }
    }
    
    /**
     * 비동기 AI 분석 시작
     */
    private void startAsyncAnalysis(Message message) {
        CompletableFuture.runAsync(() -> {
            try {
                log.debug("Starting async AI analysis for message: {}", message.getMessageId());
                
                // 메시지 상태를 PROCESSING으로 업데이트
                updateMessageStatus(message.getMessageId(), "PROCESSING");
                
                // Gemini API를 통한 분석 수행
                String decryptedContent = encryptionUtils.decrypt(message.getContentEncrypted());
                CompletableFuture<GeminiWorkerClient.AnalysisResult> analysisResult = 
                    geminiWorkerClient.analyzeMessage(decryptedContent, message.getChatRoomTitle());
                
                // 분석 결과 처리
                analysisResult.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("AI analysis failed for message: {}", message.getMessageId(), throwable);
                        updateMessageStatus(message.getMessageId(), "FAILED");
                    } else {
                        log.info("AI analysis completed for message: {} - Type: {}, Confidence: {}", 
                                message.getMessageId(), result.getDetectedType(), result.getConfidenceScore());
                        
                        // 분석 결과를 데이터베이스에 저장
                        updateAnalysisResult(message.getMessageId(), result);
                    }
                });
                
            } catch (Exception e) {
                log.error("Async analysis initialization failed for message: {}", message.getMessageId(), e);
                updateMessageStatus(message.getMessageId(), "FAILED");
            }
        });
    }
    
    /**
     * 메시지 상태 업데이트
     */
    private void updateMessageStatus(String messageId, String status) {
        try {
            int updated = messageRepository.updateMessageStatus("PENDING", status, Instant.now());
            if (updated == 0) {
                log.warn("No message found to update status for ID: {}", messageId);
            }
        } catch (Exception e) {
            log.error("Failed to update message status for ID: {}", messageId, e);
        }
    }
    
    /**
     * 분석 결과 업데이트
     */
    @Transactional
    private void updateAnalysisResult(String messageId, GeminiWorkerClient.AnalysisResult result) {
        try {
            Instant now = Instant.now();
            int updated = messageRepository.updateAnalysisResult(
                messageId,
                result.getDetectedType(),
                result.getConfidenceScore(),
                "COMPLETED",
                now,
                now
            );
            
            if (updated == 0) {
                log.warn("No message found to update analysis result for ID: {}", messageId);
            } else {
                log.debug("Analysis result updated for message: {}", messageId);
            }
            
        } catch (Exception e) {
            log.error("Failed to update analysis result for message: {}", messageId, e);
        }
    }
    
    /**
     * 서비스 헬스 체크
     */
    public boolean isHealthy() {
        try {
            // 큐 상태 확인
            MessageQueueService.QueueStatus queueStatus = messageQueueService.getQueueStatus();
            boolean queueHealthy = queueStatus.isHealthy();
            
            // 데이터베이스 연결 확인
            boolean dbHealthy = checkDatabaseHealth();
            
            // Gemini API 상태 확인 (비동기)
            CompletableFuture<Boolean> geminiHealthy = geminiWorkerClient.checkApiHealth();
            
            if (!queueHealthy) {
                log.warn("Message queue is unhealthy: {}", queueStatus);
            }
            
            if (!dbHealthy) {
                log.warn("Database is unhealthy");
            }
            
            return queueHealthy && dbHealthy;
            
        } catch (Exception e) {
            log.error("Health check failed", e);
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
     * 큐 상태 정보 조회
     */
    public MessageQueueService.QueueStatus getQueueStatus() {
        return messageQueueService.getQueueStatus();
    }
    
} 