package com.sauron.common.dto;

import org.springframework.http.HttpStatus;

/**
 * 시스템 전체에서 사용되는 표준화된 오류 코드
 * HTTP 상태 코드와 매핑되어 일관된 오류 응답을 제공합니다.
 */
public enum ErrorCode {
    
    // 400 Bad Request
    INVALID_REQUEST("INVALID_REQUEST", HttpStatus.BAD_REQUEST, "요청이 올바르지 않습니다"),
    MISSING_REQUIRED_FIELD("MISSING_REQUIRED_FIELD", HttpStatus.BAD_REQUEST, "필수 필드가 누락되었습니다"),
    INVALID_MESSAGE_FORMAT("INVALID_MESSAGE_FORMAT", HttpStatus.BAD_REQUEST, "메시지 형식이 올바르지 않습니다"),
    MESSAGE_TOO_LONG("MESSAGE_TOO_LONG", HttpStatus.BAD_REQUEST, "메시지가 너무 깁니다"),
    INVALID_DEVICE_ID("INVALID_DEVICE_ID", HttpStatus.BAD_REQUEST, "디바이스 ID가 올바르지 않습니다"),
    INVALID_CHAT_ROOM("INVALID_CHAT_ROOM", HttpStatus.BAD_REQUEST, "채팅방 정보가 올바르지 않습니다"),
    
    // 401 Unauthorized
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    INVALID_TOKEN("INVALID_TOKEN", HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
    TOKEN_EXPIRED("TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다"),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다"),
    
    // 403 Forbidden
    ACCESS_DENIED("ACCESS_DENIED", HttpStatus.FORBIDDEN, "접근이 거부되었습니다"),
    INSUFFICIENT_PERMISSIONS("INSUFFICIENT_PERMISSIONS", HttpStatus.FORBIDDEN, "권한이 부족합니다"),
    
    // 404 Not Found
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다"),
    MESSAGE_NOT_FOUND("MESSAGE_NOT_FOUND", HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다"),
    DEVICE_NOT_FOUND("DEVICE_NOT_FOUND", HttpStatus.NOT_FOUND, "디바이스를 찾을 수 없습니다"),
    
    // 409 Conflict
    DUPLICATE_MESSAGE("DUPLICATE_MESSAGE", HttpStatus.CONFLICT, "중복된 메시지입니다"),
    RESOURCE_CONFLICT("RESOURCE_CONFLICT", HttpStatus.CONFLICT, "리소스 충돌이 발생했습니다"),
    
    // 429 Too Many Requests
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS, "요청 한도를 초과했습니다"),
    DEVICE_RATE_LIMIT_EXCEEDED("DEVICE_RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS, "디바이스 요청 한도를 초과했습니다"),
    
    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "내부 서버 오류가 발생했습니다"),
    DATABASE_ERROR("DATABASE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "데이터베이스 오류가 발생했습니다"),
    QUEUE_ERROR("QUEUE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "큐 처리 중 오류가 발생했습니다"),
    EXTERNAL_API_ERROR("EXTERNAL_API_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "외부 API 호출 중 오류가 발생했습니다"),
    SERIALIZATION_ERROR("SERIALIZATION_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "데이터 직렬화 중 오류가 발생했습니다"),
    
    // 502 Bad Gateway
    EXTERNAL_SERVICE_UNAVAILABLE("EXTERNAL_SERVICE_UNAVAILABLE", HttpStatus.BAD_GATEWAY, "외부 서비스를 사용할 수 없습니다"),
    GEMINI_API_ERROR("GEMINI_API_ERROR", HttpStatus.BAD_GATEWAY, "Gemini API 호출 중 오류가 발생했습니다"),
    
    // 503 Service Unavailable
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "서비스를 일시적으로 사용할 수 없습니다"),
    REDIS_UNAVAILABLE("REDIS_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "Redis 서비스를 사용할 수 없습니다"),
    DATABASE_UNAVAILABLE("DATABASE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "데이터베이스를 사용할 수 없습니다");
    
    private final String code;
    private final HttpStatus status;
    private final String message;
    
    ErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
    
    public String getCode() {
        return code;
    }
    
    public HttpStatus getStatus() {
        return status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public int getStatusValue() {
        return status.value();
    }
} 