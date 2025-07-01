package com.sauron.common.service;

import com.sauron.alert.entity.Alert;
import com.sauron.alert.repository.AlertRepository;
import com.sauron.listener.entity.Message;
import com.sauron.listener.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

/**
 * 로그 저장 서비스
 * 메시지와 알림 로그의 저장, 조회, 관리를 담당합니다.
 * 파티셔닝, 인덱싱, 암호화와 연동하여 성능과 보안을 보장합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogStorageService {
    
    private final MessageRepository messageRepository;
    private final AlertRepository alertRepository;
    
    /**
     * 메시지 로그 저장
     * 
     * @param message 저장할 메시지 엔티티
     * @return 저장된 메시지 엔티티
     */
    @Transactional
    public Message saveMessageLog(Message message) {
        try {
            log.debug("Saving message log - ID: {}, Content length: {}", 
                     message.getMessageId(), 
                     message.getContentEncrypted() != null ? message.getContentEncrypted().length() : 0);
            
            // 파티션 자동 생성 확인 (현재 월 기준)
            ensureCurrentMonthPartition("messages");
            
            Message saved = messageRepository.save(message);
            
            log.info("Message log saved successfully - DB ID: {}, Message ID: {}", 
                    saved.getId(), saved.getMessageId());
            
            return saved;
            
        } catch (Exception e) {
            log.error("Failed to save message log - Message ID: {}", message.getMessageId(), e);
            throw new LogStorageException("Failed to save message log", e);
        }
    }
    
    /**
     * 알림 로그 저장
     * 
     * @param alert 저장할 알림 엔티티
     * @return 저장된 알림 엔티티
     */
    @Transactional
    public Alert saveAlertLog(Alert alert) {
        try {
            log.debug("Saving alert log - Message ID: {}, Channel: {}, Type: {}", 
                     alert.getMessageId(), alert.getChannel(), alert.getAlertType());
            
            // 파티션 자동 생성 확인 (현재 월 기준)
            ensureCurrentMonthPartition("alerts");
            
            Alert saved = alertRepository.save(alert);
            
            log.info("Alert log saved successfully - DB ID: {}, Message ID: {}, Channel: {}", 
                    saved.getId(), saved.getMessageId(), saved.getChannel());
            
            return saved;
            
        } catch (Exception e) {
            log.error("Failed to save alert log - Message ID: {}, Channel: {}", 
                     alert.getMessageId(), alert.getChannel(), e);
            throw new LogStorageException("Failed to save alert log", e);
        }
    }
    
    /**
     * 메시지 로그 조회 (ID 기준)
     */
    public Optional<Message> findMessageById(Long id) {
        try {
            return messageRepository.findById(id);
        } catch (Exception e) {
            log.error("Failed to find message by ID: {}", id, e);
            throw new LogStorageException("Failed to find message", e);
        }
    }
    
    /**
     * 메시지 로그 조회 (메시지 ID 기준)
     */
    public Optional<Message> findMessageByMessageId(String messageId) {
        try {
            return messageRepository.findByMessageId(messageId);
        } catch (Exception e) {
            log.error("Failed to find message by message ID: {}", messageId, e);
            throw new LogStorageException("Failed to find message", e);
        }
    }
    
    /**
     * 특정 기간 내 메시지 로그 조회 (페이징)
     */
    public Page<Message> findMessagesByDateRange(Instant startDate, Instant endDate, Pageable pageable) {
        try {
            log.debug("Querying messages by date range - Start: {}, End: {}, Page: {}", 
                     startDate, endDate, pageable.getPageNumber());
            
            Page<Message> result = messageRepository.findByCreatedAtBetween(startDate, endDate, pageable);
            
            log.debug("Found {} messages in date range - Total: {}", 
                     result.getNumberOfElements(), result.getTotalElements());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to find messages by date range - Start: {}, End: {}", startDate, endDate, e);
            throw new LogStorageException("Failed to find messages by date range", e);
        }
    }
    
    /**
     * 감지 유형별 메시지 로그 조회 (페이징)
     */
    public Page<Message> findMessagesByDetectedType(String detectedType, Pageable pageable) {
        try {
            log.debug("Querying messages by detected type: {}, Page: {}", detectedType, pageable.getPageNumber());
            
            Page<Message> result = messageRepository.findByDetectedType(detectedType, pageable);
            
            log.debug("Found {} messages of type '{}' - Total: {}", 
                     result.getNumberOfElements(), detectedType, result.getTotalElements());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to find messages by detected type: {}", detectedType, e);
            throw new LogStorageException("Failed to find messages by detected type", e);
        }
    }
    
    /**
     * 채팅방별 메시지 로그 조회 (페이징)
     */
    public Page<Message> findMessagesByChatRoom(String chatRoomId, Pageable pageable) {
        try {
            log.debug("Querying messages by chat room: {}, Page: {}", chatRoomId, pageable.getPageNumber());
            
            Page<Message> result = messageRepository.findByChatRoomId(chatRoomId, pageable);
            
            log.debug("Found {} messages in chat room '{}' - Total: {}", 
                     result.getNumberOfElements(), chatRoomId, result.getTotalElements());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to find messages by chat room: {}", chatRoomId, e);
            throw new LogStorageException("Failed to find messages by chat room", e);
        }
    }
    
    /**
     * 알림 로그 조회 (ID 기준)
     */
    public Optional<Alert> findAlertById(Long id) {
        try {
            return alertRepository.findById(id);
        } catch (Exception e) {
            log.error("Failed to find alert by ID: {}", id, e);
            throw new LogStorageException("Failed to find alert", e);
        }
    }
    
    /**
     * 메시지 ID별 알림 로그 조회
     */
    public List<Alert> findAlertsByMessageId(Long messageId) {
        try {
            log.debug("Querying alerts by message ID: {}", messageId);
            
            List<Alert> alerts = alertRepository.findByMessageId(messageId);
            
            log.debug("Found {} alerts for message ID: {}", alerts.size(), messageId);
            
            return alerts;
            
        } catch (Exception e) {
            log.error("Failed to find alerts by message ID: {}", messageId, e);
            throw new LogStorageException("Failed to find alerts by message ID", e);
        }
    }
    
    /**
     * 채널별 알림 로그 조회 (페이징)
     */
    public Page<Alert> findAlertsByChannel(String channel, Pageable pageable) {
        try {
            log.debug("Querying alerts by channel: {}, Page: {}", channel, pageable.getPageNumber());
            
            Page<Alert> result = alertRepository.findByChannel(channel, pageable);
            
            log.debug("Found {} alerts for channel '{}' - Total: {}", 
                     result.getNumberOfElements(), channel, result.getTotalElements());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to find alerts by channel: {}", channel, e);
            throw new LogStorageException("Failed to find alerts by channel", e);
        }
    }
    
    /**
     * 특정 기간 내 알림 로그 조회 (페이징)
     */
    public Page<Alert> findAlertsByDateRange(Instant startDate, Instant endDate, Pageable pageable) {
        try {
            log.debug("Querying alerts by date range - Start: {}, End: {}, Page: {}", 
                     startDate, endDate, pageable.getPageNumber());
            
            Page<Alert> result = alertRepository.findByCreatedAtBetween(startDate, endDate, pageable);
            
            log.debug("Found {} alerts in date range - Total: {}", 
                     result.getNumberOfElements(), result.getTotalElements());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to find alerts by date range - Start: {}, End: {}", startDate, endDate, e);
            throw new LogStorageException("Failed to find alerts by date range", e);
        }
    }
    
    /**
     * 최근 로그 조회 (최근 N개)
     */
    public List<Message> findRecentMessages(int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Message> result = messageRepository.findAll(pageable);
            
            log.debug("Retrieved {} recent messages", result.getNumberOfElements());
            
            return result.getContent();
            
        } catch (Exception e) {
            log.error("Failed to find recent messages - Limit: {}", limit, e);
            throw new LogStorageException("Failed to find recent messages", e);
        }
    }
    
    /**
     * 최근 알림 조회 (최근 N개)
     */
    public List<Alert> findRecentAlerts(int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Alert> result = alertRepository.findAll(pageable);
            
            log.debug("Retrieved {} recent alerts", result.getNumberOfElements());
            
            return result.getContent();
            
        } catch (Exception e) {
            log.error("Failed to find recent alerts - Limit: {}", limit, e);
            throw new LogStorageException("Failed to find recent alerts", e);
        }
    }
    
    /**
     * 필터 적용 로그 기록
     * 
     * @param messageId 메시지 ID
     * @param originalType 원본 감지 타입
     * @param finalType 최종 감지 타입
     * @param filterSummary 적용된 필터 요약
     */
    public void logFilterApplication(Long messageId, String originalType, String finalType, String filterSummary) {
        try {
            log.info("Filter applied to message {}: {} -> {} (filters: {})", 
                    messageId, originalType, finalType, filterSummary);
        } catch (Exception e) {
            log.error("Failed to log filter application for message {}", messageId, e);
            // 필터 로깅 실패는 전체 프로세스를 중단하지 않음
        }
    }

    /**
     * 로그 통계 조회
     */
    public LogStatistics getLogStatistics() {
        try {
            // 오늘 하루 통계
            LocalDate today = LocalDate.now();
            Instant startOfDay = today.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant endOfDay = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            
            long totalMessages = messageRepository.count();
            long todayMessages = messageRepository.countByCreatedAtBetween(startOfDay, endOfDay);
            long abnormalMessages = messageRepository.countByDetectedTypeNotIn(List.of("normal", "unknown"));
            
            long totalAlerts = alertRepository.count();
            long todayAlerts = alertRepository.countRecentDeliveredAlerts(startOfDay);
            long failedAlerts = alertRepository.countFailedAlertsByChannel("telegram"); // 주요 채널 기준
            
            LogStatistics stats = LogStatistics.builder()
                    .totalMessages(totalMessages)
                    .todayMessages(todayMessages)
                    .abnormalMessages(abnormalMessages)
                    .totalAlerts(totalAlerts)
                    .todayAlerts(todayAlerts)
                    .failedAlerts(failedAlerts)
                    .generatedAt(Instant.now())
                    .build();
            
            log.debug("Generated log statistics - Messages: {}, Alerts: {}", totalMessages, totalAlerts);
            
            return stats;
            
        } catch (Exception e) {
            log.error("Failed to generate log statistics", e);
            throw new LogStorageException("Failed to generate log statistics", e);
        }
    }
    
    /**
     * 파티션 생성 확인 (현재 월 기준)
     */
    private void ensureCurrentMonthPartition(String tableType) {
        try {
            // 실제 파티션 생성은 데이터베이스 마이그레이션에서 처리되므로
            // 여기서는 로깅만 수행
            LocalDate currentMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
            log.debug("Ensuring partition for {} table - Month: {}", tableType, currentMonth);
            
            // TODO: 필요 시 동적 파티션 생성 로직 추가
            
        } catch (Exception e) {
            log.warn("Failed to ensure partition for table: {}", tableType, e);
            // 파티션 생성 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }
    
    /**
     * 로그 저장 서비스 예외
     */
    public static class LogStorageException extends RuntimeException {
        public LogStorageException(String message) {
            super(message);
        }
        
        public LogStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * 로그 통계 데이터 클래스
     */
    public static class LogStatistics {
        private final long totalMessages;
        private final long todayMessages;
        private final long abnormalMessages;
        private final long totalAlerts;
        private final long todayAlerts;
        private final long failedAlerts;
        private final Instant generatedAt;
        
        private LogStatistics(Builder builder) {
            this.totalMessages = builder.totalMessages;
            this.todayMessages = builder.todayMessages;
            this.abnormalMessages = builder.abnormalMessages;
            this.totalAlerts = builder.totalAlerts;
            this.todayAlerts = builder.todayAlerts;
            this.failedAlerts = builder.failedAlerts;
            this.generatedAt = builder.generatedAt;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public long getTotalMessages() { return totalMessages; }
        public long getTodayMessages() { return todayMessages; }
        public long getAbnormalMessages() { return abnormalMessages; }
        public long getTotalAlerts() { return totalAlerts; }
        public long getTodayAlerts() { return todayAlerts; }
        public long getFailedAlerts() { return failedAlerts; }
        public Instant getGeneratedAt() { return generatedAt; }
        
        public static class Builder {
            private long totalMessages;
            private long todayMessages;
            private long abnormalMessages;
            private long totalAlerts;
            private long todayAlerts;
            private long failedAlerts;
            private Instant generatedAt;
            
            public Builder totalMessages(long totalMessages) { this.totalMessages = totalMessages; return this; }
            public Builder todayMessages(long todayMessages) { this.todayMessages = todayMessages; return this; }
            public Builder abnormalMessages(long abnormalMessages) { this.abnormalMessages = abnormalMessages; return this; }
            public Builder totalAlerts(long totalAlerts) { this.totalAlerts = totalAlerts; return this; }
            public Builder todayAlerts(long todayAlerts) { this.todayAlerts = todayAlerts; return this; }
            public Builder failedAlerts(long failedAlerts) { this.failedAlerts = failedAlerts; return this; }
            public Builder generatedAt(Instant generatedAt) { this.generatedAt = generatedAt; return this; }
            
            public LogStatistics build() { return new LogStatistics(this); }
        }
    }
} 