package com.sauron.detector.repository;

import com.sauron.detector.entity.AnnouncementPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 공지 패턴 저장소 (JPA 기반)
 * T-007: 공지/이벤트 메시지 자동 감지 기능 구현
 */
@Repository
public interface AnnouncementPatternRepository extends JpaRepository<AnnouncementPattern, Long> {

    /**
     * 활성화된 패턴만 조회
     */
    List<AnnouncementPattern> findByActiveTrue();

    /**
     * 카테고리별 활성 패턴 조회
     */
    List<AnnouncementPattern> findByCategoryAndActiveTrueOrderByPriorityDesc(String category);

    /**
     * 우선순위가 높은 활성 패턴 조회 (우선순위 7 이상)
     */
    @Query("SELECT p FROM AnnouncementPattern p WHERE p.active = true AND p.priority >= 7 ORDER BY p.priority DESC")
    List<AnnouncementPattern> findHighPriorityActivePatterns();

    /**
     * 제외 패턴 조회 (음수 가중치)
     */
    @Query("SELECT p FROM AnnouncementPattern p WHERE p.active = true AND p.confidenceWeight < 0")
    List<AnnouncementPattern> findExclusionPatterns();

    /**
     * 패턴 이름으로 조회
     */
    Optional<AnnouncementPattern> findByName(String name);

    /**
     * 카테고리별 패턴 수 조회
     */
    @Query("SELECT p.category, COUNT(p) FROM AnnouncementPattern p WHERE p.active = true GROUP BY p.category")
    List<Object[]> countByCategory();

    /**
     * 정규식 패턴으로 중복 확인
     */
    boolean existsByRegexPatternAndActiveTrue(String regexPattern);

    /**
     * 우선순위 범위로 조회
     */
    List<AnnouncementPattern> findByActiveTrueAndPriorityBetweenOrderByPriorityDesc(Integer minPriority, Integer maxPriority);

    /**
     * 신뢰도 가중치 범위로 조회
     */
    @Query("SELECT p FROM AnnouncementPattern p WHERE p.active = true AND p.confidenceWeight BETWEEN :minWeight AND :maxWeight ORDER BY p.confidenceWeight DESC")
    List<AnnouncementPattern> findByConfidenceWeightRange(@Param("minWeight") Double minWeight, @Param("maxWeight") Double maxWeight);

    /**
     * 특정 키워드를 포함하는 패턴 조회
     */
    @Query("SELECT p FROM AnnouncementPattern p WHERE p.active = true AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.regexPattern) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<AnnouncementPattern> findByKeyword(@Param("keyword") String keyword);

    /**
     * 패턴 통계 조회
     */
    @Query("SELECT COUNT(p), AVG(p.confidenceWeight), MAX(p.priority) FROM AnnouncementPattern p WHERE p.active = true")
    Object[] getPatternStatistics();
}