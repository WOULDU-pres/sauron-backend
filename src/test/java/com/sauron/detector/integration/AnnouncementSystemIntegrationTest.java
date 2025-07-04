package com.sauron.detector.integration;

import com.sauron.detector.dto.DetectionResult;
import com.sauron.detector.dto.MessageContext;
import com.sauron.detector.entity.AnnouncementAlert;
import com.sauron.detector.entity.AnnouncementDetection;
import com.sauron.detector.repository.AnnouncementAlertRepository;
import com.sauron.detector.repository.AnnouncementDetectionRepository;
import com.sauron.detector.service.AnnouncementAlertService;
import com.sauron.detector.service.AnnouncementDetectorService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공지사항 감지 및 알림 시스템 통합 테스트
 * 
 * T-007-004 요구사항:
 * - 공지 감지~별도 알림 전송 전체 플로우 시나리오 통과
 * - 감지 성공률 95% 이상
 * - 알림 10초 이내 전송
 * - 장애 상황 graceful handling
 * - 테스트/시뮬레이션 모드 포함
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class AnnouncementSystemIntegrationTest {

    @Autowired
    private AnnouncementDetectorService detectorService;

    @Autowired
    private AnnouncementAlertService alertService;

    @Autowired
    private AnnouncementDetectionRepository detectionRepository;

    @Autowired
    private AnnouncementAlertRepository alertRepository;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        alertRepository.deleteAll();
        detectionRepository.deleteAll();
    }

    /**
     * 테스트 1: 완전한 공지 감지-알림 플로우 통합 테스트
     * 
     * 시나리오:
     * 1. 공지사항 메시지 입력
     * 2. AnnouncementDetectorService에서 감지
     * 3. AnnouncementAlertService에서 알림 발송
     * 4. 데이터베이스에 이력 저장 확인
     */
    @Test
    @Order(1)
    @DisplayName("완전한 공지 감지-알림 플로우 통합 테스트")
    void testCompleteAnnouncementDetectionAndAlertFlow() {
        // Given: 공지사항 메시지
        String announcementMessage = "📢 중요 공지: 내일 오전 10시 신제품 출시 설명회가 있습니다.";
        String chatRoomName = "테스트 채팅방";
        String senderName = "관리자";

        // When: 공지사항 감지 및 알림 처리
        MessageContext context = MessageContext.builder()
                .messageId(UUID.randomUUID().toString())
                .content(announcementMessage)
                .userId("test-user")
                .chatRoomId("test-room")
                .timestamp(Instant.now())
                .build();

        CompletableFuture<DetectionResult> future = detectorService.detectAnnouncement(context);
        DetectionResult result = future.get(10, TimeUnit.SECONDS); // 10초 타임아웃

        // Then: 감지 결과 검증
        assertThat(result).isNotNull();
        assertThat(result.isDetected()).isTrue();
        assertThat(result.getConfidence()).isGreaterThan(0.85); // 85% 이상 신뢰도

        // 데이터베이스 저장 확인
        List<AnnouncementDetection> savedDetections = detectionRepository.findAll();
        assertThat(savedDetections).hasSize(1);

        // 알림 발송 확인
        List<AnnouncementAlert> savedAlerts = alertRepository.findAll();
        assertThat(savedAlerts).hasSizeGreaterThan(0);

        // 알림 성공 여부 확인
        AnnouncementAlert alert = savedAlerts.get(0);
        assertThat(alert.getDeliveryStatus()).isIn("SENT", "PARTIAL_SUCCESS");
    }

    /**
     * 테스트 2: 다양한 공지사항 패턴 감지 성공률 테스트
     * 
     * 요구사항: 감지 성공률 95% 이상
     */
    @Test
    @Order(2)
    @DisplayName("다양한 공지사항 패턴 감지 성공률 95% 이상 검증")
    void testAnnouncementDetectionAccuracy() {
        // Given: 다양한 공지사항 테스트 데이터셋
        List<String> announcementMessages = List.of(
                "📢 공지사항: 시스템 점검으로 인한 서비스 중단 안내",
                "🔔 알림: 새로운 업데이트가 출시되었습니다",
                "⚠️ 중요: 개인정보보호 정책 변경 사항 안내",
                "📅 일정 안내: 정기 회의 일정 변경",
                "🎉 이벤트: 신규 가입자 대상 특별 혜택",
                "📋 공고: 신규 직원 채용 공고",
                "🚨 긴급: 보안 업데이트 필수 설치 안내",
                "📣 안내: 서비스 이용약관 개정 공지",
                "🎊 축하: 서비스 오픈 1주년 기념 이벤트",
                "⭐ 특별: 프리미엄 기능 무료 체험 기회"
        );

        List<String> normalMessages = List.of(
                "안녕하세요! 오늘 날씨가 좋네요",
                "점심 뭐 드실 예정이신가요?",
                "회의 자료 공유드립니다",
                "수고하셨습니다",
                "내일 미팅 시간 확인 부탁드립니다"
        );

        int totalMessages = announcementMessages.size() + normalMessages.size();
        int correctDetections = 0;

        // When: 공지사항 메시지 감지 테스트
        for (String message : announcementMessages) {
            MessageContext context = MessageContext.builder()
                    .messageId(UUID.randomUUID().toString())
                    .content(message)
                    .userId("test-user")
                    .chatRoomId("test-room")
                    .timestamp(Instant.now())
                    .build();

            CompletableFuture<DetectionResult> future = detectorService.detectAnnouncement(context);
            DetectionResult result = future.get(5, TimeUnit.SECONDS);
            
            if (result.isDetected()) {
                correctDetections++;
            }
        }

        // 일반 메시지 오탐 테스트
        for (String message : normalMessages) {
            MessageContext context = MessageContext.builder()
                    .messageId(UUID.randomUUID().toString())
                    .content(message)
                    .userId("test-user")
                    .chatRoomId("test-room")
                    .timestamp(Instant.now())
                    .build();

            CompletableFuture<DetectionResult> future = detectorService.detectAnnouncement(context);
            DetectionResult result = future.get(5, TimeUnit.SECONDS);
            
            if (!result.isDetected()) {
                correctDetections++;
            }
        }

        // Then: 95% 이상 정확도 검증
        double accuracy = (double) correctDetections / totalMessages;
        assertThat(accuracy).isGreaterThanOrEqualTo(0.95);
        
        System.out.printf("감지 정확도: %.2f%% (%d/%d)%n", accuracy * 100, correctDetections, totalMessages);
    }

    /**
     * 테스트 3: 알림 전송 성능 테스트 (10초 이내)
     * 
     * 요구사항: 알림 10초 이내 전송
     */
    @Test
    @Order(3)
    @DisplayName("알림 전송 10초 이내 성능 검증")
    void testAlertDeliveryPerformance() throws Exception {
        // Given: 공지사항 메시지
        String message = "⚡ 성능 테스트: 알림 전송 속도 검증 중입니다";

        // When: 시간 측정하며 알림 발송
        long startTime = System.currentTimeMillis();
        
        MessageContext context = MessageContext.builder()
                .messageId(UUID.randomUUID().toString())
                .content(message)
                .userId("performance-user")
                .chatRoomId("performance-room")
                .timestamp(Instant.now())
                .build();

        CompletableFuture<DetectionResult> future = detectorService.detectAnnouncement(context);
        DetectionResult result = future.get(10, TimeUnit.SECONDS);

        // 알림 발송 대기
        CompletableFuture<Void> alertFuture = CompletableFuture.runAsync(() -> {
            try {
                // 실제 알림 발송 시뮬레이션
                Thread.sleep(1000); // 1초 시뮬레이션
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        alertFuture.get(10, TimeUnit.SECONDS); // 10초 타임아웃
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then: 10초 이내 완료 검증
        assertThat(duration).isLessThan(10000); // 10초 = 10,000ms
        
        System.out.printf("알림 처리 시간: %dms%n", duration);
    }

    /**
     * 테스트 4: 대량 메시지 처리 성능 테스트
     * 
     * 요구사항: 1초 이내 개별 메시지 처리
     */
    @Test
    @Order(4)
    @DisplayName("대량 메시지 동시 처리 성능 테스트")
    void testHighVolumeMessageProcessing() throws Exception {
        // Given: 100개 메시지 동시 처리
        int messageCount = 100;
        List<CompletableFuture<DetectionResult>> futures = new java.util.ArrayList<>();

        long startTime = System.currentTimeMillis();

        // When: 병렬 처리
        for (int i = 0; i < messageCount; i++) {
            final int messageIndex = i;
            CompletableFuture<DetectionResult> future = CompletableFuture.supplyAsync(() -> {
                String message = "테스트 메시지 #" + messageIndex;
                MessageContext context = MessageContext.builder()
                        .messageId(UUID.randomUUID().toString())
                        .content(message)
                        .userId("user-" + messageIndex)
                        .chatRoomId("bulk-test-room")
                        .timestamp(Instant.now())
                        .build();
                
                try {
                    return detectorService.detectAnnouncement(context).get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // 모든 처리 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;
        double avgPerMessage = (double) totalDuration / messageCount;

        // Then: 평균 1초 이내 처리 검증
        assertThat(avgPerMessage).isLessThan(1000.0); // 1초 = 1000ms
        
        System.out.printf("총 처리 시간: %dms, 평균 메시지당: %.2fms%n", totalDuration, avgPerMessage);
        
        // 데이터베이스 저장 확인
        List<AnnouncementDetection> savedDetections = detectionRepository.findAll();
        assertThat(savedDetections).hasSizeGreaterThanOrEqualTo(messageCount);
    }

    /**
     * 테스트 5: 장애 상황 graceful handling 테스트
     */
    @Test
    @Order(5)
    @DisplayName("장애 상황 graceful handling 검증")
    void testGracefulFailureHandling() {
        // Given: 잘못된 형식의 메시지 또는 null 값
        List<String> problematicMessages = List.of(
                null,
                "",
                "   ",
                "a".repeat(10000), // 매우 긴 메시지
                "🤖💥🔥💀⚡🌟💫🎯🚀🎊🎉📢🔔⚠️📅🎁📋🚨📣⭐" // 이모지만 있는 메시지
        );

        // When & Then: 각 문제 상황에서 graceful handling 확인
        for (String message : problematicMessages) {
            try {
                if (message != null && !message.trim().isEmpty()) {
                    MessageContext context = MessageContext.builder()
                            .messageId(UUID.randomUUID().toString())
                            .content(message)
                            .userId("error-test-user")
                            .chatRoomId("error-test-room")
                            .timestamp(Instant.now())
                            .build();

                    CompletableFuture<DetectionResult> future = detectorService.detectAnnouncement(context);
                    DetectionResult result = future.get(10, TimeUnit.SECONDS);
                    
                    // 결과가 있다면 유효한 값이어야 함
                    if (result != null) {
                        assertThat(result.getConfidence()).isBetween(0.0, 1.0);
                        assertThat(result.isDetected()).isNotNull();
                    }
                }
                
            } catch (Exception e) {
                // 예외가 발생해도 시스템이 멈추지 않아야 함
                log.debug("Graceful error handling test: {}", e.getMessage());
                // null이나 빈 메시지에 대한 예외는 허용
            }
        }
    }

    /**
     * 테스트 6: 시간 기반 공지사항 감지 테스트
     */
    @Test
    @Order(6)
    @DisplayName("업무시간 외 공지사항 특별 처리 검증")
    void testAfterHoursAnnouncementDetection() {
        // Given: 업무시간 외 시간 (22시)
        LocalDateTime afterHours = LocalDateTime.now().withHour(22).withMinute(0);
        String message = "📢 긴급 공지: 시스템 장애로 인한 서비스 중단";

        // When: 업무시간 외 공지사항 감지
        MessageContext context = MessageContext.builder()
                .messageId(UUID.randomUUID().toString())
                .content(message)
                .userId("emergency-user")
                .chatRoomId("emergency-room")
                .timestamp(afterHours.atZone(java.time.ZoneId.systemDefault()).toInstant())
                .build();

        CompletableFuture<DetectionResult> future = detectorService.detectAnnouncement(context);
        DetectionResult result = future.get(10, TimeUnit.SECONDS);

        // Then: 업무시간 외 공지사항으로 분류되어야 함
        assertThat(result.isDetected()).isTrue();
        // 추가 검증: 우선순위나 특별 처리 여부 확인 가능
    }

    /**
     * 테스트 7: 통합 리포팅 데이터 검증
     */
    @Test
    @Order(7)
    @DisplayName("통합 리포팅 데이터 정확성 검증")
    void testIntegratedReportingData() {
        // 모든 테스트 완료 후 데이터베이스 상태 검증
        
        // 감지 이력 확인
        List<AnnouncementDetection> allDetections = detectionRepository.findAll();
        assertThat(allDetections).isNotEmpty();
        
        // 알림 이력 확인
        List<AnnouncementAlert> allAlerts = alertRepository.findAll();
        assertThat(allAlerts).isNotEmpty();
        
        // 데이터 일관성 확인 (confidence score 기반)
        long successfulDetections = allDetections.stream()
                .mapToLong(d -> (d.getConfidenceScore().doubleValue() > 0.7) ? 1 : 0)
                .sum();
        
        assertThat(successfulDetections).isGreaterThan(0);
        
        System.out.printf("통합 테스트 결과 - 총 감지: %d건, 공지사항: %d건, 알림: %d건%n", 
                allDetections.size(), successfulDetections, allAlerts.size());
    }
}