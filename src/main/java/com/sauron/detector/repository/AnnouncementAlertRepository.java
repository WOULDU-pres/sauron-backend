package com.sauron.detector.repository;

import com.sauron.detector.entity.AnnouncementAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 공지 알림 히스토리 저장소
 * T-007: 공지/이벤트 메시지 자동 감지 기능 구현
 */
@Repository
public interface AnnouncementAlertRepository extends JpaRepository<AnnouncementAlert, Long> {

    /**
     * 감지 ID로 알림 조회
     */
    List<AnnouncementAlert> findByDetectionIdOrderBySentAtDesc(Long detectionId);

    /**
     * 전송 상태별 알림 조회
     */
    List<AnnouncementAlert> findByDeliveryStatusOrderBySentAtDesc(AnnouncementAlert.DeliveryStatus status);

    /**
     * 재시도 가능한 실패 알림 조회
     */
    @Query("SELECT a FROM AnnouncementAlert a WHERE a.deliveryStatus = 'FAILED' AND a.retryCount < 3 ORDER BY a.sentAt ASC")
    List<AnnouncementAlert> findRetryableFailedAlerts();

    /**
     * 채널별 알림 조회
     */
    List<AnnouncementAlert> findByChannelOrderBySentAtDesc(String channel);

    /**
     * 알림 타입별 조회
     */
    List<AnnouncementAlert> findByAlertTypeOrderBySentAtDesc(String alertType);

    /**
     * 기간별 알림 조회
     */
    List<AnnouncementAlert> findBySentAtBetweenOrderBySentAtDesc(ZonedDateTime startDate, ZonedDateTime endDate);

    /**
     * 수신자별 알림 조회
     */
    List<AnnouncementAlert> findByRecipientOrderBySentAtDesc(String recipient);

    // 임시로 복잡한 쿼리들을 주석 처리하여 애플리케이션 시작 가능하도록 함
    // TODO: 쿼리 최적화 후 주석 해제
    
    /*
    // 임시 주석 처리된 복잡한 쿼리들
    List<Object[]> getChannelSuccessRates(ZonedDateTime startDate);
    List<Object[]> getAverageResponseTimes(ZonedDateTime startDate);
    List<Object[]> getDailyAlertStats(ZonedDateTime startDate);
    */

    /**
     * 최근 알림 조회 (제한된 수)
     */
    List<AnnouncementAlert> findTop20ByOrderBySentAtDesc();

    /**
     * 특정 시간 내 전송된 알림 조회
     */
    @Query("SELECT a FROM AnnouncementAlert a WHERE a.sentAt >= :since ORDER BY a.sentAt DESC")
    List<AnnouncementAlert> findRecentAlerts(@Param("since") ZonedDateTime since);

    /*
    @Query("SELECT a.errorMessage, COUNT(a) " +
           "FROM AnnouncementAlert a " +
           "WHERE a.deliveryStatus = 'FAILED' AND a.sentAt >= :startDate " +
           "GROUP BY a.errorMessage " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> getFailureReasons(@Param("startDate") ZonedDateTime startDate);
    */

    /**
     * 높은 우선순위 알림 조회
     */
    @Query("SELECT a FROM AnnouncementAlert a WHERE a.alertType IN ('ANNOUNCEMENT_HIGH_PRIORITY', 'ANNOUNCEMENT_URGENT') ORDER BY a.sentAt DESC")
    List<AnnouncementAlert> findHighPriorityAlerts();

    /**
     * 시간대 외 알림 조회
     */
    @Query("SELECT a FROM AnnouncementAlert a WHERE a.alertType = 'ANNOUNCEMENT_TIME_VIOLATION' ORDER BY a.sentAt DESC")
    List<AnnouncementAlert> findOutsideHoursAlerts();

    /**
     * 기간별 알림 조회 (LocalDateTime 버전)
     */
    @Query("SELECT a FROM AnnouncementAlert a WHERE a.sentAt >= :startDate AND a.sentAt <= :endDate ORDER BY a.sentAt DESC")
    List<AnnouncementAlert> findByCreatedAtBetween(@Param("startDate") java.time.LocalDateTime startDate, @Param("endDate") java.time.LocalDateTime endDate);
}