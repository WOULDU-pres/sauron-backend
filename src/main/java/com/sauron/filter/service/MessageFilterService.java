package com.sauron.filter.service;

import com.sauron.common.service.LogStorageService;
import com.sauron.filter.entity.ExceptionWord;
import com.sauron.filter.entity.FilterApplication;
import com.sauron.filter.entity.WhitelistWord;
import com.sauron.filter.repository.ExceptionWordRepository;
import com.sauron.filter.repository.FilterApplicationRepository;
import com.sauron.filter.repository.WhitelistWordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 메시지 필터링 서비스
 * 화이트리스트와 예외 단어를 기반으로 메시지를 필터링하고 감지 결과를 조정합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageFilterService {
    
    private final WhitelistWordRepository whitelistWordRepository;
    private final ExceptionWordRepository exceptionWordRepository;
    private final FilterApplicationRepository filterApplicationRepository;
    private final LogStorageService logStorageService;
    
    /**
     * 메시지 필터링 결과 DTO
     */
    public static class FilterResult {
        private final String originalDetectionType;
        private final String finalDetectionType;
        private final BigDecimal confidenceAdjustment;
        private final List<FilterApplication> appliedFilters;
        private final boolean filterApplied;
        
        public FilterResult(String originalDetectionType, String finalDetectionType, 
                          BigDecimal confidenceAdjustment, List<FilterApplication> appliedFilters) {
            this.originalDetectionType = originalDetectionType;
            this.finalDetectionType = finalDetectionType;
            this.confidenceAdjustment = confidenceAdjustment;
            this.appliedFilters = appliedFilters != null ? appliedFilters : new ArrayList<>();
            this.filterApplied = !this.appliedFilters.isEmpty();
        }
        
        // Getters
        public String getOriginalDetectionType() { return originalDetectionType; }
        public String getFinalDetectionType() { return finalDetectionType; }
        public BigDecimal getConfidenceAdjustment() { return confidenceAdjustment; }
        public List<FilterApplication> getAppliedFilters() { return appliedFilters; }
        public boolean isFilterApplied() { return filterApplied; }
        
        /**
         * 감지 타입이 변경되었는지 확인
         */
        public boolean hasDetectionTypeChanged() {
            return !originalDetectionType.equals(finalDetectionType);
        }
        
        /**
         * 신뢰도가 조정되었는지 확인
         */
        public boolean hasConfidenceAdjusted() {
            return confidenceAdjustment != null && confidenceAdjustment.compareTo(BigDecimal.ZERO) != 0;
        }
    }
    
    /**
     * 메시지에 대해 모든 필터를 적용하고 결과를 반환
     * 
     * @param messageId 메시지 ID
     * @param content 메시지 내용 (암호화되지 않은 원본)
     * @param senderHash 발신자 해시
     * @param detectedType 원본 감지 타입
     * @param confidence 원본 신뢰도
     * @return 필터링 결과
     */
    @Transactional
    public FilterResult applyFilters(Long messageId, String content, String senderHash, 
                                   String detectedType, BigDecimal confidence) {
        log.debug("Applying filters to message {} with type: {}", messageId, detectedType);
        
        List<FilterApplication> appliedFilters = new ArrayList<>();
        String currentDetectionType = detectedType;
        BigDecimal totalConfidenceAdjustment = BigDecimal.ZERO;
        
        try {
            // 1. 화이트리스트 필터 적용 (우선순위 높음)
            FilterApplication whitelistApplication = applyWhitelistFilter(messageId, content, senderHash, detectedType);
            if (whitelistApplication != null) {
                appliedFilters.add(whitelistApplication);
                currentDetectionType = "NORMAL";
                totalConfidenceAdjustment = totalConfidenceAdjustment.add(BigDecimal.valueOf(-0.8)); // 강한 신뢰도 감소
                log.info("Whitelist filter applied to message {}: {} -> NORMAL", messageId, detectedType);
            }
            
            // 2. 예외 단어 필터 적용 (화이트리스트가 적용되지 않은 경우만)
            if (whitelistApplication == null && !"NORMAL".equals(currentDetectionType)) {
                FilterApplication exceptionApplication = applyExceptionFilter(messageId, content, senderHash, currentDetectionType);
                if (exceptionApplication != null) {
                    appliedFilters.add(exceptionApplication);
                    currentDetectionType = "NORMAL";
                    totalConfidenceAdjustment = totalConfidenceAdjustment.add(BigDecimal.valueOf(-0.5)); // 중간 신뢰도 감소
                    log.info("Exception filter applied to message {}: {} -> NORMAL", messageId, detectedType);
                }
            }
            
            // 3. 필터 적용 이력 저장
            if (!appliedFilters.isEmpty()) {
                filterApplicationRepository.saveAll(appliedFilters);
                
                // 로그 저장
                String filterSummary = appliedFilters.stream()
                    .map(fa -> fa.getFilterType() + ":" + fa.getMatchedWord())
                    .collect(Collectors.joining(", "));
                
                logStorageService.logFilterApplication(messageId, detectedType, currentDetectionType, filterSummary);
            }
            
            return new FilterResult(detectedType, currentDetectionType, totalConfidenceAdjustment, appliedFilters);
            
        } catch (Exception e) {
            log.error("Error applying filters to message {}: {}", messageId, e.getMessage(), e);
            // 필터 적용 실패 시 원본 결과 반환
            return new FilterResult(detectedType, detectedType, BigDecimal.ZERO, new ArrayList<>());
        }
    }
    
    /**
     * 화이트리스트 필터 적용
     */
    private FilterApplication applyWhitelistFilter(Long messageId, String content, String senderHash, String detectedType) {
        List<WhitelistWord> activeWhitelists = whitelistWordRepository.findAllActiveOrderByPriority();
        
        for (WhitelistWord whitelist : activeWhitelists) {
            boolean matches = false;
            String matchText = content;
            
            // 단어 타입에 따른 매칭 텍스트 선택
            switch (whitelist.getWordType()) {
                case "SENDER":
                    matchText = senderHash != null ? senderHash : "";
                    break;
                case "CONTENT_PATTERN":
                case "GENERAL":
                default:
                    matchText = content != null ? content : "";
                    break;
            }
            
            // 매칭 확인
            if (whitelist.matches(matchText)) {
                log.debug("Whitelist match found: '{}' matches '{}'", whitelist.getWord(), matchText);
                
                return FilterApplication.builder()
                    .messageId(messageId)
                    .filterType(FilterApplication.FilterType.WHITELIST.name())
                    .matchedWordId(whitelist.getId())
                    .matchedWord(whitelist.getWord())
                    .originalDetectionType(detectedType)
                    .finalDetectionType("NORMAL")
                    .confidenceAdjustment(BigDecimal.valueOf(-0.8))
                    .appliedAt(Instant.now())
                    .build();
            }
        }
        
        return null;
    }
    
    /**
     * 예외 단어 필터 적용
     */
    private FilterApplication applyExceptionFilter(Long messageId, String content, String senderHash, String detectedType) {
        List<ExceptionWord> applicableExceptions = exceptionWordRepository.findApplicableToDetectedType(detectedType);
        
        for (ExceptionWord exception : applicableExceptions) {
            boolean matches = false;
            String matchText = content;
            
            // 단어 타입에 따른 매칭 텍스트 선택
            switch (exception.getWordType()) {
                case "SENDER":
                    matchText = senderHash != null ? senderHash : "";
                    break;
                case "CONTENT_PATTERN":
                case "GENERAL":
                default:
                    matchText = content != null ? content : "";
                    break;
            }
            
            // 매칭 확인
            if (exception.matches(matchText)) {
                log.debug("Exception match found: '{}' matches '{}' for type '{}'", 
                         exception.getWord(), matchText, detectedType);
                
                return FilterApplication.builder()
                    .messageId(messageId)
                    .filterType(FilterApplication.FilterType.EXCEPTION.name())
                    .matchedWordId(exception.getId())
                    .matchedWord(exception.getWord())
                    .originalDetectionType(detectedType)
                    .finalDetectionType("NORMAL")
                    .confidenceAdjustment(BigDecimal.valueOf(-0.5))
                    .appliedAt(Instant.now())
                    .build();
            }
        }
        
        return null;
    }
    
    /**
     * 특정 메시지의 필터 적용 이력 조회
     */
    @Transactional(readOnly = true)
    public List<FilterApplication> getFilterHistory(Long messageId) {
        return filterApplicationRepository.findByMessageIdOrderByAppliedAtDesc(messageId);
    }
    
    /**
     * 최근 필터 적용 통계 조회
     */
    @Transactional(readOnly = true)
    public List<Object[]> getRecentFilterStatistics(Instant since) {
        return filterApplicationRepository.getFilterTypeStatistics(since);
    }
    
    /**
     * 필터 효과 분석
     */
    @Transactional(readOnly = true)
    public Object[] getFilterEffectivenessAnalysis(Instant startDate, Instant endDate) {
        return filterApplicationRepository.getFilterEffectivenessAnalysis(startDate, endDate);
    }
    
    /**
     * 가장 많이 매치된 단어 조회
     */
    @Transactional(readOnly = true)
    public List<Object[]> getTopMatchedWords(Instant since, int limit) {
        return filterApplicationRepository.getTopMatchedWords(since, 
            org.springframework.data.domain.PageRequest.of(0, limit));
    }
    
    /**
     * 활성화된 화이트리스트 개수 조회
     */
    @Transactional(readOnly = true)
    public long getActiveWhitelistCount() {
        return whitelistWordRepository.countActiveWords();
    }
    
    /**
     * 활성화된 예외 단어 개수 조회
     */
    @Transactional(readOnly = true)
    public long getActiveExceptionWordCount() {
        return exceptionWordRepository.countActiveWords();
    }
    
    /**
     * 특정 감지 타입에 대한 예외 단어 미리보기 (테스트용)
     */
    @Transactional(readOnly = true)
    public List<ExceptionWord> getApplicableExceptionWords(String detectedType) {
        return exceptionWordRepository.findApplicableToDetectedType(detectedType);
    }
    
    /**
     * 모든 활성화된 화이트리스트 조회 (관리용)
     */
    @Transactional(readOnly = true)
    public List<WhitelistWord> getAllActiveWhitelists() {
        return whitelistWordRepository.findAllActiveOrderByPriority();
    }
    
    /**
     * 오늘의 필터 적용 통계 조회
     */
    @Transactional(readOnly = true)
    public List<Object[]> getTodayFilterStatistics() {
        // 오늘 00:00:00 UTC 기준으로 시작 시간 계산
        Instant startOfDay = Instant.now().atZone(java.time.ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant();
        
        // 내일 00:00:00 UTC 기준으로 종료 시간 계산
        Instant startOfNextDay = startOfDay.plus(java.time.Duration.ofDays(1));
        
        return filterApplicationRepository.getTodayFilterStatistics(startOfDay, startOfNextDay);
    }
} 