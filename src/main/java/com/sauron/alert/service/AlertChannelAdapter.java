package com.sauron.alert.service;

import java.time.Instant;

/**
 * 알림 채널 어댑터 인터페이스
 * Strategy 패턴을 사용하여 다양한 알림 채널을 지원합니다.
 */
public interface AlertChannelAdapter {
    
    /**
     * 채널 이름 반환
     */
    String getChannelName();
    
    /**
     * 채널 활성화 여부
     */
    boolean isEnabled();
    
    /**
     * 채널 상태 확인
     */
    boolean isHealthy();
    
    /**
     * 특정 알림 타입 지원 여부
     */
    boolean supportsAlertType(String alertType);
    
    /**
     * 고우선순위 알림 지원 여부
     */
    boolean supportsHighPriority();
    
    /**
     * 폴백 전송 지원 여부
     */
    boolean supportsFallback();
    
    /**
     * 일반 알림 전송
     */
    void sendAlert(FormattedAlert alert) throws Exception;
    
    /**
     * 고우선순위 알림 전송
     */
    void sendHighPriorityAlert(FormattedAlert alert) throws Exception;
    
    /**
     * 폴백 알림 전송
     */
    void sendFallbackAlert(FormattedAlert alert) throws Exception;
    
    /**
     * 마지막 성공 시간
     */
    Instant getLastSuccessTime();
    
    /**
     * 연속 실패 횟수
     */
    int getConsecutiveFailures();
    
    /**
     * 채널 설정 업데이트
     */
    void updateConfiguration(java.util.Map<String, Object> config);
}