package com.sauron.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauron.common.queue.MessageQueueService;
import com.sauron.listener.dto.MessageRequest;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-004 메시지 API 성능 테스트
 * 처리 시간, 동시성, 부하 테스트
 */
@SpringBootTest
@AutoConfigureTestMvc
@ActiveProfiles("test")
@Transactional
class MessageApiPerformanceTest {
    
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
        when(messageQueueService.enqueueForAnalysis(any(MessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(true));
    }
    
    @Test
    @WithMockUser
    void testSingleMessagePerformance_Under1Second() throws Exception {
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
        
        // Then
        assertTrue(processingTime < 1000, 
                  "Single message processing should be under 1 second, but was: " + processingTime + "ms");
        
        System.out.println("Single message processing time: " + processingTime + "ms");
    }
    
    @Test
    @WithMockUser
    void testConcurrentRequests_10Threads() throws Exception {
        // Given
        int threadCount = 10;
        int requestsPerThread = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);
        
        // When
        long testStartTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        long requestStart = System.currentTimeMillis();
                        
                        MessageRequest request = createValidMessageRequest();
                        request.setMessageId("thread-" + threadId + "-msg-" + j);
                        
                        try {
                            mockMvc.perform(post(API_BASE_URL)
                                    .with(jwt())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                                    .andExpect(status().isCreated());
                            
                            long requestEnd = System.currentTimeMillis();
                            totalTime.addAndGet(requestEnd - requestStart);
                            successCount.incrementAndGet();
                            
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            System.err.println("Request failed: " + e.getMessage());
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Then
        latch.await();
        executor.shutdown();
        
        long testEndTime = System.currentTimeMillis();
        long totalTestTime = testEndTime - testStartTime;
        
        int totalRequests = threadCount * requestsPerThread;
        double averageTime = totalTime.get() / (double) successCount.get();
        double requestsPerSecond = (successCount.get() * 1000.0) / totalTestTime;
        
        // 성능 검증
        assertTrue(successCount.get() > 0, "At least some requests should succeed");
        assertTrue(averageTime < 2000, "Average processing time should be under 2 seconds");
        assertTrue(requestsPerSecond > 10, "Should handle at least 10 requests per second");
        
        // 결과 출력
        System.out.println("=== Concurrent Request Performance Test Results ===");
        System.out.println("Total requests: " + totalRequests);
        System.out.println("Successful requests: " + successCount.get());
        System.out.println("Failed requests: " + errorCount.get());
        System.out.println("Success rate: " + (successCount.get() * 100.0 / totalRequests) + "%");
        System.out.println("Average response time: " + averageTime + "ms");
        System.out.println("Requests per second: " + requestsPerSecond);
        System.out.println("Total test time: " + totalTestTime + "ms");
        
        // 성공률 검증
        double successRate = successCount.get() * 100.0 / totalRequests;
        assertTrue(successRate >= 90, "Success rate should be at least 90%, but was: " + successRate + "%");
    }
    
    @Test
    @WithMockUser
    void testBurstLoad_100Requests() throws Exception {
        // Given
        int burstSize = 100;
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        // When
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < burstSize; i++) {
            final int requestId = i;
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    MessageRequest request = createValidMessageRequest();
                    request.setMessageId("burst-msg-" + requestId);
                    
                    mockMvc.perform(post(API_BASE_URL)
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                            .andExpect(status().isCreated());
                    
                    return true;
                } catch (Exception e) {
                    System.err.println("Request " + requestId + " failed: " + e.getMessage());
                    return false;
                }
            });
            futures.add(future);
        }
        
        // Then
        List<Boolean> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        long successCount = results.stream().mapToLong(success -> success ? 1 : 0).sum();
        double successRate = (successCount * 100.0) / burstSize;
        double requestsPerSecond = (successCount * 1000.0) / totalTime;
        
        // 결과 출력
        System.out.println("=== Burst Load Test Results ===");
        System.out.println("Total requests: " + burstSize);
        System.out.println("Successful requests: " + successCount);
        System.out.println("Success rate: " + successRate + "%");
        System.out.println("Requests per second: " + requestsPerSecond);
        System.out.println("Total time: " + totalTime + "ms");
        
