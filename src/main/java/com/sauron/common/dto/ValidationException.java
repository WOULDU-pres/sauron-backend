package com.sauron.common.dto;

/**
 * 검증 실패 시 발생하는 예외
 */
public class ValidationException extends SauronException {
    
    public ValidationException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }
    
    public ValidationException(String message, Object data) {
        super(ErrorCode.INVALID_REQUEST, message, data);
    }
    
    public ValidationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public ValidationException(ErrorCode errorCode, String message, Object data) {
        super(errorCode, message, data);
    }
} 