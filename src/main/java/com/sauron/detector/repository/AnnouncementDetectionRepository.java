package com.sauron.detector.repository;

import com.sauron.detector.entity.AnnouncementDetection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 공지 감지 결과 저장소
 * T-007: 공지/이벤트 메시지 자동 감지 기능 구현
 */
@Repository
public interface AnnouncementDetectionRepository extends JpaRepository<AnnouncementDetection, Long> {

    /**
     * 메시지 ID로 감지 결과 조회
     */
    List<AnnouncementDetection> findByMessageId(Long messageId);

    /**
     * 알림 미전송 감지 결과 조회
     */
    List<AnnouncementDetection> findByAlertSentFalseOrderByDetectedAtDesc();

    /**
     * 높은 신뢰도 감지 결과 조회 (0.8 이상)
     */
    @Query("SELECT d FROM AnnouncementDetection d WHERE d.confidenceScore >= 0.8 ORDER BY d.detectedAt DESC")
    List<AnnouncementDetection> findHighConfidenceDetections();

    /**
     * 시간대 외 감지 결과 조회
     */
    @Query("SELECT d FROM AnnouncementDetection d WHERE d.timeFactor < 0.5 ORDER BY d.detectedAt DESC")
    List<AnnouncementDetection> findOutsideBusinessHoursDetections();

    /**
     * 기간별 감지 결과 조회
     */
    List<AnnouncementDetection> findByDetectedAtBetweenOrderByDetectedAtDesc(ZonedDateTime startDate, ZonedDateTime endDate);

    /**
     * 패턴별 감지 결과 조회
     */
    List<AnnouncementDetection> findByPatternMatchedOrderByDetectedAtDesc(String patternMatched);

    // TODO: DATE() 함수 이슈로 인한 임시 주석 처리
    /*
    @Query("SELECT DATE(d.detectedAt), COUNT(d), AVG(d.confidenceScore) " +
           "FROM AnnouncementDetection d " +
           "WHERE d.detectedAt >= :startDate " +
           "GROUP BY DATE(d.detectedAt) " +
           "ORDER BY DATE(d.detectedAt) DESC")
    List<Object[]> getDailyDetectionStats(@Param("startDate") ZonedDateTime startDate);
    */

    /**
     * 패턴별 감지 통계 조회
     */
    @Query("SELECT d.patternMatched, COUNT(d), AVG(d.confidenceScore) " +
           "FROM AnnouncementDetection d " +
           "WHERE d.detectedAt >= :startDate " +
           "GROUP BY d.patternMatched " +
           "ORDER BY COUNT(d) DESC")
    List<Object[]> getPatternDetectionStats(@Param("startDate") ZonedDateTime startDate);

    // TODO: 복잡한 쿼리 임시 주석 처리
    /*
    @Query("SELECT " +
           "CASE " +
           "  WHEN d.confidenceScore >= 0.9 THEN 'HIGH' " +
           "  WHEN d.confidenceScore >= 0.7 THEN 'MEDIUM' " +
           "  ELSE 'LOW' " +
           "END as confidence_level, " +
           "COUNT(d) " +
           "FROM AnnouncementDetection d " +
           "WHERE d.detectedAt >= :startDate " +
           "GROUP BY confidence_level")
    List<Object[]> getConfidenceDistribution(@Param("startDate") ZonedDateTime startDate);
    */

    // TODO: ROUND 함수 이슈로 인한 임시 주석 처리
    /*
    @Query("SELECT " +
           "COUNT(d) as total, " +
           "SUM(CASE WHEN d.alertSent = true THEN 1 ELSE 0 END) as sent, " +
           "ROUND(SUM(CASE WHEN d.alertSent = true THEN 1 ELSE 0 END) * 100.0 / COUNT(d), 2) as send_rate " +
           "FROM AnnouncementDetection d " +
           "WHERE d.detectedAt >= :startDate")
    Object[] getAlertSendRate(@Param("startDate") ZonedDateTime startDate);
    */

    /**
     * 최근 감지 결과 조회 (제한된 수)
     */
    List<AnnouncementDetection> findTop10ByOrderByDetectedAtDesc();

    /**
     * 특정 신뢰도 이상의 감지 결과 조회
     */
    @Query("SELECT d FROM AnnouncementDetection d WHERE d.confidenceScore >= :minConfidence ORDER BY d.detectedAt DESC")
    List<AnnouncementDetection> findByMinConfidence(@Param("minConfidence") Double minConfidence);

    /**
     * 중복 감지 방지를 위한 메시지 체크
     */
    boolean existsByMessageId(Long messageId);

    /**
     * 기간별 감지 결과 조회 (LocalDateTime 버전)
     */
    @Query("SELECT d FROM AnnouncementDetection d WHERE d.detectedAt >= :startDate AND d.detectedAt <= :endDate ORDER BY d.detectedAt DESC")
    List<AnnouncementDetection> findByDetectedAtBetween(@Param("startDate") java.time.LocalDateTime startDate, @Param("endDate") java.time.LocalDateTime endDate);
}