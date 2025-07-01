package com.sauron.filter.service;

import com.sauron.filter.entity.WhitelistWord;
import com.sauron.filter.repository.WhitelistWordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 화이트리스트 관리 서비스
 * 화이트리스트 단어의 생성, 수정, 삭제, 조회 및 관리 기능을 제공합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhitelistManagementService {
    
    private final WhitelistWordRepository whitelistWordRepository;
    
    /**
     * 화이트리스트 단어 생성
     * 
     * @param word 단어
     * @param wordType 단어 타입
     * @param description 설명
     * @param isRegex 정규표현식 여부
     * @param isCaseSensitive 대소문자 구분 여부
     * @param priority 우선순위
     * @param createdBy 생성자
     * @return 생성된 화이트리스트 단어
     */
    @Transactional
    public WhitelistWord createWhitelist(String word, String wordType, String description, 
                                       Boolean isRegex, Boolean isCaseSensitive, 
                                       Integer priority, String createdBy) {
        log.debug("Creating new whitelist word: {}", word);
        
        // 입력 검증
        if (!StringUtils.hasText(word)) {
            throw new IllegalArgumentException("Word cannot be empty");
        }
        if (!StringUtils.hasText(wordType)) {
            throw new IllegalArgumentException("Word type cannot be empty");
        }
        if (word.length() > 255) {
            throw new IllegalArgumentException("Word length cannot exceed 255 characters");
        }
        
        // 중복 확인
        if (whitelistWordRepository.existsByWordAndWordType(word, wordType)) {
            throw new WhitelistManagementException(
                String.format("Whitelist word already exists: %s (%s)", word, wordType)
            );
        }
        
        // 정규표현식 유효성 검사
        if (Boolean.TRUE.equals(isRegex)) {
            validateRegexPattern(word);
        }
        
        try {
            WhitelistWord whitelist = WhitelistWord.builder()
                .word(word.trim())
                .wordType(wordType)
                .description(description)
                .isRegex(isRegex != null ? isRegex : false)
                .isCaseSensitive(isCaseSensitive != null ? isCaseSensitive : false)
                .priority(priority != null ? priority : 0)
                .createdBy(createdBy)
                .isActive(true)
                .build();
            
            WhitelistWord saved = whitelistWordRepository.save(whitelist);
            
            log.info("Whitelist word created successfully - ID: {}, Word: '{}', Type: {}", 
                    saved.getId(), saved.getWord(), saved.getWordType());
            
            return saved;
            
        } catch (Exception e) {
            log.error("Failed to create whitelist word: {}", word, e);
            throw new WhitelistManagementException("Failed to create whitelist word", e);
        }
    }
    
    /**
     * 화이트리스트 단어 수정
     */
    @Transactional
    public WhitelistWord updateWhitelist(Long id, String word, String wordType, String description,
                                       Boolean isRegex, Boolean isCaseSensitive, 
                                       Integer priority, Boolean isActive) {
        log.debug("Updating whitelist word ID: {}", id);
        
        WhitelistWord existing = whitelistWordRepository.findById(id)
            .orElseThrow(() -> new WhitelistManagementException("Whitelist word not found: " + id));
        
        // 단어나 타입이 변경되는 경우 중복 확인
        if ((word != null && !word.equals(existing.getWord())) ||
            (wordType != null && !wordType.equals(existing.getWordType()))) {
            
            String newWord = word != null ? word : existing.getWord();
            String newType = wordType != null ? wordType : existing.getWordType();
            
            if (whitelistWordRepository.existsByWordAndWordType(newWord, newType)) {
                throw new WhitelistManagementException(
                    String.format("Whitelist word already exists: %s (%s)", newWord, newType)
                );
            }
        }
        
        // 정규표현식 유효성 검사
        Boolean finalIsRegex = isRegex != null ? isRegex : existing.getIsRegex();
        String finalWord = word != null ? word : existing.getWord();
        if (Boolean.TRUE.equals(finalIsRegex)) {
            validateRegexPattern(finalWord);
        }
        
        try {
            // 업데이트 적용
            if (word != null) existing.setWord(word.trim());
            if (wordType != null) existing.setWordType(wordType);
            if (description != null) existing.setDescription(description);
            if (isRegex != null) existing.setIsRegex(isRegex);
            if (isCaseSensitive != null) existing.setIsCaseSensitive(isCaseSensitive);
            if (priority != null) existing.setPriority(priority);
            if (isActive != null) existing.setIsActive(isActive);
            
            WhitelistWord updated = whitelistWordRepository.save(existing);
            
            log.info("Whitelist word updated successfully - ID: {}, Word: '{}'", 
                    updated.getId(), updated.getWord());
            
            return updated;
            
        } catch (Exception e) {
            log.error("Failed to update whitelist word ID: {}", id, e);
            throw new WhitelistManagementException("Failed to update whitelist word", e);
        }
    }
    
    /**
     * 화이트리스트 단어 삭제
     */
    @Transactional
    public void deleteWhitelist(Long id) {
        log.debug("Deleting whitelist word ID: {}", id);
        
        WhitelistWord existing = whitelistWordRepository.findById(id)
            .orElseThrow(() -> new WhitelistManagementException("Whitelist word not found: " + id));
        
        try {
            whitelistWordRepository.delete(existing);
            
            log.info("Whitelist word deleted successfully - ID: {}, Word: '{}'", 
                    id, existing.getWord());
            
        } catch (Exception e) {
            log.error("Failed to delete whitelist word ID: {}", id, e);
            throw new WhitelistManagementException("Failed to delete whitelist word", e);
        }
    }
    
    /**
     * 화이트리스트 단어 조회 (ID)
     */
    @Transactional(readOnly = true)
    public Optional<WhitelistWord> findById(Long id) {
        return whitelistWordRepository.findById(id);
    }
    
    /**
     * 화이트리스트 단어 검색
     */
    @Transactional(readOnly = true)
    public Page<WhitelistWord> searchWhitelists(String word, String wordType, Boolean isActive, Pageable pageable) {
        return whitelistWordRepository.searchWhitelistWords(word, wordType, isActive, pageable);
    }
    
    /**
     * 모든 활성화된 화이트리스트 조회
     */
    @Transactional(readOnly = true)
    public List<WhitelistWord> findAllActive() {
        return whitelistWordRepository.findAllActiveOrderByPriority();
    }
    
    /**
     * 단어 타입별 활성화된 화이트리스트 조회
     */
    @Transactional(readOnly = true)
    public List<WhitelistWord> findActiveByType(String wordType) {
        return whitelistWordRepository.findActiveByWordTypeOrderByPriority(wordType);
    }
    
    /**
     * 화이트리스트 통계 조회
     */
    @Transactional(readOnly = true)
    public WhitelistStatistics getStatistics() {
        try {
            long totalCount = whitelistWordRepository.count();
            long activeCount = whitelistWordRepository.countActiveWords();
            
            List<Object[]> typeStats = whitelistWordRepository.getWordTypeStatistics();
            List<Object[]> creatorStats = whitelistWordRepository.getCreatorStatistics();
            
            return new WhitelistStatistics(totalCount, activeCount, totalCount - activeCount, 
                                         typeStats, creatorStats, Instant.now());
                
        } catch (Exception e) {
            log.error("Failed to generate whitelist statistics", e);
            throw new WhitelistManagementException("Failed to generate statistics", e);
        }
    }
    
    /**
     * 화이트리스트 일괄 활성화/비활성화
     */
    @Transactional
    public void bulkUpdateStatus(List<Long> ids, boolean active) {
        try {
            List<WhitelistWord> whitelists = whitelistWordRepository.findAllById(ids);
            
            for (WhitelistWord whitelist : whitelists) {
                whitelist.setIsActive(active);
            }
            
            whitelistWordRepository.saveAll(whitelists);
            
            log.info("Bulk updated status for {} whitelist words to {}", whitelists.size(), active);
            
        } catch (Exception e) {
            log.error("Failed to bulk update whitelist status", e);
            throw new WhitelistManagementException("Failed to bulk update status", e);
        }
    }
    
    private void validateRegexPattern(String pattern) {
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex pattern: " + pattern, e);
        }
    }
    
    /**
     * 화이트리스트 통계 데이터 클래스
     */
    public static class WhitelistStatistics {
        private final long totalCount;
        private final long activeCount;
        private final long inactiveCount;
        private final List<Object[]> typeStatistics;
        private final List<Object[]> creatorStatistics;
        private final Instant generatedAt;
        
        public WhitelistStatistics(long totalCount, long activeCount, long inactiveCount,
                                 List<Object[]> typeStatistics, List<Object[]> creatorStatistics,
                                 Instant generatedAt) {
            this.totalCount = totalCount;
            this.activeCount = activeCount;
            this.inactiveCount = inactiveCount;
            this.typeStatistics = typeStatistics;
            this.creatorStatistics = creatorStatistics;
            this.generatedAt = generatedAt;
        }
        
        // Getters
        public long getTotalCount() { return totalCount; }
        public long getActiveCount() { return activeCount; }
        public long getInactiveCount() { return inactiveCount; }
        public List<Object[]> getTypeStatistics() { return typeStatistics; }
        public List<Object[]> getCreatorStatistics() { return creatorStatistics; }
        public Instant getGeneratedAt() { return generatedAt; }
    }
    
    /**
     * 화이트리스트 관리 예외
     */
    public static class WhitelistManagementException extends RuntimeException {
        public WhitelistManagementException(String message) {
            super(message);
        }
        
        public WhitelistManagementException(String message, Throwable cause) {
            super(message, cause);
        }
    }
} 