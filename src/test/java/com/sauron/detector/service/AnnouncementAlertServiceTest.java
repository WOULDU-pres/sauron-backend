package com.sauron.detector.service;

import com.sauron.common.core.async.AsyncExecutor;
import com.sauron.detector.dto.DetectionResult;
import com.sauron.detector.dto.MessageContext;
import com.sauron.detector.entity.AnnouncementAlert;
import com.sauron.detector.entity.AnnouncementDetection;
import com.sauron.detector.repository.AnnouncementAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * 공지 알림 서비스 테스트
 * T-007-003: 별도 알림 트리거 및 관리자 알림 모듈 구현 검증
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementAlertServiceTest {

    @Mock
    private AnnouncementAlertRepository alertRepository;

    @Mock
    private AsyncExecutor asyncExecutor;

    @Mock
    private KakaoTalkAlertChannel kakaoTalkChannel;

    @Mock
    private TelegramAlertChannel telegramChannel;

    @Mock
    private EmailAlertChannel emailChannel;

    @InjectMocks
    private AnnouncementAlertService alertService;

    private AnnouncementDetection mockDetection;
    private MessageContext mockMessageContext;
    private DetectionResult mockDetectionResult;

    @BeforeEach
    void setUp() {
        // 서비스 설정값 초기화
        ReflectionTestUtils.setField(alertService, "alertEnabled", true);
        ReflectionTestUtils.setField(alertService, "alertTimeoutMs", 5000L);
        ReflectionTestUtils.setField(alertService, "maxRetries", 3);
        ReflectionTestUtils.setField(alertService, "enabledChannels", Arrays.asList("kakaotalk", "telegram"));
        ReflectionTestUtils.setField(alertService, "adminRecipients", Arrays.asList("admin@example.com"));

        // Mock 데이터 준비
        prepareMockData();
        
        // Mock 설정
        setupMocks();
    }

    /**
     * Mock 데이터 준비
     */
    private void prepareMockData() {
        // AnnouncementDetection Mock
        mockDetection = AnnouncementDetection.builder()
            .id(1L)
            .patternMatched("공지사항")
            .confidenceScore(BigDecimal.valueOf(0.95))
            .timeFactor(BigDecimal.valueOf(0.8))
            .keywordsMatched("공지,중요,알림")
            .timeExpressions("오후 2시")
            .detectedAt(ZonedDateTime.now())
            .alertSent(false)
            .build();

        // MessageContext Mock
        mockMessageContext = MessageContext.builder()
            .messageId("test_msg_001")
            .content("📢 중요 공지사항입니다. 오늘 오후 2시에 회의가 있습니다.")
            .userId("test_user")
            .username("홍길동")
            .chatRoomId("test_room")
            .chatRoomTitle("개발팀 채팅방")
            .timestamp(java.time.Instant.now())
            .build();

        // DetectionResult Mock
        mockDetectionResult = DetectionResult.builder()
            .detected(true)
            .confidence(0.95)
            .reason("높은 신뢰도의 공지 패턴 감지")
            .detectionType("integrated")
            .build();
    }

    /**
     * Mock 설정
     */
    private void setupMocks() {
        // AsyncExecutor Mock - 실제 실행
        when(asyncExecutor.executeWithTimeout(any(), anyString(), anyLong()))
            .thenAnswer(invocation -> {
                java.util.function.Supplier<?> supplier = invocation.getArgument(0);
                return CompletableFuture.supplyAsync(() -> supplier.get());
            });

        // Alert Channel Mocks
        try {
            lenient().when(kakaoTalkChannel.sendAlert(anyString(), anyString(), any(List.class))).thenReturn(true);
            lenient().when(telegramChannel.sendAlert(anyString(), anyString(), any(List.class))).thenReturn(true);
            lenient().when(emailChannel.sendAlert(anyString(), anyString(), any(List.class))).thenReturn(true);
        } catch (Exception e) {
            // This should not happen in mocking setup
        }

        when(kakaoTalkChannel.getChannelType()).thenReturn("kakaotalk");
        when(telegramChannel.getChannelType()).thenReturn("telegram");
        when(emailChannel.getChannelType()).thenReturn("email");

        when(kakaoTalkChannel.isEnabled()).thenReturn(true);
        when(telegramChannel.isEnabled()).thenReturn(true);
        when(emailChannel.isEnabled()).thenReturn(false);

        // Repository Mock
        when(alertRepository.save(any(AnnouncementAlert.class)))
            .thenAnswer(invocation -> {
                AnnouncementAlert alert = invocation.getArgument(0);
                ReflectionTestUtils.setField(alert, "id", 1L);
                return alert;
            });
    }

    /**
     * 알림 전송 성공 테스트 (5초 이내 요구사항 검증)
     */
    @Test
    void testSendAnnouncementAlert_ShouldCompleteWithin5Seconds() throws Exception {
        System.out.println("🚨 알림 전송 성능 테스트 시작 (5초 이내 요구사항 검증)");
        
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<Boolean> future = alertService.sendAnnouncementAlert(
            mockDetection, mockMessageContext, mockDetectionResult);
        
        Boolean result = assertDoesNotThrow(() -> future.get(6, TimeUnit.SECONDS));
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertThat(result).isTrue();
        assertThat(processingTime).isLessThanOrEqualTo(5000L);
        
        System.out.printf("✅ 알림 전송 성공: %dms (5초 이내 요구사항 달성)\n", processingTime);
    }

    /**
     * 높은 우선순위 알림 타입 결정 테스트
     */
    @Test
    void testDetermineAlertType_HighPriority() {
        System.out.println("⚠️ 높은 우선순위 알림 타입 테스트");
        
        DetectionResult highConfidenceResult = DetectionResult.builder()
            .detected(true)
            .confidence(0.95)
            .reason("높은 신뢰도")
            .build();

        CompletableFuture<Boolean> future = alertService.sendAnnouncementAlert(
            mockDetection, mockMessageContext, highConfidenceResult);
        
        Boolean result = assertDoesNotThrow(() -> future.get(2, TimeUnit.SECONDS));
        assertThat(result).isTrue();
        
        // 카카오톡과 텔레그램 채널 모두 호출되었는지 확인
        try {
            verify(kakaoTalkChannel, times(1)).sendAlert(
                eq("ANNOUNCEMENT_HIGH_PRIORITY"), anyString(), any(List.class));
            verify(telegramChannel, times(1)).sendAlert(
                eq("ANNOUNCEMENT_HIGH_PRIORITY"), anyString(), any(List.class));
        } catch (Exception e) {
            fail("Verification failed: " + e.getMessage());
        }
        
        System.out.println("✅ 높은 우선순위 알림 처리 성공");
    }

    /**
     * 다중 채널 병렬 전송 테스트
     */
    @Test
    void testMultiChannelParallelSending() throws Exception {
        System.out.println("📡 다중 채널 병렬 전송 테스트");
        
        CompletableFuture<Boolean> future = alertService.sendAnnouncementAlert(
            mockDetection, mockMessageContext, mockDetectionResult);
        
        Boolean result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isTrue();
        
        // 모든 활성화된 채널에 병렬 전송되었는지 확인
        try {
            verify(kakaoTalkChannel, times(1)).sendAlert(anyString(), anyString(), any(List.class));
            verify(telegramChannel, times(1)).sendAlert(anyString(), anyString(), any(List.class));
            verify(emailChannel, never()).sendAlert(anyString(), anyString(), any(List.class)); // 비활성화됨
        } catch (Exception e) {
            fail("Verification failed: " + e.getMessage());
        }
        
        // 성공 알림이 저장되었는지 확인
        verify(alertRepository, atLeast(2)).save(any(AnnouncementAlert.class));
        
        System.out.println("✅ 다중 채널 병렬 전송 성공");
    }

    /**
     * 채널 실패 시 폴백 처리 테스트
     */
    @Test
    void testChannelFailureFallback() throws Exception {
        System.out.println("🔄 채널 실패 폴백 처리 테스트");
        
        // 카카오톡 채널 실패 설정
        try {
            when(kakaoTalkChannel.sendAlert(anyString(), anyString(), any(List.class)))
                .thenThrow(new RuntimeException("카카오톡 전송 실패"));
        } catch (Exception e) {
            // Mock setup exception handling
        }
        
        CompletableFuture<Boolean> future = alertService.sendAnnouncementAlert(
            mockDetection, mockMessageContext, mockDetectionResult);
        
        Boolean result = future.get(5, TimeUnit.SECONDS);
        
        // 하나의 채널이라도 성공하면 전체 성공으로 처리
        assertThat(result).isTrue();
        
        // 텔레그램은 성공, 카카오톡은 실패로 기록되어야 함
        try {
            verify(telegramChannel, times(1)).sendAlert(anyString(), anyString(), any(List.class));
        } catch (Exception e) {
            fail("Verification failed: " + e.getMessage());
        }
        verify(alertRepository, atLeast(1)).save(any(AnnouncementAlert.class));
        
        System.out.println("✅ 채널 실패 시 폴백 처리 성공");
    }

    /**
     * 알림 비활성화 상태 테스트
     */
    @Test
    void testDisabledAlert() throws Exception {
        System.out.println("🔇 알림 비활성화 상태 테스트");
        
        ReflectionTestUtils.setField(alertService, "alertEnabled", false);
        
        CompletableFuture<Boolean> future = alertService.sendAnnouncementAlert(
            mockDetection, mockMessageContext, mockDetectionResult);
        
        Boolean result = future.get(1, TimeUnit.SECONDS);
        assertThat(result).isFalse();
        
        // 채널 호출이 없어야 함
        try {
            verify(kakaoTalkChannel, never()).sendAlert(anyString(), anyString(), any(List.class));
            verify(telegramChannel, never()).sendAlert(anyString(), anyString(), any(List.class));
        } catch (Exception e) {
            fail("Verification failed: " + e.getMessage());
        }
        
        System.out.println("✅ 알림 비활성화 상태 처리 성공");
    }

    /**
     * 업무시간 외 감지 알림 테스트
     */
    @Test
    void testOutsideBusinessHoursAlert() throws Exception {
        System.out.println("🌙 업무시간 외 알림 테스트");
        
        // 새벽 시간으로 설정
        MessageContext nightTimeContext = MessageContext.builder()
            .messageId("night_msg")
            .content("밤늦은 공지사항입니다.")
            .timestamp(java.time.Instant.parse("2024-03-15T02:30:00Z"))
            .build();
        
        CompletableFuture<Boolean> future = alertService.sendAnnouncementAlert(
            mockDetection, nightTimeContext, mockDetectionResult);
        
        Boolean result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isTrue();
        
        // TIME_VIOLATION 알림 타입으로 전송되었는지 확인
        try {
            verify(kakaoTalkChannel, times(1)).sendAlert(
                eq("ANNOUNCEMENT_TIME_VIOLATION"), anyString(), any(List.class));
        } catch (Exception e) {
            fail("Verification failed: " + e.getMessage());
        }
        
        System.out.println("✅ 업무시간 외 알림 처리 성공");
    }

    /**
     * 재시도 가능한 실패 알림 처리 테스트
     */
    @Test
    void testProcessRetryableAlerts() {
        System.out.println("🔄 재시도 가능한 실패 알림 처리 테스트");
        
        // 재시도 대상 알림 Mock
        AnnouncementAlert retryableAlert = AnnouncementAlert.builder()
            .id(1L)
            .detection(mockDetection)
            .alertType("ANNOUNCEMENT_HIGH_PRIORITY")
            .channel("kakaotalk")
            .messageContent("재시도 대상 메시지")
            .recipient("admin@example.com")
            .deliveryStatus(AnnouncementAlert.DeliveryStatus.FAILED)
            .retryCount(1)
            .errorMessage("첫 번째 시도 실패")
            .build();
        
        when(alertRepository.findRetryableFailedAlerts())
            .thenReturn(Arrays.asList(retryableAlert));
        
        // 재시도에서는 성공하도록 설정
        try {
            when(kakaoTalkChannel.sendAlert(anyString(), anyString(), any(List.class)))
                .thenReturn(true);
        } catch (Exception e) {
            // Mock setup exception handling
        }
        
        alertService.processRetryableAlerts();
        
        // 재시도 호출 확인
        try {
            verify(kakaoTalkChannel, times(1)).sendAlert(
                eq("ANNOUNCEMENT_HIGH_PRIORITY"), eq("재시도 대상 메시지"), any(List.class));
        } catch (Exception e) {
            fail("Verification failed: " + e.getMessage());
        }
        
        // 상태 업데이트 확인
        verify(alertRepository, times(1)).save(retryableAlert);
        
        System.out.println("✅ 재시도 가능한 실패 알림 처리 성공");
    }

    /**
     * 알림 메시지 생성 테스트
     */
    @Test
    void testAlertMessageGeneration() throws Exception {
        System.out.println("📝 알림 메시지 생성 테스트");
        
        CompletableFuture<Boolean> future = alertService.sendAnnouncementAlert(
            mockDetection, mockMessageContext, mockDetectionResult);
        
        Boolean result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isTrue();
        
        // 메시지가 생성되어 채널에 전달되었는지 확인
        try {
            verify(kakaoTalkChannel, times(1)).sendAlert(anyString(), argThat(message -> 
                message.contains("🔔 공지/이벤트 감지 알림") &&
                message.contains("95.0%") && // 신뢰도
                message.contains("개발팀 채팅방") && // 채팅방 제목
                message.contains("홍길동") && // 발송자
                message.contains("공지사항") && // 패턴
                message.contains("공지,중요,알림") && // 키워드
                message.contains("오후 2시") // 시간 표현
            ), any(List.class));
        } catch (Exception e) {
            fail("Verification failed: " + e.getMessage());
        }
        
        System.out.println("✅ 알림 메시지 생성 및 내용 검증 성공");
    }
}