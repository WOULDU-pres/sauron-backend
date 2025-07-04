package com.sauron.common.validation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 검증 결과를 담는 클래스
 * 성공/실패 여부와 에러 메시지들을 포함합니다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    
    /**
     * 검증 성공 여부
     */
    private boolean valid;
    
    /**
     * 에러 메시지 목록
     */
    private List<String> errors;
    
    /**
     * 성공 결과 생성
     */
    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }
    
    /**
     * 실패 결과 생성 (단일 에러)
     */
    public static ValidationResult failure(String error) {
        return new ValidationResult(false, List.of(error));
    }
    
    /**
     * 실패 결과 생성 (다중 에러)
     */
    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors);
    }
    
    /**
     * 에러가 있는지 확인
     */
    public boolean hasErrors() {
        return !valid;
    }
    
    /**
     * 첫 번째 에러 메시지 반환
     */
    public String getFirstError() {
        return errors != null && !errors.isEmpty() ? errors.get(0) : null;
    }
    
    /**
     * 모든 에러를 하나의 문자열로 결합
     */
    public String getAllErrorsAsString() {
        return errors != null ? String.join(", ", errors) : "";
    }
}