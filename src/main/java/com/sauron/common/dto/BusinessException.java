package com.sauron.common.dto;

/**
 * 비즈니스 로직 처리 중 발생하는 예외
 */
public class BusinessException extends SauronException {
    
    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
    
    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(errorCode, message, data);
    }
    
    public BusinessException(ErrorCode errorCode, String message, Throwable cause, Object data) {
        super(errorCode, message, cause, data);
    }
} 