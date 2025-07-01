package com.sauron.alert.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 알림 로그 엔티티
 * 관리자에게 전송된 알림 정보를 저장합니다.
 */
@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alerts_message_id", columnList = "message_id"),
    @Index(name = "idx_alerts_channel", columnList = "channel"),
    @Index(name = "idx_alerts_status", columnList = "status"),
    @Index(name = "idx_alerts_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 연관된 메시지 ID
     */
    @Column(name = "message_id", nullable = false)
    private Long messageId;
    
    /**
     * 알림 채널 (telegram, discord, console 등)
     */
    @Column(name = "channel", nullable = false, length = 50)
    private String channel;
    
    /**
     * 알림 유형 (ABNORMAL_DETECTED, NOTICE_DETECTED 등)
     */
    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;
    
    /**
     * 수신자 정보 (해시된 형태)
     */
    @Column(name = "recipient_hash", length = 100)
    private String recipientHash;
    
    /**
     * 알림 내용 (암호화된 형태)
     */
    @Column(name = "content_encrypted", columnDefinition = "TEXT")
    private String contentEncrypted;
    
    /**
     * 전송 상태 (PENDING/SENT/FAILED/RETRY)
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";
    
    /**
     * 전송 시도 횟수
     */
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;
    
    /**
     * 에러 메시지 (전송 실패 시)
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    /**
     * 메타데이터 (JSON 형태)
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    /**
     * 알림 생성 시각
     */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    /**
     * 전송 완료 시각
     */
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    
    /**
     * 마지막 업데이트 시각
     */
    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
    
    /**
     * 엔티티 업데이트 전 자동 호출
     */
    @PreUpdate
    private void preUpdate() {
        this.updatedAt = Instant.now();
    }
    
    /**
     * 알림이 성공적으로 전송되었는지 확인
     */
    public boolean isDelivered() {
        return "SENT".equalsIgnoreCase(status);
    }
    
    /**
     * 알림 전송이 실패했는지 확인
     */
    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status);
    }
    
    /**
     * 재시도가 필요한지 확인
     */
    public boolean needsRetry() {
        return "RETRY".equalsIgnoreCase(status) && retryCount < 3;
    }
} 