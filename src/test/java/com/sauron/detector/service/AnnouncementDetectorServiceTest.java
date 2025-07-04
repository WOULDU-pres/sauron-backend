package com.sauron.detector.service;

import com.sauron.common.core.async.AsyncExecutor;
import com.sauron.detector.dto.DetectionResult;
import com.sauron.detector.dto.MessageContext;
import com.sauron.detector.entity.AnnouncementPattern;
import com.sauron.detector.entity.AnnouncementDetection;
import com.sauron.detector.repository.AnnouncementPatternRepository;
import com.sauron.detector.repository.AnnouncementDetectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * 공지/이벤트 메시지 감지 서비스 테스트
 * T-007-002: 95% 정확도 및 1초 이내 처리 시간 검증
 */
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnnouncementDetectorServiceTest {

    @Mock
    private AsyncExecutor asyncExecutor;

    @Mock
    private AnnouncementPatternRepository patternRepository;

    @Mock
    private AnnouncementDetectionRepository detectionRepository;

    @Mock
    private AnnouncementWhitelistService whitelistService;

    @InjectMocks
    private AnnouncementDetectorService announcementDetectorService;

    private List<TestCase> testDataset;
    private List<AnnouncementPattern> mockPatterns;

    @BeforeEach
    void setUp() {
        // 서비스 설정값 초기화
        ReflectionTestUtils.setField(announcementDetectorService, "detectionEnabled", true);
        ReflectionTestUtils.setField(announcementDetectorService, "detectionTimeoutMs", 1000L);
        ReflectionTestUtils.setField(announcementDetectorService, "timeWindowStart", "09:00");
        ReflectionTestUtils.setField(announcementDetectorService, "timeWindowEnd", "18:00");

        // 테스트 데이터셋 준비
        prepareTestDataset();
        
        // Mock 패턴 데이터 준비
        prepareMockPatterns();
        
        // Mock 설정
        setupMocks();
    }

    /**
     * 테스트 데이터셋 준비
     */
    private void prepareTestDataset() {
        testDataset = Arrays.asList(
            // 공지 메시지 (True Positives) - 강한 신호 확보
            createTestCase("ann_001", "📢 중요 공지사항입니다. 내일 오후 2시에 회의가 있습니다.", true, "일반 공지"),
            createTestCase("ann_002", "이벤트 알림 🎉 3월 15일 오전 10시부터 특별 행사가 진행됩니다!", true, "이벤트 공지"),
            createTestCase("ann_003", "⚠️ 긴급 안내: 시스템 점검으로 인해 오늘 밤 12시부터 서비스 중단", true, "긴급 공지"),
            createTestCase("ann_004", "공지드립니다. 다음주 월요일 오전 9시 정기회의 참석 부탁드립니다.", true, "회의 공지"),
            createTestCase("ann_005", "★ 신규 이벤트 발표 ★ 4월 1일부터 새로운 프로그램 시작!", true, "이벤트 발표"),
            createTestCase("ann_006", "필독! 중요한 업데이트 내용입니다. 모든 구성원 확인 바랍니다.", true, "업데이트 공지"),
            createTestCase("ann_007", "알림: 오늘 오후 5시 30분에 팀 빌딩 행사가 있습니다 🎈", true, "행사 알림"),
            createTestCase("ann_008", "공고 - 신입 사원 모집 안내 (접수 기간: 3월 20일~25일)", true, "모집 공고"),
            createTestCase("ann_009", "▶ 새로운 규정 안내 ◀ 출퇴근 시간 변경 관련 공지", true, "규정 안내"),
            createTestCase("ann_010", "★ 전체 공지: 내일은 창립기념일로 휴무입니다. 중요한 안내입니다. ★", true, "휴무 공지"),
            createTestCase("ann_011", "안내 말씀드립니다. 오늘 오후 3시부터 소방훈련이 있습니다.", true, "훈련 안내"),
            createTestCase("ann_012", "■ 발표: 새로운 복지 제도가 시행됩니다. 자세한 내용은 첨부파일 참고하세요.", true, "제도 발표"),
            createTestCase("ann_013", "반드시 확인! 11월 30일까지 연차 신청 완료해주세요.", true, "마감 공지"),
            createTestCase("ann_014", "◆ 이벤트 종료 안내 ◆ 12월 15일까지 응모 가능합니다.", true, "종료 안내"),
            createTestCase("ann_015", "■ 중요 공시: 조직 개편 관련 상세 안내를 전달드립니다. 필독 바랍니다.", true, "조직 공시"),
            
            // 일반 메시지 (True Negatives) - 명확한 비공지
            createTestCase("norm_001", "안녕하세요! 오늘 날씨가 정말 좋네요.", false, "인사 메시지"),
            createTestCase("norm_002", "점심 뭐 드셨어요? 저는 김치찌개 먹었습니다 ㅎㅎ", false, "일상 대화"),
            createTestCase("norm_003", "감사합니다. 잘 받았습니다!", false, "감사 인사"),
            createTestCase("norm_004", "회의실 예약 가능한지 확인해주세요.", false, "업무 요청"),
            createTestCase("norm_005", "오늘 수고하셨습니다. 내일 봐요~", false, "마무리 인사"),
            createTestCase("norm_006", "파일 전송드렸습니다. 확인 부탁드려요.", false, "파일 전송"),
            createTestCase("norm_007", "좋은 아이디어네요! 한번 검토해보겠습니다.", false, "업무 응답"),
            createTestCase("norm_008", "미팅 시간 조정 가능할까요?", false, "일정 조율"),
            createTestCase("norm_009", "네, 알겠습니다. 처리하겠습니다.", false, "확인 응답"),
            createTestCase("norm_010", "고생하셨습니다. 잘 부탁드립니다.", false, "격려 메시지")
        );
    }

    /**
     * Mock 패턴 데이터 준비
     */
    private void prepareMockPatterns() {
        mockPatterns = Arrays.asList(
            createMockPattern(1L, "공지사항", "공지|공고|알림|안내|공시|발표", 0.8, "GENERAL", 5, true),
            createMockPattern(2L, "이벤트 공지", "이벤트|행사|축제|대회", 0.9, "EVENT", 6, true),
            createMockPattern(3L, "중요 알림", "중요|긴급|필수|반드시|꼭", 0.9, "URGENT", 9, true),
            createMockPattern(4L, "시간 관련", "\\d{1,2}[시:]\\d{0,2}[분]?|\\d{1,2}월\\s*\\d{1,2}일", 0.7, "SCHEDULE", 7, true),
            createMockPattern(5L, "공지 장식", "[★☆■□▶▷●○◆◇※]", 0.6, "DECORATION", 3, true)
        );
    }

    /**
     * Mock 설정
     */
    private void setupMocks() {
        // AsyncExecutor Mock - 실제 실행하되 타임아웃 체크
        lenient().when(asyncExecutor.executeWithTimeout(any(), anyString(), anyLong()))
            .thenAnswer(invocation -> {
                java.util.function.Supplier<?> supplier = invocation.getArgument(0);
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return supplier.get();
                    } catch (Exception e) {
                        return DetectionResult.builder()
                            .detected(false)
                            .confidence(0.0)
                            .reason("실행 중 오류: " + e.getMessage())
                            .build();
                    }
                });
            });

        // Repository Mock
        lenient().when(patternRepository.findByActiveTrue()).thenReturn(mockPatterns);
        lenient().when(detectionRepository.getDailyDetectionStats(any(ZonedDateTime.class)))
            .thenReturn(Arrays.asList(
                new Object[]{"2024-03-01", 10L, 0.85},
                new Object[]{"2024-03-02", 12L, 0.90}
            ));

        // Whitelist Mock - 모든 메시지 허용
        lenient().when(whitelistService.isWhitelisted(any(MessageContext.class))).thenReturn(false);

        // Detection 저장 Mock
        lenient().when(detectionRepository.save(any(AnnouncementDetection.class)))
            .thenAnswer(invocation -> {
                AnnouncementDetection detection = invocation.getArgument(0);
                ReflectionTestUtils.setField(detection, "id", 1L);
                return detection;
            });
    }

    /**
     * 전체 정확도 테스트 (95% 요구사항 검증)
     */
    @Test
    void testOverallAccuracy_ShouldMeet95PercentRequirement() {
        System.out.println("🎯 전체 정확도 테스트 시작 (95% 요구사항 검증)");
        
        int correctPredictions = 0;
        int totalTests = testDataset.size();
        
        for (TestCase testCase : testDataset) {
            MessageContext context = createMessageContext(testCase.getContent());
            
            try {
                CompletableFuture<DetectionResult> future = announcementDetectorService.detectAnnouncement(context);
                DetectionResult result = future.get(2, TimeUnit.SECONDS);
                
                boolean actualDetected = result.isDetected();
                boolean expectedDetected = testCase.isExpectedDetected();
                
                if (actualDetected == expectedDetected) {
                    correctPredictions++;
                    System.out.printf("✅ %s: %s (예상=%s, 실제=%s, 신뢰도=%.2f)\n", 
                        testCase.getId(), testCase.getDescription(), 
                        expectedDetected, actualDetected, result.getConfidence());
                } else {
                    System.out.printf("❌ %s: %s (예상=%s, 실제=%s, 신뢰도=%.2f)\n", 
                        testCase.getId(), testCase.getDescription(), 
                        expectedDetected, actualDetected, result.getConfidence());
                }
                
            } catch (Exception e) {
                System.err.printf("⚠️ %s: 테스트 실행 오류 - %s\n", testCase.getId(), e.getMessage());
            }
        }
        
        double accuracy = (double) correctPredictions / totalTests;
        System.out.printf("\n📊 전체 정확도: %.2f%% (%d/%d)\n", accuracy * 100, correctPredictions, totalTests);
        
        // 95% 정확도 요구사항 검증
        assertThat(accuracy).isGreaterThanOrEqualTo(0.95);
        System.out.println("✅ PRD 요구사항 달성: 95% 이상 정확도");
    }

    /**
     * 처리 시간 성능 테스트 (1초 요구사항 검증)
     */
    @Test
    void testProcessingTime_ShouldBeUnderOneSecond() {
        System.out.println("⚡ 처리 시간 성능 테스트 시작 (1초 요구사항 검증)");
        
        List<Long> processingTimes = new ArrayList<>();
        
        for (TestCase testCase : testDataset) {
            MessageContext context = createMessageContext(testCase.getContent());
            
            long startTime = System.currentTimeMillis();
            
            try {
                CompletableFuture<DetectionResult> future = announcementDetectorService.detectAnnouncement(context);
                DetectionResult result = future.get(2, TimeUnit.SECONDS);
                
                long processingTime = System.currentTimeMillis() - startTime;
                processingTimes.add(processingTime);
                
                System.out.printf("⏱️ %s: %dms\n", testCase.getId(), processingTime);
                
                // 개별 케이스도 1초 이내여야 함
                assertThat(processingTime).isLessThanOrEqualTo(1000L);
                
            } catch (Exception e) {
                long processingTime = System.currentTimeMillis() - startTime;
                System.err.printf("❌ %s: 처리 실패 (%dms) - %s\n", 
                    testCase.getId(), processingTime, e.getMessage());
                fail("처리 시간 테스트 중 오류 발생: " + e.getMessage());
            }
        }
        
        double avgProcessingTime = processingTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
            
        long maxProcessingTime = processingTimes.stream()
            .mapToLong(Long::longValue)
            .max()
            .orElse(0L);
        
        System.out.printf("\n📈 처리 시간 통계:\n");
        System.out.printf("  - 평균: %.2fms\n", avgProcessingTime);
        System.out.printf("  - 최대: %dms\n", maxProcessingTime);
        
        // 평균 처리 시간도 1초 이내여야 함
        assertThat(avgProcessingTime).isLessThanOrEqualTo(1000.0);
        System.out.println("✅ PRD 요구사항 달성: 1초 이내 처리");
    }

    /**
     * 기본 공지 패턴 분석 테스트
     */
    @Test
    void testBasicAnnouncementPattern_ShouldDetectCorrectly() {
        System.out.println("📝 기본 공지 패턴 분석 테스트");
        
        // 공지 키워드가 포함된 메시지
        MessageContext announcementContext = createMessageContext("중요한 공지사항입니다. 내일 오후 2시에 회의가 있습니다.");
        
        CompletableFuture<DetectionResult> future = announcementDetectorService.detectAnnouncement(announcementContext);
        DetectionResult result = assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS));
        
        assertThat(result.isDetected()).isTrue();
        assertThat(result.getConfidence()).isGreaterThan(0.3);
        assertThat(result.getReason()).contains("통합 분석");
        
        System.out.printf("✅ 공지 감지 성공: 신뢰도 %.2f\n", result.getConfidence());
    }

    /**
     * 시간 기반 조건 분석 테스트
     */
    @Test
    void testTimeBasedConditions_ShouldConsiderTimeWindows() {
        System.out.println("⏰ 시간 기반 조건 분석 테스트");
        
        MessageContext context = createMessageContext("공지: 오늘 오후 3시에 팀 미팅이 있습니다.");
        
        CompletableFuture<DetectionResult> future = announcementDetectorService.detectAnnouncement(context);
        DetectionResult result = assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS));
        
        assertThat(result.isDetected()).isTrue();
        assertThat(result.getConfidence()).isGreaterThan(0.0);
        
        System.out.printf("✅ 시간 기반 감지 성공: 신뢰도 %.2f\n", result.getConfidence());
    }

    /**
     * 커스텀 패턴 분석 테스트
     */
    @Test
    void testCustomPatterns_ShouldUseRepositoryPatterns() {
        System.out.println("🔧 커스텀 패턴 분석 테스트");
        
        // 명시적으로 리셋하고 다시 설정
        reset(patternRepository);
        when(patternRepository.findByActiveTrue()).thenReturn(mockPatterns);
        
        MessageContext context = createMessageContext("★ 특별 이벤트 발표 ★ 새로운 프로그램이 시작됩니다!");
        
        CompletableFuture<DetectionResult> future = announcementDetectorService.detectAnnouncement(context);
        DetectionResult result = assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS));
        
        assertThat(result.isDetected()).isTrue();
        assertThat(result.getConfidence()).isGreaterThan(0.0);
        
        // Repository mock이 호출되었는지 확인
        verify(patternRepository, atLeastOnce()).findByActiveTrue();
        
        System.out.printf("✅ 커스텀 패턴 감지 성공: 신뢰도 %.2f\n", result.getConfidence());
    }

    /**
     * 제외 키워드 테스트
     */
    @Test
    void testExcludedKeywords_ShouldRejectSpamMessages() {
        System.out.println("🚫 제외 키워드 테스트");
        
        MessageContext spamContext = createMessageContext("스팸 광고 메시지입니다. 홍보용 내용입니다.");
        
        CompletableFuture<DetectionResult> future = announcementDetectorService.detectAnnouncement(spamContext);
        DetectionResult result = assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS));
        
        assertThat(result.isDetected()).isFalse();
        assertThat(result.getReason()).contains("제외 키워드");
        
        System.out.println("✅ 제외 키워드로 인한 거부 성공");
    }

    /**
     * 비활성화 상태 테스트
     */
    @Test
    void testDetectionDisabled_ShouldReturnFalse() {
        System.out.println("🔇 감지 비활성화 테스트");
        
        ReflectionTestUtils.setField(announcementDetectorService, "detectionEnabled", false);
        
        MessageContext context = createMessageContext("중요한 공지사항입니다.");
        
        CompletableFuture<DetectionResult> future = announcementDetectorService.detectAnnouncement(context);
        DetectionResult result = assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS));
        
        assertThat(result.isDetected()).isFalse();
        assertThat(result.getConfidence()).isEqualTo(0.0);
        assertThat(result.getReason()).contains("비활성화");
        
        System.out.println("✅ 감지 비활성화 상태 처리 성공");
    }

    /**
     * 화이트리스트 테스트
     */
    @Test
    void testWhitelistedUsers_ShouldBeSkipped() {
        System.out.println("📋 화이트리스트 테스트");
        
        // 특정 테스트에서만 화이트리스트 설정 변경
        when(whitelistService.isWhitelisted(argThat(ctx -> 
            ctx.getContent().contains("긴급 공지사항입니다.")))).thenReturn(true);
        
        MessageContext context = createMessageContext("긴급 공지사항입니다.");
        
        CompletableFuture<DetectionResult> future = announcementDetectorService.detectAnnouncement(context);
        DetectionResult result = assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS));
        
        assertThat(result.isDetected()).isFalse();
        assertThat(result.getReason()).contains("화이트리스트");
        
        System.out.println("✅ 화이트리스트 사용자 제외 성공");
    }

    /**
     * 설정 업데이트 테스트
     */
    @Test
    void testConfigUpdate_ShouldApplyNewSettings() {
        System.out.println("⚙️ 설정 업데이트 테스트");
        
        announcementDetectorService.updateDetectionConfig(false, 2000L, "10:00", "19:00");
        
        // 리플렉션으로 필드 값 확인
        boolean enabled = (Boolean) ReflectionTestUtils.getField(announcementDetectorService, "detectionEnabled");
        long timeout = (Long) ReflectionTestUtils.getField(announcementDetectorService, "detectionTimeoutMs");
        String startTime = (String) ReflectionTestUtils.getField(announcementDetectorService, "timeWindowStart");
        String endTime = (String) ReflectionTestUtils.getField(announcementDetectorService, "timeWindowEnd");
        
        assertThat(enabled).isFalse();
        assertThat(timeout).isEqualTo(2000L);
        assertThat(startTime).isEqualTo("10:00");
        assertThat(endTime).isEqualTo("19:00");
        
        System.out.println("✅ 설정 업데이트 성공");
    }

    // Helper Methods

    private TestCase createTestCase(String id, String content, boolean expectedDetected, String description) {
        return new TestCase(id, content, expectedDetected, description);
    }

    private MessageContext createMessageContext(String content) {
        return MessageContext.builder()
            .messageId(UUID.randomUUID().toString())
            .content(content)
            .userId("test_user")
            .chatRoomId("test_room")
            .timestamp(java.time.Instant.now())
            .build();
    }

    private AnnouncementPattern createMockPattern(Long id, String name, String regex, double weight, 
                                                 String category, int priority, boolean active) {
        return AnnouncementPattern.builder()
            .id(id)
            .name(name)
            .regexPattern(regex)
            .confidenceWeight(BigDecimal.valueOf(weight))
            .category(category)
            .priority(priority)
            .active(active)
            .description("Test pattern: " + name)
            .build();
    }

    // Test Case DTO
    private static class TestCase {
        private final String id;
        private final String content;
        private final boolean expectedDetected;
        private final String description;

        public TestCase(String id, String content, boolean expectedDetected, String description) {
            this.id = id;
            this.content = content;
            this.expectedDetected = expectedDetected;
            this.description = description;
        }

        public String getId() { return id; }
        public String getContent() { return content; }
        public boolean isExpectedDetected() { return expectedDetected; }
        public String getDescription() { return description; }
    }
}