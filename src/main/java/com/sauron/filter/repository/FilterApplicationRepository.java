package com.sauron.filter.repository;

import com.sauron.filter.entity.FilterApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 필터 적용 이력 레포지토리
 * 필터 적용 기록에 대한 CRUD 및 분석 쿼리 작업을 제공합니다.
 */
@Repository
public interface FilterApplicationRepository extends JpaRepository<FilterApplication, Long> {
    
    /**
     * 특정 메시지의 필터 적용 이력 조회
     */
    List<FilterApplication> findByMessageIdOrderByAppliedAtDesc(Long messageId);
    
    /**
     * 필터 타입별 적용 이력 조회 (페이징)
     */
    Page<FilterApplication> findByFilterTypeOrderByAppliedAtDesc(String filterType, Pageable pageable);
    
    /**
     * 특정 기간의 필터 적용 이력 조회
     */
    @Query("SELECT f FROM FilterApplication f WHERE f.appliedAt BETWEEN :startDate AND :endDate ORDER BY f.appliedAt DESC")
    Page<FilterApplication> findByAppliedAtBetween(@Param("startDate") Instant startDate, 
                                                  @Param("endDate") Instant endDate, 
                                                  Pageable pageable);
    
    /**
     * 특정 단어가 매치된 이력 조회
     */
    List<FilterApplication> findByMatchedWordOrderByAppliedAtDesc(String matchedWord);
    
    /**
     * 감지 타입이 변경된 이력만 조회
     */
    @Query("SELECT f FROM FilterApplication f WHERE f.originalDetectionType != f.finalDetectionType ORDER BY f.appliedAt DESC")
    Page<FilterApplication> findDetectionTypeChangedApplications(Pageable pageable);
    
    /**
     * 신뢰도가 조정된 이력만 조회
     */
    @Query("SELECT f FROM FilterApplication f WHERE f.confidenceAdjustment IS NOT NULL AND f.confidenceAdjustment != 0 ORDER BY f.appliedAt DESC")
    Page<FilterApplication> findConfidenceAdjustedApplications(Pageable pageable);
    
    /**
     * 필터 타입별 통계 조회
     */
    @Query("SELECT f.filterType, COUNT(f), " +
           "SUM(CASE WHEN f.originalDetectionType != f.finalDetectionType THEN 1 ELSE 0 END), " +
           "AVG(CASE WHEN f.confidenceAdjustment IS NOT NULL THEN f.confidenceAdjustment ELSE 0 END) " +
           "FROM FilterApplication f " +
           "WHERE f.appliedAt >= :since " +
           "GROUP BY f.filterType")
    List<Object[]> getFilterTypeStatistics(@Param("since") Instant since);
    
    /**
     * 특정 기간의 필터 효과 분석
     */
    @Query("SELECT " +
           "COUNT(f) as totalApplications, " +
           "SUM(CASE WHEN f.filterType = 'WHITELIST' THEN 1 ELSE 0 END) as whitelistCount, " +
           "SUM(CASE WHEN f.filterType = 'EXCEPTION' THEN 1 ELSE 0 END) as exceptionCount, " +
           "SUM(CASE WHEN f.originalDetectionType != f.finalDetectionType THEN 1 ELSE 0 END) as typeChangedCount, " +
           "AVG(CASE WHEN f.confidenceAdjustment IS NOT NULL THEN f.confidenceAdjustment ELSE 0 END) as avgConfidenceAdjustment " +
           "FROM FilterApplication f " +
           "WHERE f.appliedAt BETWEEN :startDate AND :endDate")
    Object[] getFilterEffectivenessAnalysis(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
    
    /**
     * 가장 많이 매치된 단어 순위 조회
     */
    @Query("SELECT f.matchedWord, f.filterType, COUNT(f) as matchCount " +
           "FROM FilterApplication f " +
           "WHERE f.appliedAt >= :since " +
           "GROUP BY f.matchedWord, f.filterType " +
           "ORDER BY matchCount DESC")
    List<Object[]> getTopMatchedWords(@Param("since") Instant since, Pageable pageable);
    
    /**
     * 원본 감지 타입에서 최종 감지 타입으로의 변환 통계
     */
    @Query("SELECT f.originalDetectionType, f.finalDetectionType, COUNT(f) " +
           "FROM FilterApplication f " +
           "WHERE f.originalDetectionType != f.finalDetectionType " +
           "AND f.appliedAt >= :since " +
           "GROUP BY f.originalDetectionType, f.finalDetectionType " +
           "ORDER BY COUNT(f) DESC")
    List<Object[]> getDetectionTypeTransitionStatistics(@Param("since") Instant since);
    
    /**
     * 특정 필터 타입의 최근 적용 이력 조회
     */
    @Query("SELECT f FROM FilterApplication f WHERE f.filterType = :filterType ORDER BY f.appliedAt DESC")
    List<FilterApplication> findRecentApplicationsByType(@Param("filterType") String filterType, Pageable pageable);
    
    /**
     * 특정 기간의 필터 적용 개수 조회
     */
    @Query("SELECT COUNT(f) FROM FilterApplication f WHERE f.appliedAt BETWEEN :startDate AND :endDate")
    long countApplicationsBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
    
    /**
     * 필터 타입별 적용 개수 조회
     */
    @Query("SELECT COUNT(f) FROM FilterApplication f WHERE f.filterType = :filterType AND f.appliedAt >= :since")
    long countApplicationsByTypeAndSince(@Param("filterType") String filterType, @Param("since") Instant since);
    
    /**
     * 메시지별 적용된 필터 개수 조회
     */
    @Query("SELECT f.messageId, COUNT(f) " +
           "FROM FilterApplication f " +
           "WHERE f.appliedAt >= :since " +
           "GROUP BY f.messageId " +
           "HAVING COUNT(f) > 1 " +
           "ORDER BY COUNT(f) DESC")
    List<Object[]> getMessagesWithMultipleFilters(@Param("since") Instant since);
    
    /**
     * 특정 단어 ID가 매치된 필터 적용 이력 조회
     */
    List<FilterApplication> findByMatchedWordIdOrderByAppliedAtDesc(Long matchedWordId);
    
    /**
     * 오늘의 필터 적용 통계
     */
    @Query("SELECT f.filterType, COUNT(f) " +
           "FROM FilterApplication f " +
           "WHERE DATE(f.appliedAt) = CURRENT_DATE " +
           "GROUP BY f.filterType")
    List<Object[]> getTodayFilterStatistics();
} 