        // 성능 검증
        assertTrue(successRate >= 80, "Success rate should be at least 80% for burst load");
        assertTrue(requestsPerSecond >= 50, "Should handle at least 50 requests per second in burst");
    }
    
    @Test
    @WithMockUser
    void testLargeMessagePerformance() throws Exception {
        // Given - 큰 메시지 (1500자)
        MessageRequest request = createValidMessageRequest();
        String largeContent = "가".repeat(1500); // 한글 1500자
        request.setContent(largeContent);
        
        // When
        long startTime = System.currentTimeMillis();
        
        mockMvc.perform(post(API_BASE_URL)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        
        long endTime = System.currentTimeMillis();
        long processingTime = endTime - startTime;
        
        // Then
        assertTrue(processingTime < 2000, 
                  "Large message processing should be under 2 seconds, but was: " + processingTime + "ms");
        
        System.out.println("Large message processing time: " + processingTime + "ms");
        System.out.println("Message size: " + largeContent.length() + " characters");
    }
    
    @Test
    @WithMockUser
    void testMemoryUsage_MultipleRequests() throws Exception {
        // Given
        Runtime runtime = Runtime.getRuntime();
        int requestCount = 50;
        
        // 초기 메모리 사용량 측정
        System.gc(); // 가비지 컬렉션 강제 실행
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // When
        for (int i = 0; i < requestCount; i++) {
            MessageRequest request = createValidMessageRequest();
            request.setMessageId("memory-test-" + i);
            
            mockMvc.perform(post(API_BASE_URL)
                    .with(jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
        
        // Then
        System.gc(); // 가비지 컬렉션 강제 실행
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        System.out.println("=== Memory Usage Test Results ===");
        System.out.println("Initial memory: " + (initialMemory / 1024 / 1024) + " MB");
        System.out.println("Final memory: " + (finalMemory / 1024 / 1024) + " MB");
        System.out.println("Memory increase: " + (memoryIncrease / 1024 / 1024) + " MB");
        System.out.println("Memory per request: " + (memoryIncrease / requestCount / 1024) + " KB");
        
        // 메모리 증가량이 합리적인 범위인지 검증 (요청당 1MB 미만)
        long memoryPerRequest = memoryIncrease / requestCount;
        assertTrue(memoryPerRequest < 1024 * 1024, 
                  "Memory usage per request should be under 1MB, but was: " + (memoryPerRequest / 1024) + "KB");
    }
    
    @Test
    @WithMockUser
    void testResponseTimeConsistency() throws Exception {
        // Given
        int requestCount = 20;
        List<Long> responseTimes = new ArrayList<>();
        
        // When
        for (int i = 0; i < requestCount; i++) {
            MessageRequest request = createValidMessageRequest();
            request.setMessageId("consistency-test-" + i);
            
            long startTime = System.currentTimeMillis();
            
            mockMvc.perform(post(API_BASE_URL)
                    .with(jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
            
            long endTime = System.currentTimeMillis();
            responseTimes.add(endTime - startTime);
        }
        
        // Then
        double average = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long min = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
        long max = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        
        // 표준편차 계산
        double variance = responseTimes.stream()
                .mapToDouble(time -> Math.pow(time - average, 2))
                .average().orElse(0);
        double standardDeviation = Math.sqrt(variance);
        
        System.out.println("=== Response Time Consistency Results ===");
        System.out.println("Average response time: " + average + "ms");
        System.out.println("Min response time: " + min + "ms");
        System.out.println("Max response time: " + max + "ms");
        System.out.println("Standard deviation: " + standardDeviation + "ms");
        
        // 일관성 검증 (표준편차가 평균의 50% 미만이어야 함)
        assertTrue(standardDeviation < average * 0.5, 
                  "Response time should be consistent (std dev < 50% of average)");
        
        // 최대 응답시간이 평균의 3배를 넘지 않아야 함
        assertTrue(max < average * 3, 
                  "Max response time should not exceed 3x average");
    }
    
    /**
     * 유효한 메시지 요청 생성 헬퍼
     */
    private MessageRequest createValidMessageRequest() {
        MessageRequest request = new MessageRequest();
        request.setMessageId(UUID.randomUUID().toString());
        request.setDeviceId("perf-test-device");
        request.setChatRoomTitle("성능테스트방");
        request.setContent("성능 테스트용 메시지입니다.");
        request.setPriority("normal");
        return request;
    }
}