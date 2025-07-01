package com.sauron.common.dto;

/**
 * 인증 실패 시 발생하는 예외
 */
public class AuthenticationException extends SauronException {
    
    public AuthenticationException() {
        super(ErrorCode.UNAUTHORIZED);
    }
    
    public AuthenticationException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
    
    public AuthenticationException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public AuthenticationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public AuthenticationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
} 