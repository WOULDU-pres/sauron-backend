package com.sauron.detector.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 감지 결과 DTO
 * 공고/이벤트 감지 결과를 담습니다.
 */
@Data
@Builder
public class DetectionResult {
    
    private boolean detected;
    private double confidence;
    private String reason;
    private String detectionType;
    private Instant timestamp;
    private Map<String, Object> metadata;
    
    /**
     * 감지 성공 여부와 임계값 확인
     */
    public boolean isConfidentDetection(double threshold) {
        return detected && confidence >= threshold;
    }
    
    /**
     * 신뢰도 백분율로 변환
     */
    public double getConfidencePercentage() {
        return confidence * 100.0;
    }
    
    /**
     * 메타데이터에서 값 추출
     */
    public Object getMetadataValue(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
    
    /**
     * 간단한 텍스트 표현
     */
    public String toSimpleString() {
        return String.format("DetectionResult[detected=%s, confidence=%.2f, type=%s]",
            detected, confidence, detectionType);
    }
    
    /**
     * 상세 텍스트 표현
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Detection Result:\n");
        sb.append("  Detected: ").append(detected).append("\n");
        sb.append("  Confidence: ").append(String.format("%.1f%%", getConfidencePercentage())).append("\n");
        sb.append("  Type: ").append(detectionType).append("\n");
        sb.append("  Reason: ").append(reason).append("\n");
        if (timestamp != null) {
            sb.append("  Timestamp: ").append(timestamp).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 성공적인 감지 결과 생성
     */
    public static DetectionResult success(double confidence, String reason, String type) {
        return DetectionResult.builder()
            .detected(true)
            .confidence(confidence)
            .reason(reason)
            .detectionType(type)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * 실패한 감지 결과 생성
     */
    public static DetectionResult failure(String reason, String type) {
        return DetectionResult.builder()
            .detected(false)
            .confidence(0.0)
            .reason(reason)
            .detectionType(type)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * 부분 감지 결과 생성 (낮은 신뢰도)
     */
    public static DetectionResult partial(double confidence, String reason, String type) {
        return DetectionResult.builder()
            .detected(false) // 임계값 미달로 미감지 처리
            .confidence(confidence)
            .reason(reason)
            .detectionType(type)
            .timestamp(Instant.now())
            .build();
    }
}