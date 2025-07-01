package com.sauron.common.dto;

/**
 * Sauron 시스템의 기본 예외 클래스
 * 모든 사용자 정의 예외는 이 클래스를 상속받아야 합니다.
 */
public class SauronException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final Object data;
    
    public SauronException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = null;
    }
    
    public SauronException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.data = null;
    }
    
    public SauronException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.data = null;
    }
    
    public SauronException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }
    
    public SauronException(ErrorCode errorCode, String message, Throwable cause, Object data) {
        super(message, cause);
        this.errorCode = errorCode;
        this.data = data;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    public Object getData() {
        return data;
    }
} 