package com.sauron.common.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauron.common.external.GeminiWorkerClient;
import com.sauron.listener.dto.MessageRequest;
import com.sauron.listener.entity.Message;
import com.sauron.listener.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gemini 분석 워커 서비스
 * Redis Stream에서 메시지를 소비하여 비동기적으로 AI 분석을 수행합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiAnalysisWorker {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final GeminiWorkerClient geminiClient;
    private final MessageRepository messageRepository;
    
    @Value("${gemini.worker.enabled:true}")
    private boolean workerEnabled;
    
    @Value("${gemini.worker.threads:4}")
    private int workerThreads;
    
    @Value("${gemini.worker.batch-size:10}")
    private int batchSize;
    
    @Value("${gemini.worker.poll-timeout:5s}")
    private Duration pollTimeout;
    
    private static final String MESSAGE_STREAM = "sauron:message:analysis";
    private static final String DLQ_STREAM = "sauron:message:dlq";
    private static final String CONSUMER_GROUP = "gemini-workers";
    private static final String CONSUMER_NAME = "worker-1";
    
    private ExecutorService workerExecutor;
    private ScheduledExecutorService scheduledExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // 모니터링 메트릭
    private final AtomicLong processedMessages = new AtomicLong(0);
    private final AtomicLong failedMessages = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    
    /**
     * 워커 서비스 초기화
     */
    @PostConstruct
    public void initialize() {
        if (!workerEnabled) {
            log.info("Gemini worker is disabled");
            return;
        }
        
        try {
            createConsumerGroupIfNotExists();
            startWorker();
            log.info("Gemini analysis worker initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize Gemini worker", e);
        }
    }
    
    /**
     * 워커 서비스 종료
     */
    @PreDestroy
    public void shutdown() {
        if (!workerEnabled) {
            return;
        }
        
        log.info("Shutting down Gemini analysis worker");
        running.set(false);
        
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (workerExecutor != null) {
            workerExecutor.shutdown();
            try {
                if (!workerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    workerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("Gemini analysis worker shutdown completed");
    }
    
    /**
     * Consumer Group이 존재하지 않으면 생성
     */
    private void createConsumerGroupIfNotExists() {
        try {
            redisTemplate.opsForStream()
                    .createGroup(MESSAGE_STREAM, ReadOffset.earliest(), CONSUMER_GROUP);
            log.info("Created consumer group: {}", CONSUMER_GROUP);
            
        } catch (Exception e) {
            // Consumer Group이 이미 존재하는 경우 무시
            log.debug("Consumer group already exists or failed to create: {}", e.getMessage());
        }
    }
    
    /**
     * 워커 스레드 시작
     */
    private void startWorker() {
        workerExecutor = Executors.newFixedThreadPool(workerThreads);
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        running.set(true);
        
        // 메인 워커 루프
        for (int i = 0; i < workerThreads; i++) {
            final String workerName = CONSUMER_NAME + "-" + i;
            workerExecutor.submit(() -> workerLoop(workerName));
        }
        
        // 모니터링 스케줄러
        scheduledExecutor.scheduleAtFixedRate(this::logWorkerStats, 60, 60, TimeUnit.SECONDS);
        
        log.info("Started {} worker threads", workerThreads);
    }
    
    /**
     * 워커 메인 루프
     */
    private void workerLoop(String workerName) {
        log.info("Worker {} started", workerName);
        
        while (running.get()) {
            try {
                processMessageBatch(workerName);
                
            } catch (InterruptedException e) {
                log.info("Worker {} interrupted", workerName);
                Thread.currentThread().interrupt();
                break;
                
            } catch (Exception e) {
                log.error("Worker {} encountered error", workerName, e);
                
                try {
                    Thread.sleep(5000); // 에러 발생 시 잠시 대기
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.info("Worker {} stopped", workerName);
    }
    
    /**
     * 메시지 배치 처리
     */
    private void processMessageBatch(String workerName) throws InterruptedException {
        Consumer consumer = Consumer.from(CONSUMER_GROUP, workerName);
        StreamOffset<String> streamOffset = StreamOffset.create(MESSAGE_STREAM, ReadOffset.lastConsumed());
        
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .read(consumer, 
                      org.springframework.data.redis.connection.stream.StreamReadOptions.empty()
                          .count(batchSize)
                          .block(pollTimeout),
                      streamOffset);
        
        if (records == null || records.isEmpty()) {
            return;
        }
        
        log.debug("Worker {} processing {} messages", workerName, records.size());
        
        // 메시지를 병렬로 처리
        List<CompletableFuture<Void>> futures = records.stream()
                .map(record -> processMessageAsync(record, workerName))
                .toList();
        
        // 모든 처리 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .join();
    }
    
    /**
     * 개별 메시지 비동기 처리
     */
    private CompletableFuture<Void> processMessageAsync(MapRecord<String, Object, Object> record, String workerName) {
        return CompletableFuture.runAsync(() -> {
            try {
                processMessage(record, workerName);
                
                // 메시지 처리 완료 후 ACK
                redisTemplate.opsForStream().acknowledge(CONSUMER_GROUP, record);
                processedMessages.incrementAndGet();
                
            } catch (Exception e) {
                log.error("Failed to process message {}", record.getId(), e);
                failedMessages.incrementAndGet();
                
                // 실패한 메시지는 DLQ로 이동 또는 재시도 처리
                handleFailedMessage(record, e);
            }
        }, workerExecutor);
    }
    
    /**
     * 개별 메시지 처리 로직
     */
    private void processMessage(MapRecord<String, Object, Object> record, String workerName) {
        try {
            String payload = (String) record.getValue().get("payload");
            String messageId = (String) record.getValue().get("messageId");
            
            if (payload == null || messageId == null) {
                log.warn("Invalid message format in record {}", record.getId());
                return;
            }
            
            // 메시지 요청 역직렬화
            MessageRequest messageRequest = objectMapper.readValue(payload, MessageRequest.class);
            
            log.debug("Worker {} processing message: {}", workerName, messageId);
            
            // 데이터베이스에서 메시지 조회
            Message message = messageRepository.findByMessageId(messageId);
            if (message == null) {
                log.warn("Message not found in database: {}", messageId);
                return;
            }
            
            // 이미 처리된 메시지인지 확인
            if (message.isAnalysisCompleted()) {
                log.debug("Message {} already analyzed, skipping", messageId);
                return;
            }
            
            // 처리 상태 업데이트
            message.setDetectionStatus("PROCESSING");
            messageRepository.save(message);
            
            // Gemini 분석 수행
            GeminiWorkerClient.AnalysisResult analysisResult = geminiClient
                    .analyzeMessage(messageRequest.getContent(), messageRequest.getChatRoomTitle())
                    .get(); // 동기적으로 대기
            
            // 분석 결과를 데이터베이스에 저장
            updateMessageWithAnalysisResult(message, analysisResult);
            
            log.info("Message {} analyzed successfully - Type: {}, Confidence: {}", 
                    messageId, analysisResult.getDetectedType(), analysisResult.getConfidenceScore());
            
        } catch (Exception e) {
            log.error("Error processing message in record {}", record.getId(), e);
            throw new RuntimeException("Message processing failed", e);
        }
    }
    
    /**
     * 분석 결과로 메시지 엔티티 업데이트
     */
    private void updateMessageWithAnalysisResult(Message message, GeminiWorkerClient.AnalysisResult result) {
        message.setDetectedType(result.getDetectedType());
        message.setConfidenceScore(result.getConfidenceScore());
        message.setDetectionStatus("COMPLETED");
        message.setAnalyzedAt(Instant.now());
        message.setMetadata(result.getMetadata());
        
        messageRepository.save(message);
    }
    
    /**
     * 실패한 메시지 처리
     */
    private void handleFailedMessage(MapRecord<String, Object, Object> record, Exception error) {
        try {
            String messageId = (String) record.getValue().get("messageId");
            String retryCountStr = (String) record.getValue().get("retryCount");
            int retryCount = retryCountStr != null ? Integer.parseInt(retryCountStr) : 0;
            
            if (retryCount >= 3) {
                // 최대 재시도 횟수 초과 시 DLQ로 이동
                log.warn("Moving message {} to DLQ after {} retries", messageId, retryCount);
                // TODO: DLQ로 이동하는 로직 구현
                
                // 데이터베이스 상태 업데이트
                Message message = messageRepository.findByMessageId(messageId);
                if (message != null) {
                    message.setDetectionStatus("FAILED");
                    message.setMetadata("Failed after " + retryCount + " retries: " + error.getMessage());
                    messageRepository.save(message);
                }
            }
            
            // 메시지 ACK (재시도 하지 않음)
            redisTemplate.opsForStream().acknowledge(CONSUMER_GROUP, record);
            
        } catch (Exception e) {
            log.error("Failed to handle failed message", e);
        }
    }
    
    /**
     * 워커 통계 로깅
     */
    private void logWorkerStats() {
        log.info("Gemini Worker Stats - Processed: {}, Failed: {}, Cache Hits: {}", 
                processedMessages.get(), failedMessages.get(), cacheHits.get());
    }
    
    /**
     * 워커 상태 정보 반환
     */
    public WorkerStatus getWorkerStatus() {
        return new WorkerStatus(
                running.get(),
                workerThreads,
                processedMessages.get(),
                failedMessages.get(),
                cacheHits.get()
        );
    }
    
    /**
     * 워커 상태 정보 클래스
     */
    public static class WorkerStatus {
        private final boolean running;
        private final int threadCount;
        private final long processedMessages;
        private final long failedMessages;
        private final long cacheHits;
        
        public WorkerStatus(boolean running, int threadCount, long processedMessages, 
                           long failedMessages, long cacheHits) {
            this.running = running;
            this.threadCount = threadCount;
            this.processedMessages = processedMessages;
            this.failedMessages = failedMessages;
            this.cacheHits = cacheHits;
        }
        
        public boolean isRunning() { return running; }
        public int getThreadCount() { return threadCount; }
        public long getProcessedMessages() { return processedMessages; }
        public long getFailedMessages() { return failedMessages; }
        public long getCacheHits() { return cacheHits; }
        
        @Override
        public String toString() {
            return String.format("WorkerStatus{running=%s, threads=%d, processed=%d, failed=%d, cacheHits=%d}", 
                               running, threadCount, processedMessages, failedMessages, cacheHits);
        }
    }
}