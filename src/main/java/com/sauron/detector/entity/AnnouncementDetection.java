package com.sauron.detector.entity;

import com.sauron.listener.entity.Message;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 공지 감지 결과를 저장하는 엔티티
 * T-007: 공지/이벤트 메시지 자동 감지 기능 구현
 */
@Entity
@Table(name = "announcement_detections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "pattern_matched", length = 100)
    private String patternMatched;

    @Column(name = "confidence_score", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "time_factor", precision = 3, scale = 2)
    private BigDecimal timeFactor;

    @Column(name = "keywords_matched", columnDefinition = "TEXT")
    private String keywordsMatched; // JSON array as string

    @Column(name = "special_chars_found", columnDefinition = "TEXT")
    private String specialCharsFound;

    @Column(name = "time_expressions", columnDefinition = "TEXT")
    private String timeExpressions; // JSON array as string

    @CreationTimestamp
    @Column(name = "detected_at", nullable = false)
    private ZonedDateTime detectedAt;

    @Column(name = "alert_sent", nullable = false)
    @Builder.Default
    private Boolean alertSent = false;

    @Column(name = "alert_type", length = 50)
    private String alertType;

    /**
     * 신뢰도 점수를 double로 반환
     */
    public double getConfidenceScoreAsDouble() {
        return this.confidenceScore != null ? this.confidenceScore.doubleValue() : 0.0;
    }

    /**
     * 시간 팩터를 double로 반환
     */
    public double getTimeFactorAsDouble() {
        return this.timeFactor != null ? this.timeFactor.doubleValue() : 0.0;
    }

    /**
     * 높은 신뢰도인지 확인 (0.8 이상)
     */
    public boolean isHighConfidence() {
        return getConfidenceScoreAsDouble() >= 0.8;
    }

    /**
     * 시간대 외 감지인지 확인 (timeFactor가 0.5 미만)
     */
    public boolean isOutsideBusinessHours() {
        return getTimeFactorAsDouble() < 0.5;
    }

    /**
     * 알림 전송 완료 표시
     */
    public void markAlertSent(String alertType) {
        this.alertSent = true;
        this.alertType = alertType;
    }

    /**
     * 감지된 키워드 수 반환
     */
    public int getMatchedKeywordCount() {
        if (this.keywordsMatched == null || this.keywordsMatched.trim().isEmpty()) {
            return 0;
        }
        // Simple comma-separated count
        return this.keywordsMatched.split(",").length;
    }

    /**
     * 시간 표현 감지 여부
     */
    public boolean hasTimeExpressions() {
        return this.timeExpressions != null && !this.timeExpressions.trim().isEmpty();
    }

    /**
     * 감지된 키워드 목록을 List로 반환
     */
    public List<String> getKeywordsMatchedAsList() {
        if (this.keywordsMatched == null || this.keywordsMatched.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.asList(this.keywordsMatched.split(","));
    }

    /**
     * 시간 표현 목록을 List로 반환
     */
    public List<String> getTimeExpressionsAsList() {
        if (this.timeExpressions == null || this.timeExpressions.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.asList(this.timeExpressions.split(","));
    }

    /**
     * 특수문자 감지 여부
     */
    public boolean hasSpecialChars() {
        return this.specialCharsFound != null && !this.specialCharsFound.trim().isEmpty();
    }

    /**
     * 종합 분석 점수 계산 (신뢰도 + 시간팩터 + 키워드수)
     */
    public double calculateOverallScore() {
        double score = getConfidenceScoreAsDouble();
        score += getTimeFactorAsDouble() * 0.3; // 시간 가중치
        score += Math.min(getMatchedKeywordCount() * 0.1, 0.2); // 키워드 보너스 (최대 0.2)
        return Math.min(score, 1.0);
    }

    /**
     * 공지 감지 여부 확인 (신뢰도 기반)
     */
    public boolean getIsAnnouncement() {
        return getConfidenceScoreAsDouble() >= 0.7;
    }

    /**
     * 메시지 ID 반환 (편의 메서드)
     */
    public Long getMessageId() {
        return this.message != null ? this.message.getId() : null;
    }
}