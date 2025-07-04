package com.sauron.detector.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * 공지/이벤트 감지를 위한 패턴 엔티티
 * T-007: 공지/이벤트 메시지 자동 감지 기능 구현
 */
@Entity
@Table(name = "announcement_patterns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "regex_pattern", nullable = false, columnDefinition = "TEXT")
    private String regexPattern;

    @Column(name = "confidence_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidenceWeight;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "category", length = 50)
    @Builder.Default
    private String category = "GENERAL";

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    /**
     * 패턴 활성 상태 토글
     */
    public void toggleActive() {
        this.active = !this.active;
    }

    /**
     * 신뢰도 가중치를 double로 반환
     */
    public double getConfidenceWeightAsDouble() {
        return this.confidenceWeight != null ? this.confidenceWeight.doubleValue() : 0.0;
    }

    /**
     * 패턴이 활성화되어 있는지 확인
     */
    public boolean isActivePattern() {
        return Boolean.TRUE.equals(this.active);
    }

    /**
     * 우선순위가 높은 패턴인지 확인 (우선순위 7 이상)
     */
    public boolean isHighPriority() {
        return this.priority != null && this.priority >= 7;
    }

    /**
     * 제외 패턴인지 확인 (음수 가중치)
     */
    public boolean isExclusionPattern() {
        return this.confidenceWeight != null && this.confidenceWeight.compareTo(BigDecimal.ZERO) < 0;
    }
}