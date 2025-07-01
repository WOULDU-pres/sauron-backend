package com.sauron.common.external.telegram;

import com.sauron.common.config.TelegramConfig;
import com.sauron.listener.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 텔레그램 알림 전송 서비스
 * 이상 메시지 감지 시 관리자에게 알림 전송
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService {
    
    private final TelegramBotClient telegramBotClient;
    private final TelegramConfig telegramConfig;
    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String ALERT_THROTTLE_KEY = "telegram:alert:throttle:";
    private static final String HOURLY_ALERT_COUNT_KEY = "telegram:alert:hourly:";
    
    /**
     * 이상 메시지 감지 시 텔레그램 알림 전송
     */
    public CompletableFuture<Boolean> sendAbnormalMessageAlert(Message message, String detectedType, Double confidence) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 알림 전송 가능 여부 확인
                if (!shouldSendAlert(message, detectedType, confidence)) {
                    return false;
                }
                
                // 알림 대상 채널 목록 조회
                List<String> targetChatIds = getTargetChatIds();
                if (targetChatIds.isEmpty()) {
                    log.warn("No target chat IDs configured for alerts");
                    return false;
                }
                
                // 알림 메시지 생성
                String alertTitle = generateAlertTitle(detectedType, confidence);
                String alertContent = generateAlertContent(message, detectedType, confidence);
                
                // 모든 대상 채널에 알림 전송
                boolean allSuccess = true;
                for (String chatId : targetChatIds) {
                    try {
                        boolean sent = telegramBotClient.sendAlert(chatId, alertTitle, alertContent, detectedType)
                                .get(); // 동기 대기
                        if (!sent) {
                            allSuccess = false;
                            log.error("Failed to send alert to chat: {}", chatId);
                        }
                    } catch (Exception e) {
                        allSuccess = false;
                        log.error("Error sending alert to chat: {}", chatId, e);
                    }
                }
                
                if (allSuccess) {
                    // 전송 성공 시 스로틀링 및 카운트 업데이트
                    updateAlertThrottling(message);
                    incrementHourlyAlertCount();
                    
                    log.info("Alert sent successfully for message {} with type {} (confidence: {})", 
                            message.getMessageId(), detectedType, confidence);
                } else {
                    log.warn("Some alerts failed to send for message {}", message.getMessageId());
                }
                
                return allSuccess;
                
            } catch (Exception e) {
                log.error("Unexpected error sending telegram alert for message {}", 
                        message.getMessageId(), e);
                return false;
            }
        });
    }
    
    /**
     * 알림 전송 가능 여부 판단
     */
    private boolean shouldSendAlert(Message message, String detectedType, Double confidence) {
        // 텔레그램 알림 비활성화 상태 확인
        if (!telegramConfig.getBot().isEnabled() || !telegramConfig.getAlerts().isEnabled()) {
            log.debug("Telegram alerts are disabled");
            return false;
        }
        
        // 신뢰도 임계값 확인
        if (confidence < telegramConfig.getAlerts().getMinConfidence()) {
            log.debug("Message confidence {} below threshold {}", 
                    confidence, telegramConfig.getAlerts().getMinConfidence());
            return false;
        }
        
        // 알림 대상 유형 확인
        if (!telegramConfig.getAlerts().getAlertTypes().contains(detectedType.toLowerCase())) {
            log.debug("Message type {} not in alert types list", detectedType);
            return false;
        }
        
        // 스로틀링 확인 (동일 채팅방에서 짧은 시간 내 중복 알림 방지)
        if (isAlertThrottled(message)) {
            log.debug("Alert throttled for chatroom {}", message.getChatRoomId());
            return false;
        }
        
        // 시간당 알림 수 제한 확인
        if (isHourlyLimitExceeded()) {
            log.warn("Hourly alert limit exceeded");
            return false;
        }
        
        return true;
    }
    
    /**
     * 대상 채팅 ID 목록 조회
     */
    private List<String> getTargetChatIds() {
        List<String> chatIds = telegramConfig.getChannels().getAdminChatIdList();
        
        // 관리자 채팅 ID가 설정되지 않은 경우 기본 채팅 ID 사용
        if (chatIds.isEmpty()) {
            String defaultChatId = telegramConfig.getChannels().getDefaultChatId();
            if (defaultChatId != null && !defaultChatId.trim().isEmpty()) {
                chatIds = List.of(defaultChatId.trim());
            }
        }
        
        return chatIds.stream()
                .filter(chatId -> chatId != null && !chatId.trim().isEmpty())
                .toList();
    }
    
    /**
     * 알림 제목 생성
     */
    private String generateAlertTitle(String detectedType, Double confidence) {
        String typeDisplay = switch (detectedType.toLowerCase()) {
            case "spam" -> "스팸 메시지";
            case "advertisement" -> "광고 메시지";
            case "abuse" -> "욕설/비방";
            case "inappropriate" -> "부적절한 내용";
            case "conflict" -> "분쟁/갈등";
            default -> "이상 메시지";
        };
        
        return String.format("%s 감지 (신뢰도: %.1f%%)", typeDisplay, confidence * 100);
    }
    
    /**
     * 알림 내용 생성
     */
    private String generateAlertContent(Message message, String detectedType, Double confidence) {
        return String.format(
                "<b>채팅방:</b> %s\n" +
                "<b>감지 유형:</b> %s\n" +
                "<b>신뢰도:</b> %.1f%%\n" +
                "<b>메시지 ID:</b> %s\n" +
                "<b>감지 시간:</b> %s\n\n" +
                "<i>* 개인정보 보호를 위해 메시지 내용은 표시되지 않습니다.</i>",
                message.getChatRoomTitle() != null ? message.getChatRoomTitle() : "알 수 없음",
                detectedType.toUpperCase(),
                confidence * 100,
                message.getMessageId(),
                message.getAnalyzedAt() != null ? 
                        message.getAnalyzedAt().toString() : 
                        Instant.now().toString()
        );
    }
    
    /**
     * 알림 스로틀링 확인
     */
    private boolean isAlertThrottled(Message message) {
        String throttleKey = ALERT_THROTTLE_KEY + message.getChatRoomId();
        String lastAlert = redisTemplate.opsForValue().get(throttleKey);
        return lastAlert != null;
    }
    
    /**
     * 알림 스로틀링 업데이트
     */
    private void updateAlertThrottling(Message message) {
        String throttleKey = ALERT_THROTTLE_KEY + message.getChatRoomId();
        Duration throttleDuration = Duration.ofMinutes(telegramConfig.getAlerts().getThrottleMinutes());
        
        redisTemplate.opsForValue().set(throttleKey, 
                Instant.now().toString(), 
                throttleDuration);
    }
    
    /**
     * 시간당 알림 수 제한 확인
     */
    private boolean isHourlyLimitExceeded() {
        String countKey = HOURLY_ALERT_COUNT_KEY + 
                Instant.now().toString().substring(0, 13); // YYYY-MM-DDTHH 형식
        
        String currentCount = redisTemplate.opsForValue().get(countKey);
        int count = currentCount != null ? Integer.parseInt(currentCount) : 0;
        
        return count >= telegramConfig.getAlerts().getMaxAlertsPerHour();
    }
    
    /**
     * 시간당 알림 수 증가
     */
    private void incrementHourlyAlertCount() {
        String countKey = HOURLY_ALERT_COUNT_KEY + 
                Instant.now().toString().substring(0, 13); // YYYY-MM-DDTHH 형식
        
        redisTemplate.opsForValue().increment(countKey);
        redisTemplate.expire(countKey, Duration.ofHours(2)); // 2시간 후 만료
    }
    
    /**
     * 텔레그램 서비스 상태 확인
     */
    public CompletableFuture<Boolean> checkServiceHealth() {
        return telegramBotClient.checkBotHealth();
    }
    
    /**
     * 테스트 알림 전송
     */
    public CompletableFuture<Boolean> sendTestAlert(String chatId) {
        String testTitle = "Sauron 시스템 테스트";
        String testContent = "텔레그램 알림 시스템이 정상적으로 작동하고 있습니다.";
        
        return telegramBotClient.sendAlert(chatId, testTitle, testContent, "test");
    }
}