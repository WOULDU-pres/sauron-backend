package com.sauron.detector.dto;

import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.time.Instant;

/**
 * 공고 패턴 DTO
 * 커스텀 공고 감지 패턴을 정의합니다.
 */
@Data
@Builder(toBuilder = true)
public class AnnouncementPattern {
    
    private Long id;
    private String name;
    private String description;
    private String regexPattern;
    private double confidenceWeight;
    private boolean active;
    private String category;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    
    /**
     * 패턴 유효성 확인
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() &&
               regexPattern != null && !regexPattern.trim().isEmpty() &&
               confidenceWeight >= 0.0 && confidenceWeight <= 1.0;
    }
    
    /**
     * 신뢰도 백분율로 변환
     */
    public double getConfidencePercentage() {
        return confidenceWeight * 100.0;
    }
    
    /**
     * 패턴 설명 또는 이름 반환
     */
    public String getDisplayName() {
        return description != null && !description.trim().isEmpty() ? 
               description : name;
    }
    
    /**
     * 간단한 텍스트 표현
     */
    public String toSimpleString() {
        return String.format("AnnouncementPattern[id=%d, name=%s, confidence=%.1f%%]",
            id, name, getConfidencePercentage());
    }
    
    /**
     * 활성 패턴 생성
     */
    public static AnnouncementPattern createActive(String name, String regexPattern, 
                                                 double confidence, String description) {
        return AnnouncementPattern.builder()
            .name(name)
            .regexPattern(regexPattern)
            .confidenceWeight(confidence)
            .description(description)
            .active(true)
            .createdAt(Instant.now())
            .build();
    }
    
    /**
     * 비활성 패턴으로 변경
     */
    public AnnouncementPattern deactivate() {
        return this.toBuilder()
            .active(false)
            .updatedAt(Instant.now())
            .build();
    }
    
    /**
     * 패턴 업데이트
     */
    public AnnouncementPattern updatePattern(String newPattern, double newConfidence, String newDescription) {
        return this.toBuilder()
            .regexPattern(newPattern)
            .confidenceWeight(newConfidence)
            .description(newDescription)
            .updatedAt(Instant.now())
            .build();
    }
}