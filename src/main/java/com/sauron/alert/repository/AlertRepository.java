package com.sauron.alert.repository;

import com.sauron.alert.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 알림 로그 레포지토리
 * 알림 관련 데이터베이스 작업을 처리합니다.
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    
    /**
     * 메시지 ID로 알림 목록 조회
     */
    List<Alert> findByMessageId(Long messageId);
    
    /**
     * 채널별 알림 목록 조회
     */
    Page<Alert> findByChannel(String channel, Pageable pageable);
    
    /**
     * 상태별 알림 목록 조회
     */
    Page<Alert> findByStatus(String status, Pageable pageable);
    
    /**
     * 특정 기간 내 알림 목록 조회
     */
    @Query("SELECT a FROM Alert a WHERE a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    Page<Alert> findByCreatedAtBetween(@Param("startDate") Instant startDate, 
                                      @Param("endDate") Instant endDate, 
                                      Pageable pageable);
    
    /**
     * 재시도가 필요한 알림 목록 조회
     */
    @Query("SELECT a FROM Alert a WHERE a.status = 'RETRY' AND a.retryCount < 3")
    List<Alert> findAlertsNeedingRetry();
    
    /**
     * 특정 채널의 실패한 알림 개수 조회
     */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.channel = :channel AND a.status = 'FAILED'")
    long countFailedAlertsByChannel(@Param("channel") String channel);
    
    /**
     * 최근 24시간 내 전송된 알림 개수 조회
     */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.status = 'SENT' AND a.deliveredAt >= :since")
    long countRecentDeliveredAlerts(@Param("since") Instant since);
    
    /**
     * 메시지별 알림 전송 여부 확인
     */
    boolean existsByMessageIdAndStatus(Long messageId, String status);
    
    /**
     * 특정 알림 유형의 최근 알림 조회
     */
    @Query("SELECT a FROM Alert a WHERE a.alertType = :alertType ORDER BY a.createdAt DESC")
    Page<Alert> findByAlertTypeOrderByCreatedAtDesc(@Param("alertType") String alertType, Pageable pageable);
} 