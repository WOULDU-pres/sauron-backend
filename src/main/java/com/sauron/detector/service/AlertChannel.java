package com.sauron.detector.service;

import java.util.List;

/**
 * 알림 채널 인터페이스
 * T-007-003: 다양한 채널을 통한 공지 알림 전송
 */
public interface AlertChannel {
    
    /**
     * 알림 전송
     * 
     * @param alertType 알림 타입 (ANNOUNCEMENT_HIGH_PRIORITY, ANNOUNCEMENT_MEDIUM_PRIORITY 등)
     * @param message 전송할 메시지 내용
     * @param recipients 수신자 목록
     * @return 전송 성공 여부
     * @throws Exception 전송 실패 시
     */
    boolean sendAlert(String alertType, String message, List<String> recipients) throws Exception;
    
    /**
     * 채널 타입 반환
     */
    String getChannelType();
    
    /**
     * 채널 활성화 상태 확인
     */
    boolean isEnabled();
    
    /**
     * 채널 연결 상태 확인
     */
    boolean isHealthy();
}