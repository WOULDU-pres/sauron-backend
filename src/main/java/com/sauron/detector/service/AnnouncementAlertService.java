package com.sauron.detector.service;

import com.sauron.detector.dto.DetectionResult;
import com.sauron.detector.dto.MessageContext;
import com.sauron.detector.entity.AnnouncementAlert;
import com.sauron.detector.entity.AnnouncementDetection;
import com.sauron.detector.repository.AnnouncementAlertRepository;
import com.sauron.common.core.async.AsyncExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 공지/이벤트 감지 시 별도 알림 트리거 서비스
 * T-007-003: 별도 알림 트리거 및 관리자 알림 모듈 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementAlertService {

    private final AnnouncementAlertRepository alertRepository;
    private final AsyncExecutor asyncExecutor;
    private final KakaoTalkAlertChannel kakaoTalkChannel;
    private final TelegramAlertChannel telegramChannel;
    private final EmailAlertChannel emailChannel;
    
    @Value("${announcement.alert.enabled:true}")
    private boolean alertEnabled;
    
    @Value("${announcement.alert.timeout:5000}")
    private long alertTimeoutMs;
    
    @Value("${announcement.alert.retry.max:3}")
    private int maxRetries;
    
    @Value("${announcement.alert.channels:kakaotalk,telegram}")
    private List<String> enabledChannels;
    
    @Value("${announcement.alert.admin.recipients:}")
    private List<String> adminRecipients;

    /**
     * 공지/이벤트 감지 시 알림 전송
     */
    public CompletableFuture<Boolean> sendAnnouncementAlert(
            AnnouncementDetection detection, 
            MessageContext messageContext, 
            DetectionResult detectionResult) {
            
        if (!alertEnabled) {
            log.debug("공지 알림이 비활성화되어 있습니다.");
            return CompletableFuture.completedFuture(false);
        }
        
        return asyncExecutor.executeWithTimeout(() -> {
            try {
                log.info("공지 감지 알림 전송 시작: detection={}, confidence={}", 
                        detection.getId(), detectionResult.getConfidence());
                
                // 알림 타입 결정
                String alertType = determineAlertType(detectionResult, messageContext);
                
                // 알림 메시지 생성
                String alertMessage = generateAlertMessage(detection, messageContext, detectionResult);
                
                // 병렬로 다중 채널 전송
                List<CompletableFuture<AlertResult>> channelFutures = enabledChannels.stream()
                    .map(channel -> sendToChannel(channel, alertType, alertMessage, detection))
                    .toList();
                
                // 모든 채널 결과 대기 (최대 5초)
                CompletableFuture<Void> allChannels = CompletableFuture.allOf(
                    channelFutures.toArray(new CompletableFuture[0])
                );
                
                allChannels.get(alertTimeoutMs, TimeUnit.MILLISECONDS);
                
                // 결과 수집 및 로깅
                boolean anySuccess = false;
                for (CompletableFuture<AlertResult> future : channelFutures) {
                    AlertResult result = future.get();
                    if (result.isSuccess()) {
                        anySuccess = true;
                        saveSuccessfulAlert(detection, result);
                    } else {
                        saveFailedAlert(detection, result);
                    }
                }
                
                if (anySuccess) {
                    log.info("공지 알림 전송 완료: detection={}", detection.getId());
                } else {
                    log.error("모든 채널 알림 전송 실패: detection={}", detection.getId());
                }
                
                return anySuccess;
                
            } catch (Exception e) {
                log.error("공지 알림 전송 중 오류 발생: detection={}", detection.getId(), e);
                saveFailedAlert(detection, AlertResult.error("SYSTEM_ERROR", e.getMessage()));
                return false;
            }
        }, "AnnouncementAlert", alertTimeoutMs);
    }

    /**
     * 알림 타입 결정
     */
    private String determineAlertType(DetectionResult detectionResult, MessageContext messageContext) {
        if (detectionResult.getConfidence() >= 0.9) {
            return "ANNOUNCEMENT_HIGH_PRIORITY";
        } else if (detectionResult.getConfidence() >= 0.7) {
            return "ANNOUNCEMENT_MEDIUM_PRIORITY";
        } else if (isOutsideBusinessHours(messageContext)) {
            return "ANNOUNCEMENT_TIME_VIOLATION";
        } else {
            return "ANNOUNCEMENT_LOW_PRIORITY";
        }
    }

    /**
     * 업무 시간 외 확인
     */
    private boolean isOutsideBusinessHours(MessageContext messageContext) {
        if (messageContext.getTimestamp() == null) return false;
        
        java.time.LocalTime messageTime = messageContext.getTimestamp()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime();
            
        java.time.LocalTime businessStart = java.time.LocalTime.of(9, 0);
        java.time.LocalTime businessEnd = java.time.LocalTime.of(18, 0);
        
        return messageTime.isBefore(businessStart) || messageTime.isAfter(businessEnd);
    }

    /**
     * 알림 메시지 생성
     */
    private String generateAlertMessage(AnnouncementDetection detection, 
                                      MessageContext messageContext, 
                                      DetectionResult detectionResult) {
        StringBuilder message = new StringBuilder();
        
        message.append("🔔 공지/이벤트 감지 알림\n\n");
        message.append("📊 신뢰도: ").append(String.format("%.1f%%", detectionResult.getConfidence() * 100)).append("\n");
        message.append("🕒 감지 시간: ").append(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
                .format(detection.getDetectedAt())).append("\n");
        
        if (messageContext.getChatRoomTitle() != null) {
            message.append("💬 채팅방: ").append(messageContext.getChatRoomTitle()).append("\n");
        }
        
        if (messageContext.getUsername() != null) {
            message.append("👤 발송자: ").append(messageContext.getUsername()).append("\n");
        }
        
        // 매칭된 패턴 정보
        if (detection.getPatternMatched() != null) {
            message.append("🎯 감지 패턴: ").append(detection.getPatternMatched()).append("\n");
        }
        
        // 키워드 정보
        List<String> keywords = detection.getKeywordsMatchedAsList();
        if (!keywords.isEmpty()) {
            message.append("🔤 키워드: ").append(String.join(", ", keywords)).append("\n");
        }
        
        // 시간 관련 정보
        List<String> timeExpressions = detection.getTimeExpressionsAsList();
        if (!timeExpressions.isEmpty()) {
            message.append("⏰ 시간 표현: ").append(String.join(", ", timeExpressions)).append("\n");
        }
        
        // 우선순위별 추가 정보
        if (detectionResult.getConfidence() >= 0.9) {
            message.append("\n⚠️ 높은 신뢰도의 공지입니다. 즉시 확인이 필요합니다.");
        } else if (isOutsideBusinessHours(messageContext)) {
            message.append("\n🌙 업무시간 외 공지 감지. 긴급 사안일 수 있습니다.");
        }
        
        // 메시지 원문 (일부만)
        String content = messageContext.getContent();
        if (content != null && content.length() > 0) {
            String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            message.append("\n\n📝 메시지 내용:\n").append(preview);
        }
        
        return message.toString();
    }

    /**
     * 특정 채널로 알림 전송
     */
    private CompletableFuture<AlertResult> sendToChannel(String channelType, 
                                                        String alertType, 
                                                        String message, 
                                                        AnnouncementDetection detection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("채널 알림 전송 시작: channel={}, detection={}", channelType, detection.getId());
                
                AlertChannel channel = getChannelByType(channelType);
                if (channel == null) {
                    return AlertResult.error(channelType, "지원하지 않는 채널 타입: " + channelType);
                }
                
                // 채널별 재시도 로직
                Exception lastException = null;
                for (int attempt = 1; attempt <= maxRetries; attempt++) {
                    try {
                        boolean sent = channel.sendAlert(alertType, message, adminRecipients);
                        if (sent) {
                            log.info("채널 알림 전송 성공: channel={}, detection={}, attempt={}", 
                                    channelType, detection.getId(), attempt);
                            return AlertResult.success(channelType, "전송 성공");
                        }
                    } catch (Exception e) {
                        lastException = e;
                        log.warn("채널 알림 전송 실패 (시도 {}/{}): channel={}, error={}", 
                                attempt, maxRetries, channelType, e.getMessage());
                        
                        if (attempt < maxRetries) {
                            try {
                                Thread.sleep(1000 * attempt); // 지수 백오프
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                
                String errorMsg = lastException != null ? lastException.getMessage() : "전송 실패";
                return AlertResult.error(channelType, errorMsg);
                
            } catch (Exception e) {
                log.error("채널 알림 전송 중 예상치 못한 오류: channel={}, detection={}", 
                         channelType, detection.getId(), e);
                return AlertResult.error(channelType, "시스템 오류: " + e.getMessage());
            }
        });
    }

    /**
     * 채널 타입에 따른 채널 인스턴스 반환
     */
    private AlertChannel getChannelByType(String channelType) {
        return switch (channelType.toLowerCase()) {
            case "kakaotalk", "kakao" -> kakaoTalkChannel;
            case "telegram" -> telegramChannel;
            case "email" -> emailChannel;
            default -> null;
        };
    }

    /**
     * 성공한 알림 저장
     */
    private void saveSuccessfulAlert(AnnouncementDetection detection, AlertResult result) {
        try {
            AnnouncementAlert alert = AnnouncementAlert.builder()
                .detection(detection)
                .alertType(result.getAlertType())
                .channel(result.getChannelType())
                .messageContent(result.getMessage())
                .recipient(String.join(",", adminRecipients))
                .deliveryStatus(AnnouncementAlert.DeliveryStatus.DELIVERED)
                .retryCount(0)
                .build();
            
            alertRepository.save(alert);
            
        } catch (Exception e) {
            log.error("성공 알림 저장 실패: detection={}, channel={}", 
                     detection.getId(), result.getChannelType(), e);
        }
    }

    /**
     * 실패한 알림 저장
     */
    private void saveFailedAlert(AnnouncementDetection detection, AlertResult result) {
        try {
            AnnouncementAlert alert = AnnouncementAlert.builder()
                .detection(detection)
                .alertType(result.getAlertType())
                .channel(result.getChannelType())
                .messageContent(result.getMessage())
                .recipient(String.join(",", adminRecipients))
                .deliveryStatus(AnnouncementAlert.DeliveryStatus.FAILED)
                .retryCount(maxRetries)
                .errorMessage(result.getErrorMessage())
                .build();
            
            alertRepository.save(alert);
            
        } catch (Exception e) {
            log.error("실패 알림 저장 실패: detection={}, channel={}", 
                     detection.getId(), result.getChannelType(), e);
        }
    }

    /**
     * 재시도 대상 실패 알림 처리
     */
    public void processRetryableAlerts() {
        try {
            List<AnnouncementAlert> retryableAlerts = alertRepository.findRetryableFailedAlerts();
            
            for (AnnouncementAlert alert : retryableAlerts) {
                log.info("실패 알림 재시도: alert={}, channel={}", alert.getId(), alert.getChannel());
                
                AlertChannel channel = getChannelByType(alert.getChannel());
                if (channel != null) {
                    try {
                        boolean sent = channel.sendAlert(
                            alert.getAlertType(), 
                            alert.getMessageContent(), 
                            List.of(alert.getRecipient().split(","))
                        );
                        
                        if (sent) {
                            alert.markAsDelivered();
                            alertRepository.save(alert);
                            log.info("실패 알림 재시도 성공: alert={}", alert.getId());
                        } else {
                            alert.incrementRetryCount();
                            alertRepository.save(alert);
                        }
                    } catch (Exception e) {
                        alert.incrementRetryCount();
                        alert.setErrorMessage(e.getMessage());
                        alertRepository.save(alert);
                        log.warn("실패 알림 재시도 실패: alert={}, error={}", alert.getId(), e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("재시도 대상 알림 처리 중 오류", e);
        }
    }

    /**
     * 알림 결과 DTO
     */
    public static class AlertResult {
        private final String channelType;
        private final String alertType;
        private final String message;
        private final boolean success;
        private final String errorMessage;
        
        private AlertResult(String channelType, String alertType, String message, boolean success, String errorMessage) {
            this.channelType = channelType;
            this.alertType = alertType;
            this.message = message;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public static AlertResult success(String channelType, String message) {
            return new AlertResult(channelType, null, message, true, null);
        }
        
        public static AlertResult error(String channelType, String errorMessage) {
            return new AlertResult(channelType, null, null, false, errorMessage);
        }
        
        // Getters
        public String getChannelType() { return channelType; }
        public String getAlertType() { return alertType; }
        public String getMessage() { return message; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
}