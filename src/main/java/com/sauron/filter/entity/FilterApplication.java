package com.sauron.filter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 필터 적용 이력 엔티티
 * 메시지에 화이트리스트/예외 단어 필터가 적용된 기록을 저장합니다.
 */
@Entity
@Table(name = "filter_applications", indexes = {
    @Index(name = "idx_filter_applications_message_id", columnList = "message_id"),
    @Index(name = "idx_filter_applications_filter_type", columnList = "filter_type"),
    @Index(name = "idx_filter_applications_applied_at", columnList = "applied_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterApplication {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 연관된 메시지 ID
     */
    @Column(name = "message_id", nullable = false)
    private Long messageId;
    
    /**
     * 필터 타입 (WHITELIST, EXCEPTION)
     */
    @Column(name = "filter_type", nullable = false, length = 50)
    private String filterType;
    
    /**
     * 매치된 단어 ID (WhitelistWord 또는 ExceptionWord ID)
     */
    @Column(name = "matched_word_id")
    private Long matchedWordId;
    
    /**
     * 매치된 단어 내용
     */
    @Column(name = "matched_word", nullable = false, length = 255)
    private String matchedWord;
    
    /**
     * 원래 감지된 타입 (필터 적용 전)
     */
    @Column(name = "original_detection_type", length = 50)
    private String originalDetectionType;
    
    /**
     * 최종 감지 타입 (필터 적용 후)
     */
    @Column(name = "final_detection_type", length = 50)
    private String finalDetectionType;
    
    /**
     * 신뢰도 조정값 (-1.0 ~ 1.0)
     */
    @Column(name = "confidence_adjustment", precision = 3, scale = 2)
    private BigDecimal confidenceAdjustment;
    
    /**
     * 필터 적용 시각
     */
    @Column(name = "applied_at", nullable = false)
    @Builder.Default
    private Instant appliedAt = Instant.now();
    
    /**
     * 필터 타입 열거형
     */
    public enum FilterType {
        WHITELIST("화이트리스트"),
        EXCEPTION("예외 단어");
        
        private final String description;
        
        FilterType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 감지 타입이 변경되었는지 확인
     */
    public boolean hasDetectionTypeChanged() {
        if (originalDetectionType == null || finalDetectionType == null) {
            return false;
        }
        return !originalDetectionType.equals(finalDetectionType);
    }
    
    /**
     * 신뢰도가 조정되었는지 확인
     */
    public boolean hasConfidenceAdjusted() {
        return confidenceAdjustment != null && 
               confidenceAdjustment.compareTo(BigDecimal.ZERO) != 0;
    }
    
    /**
     * 화이트리스트 필터 적용인지 확인
     */
    public boolean isWhitelistFilter() {
        return FilterType.WHITELIST.name().equals(filterType);
    }
    
    /**
     * 예외 단어 필터 적용인지 확인
     */
    public boolean isExceptionFilter() {
        return FilterType.EXCEPTION.name().equals(filterType);
    }
} 