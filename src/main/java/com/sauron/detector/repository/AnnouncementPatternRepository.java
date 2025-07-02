package com.sauron.detector.repository;

import com.sauron.detector.dto.AnnouncementPattern;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 공고 패턴 저장소 (메모리 기반 구현)
 * 실제 환경에서는 JPA Repository로 교체 필요
 */
@Repository
public class AnnouncementPatternRepository {
    
    private final ConcurrentHashMap<Long, AnnouncementPattern> patterns = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    public AnnouncementPatternRepository() {
        initializeDefaultPatterns();
    }
    
    /**
     * 활성 패턴 조회
     */
    public List<AnnouncementPattern> findActivePatterns() {
        return patterns.values().stream()
            .filter(AnnouncementPattern::isActive)
            .collect(Collectors.toList());
    }
    
    /**
     * 모든 패턴 조회
     */
    public List<AnnouncementPattern> findAll() {
        return new ArrayList<>(patterns.values());
    }
    
    /**
     * ID로 패턴 조회
     */
    public AnnouncementPattern findById(Long id) {
        return patterns.get(id);
    }
    
    /**
     * 패턴 저장
     */
    public AnnouncementPattern save(AnnouncementPattern pattern) {
        if (pattern.getId() == null) {
            pattern = pattern.toBuilder()
                .id(idGenerator.getAndIncrement())
                .createdAt(Instant.now())
                .build();
        } else {
            pattern = pattern.toBuilder()
                .updatedAt(Instant.now())
                .build();
        }
        patterns.put(pattern.getId(), pattern);
        return pattern;
    }
    
    /**
     * 패턴 삭제
     */
    public void deleteById(Long id) {
        patterns.remove(id);
    }
    
    /**
     * 카테고리별 활성 패턴 조회
     */
    public List<AnnouncementPattern> findActiveByCategoryOrderByConfidenceDesc(String category) {
        return patterns.values().stream()
            .filter(p -> p.isActive() && category.equals(p.getCategory()))
            .sorted((p1, p2) -> Double.compare(p2.getConfidenceWeight(), p1.getConfidenceWeight()))
            .collect(Collectors.toList());
    }
    
    /**
     * 신뢰도별 활성 패턴 조회
     */
    public List<AnnouncementPattern> findActiveByConfidenceGreaterThan(double minConfidence) {
        return patterns.values().stream()
            .filter(p -> p.isActive() && p.getConfidenceWeight() > minConfidence)
            .collect(Collectors.toList());
    }
    
    /**
     * 기본 패턴 초기화
     */
    private void initializeDefaultPatterns() {
        // 기본 공고 패턴들
        save(AnnouncementPattern.builder()
            .name("공지사항")
            .description("일반적인 공지사항 패턴")
            .regexPattern("(공지|공고|알림|안내).*\\s*(사항|내용|말씀)")
            .confidenceWeight(0.8)
            .active(true)
            .category("official")
            .createdBy("system")
            .build());
        
        save(AnnouncementPattern.builder()
            .name("이벤트공지")
            .description("이벤트 관련 공지 패턴")
            .regexPattern("(이벤트|event).*\\s*(개최|진행|참여|시작)")
            .confidenceWeight(0.7)
            .active(true)
            .category("event")
            .createdBy("system")
            .build());
        
        save(AnnouncementPattern.builder()
            .name("긴급공지")
            .description("긴급한 공지사항 패턴")
            .regexPattern("(긴급|urgent|중요|important).*\\s*(공지|알림|안내)")
            .confidenceWeight(0.9)
            .active(true)
            .category("urgent")
            .createdBy("system")
            .build());
        
        save(AnnouncementPattern.builder()
            .name("업데이트공지")
            .description("시스템 업데이트 관련 공지")
            .regexPattern("(업데이트|update|버전|version).*\\s*(공지|안내|배포)")
            .confidenceWeight(0.6)
            .active(true)
            .category("system")
            .createdBy("system")
            .build());
        
        save(AnnouncementPattern.builder()
            .name("시간공지")
            .description("시간이 포함된 공지 패턴")
            .regexPattern(".*(\\d{1,2}시|\\d{1,2}:\\d{2}|오전|오후).*\\s*(부터|까지|예정|진행)")
            .confidenceWeight(0.5)
            .active(true)
            .category("schedule")
            .createdBy("system")
            .build());
    }
}