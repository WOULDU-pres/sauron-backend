package com.sauron.common.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauron.listener.dto.MessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Redis Stream 기반 메시지 큐 서비스
 * 메시지를 비동기적으로 큐에 전송하여 AI 분석 워커에서 처리할 수 있도록 합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageQueueService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // Redis Stream 이름
    private static final String MESSAGE_STREAM = "sauron:message:analysis";
    private static final String DLQ_STREAM = "sauron:message:dlq"; // Dead Letter Queue
    
    // 메시지 필드명
    private static final String FIELD_MESSAGE_ID = "messageId";
    private static final String FIELD_PAYLOAD = "payload";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_DEVICE_ID = "deviceId";
    private static final String FIELD_CHAT_ROOM = "chatRoom";
    private static final String FIELD_PRIORITY = "priority";
    private static final String FIELD_RETRY_COUNT = "retryCount";
    
    /**
     * 메시지를 분석 큐에 비동기로 전송합니다.
     * 
     * @param messageRequest 큐에 전송할 메시지
     * @return 큐 전송 성공 여부를 나타내는 CompletableFuture
     */
    public CompletableFuture<Boolean> enqueueForAnalysis(MessageRequest messageRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return enqueueMessage(MESSAGE_STREAM, messageRequest, 0);
                
            } catch (Exception e) {
                log.error("Failed to enqueue message asynchronously: {}", 
                         messageRequest.getMessageId(), e);
                return false;
            }
        });
    }
    
    /**
     * 메시지를 동기적으로 큐에 전송합니다.
     * 
     * @param messageRequest 큐에 전송할 메시지
     * @return 큐 전송 성공 여부
     */
    public boolean enqueueForAnalysisSync(MessageRequest messageRequest) {
        try {
            return enqueueMessage(MESSAGE_STREAM, messageRequest, 0);
            
        } catch (Exception e) {
            log.error("Failed to enqueue message synchronously: {}", 
                     messageRequest.getMessageId(), e);
            return false;
        }
    }
    
    /**
     * 실패한 메시지를 재시도 큐에 전송합니다.
     * 
     * @param messageRequest 재시도할 메시지
     * @param retryCount 현재 재시도 횟수
     * @return 큐 전송 성공 여부
     */
    public boolean enqueueForRetry(MessageRequest messageRequest, int retryCount) {
        try {
            if (retryCount >= 3) {
                // 최대 재시도 횟수 초과 시 DLQ로 전송
                return enqueueMessage(DLQ_STREAM, messageRequest, retryCount);
            } else {
                // 재시도 큐로 전송 (일정 시간 후 처리되도록)
                return enqueueMessage(MESSAGE_STREAM, messageRequest, retryCount);
            }
            
        } catch (Exception e) {
            log.error("Failed to enqueue message for retry: {}, retryCount: {}", 
                     messageRequest.getMessageId(), retryCount, e);
            return false;
        }
    }
    
    /**
     * 메시지를 지정된 스트림에 전송하는 내부 메서드
     */
    private boolean enqueueMessage(String streamName, MessageRequest messageRequest, int retryCount) {
        try {
            // 메시지 직렬화
            String payload = objectMapper.writeValueAsString(messageRequest);
            
            // Redis Stream 레코드 생성
            Map<String, String> messageFields = new HashMap<>();
            messageFields.put(FIELD_MESSAGE_ID, messageRequest.getMessageId());
            messageFields.put(FIELD_PAYLOAD, payload);
            messageFields.put(FIELD_TIMESTAMP, Instant.now().toString());
            messageFields.put(FIELD_DEVICE_ID, messageRequest.getDeviceId());
            messageFields.put(FIELD_CHAT_ROOM, messageRequest.getChatRoomTitle());
            messageFields.put(FIELD_PRIORITY, messageRequest.getPriority() != null ? messageRequest.getPriority() : "normal");
            messageFields.put(FIELD_RETRY_COUNT, String.valueOf(retryCount));
            
            // Redis Stream에 메시지 추가
            ObjectRecord<String, Map<String, String>> record = StreamRecords.objectBacked(messageFields)
                    .withStreamKey(streamName);
            
            String recordId = redisTemplate.opsForStream().add(record).getValue();
            
            log.info("Message enqueued successfully - Stream: {}, MessageID: {}, RecordID: {}, RetryCount: {}", 
                    streamName, messageRequest.getMessageId(), recordId, retryCount);
            
            return true;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message: {}", messageRequest.getMessageId(), e);
            throw new MessageQueueException("Message serialization failed", e);
            
        } catch (Exception e) {
            log.error("Failed to enqueue message to stream {}: {}", 
                     streamName, messageRequest.getMessageId(), e);
            throw new MessageQueueException("Message enqueue failed", e);
        }
    }
    
    /**
     * 큐의 상태 정보를 반환합니다.
     * 
     * @return 큐 상태 정보
     */
    public QueueStatus getQueueStatus() {
        try {
            Long messageStreamLength = redisTemplate.opsForStream().size(MESSAGE_STREAM);
            Long dlqStreamLength = redisTemplate.opsForStream().size(DLQ_STREAM);
            
            return new QueueStatus(
                messageStreamLength != null ? messageStreamLength : 0,
                dlqStreamLength != null ? dlqStreamLength : 0,
                isRedisHealthy()
            );
            
        } catch (Exception e) {
            log.error("Failed to get queue status", e);
            return new QueueStatus(0, 0, false);
        }
    }
    
    /**
     * 특정 메시지가 큐에 있는지 확인합니다.
     * 
     * @param messageId 확인할 메시지 ID
     * @return 큐에 있으면 true, 없으면 false
     */
    public boolean isMessageInQueue(String messageId) {
        try {
            // 실제 구현에서는 Stream 검색이 복잡하므로 별도 추적 메커니즘 필요
            // 현재는 단순히 로그만 남김
            log.debug("Checking if message {} is in queue", messageId);
            return false; // TODO: 실제 구현 필요
            
        } catch (Exception e) {
            log.error("Failed to check message in queue: {}", messageId, e);
            return false;
        }
    }
    
    /**
     * DLQ의 메시지를 다시 메인 큐로 이동시킵니다.
     * 
     * @param messageId 재처리할 메시지 ID
     * @return 성공 여부
     */
    public boolean requeueFromDLQ(String messageId) {
        try {
            // TODO: DLQ에서 메시지를 찾아서 메인 큐로 이동
            log.info("Requeuing message from DLQ: {}", messageId);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to requeue message from DLQ: {}", messageId, e);
            return false;
        }
    }
    
    /**
     * Redis 연결 상태를 확인합니다.
     */
    private boolean isRedisHealthy() {
        try {
            redisTemplate.opsForValue().get("health-check");
            return true;
        } catch (Exception e) {
            log.warn("Redis health check failed", e);
            return false;
        }
    }
    
    /**
     * 우선순위가 있는 메시지를 큐에 전송합니다.
     * 
     * @param messageRequest 큐에 전송할 메시지
     * @param priority 우선순위 (HIGH, NORMAL, LOW)
     * @return 큐 전송 성공 여부
     */
    public boolean enqueueWithPriority(MessageRequest messageRequest, String priority) {
        try {
            // 우선순위별 스트림 이름 생성
            String priorityStream = MESSAGE_STREAM + ":" + priority.toLowerCase();
            return enqueueMessage(priorityStream, messageRequest, 0);
            
        } catch (Exception e) {
            log.error("Failed to enqueue message with priority {}: {}", 
                     priority, messageRequest.getMessageId(), e);
            return false;
        }
    }
    
    /**
     * 큐 상태 정보를 담는 클래스
     */
    public static class QueueStatus {
        private final long mainQueueSize;
        private final long dlqSize;
        private final boolean healthy;
        private final String errorMessage;
        
        public QueueStatus(long mainQueueSize, long dlqSize, boolean healthy) {
            this(mainQueueSize, dlqSize, healthy, null);
        }
        
        public QueueStatus(long mainQueueSize, long dlqSize, boolean healthy, String errorMessage) {
            this.mainQueueSize = mainQueueSize;
            this.dlqSize = dlqSize;
            this.healthy = healthy;
            this.errorMessage = errorMessage;
        }
        
        public static QueueStatusBuilder builder() {
            return new QueueStatusBuilder();
        }
        
        public long getMainQueueSize() {
            return mainQueueSize;
        }
        
        public long getDlqSize() {
            return dlqSize;
        }
        
        public boolean isHealthy() {
            return healthy;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        // 편의를 위한 별칭 메서드들
        public long getPendingMessages() {
            return mainQueueSize;
        }
        
        public long getProcessingMessages() {
            return 0L; // TODO: 실제 처리 중인 메시지 수 추적 구현
        }
        
        @Override
        public String toString() {
            return String.format("QueueStatus{mainQueue=%d, dlq=%d, healthy=%s, error=%s}", 
                               mainQueueSize, dlqSize, healthy, errorMessage);
        }
        
        public static class QueueStatusBuilder {
            private long mainQueueSize = 0;
            private long dlqSize = 0;
            private boolean healthy = true;
            private String errorMessage;
            
            public QueueStatusBuilder mainQueueSize(long mainQueueSize) {
                this.mainQueueSize = mainQueueSize;
                return this;
            }
            
            public QueueStatusBuilder dlqSize(long dlqSize) {
                this.dlqSize = dlqSize;
                return this;
            }
            
            public QueueStatusBuilder healthy(boolean healthy) {
                this.healthy = healthy;
                return this;
            }
            
            public QueueStatusBuilder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }
            
            // 별칭 메서드들
            public QueueStatusBuilder pendingMessages(long pendingMessages) {
                this.mainQueueSize = pendingMessages;
                return this;
            }
            
            public QueueStatusBuilder processingMessages(long processingMessages) {
                // TODO: 처리 중인 메시지 수 필드 추가 시 구현
                return this;
            }
            
            public QueueStatus build() {
                return new QueueStatus(mainQueueSize, dlqSize, healthy, errorMessage);
            }
        }
    }
} 