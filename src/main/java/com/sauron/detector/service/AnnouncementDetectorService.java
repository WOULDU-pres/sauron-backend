package com.sauron.detector.service;

import com.sauron.common.core.async.AsyncExecutor;
import com.sauron.detector.dto.AnnouncementPattern;
import com.sauron.detector.dto.DetectionResult;
import com.sauron.detector.dto.MessageContext;
import com.sauron.detector.repository.AnnouncementPatternRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * 공고/이벤트 감지 서비스
 * 메시지에서 공고나 이벤트 패턴을 감지하고 시간 기반 조건을 확인합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementDetectorService {
    
    private final AsyncExecutor asyncExecutor;
    private final AnnouncementPatternRepository patternRepository;
    private final AnnouncementWhitelistService whitelistService;
    
    @Value("${announcement.detection.enabled:true}")
    private boolean detectionEnabled;
    
    @Value("${announcement.detection.timeout:5000}")
    private long detectionTimeoutMs;
    
    @Value("${announcement.time-window.start:09:00}")
    private String timeWindowStart;
    
    @Value("${announcement.time-window.end:18:00}")
    private String timeWindowEnd;
    
    // 공고 관련 키워드 패턴
    private static final Set<String> ANNOUNCEMENT_KEYWORDS = Set.of(
        "공지", "알림", "안내", "이벤트", "공고", "발표", "소식", "업데이트",
        "중요", "긴급", "필독", "확인", "참고", "공유", "전달", "알려드립니다"
    );
    
    // 시간 관련 패턴
    private static final Pattern TIME_PATTERN = Pattern.compile(
        "\\b(\\d{1,2})시\\s*(\\d{1,2}분)?|\\b(\\d{1,2}):(\\d{2})|" +
        "(오전|오후)\\s*(\\d{1,2})시|(\\d{1,2}월\\s*\\d{1,2}일)"
    );
    
    // 예약어/제외 키워드
    private static final Set<String> EXCLUDED_KEYWORDS = Set.of(
        "스팸", "광고", "홍보", "판매", "구매", "할인", "이벤트참여", "당첨"
    );
    
    /**
     * 메시지에서 공고/이벤트 패턴 감지
     */
    public CompletableFuture<DetectionResult> detectAnnouncement(MessageContext messageContext) {
        if (!detectionEnabled) {
            return CompletableFuture.completedFuture(
                DetectionResult.builder()
                    .detected(false)
                    .confidence(0.0)
                    .reason("공고 감지가 비활성화됨")
                    .build()
            );
        }
        
        return asyncExecutor.executeWithTimeout(() -> {
            try {
                log.debug("Starting announcement detection for message: {}", 
                         messageContext.getMessageId());
                
                // 1. 화이트리스트 확인
                if (whitelistService.isWhitelisted(messageContext)) {
                    return DetectionResult.builder()
                        .detected(false)
                        .confidence(0.0)
                        .reason("화이트리스트에 포함된 사용자/채팅방")
                        .build();
                }
                
                // 2. 기본 공고 패턴 분석
                DetectionResult basicResult = analyzeBasicAnnouncementPattern(messageContext);
                
                // 3. 시간 기반 조건 확인
                DetectionResult timeResult = analyzeTimeBasedConditions(messageContext);
                
                // 4. 커스텀 패턴 확인
                DetectionResult customResult = analyzeCustomPatterns(messageContext);
                
                // 5. 결과 통합
                return integrateResults(basicResult, timeResult, customResult);
                
            } catch (Exception e) {
                log.error("Error during announcement detection for message: {}", 
                         messageContext.getMessageId(), e);
                return DetectionResult.builder()
                    .detected(false)
                    .confidence(0.0)
                    .reason("감지 중 오류 발생: " + e.getMessage())
                    .build();
            }
        }, "AnnouncementDetection", detectionTimeoutMs)
        .exceptionally(throwable -> {
            log.error("Announcement detection timeout for message: {}", 
                     messageContext.getMessageId(), throwable);
            return DetectionResult.builder()
                .detected(false)
                .confidence(0.0)
                .reason("감지 시간 초과")
                .build();
        });
    }
    
    /**
     * 기본 공고 패턴 분석
     */
    private DetectionResult analyzeBasicAnnouncementPattern(MessageContext context) {
        String content = context.getContent().toLowerCase();
        StringBuilder reasonBuilder = new StringBuilder();
        double confidence = 0.0;
        boolean detected = false;
        
        // 제외 키워드 확인
        for (String excluded : EXCLUDED_KEYWORDS) {
            if (content.contains(excluded)) {
                return DetectionResult.builder()
                    .detected(false)
                    .confidence(0.0)
                    .reason("제외 키워드 감지: " + excluded)
                    .build();
            }
        }
        
        // 공고 키워드 확인
        int keywordCount = 0;
        for (String keyword : ANNOUNCEMENT_KEYWORDS) {
            if (content.contains(keyword)) {
                keywordCount++;
                reasonBuilder.append("공고 키워드 발견: ").append(keyword).append("; ");
            }
        }
        
        if (keywordCount > 0) {
            confidence += keywordCount * 0.2; // 키워드당 20% 가중치
            detected = true;
        }
        
        // 시간 패턴 확인
        if (TIME_PATTERN.matcher(content).find()) {
            confidence += 0.3;
            reasonBuilder.append("시간 패턴 감지; ");
        }
        
        // 메시지 길이 고려 (공고는 보통 길다)
        if (context.getContent().length() > 100) {
            confidence += 0.1;
            reasonBuilder.append("긴 메시지 (공고 특성); ");
        }
        
        // 특수 문자 사용 패턴 (공고에서 자주 사용)
        if (content.matches(".*[★☆■□▶▷●○◆◇※].*")) {
            confidence += 0.2;
            reasonBuilder.append("공고용 특수문자 사용; ");
        }
        
        confidence = Math.min(confidence, 1.0);
        
        return DetectionResult.builder()
            .detected(detected && confidence >= 0.3)
            .confidence(confidence)
            .reason(reasonBuilder.toString())
            .detectionType("basic_pattern")
            .build();
    }
    
    /**
     * 시간 기반 조건 분석
     */
    private DetectionResult analyzeTimeBasedConditions(MessageContext context) {
        LocalTime currentTime = LocalTime.now();
        LocalTime startTime = LocalTime.parse(timeWindowStart);
        LocalTime endTime = LocalTime.parse(timeWindowEnd);
        
        StringBuilder reasonBuilder = new StringBuilder();
        double confidence = 0.0;
        boolean detected = false;
        
        // 공고 시간대 확인
        if (isWithinTimeWindow(currentTime, startTime, endTime)) {
            confidence += 0.2;
            reasonBuilder.append("공고 시간대 내 메시지; ");
            detected = true;
        } else {
            reasonBuilder.append("공고 시간대 외 메시지; ");
        }
        
        // 주말/공휴일 체크 (공고가 적을 가능성)
        if (isWeekend()) {
            confidence -= 0.1;
            reasonBuilder.append("주말 메시지 (공고 가능성 낮음); ");
        }
        
        // 채팅방별 공고 패턴 분석
        if (context.getChatRoomId() != null) {
            // 이전 공고 패턴과 비교
            confidence += analyzeHistoricalPattern(context);
            reasonBuilder.append("히스토리 패턴 분석 완료; ");
        }
        
        confidence = Math.max(0.0, Math.min(confidence, 1.0));
        
        return DetectionResult.builder()
            .detected(detected)
            .confidence(confidence)
            .reason(reasonBuilder.toString())
            .detectionType("time_based")
            .build();
    }
    
    /**
     * 커스텀 패턴 분석
     */
    private DetectionResult analyzeCustomPatterns(MessageContext context) {
        List<AnnouncementPattern> customPatterns = patternRepository.findActivePatterns();
        
        StringBuilder reasonBuilder = new StringBuilder();
        double maxConfidence = 0.0;
        boolean detected = false;
        
        for (AnnouncementPattern pattern : customPatterns) {
            try {
                Pattern regex = Pattern.compile(pattern.getRegexPattern(), Pattern.CASE_INSENSITIVE);
                if (regex.matcher(context.getContent()).find()) {
                    double patternConfidence = pattern.getConfidenceWeight();
                    if (patternConfidence > maxConfidence) {
                        maxConfidence = patternConfidence;
                    }
                    detected = true;
                    reasonBuilder.append("커스텀 패턴 매칭: ").append(pattern.getName()).append("; ");
                }
            } catch (Exception e) {
                log.warn("Invalid regex pattern: {}", pattern.getRegexPattern(), e);
            }
        }
        
        return DetectionResult.builder()
            .detected(detected)
            .confidence(maxConfidence)
            .reason(reasonBuilder.toString())
            .detectionType("custom_pattern")
            .build();
    }
    
    /**
     * 결과 통합
     */
    private DetectionResult integrateResults(DetectionResult basic, DetectionResult time, DetectionResult custom) {
        double combinedConfidence = (basic.getConfidence() * 0.5) + 
                                   (time.getConfidence() * 0.3) + 
                                   (custom.getConfidence() * 0.2);
        
        boolean detected = basic.isDetected() || custom.isDetected();
        
        // 최소 신뢰도 임계값
        if (combinedConfidence < 0.4) {
            detected = false;
        }
        
        String combinedReason = String.format("통합 분석 - 기본: %s 시간: %s 커스텀: %s", 
                                            basic.getReason(), time.getReason(), custom.getReason());
        
        return DetectionResult.builder()
            .detected(detected)
            .confidence(combinedConfidence)
            .reason(combinedReason)
            .detectionType("integrated")
            .metadata(Map.of(
                "basicResult", basic,
                "timeResult", time,
                "customResult", custom
            ))
            .build();
    }
    
    /**
     * 시간 윈도우 내 확인
     */
    private boolean isWithinTimeWindow(LocalTime current, LocalTime start, LocalTime end) {
        if (start.isBefore(end)) {
            return !current.isBefore(start) && !current.isAfter(end);
        } else {
            // 자정을 넘나드는 경우
            return !current.isBefore(start) || !current.isAfter(end);
        }
    }
    
    /**
     * 주말 확인
     */
    private boolean isWeekend() {
        java.time.DayOfWeek dayOfWeek = java.time.LocalDate.now().getDayOfWeek();
        return dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY;
    }
    
    /**
     * 히스토리 패턴 분석
     */
    private double analyzeHistoricalPattern(MessageContext context) {
        // TODO: 실제 구현에서는 히스토리 데이터를 분석
        // 현재는 기본값 반환
        return 0.1;
    }
    
    /**
     * 감지 설정 업데이트
     */
    public void updateDetectionConfig(boolean enabled, long timeoutMs, String startTime, String endTime) {
        this.detectionEnabled = enabled;
        this.detectionTimeoutMs = timeoutMs;
        this.timeWindowStart = startTime;
        this.timeWindowEnd = endTime;
        
        log.info("Announcement detection config updated - enabled: {}, timeout: {}ms, window: {}-{}", 
                enabled, timeoutMs, startTime, endTime);
    }
}