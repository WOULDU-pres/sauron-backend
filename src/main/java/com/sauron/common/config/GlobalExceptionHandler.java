package com.sauron.common.config;

import com.sauron.common.dto.*;
import com.sauron.common.ratelimit.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 글로벌 예외 처리 핸들러
 * 모든 API 예외를 중앙에서 처리하여 일관된 에러 응답을 제공합니다.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Sauron 커스텀 예외 처리 (최우선)
     */
    @ExceptionHandler(SauronException.class)
    public ResponseEntity<ErrorResponse> handleSauronException(
            SauronException ex, WebRequest request) {
        
        ErrorCode errorCode = ex.getErrorCode();
        String path = request.getDescription(false).replace("uri=", "");
        
        log.warn("Sauron exception - Code: {}, Message: {}, Path: {}", 
                errorCode.getCode(), ex.getMessage(), path);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(errorCode.getStatusValue())
                .error(errorCode.getCode())
                .message(ex.getMessage())
                .path(path)
                .build();
        
        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }
    
    /**
     * 검증 예외 처리
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex, WebRequest request) {
        
        ErrorCode errorCode = ex.getErrorCode();
        String path = request.getDescription(false).replace("uri=", "");
        
        log.warn("Validation exception - Code: {}, Message: {}, Path: {}", 
                errorCode.getCode(), ex.getMessage(), path);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(errorCode.getStatusValue())
                .error(errorCode.getCode())
                .message(ex.getMessage())
                .path(path)
                .build();
        
        // ValidationException의 추가 데이터가 있다면 포함
        if (ex.getData() instanceof List) {
            try {
                @SuppressWarnings("unchecked")
                List<ErrorResponse.ValidationError> validationErrors = (List<ErrorResponse.ValidationError>) ex.getData();
                errorResponse.setValidationErrors(validationErrors);
            } catch (ClassCastException e) {
                log.warn("Invalid validation error data format", e);
            }
        }
        
        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }
    
    /**
     * 인증 예외 처리
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        
        ErrorCode errorCode = ex.getErrorCode();
        String path = request.getDescription(false).replace("uri=", "");
        
        log.warn("Authentication exception - Code: {}, Message: {}, Path: {}", 
                errorCode.getCode(), ex.getMessage(), path);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(errorCode.getStatusValue())
                .error(errorCode.getCode())
                .message(ex.getMessage())
                .path(path)
                .build();
        
        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }
    
    /**
     * 비즈니스 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, WebRequest request) {
        
        ErrorCode errorCode = ex.getErrorCode();
        String path = request.getDescription(false).replace("uri=", "");
        
        log.warn("Business exception - Code: {}, Message: {}, Path: {}", 
                errorCode.getCode(), ex.getMessage(), path);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(errorCode.getStatusValue())
                .error(errorCode.getCode())
                .message(ex.getMessage())
                .path(path)
                .build();
        
        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }
    
    /**
     * 요청 데이터 검증 실패 처리 (Jakarta Validation)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        String path = request.getDescription(false).replace("uri=", "");
        
        log.warn("Method argument validation error for request: {}", path);
        
        List<ErrorResponse.ValidationError> validationErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ErrorResponse.ValidationError(
                        error.getField(),
                        error.getRejectedValue(),
                        error.getDefaultMessage()
                ))
                .collect(Collectors.toList());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(ErrorCode.INVALID_REQUEST.getStatusValue())
                .error(ErrorCode.INVALID_REQUEST.getCode())
                .message("Request validation failed")
                .path(path)
                .validationErrors(validationErrors)
                .build();
        
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus()).body(errorResponse);
    }
    
    /**
     * 비즈니스 로직 검증 실패 처리 (기존 코드 호환성)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        String path = request.getDescription(false).replace("uri=", "");
        
        log.warn("Illegal argument error: {} for request: {}", ex.getMessage(), path);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(ErrorCode.INVALID_REQUEST.getStatusValue())
                .error(ErrorCode.INVALID_REQUEST.getCode())
                .message(ex.getMessage())
                .path(path)
                .build();
        
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus()).body(errorResponse);
    }
    
    /**
     * Rate Limit 초과 처리 (기존 RateLimitException 호환성)
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitException(
            RateLimitException ex, WebRequest request) {
        
        String path = request.getDescription(false).replace("uri=", "");
        
        log.warn("Rate limit exceeded: {} for request: {}", ex.getMessage(), path);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(ErrorCode.RATE_LIMIT_EXCEEDED.getStatusValue())
                .error(ErrorCode.RATE_LIMIT_EXCEEDED.getCode())
                .message(ex.getMessage())
                .path(path)
                .build();
        
        return ResponseEntity.status(ErrorCode.RATE_LIMIT_EXCEEDED.getStatus()).body(errorResponse);
    }
    
    /**
     * 일반적인 런타임 예외 처리
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        String path = request.getDescription(false).replace("uri=", "");
        
        log.error("Runtime error: {} for request: {}", ex.getMessage(), path, ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatusValue())
                .error(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                .message("An unexpected error occurred")
                .path(path)
                .build();
        
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus()).body(errorResponse);
    }
    
    /**
     * 모든 기타 예외 처리 (최후 수단)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        
        String path = request.getDescription(false).replace("uri=", "");
        
        log.error("Unexpected error: {} for request: {}", ex.getMessage(), path, ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatusValue())
                .error(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                .message("An unexpected error occurred")
                .path(path)
                .build();
        
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus()).body(errorResponse);
    }
} 