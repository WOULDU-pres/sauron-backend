package com.sauron.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 표준화된 에러 응답 DTO
 * 모든 API 에러에 대해 일관된 응답 구조를 제공합니다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {
    
    /**
     * HTTP 상태 코드
     */
    private int status;
    
    /**
     * 에러 코드 (애플리케이션 정의)
     */
    private String error;
    
    /**
     * 에러 메시지
     */
    private String message;
    
    /**
     * 에러 발생 시각
     */
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    /**
     * 요청 경로
     */
    private String path;
    
    /**
     * 검증 에러 세부사항 (선택적)
     */
    private List<ValidationError> validationErrors;
    
    /**
     * 기본 에러 응답 생성자
     */
    public ErrorResponse(int status, String error, String message, String path) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.timestamp = Instant.now();
    }
    
    /**
     * 검증 에러 세부사항
     */
    @Data
    @AllArgsConstructor
    public static class ValidationError {
        private String field;
        private Object rejectedValue;
        private String message;
    }
} 