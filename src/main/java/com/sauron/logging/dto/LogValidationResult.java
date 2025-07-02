package com.sauron.logging.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 로그 유효성 검사 결과 DTO
 */
@Data
@Builder
public class LogValidationResult {
    
    private boolean valid;
    private String errorMessage;
    private List<String> validationErrors;
    private String severity;
    
    /**
     * 성공 결과 생성
     */
    public static LogValidationResult success() {
        return LogValidationResult.builder()
            .valid(true)
            .build();
    }
    
    /**
     * 실패 결과 생성
     */
    public static LogValidationResult failure(String errorMessage) {
        return LogValidationResult.builder()
            .valid(false)
            .errorMessage(errorMessage)
            .build();
    }
    
    /**
     * 여러 오류가 있는 실패 결과 생성
     */
    public static LogValidationResult failure(List<String> errors) {
        return LogValidationResult.builder()
            .valid(false)
            .validationErrors(errors)
            .errorMessage(String.join("; ", errors))
            .build();
    }
}