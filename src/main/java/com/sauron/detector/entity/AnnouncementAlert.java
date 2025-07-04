package com.sauron.detector.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

/**
 * 공지 알림 히스토리를 저장하는 엔티티
 * T-007: 공지/이벤트 메시지 자동 감지 기능 구현
 */
@Entity
@Table(name = "announcement_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detection_id", nullable = false)
    private AnnouncementDetection detection;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    @Column(name = "message_content", nullable = false, columnDefinition = "TEXT")
    private String messageContent;

    @Column(name = "recipient", length = 100)
    private String recipient;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false)
    private ZonedDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    @Builder.Default
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 알림 전송 상태 열거형
     */
    public enum DeliveryStatus {
        PENDING("대기중"),
        SENT("전송됨"),
        FAILED("실패"),
        DELIVERED("전달완료");

        private final String description;

        DeliveryStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 알림 전송 성공 처리
     */
    public void markAsDelivered() {
        this.deliveryStatus = DeliveryStatus.DELIVERED;
        this.errorMessage = null;
    }

    /**
     * 알림 전송 실패 처리
     */
    public void markAsFailed(String errorMessage) {
        this.deliveryStatus = DeliveryStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }

    /**
     * 알림 전송 중 상태로 변경
     */
    public void markAsSent() {
        this.deliveryStatus = DeliveryStatus.SENT;
    }

    /**
     * 재시도 횟수 증가
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * 재시도 가능 여부 확인 (최대 3회)
     */
    public boolean canRetry() {
        return this.retryCount < 3 && this.deliveryStatus == DeliveryStatus.FAILED;
    }

    /**
     * 전송 성공 여부 확인
     */
    public boolean isSuccessful() {
        return this.deliveryStatus == DeliveryStatus.DELIVERED;
    }

    /**
     * 높은 우선순위 알림인지 확인
     */
    public boolean isHighPriorityAlert() {
        return "ANNOUNCEMENT_HIGH_PRIORITY".equals(this.alertType) || 
               "ANNOUNCEMENT_URGENT".equals(this.alertType);
    }

    /**
     * 시간대 외 공지 알림인지 확인
     */
    public boolean isOutsideHoursAlert() {
        return "ANNOUNCEMENT_TIME_VIOLATION".equals(this.alertType);
    }

    /**
     * 전송 소요 시간 계산 (초)
     */
    public long getDeliveryTimeSeconds() {
        if (this.detection == null || this.detection.getDetectedAt() == null || this.sentAt == null) {
            return 0;
        }
        return java.time.Duration.between(this.detection.getDetectedAt(), this.sentAt).getSeconds();
    }
}