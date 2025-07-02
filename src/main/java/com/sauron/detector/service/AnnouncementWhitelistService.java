package com.sauron.detector.service;

import com.sauron.detector.dto.MessageContext;
import com.sauron.detector.entity.WhitelistEntry;
import com.sauron.detector.repository.WhitelistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 공고 감지 화이트리스트 서비스
 * 특정 사용자, 채팅방, 키워드를 화이트리스트로 관리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementWhitelistService {
    
    private final WhitelistRepository whitelistRepository;
    
    // 시스템 관리자 키워드
    private static final Set<String> ADMIN_KEYWORDS = Set.of(
        "관리자", "운영자", "admin", "moderator", "시스템", "system"
    );
    
    // 공식 공고 키워드
    private static final Set<String> OFFICIAL_KEYWORDS = Set.of(
        "공식", "official", "정식", "formal", "공지사항", "announcement"
    );
    
    /**
     * 메시지가 화이트리스트에 포함되는지 확인
     */
    @Cacheable(value = "whitelist", key = "#messageContext.userId + ':' + #messageContext.chatRoomId")
    public boolean isWhitelisted(MessageContext messageContext) {
        try {
            // 1. 사용자 화이트리스트 확인
            if (isUserWhitelisted(messageContext.getUserId())) {
                log.debug("User {} is whitelisted", messageContext.getUserId());
                return true;
            }
            
            // 2. 채팅방 화이트리스트 확인
            if (isChatRoomWhitelisted(messageContext.getChatRoomId())) {
                log.debug("ChatRoom {} is whitelisted", messageContext.getChatRoomId());
                return true;
            }
            
            // 3. 키워드 화이트리스트 확인
            if (isKeywordWhitelisted(messageContext.getContent())) {
                log.debug("Message contains whitelisted keywords");
                return true;
            }
            
            // 4. 관리자/공식 키워드 확인
            if (containsAdminKeywords(messageContext)) {
                log.debug("Message contains admin/official keywords");
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking whitelist for message: {}", messageContext.getMessageId(), e);
            return false; // 오류 시 화이트리스트 통과 안 함
        }
    }
    
    /**
     * 사용자 화이트리스트 확인
     */
    private boolean isUserWhitelisted(String userId) {
        if (userId == null) return false;
        
        List<WhitelistEntry> userEntries = whitelistRepository.findByTypeAndTargetId("USER", userId);
        return userEntries.stream().anyMatch(WhitelistEntry::isActive);
    }
    
    /**
     * 채팅방 화이트리스트 확인
     */
    private boolean isChatRoomWhitelisted(String chatRoomId) {
        if (chatRoomId == null) return false;
        
        List<WhitelistEntry> chatRoomEntries = whitelistRepository.findByTypeAndTargetId("CHATROOM", chatRoomId);
        return chatRoomEntries.stream().anyMatch(WhitelistEntry::isActive);
    }
    
    /**
     * 키워드 화이트리스트 확인
     */
    private boolean isKeywordWhitelisted(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        
        List<WhitelistEntry> keywordEntries = whitelistRepository.findByType("KEYWORD");
        
        for (WhitelistEntry entry : keywordEntries) {
            if (!entry.isActive()) continue;
            
            try {
                if (entry.isRegexPattern()) {
                    Pattern pattern = Pattern.compile(entry.getPattern(), Pattern.CASE_INSENSITIVE);
                    if (pattern.matcher(content).find()) {
                        log.debug("Content matches whitelisted regex pattern: {}", entry.getPattern());
                        return true;
                    }
                } else {
                    if (content.toLowerCase().contains(entry.getPattern().toLowerCase())) {
                        log.debug("Content contains whitelisted keyword: {}", entry.getPattern());
                        return true;
                    }
                }
            } catch (Exception e) {
                log.warn("Invalid regex pattern in whitelist: {}", entry.getPattern(), e);
            }
        }
        
        return false;
    }
    
    /**
     * 관리자/공식 키워드 포함 확인
     */
    private boolean containsAdminKeywords(MessageContext context) {
        String content = context.getContent().toLowerCase();
        String username = context.getUsername() != null ? context.getUsername().toLowerCase() : "";
        
        // 사용자 이름에서 관리자 키워드 확인
        for (String keyword : ADMIN_KEYWORDS) {
            if (username.contains(keyword)) {
                return true;
            }
        }
        
        // 메시지 내용에서 공식 키워드 확인
        for (String keyword : OFFICIAL_KEYWORDS) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 화이트리스트 엔트리 추가
     */
    public WhitelistEntry addWhitelistEntry(String type, String targetId, String pattern, 
                                          boolean isRegex, String description) {
        WhitelistEntry entry = WhitelistEntry.builder()
            .type(type.toUpperCase())
            .targetId(targetId)
            .pattern(pattern)
            .regexPattern(isRegex)
            .active(true)
            .description(description)
            .build();
        
        WhitelistEntry saved = whitelistRepository.save(entry);
        log.info("Added whitelist entry - type: {}, targetId: {}, pattern: {}", 
                type, targetId, pattern);
        
        // 캐시 무효화
        clearWhitelistCache();
        
        return saved;
    }
    
    /**
     * 화이트리스트 엔트리 비활성화
     */
    public void deactivateWhitelistEntry(Long entryId) {
        whitelistRepository.findById(entryId).ifPresent(entry -> {
            entry.setActive(false);
            whitelistRepository.save(entry);
            log.info("Deactivated whitelist entry: {}", entryId);
            clearWhitelistCache();
        });
    }
    
    /**
     * 화이트리스트 엔트리 삭제
     */
    public void deleteWhitelistEntry(Long entryId) {
        whitelistRepository.deleteById(entryId);
        log.info("Deleted whitelist entry: {}", entryId);
        clearWhitelistCache();
    }
    
    /**
     * 타입별 화이트리스트 조회
     */
    public List<WhitelistEntry> getWhitelistByType(String type) {
        return whitelistRepository.findByType(type.toUpperCase());
    }
    
    /**
     * 활성 화이트리스트 조회
     */
    public List<WhitelistEntry> getActiveWhitelist() {
        return whitelistRepository.findByActive(true);
    }
    
    /**
     * 화이트리스트 통계
     */
    public WhitelistStatistics getWhitelistStatistics() {
        List<WhitelistEntry> allEntries = whitelistRepository.findAll();
        
        long totalEntries = allEntries.size();
        long activeEntries = allEntries.stream().mapToLong(e -> e.isActive() ? 1 : 0).sum();
        
        long userEntries = allEntries.stream()
            .filter(e -> "USER".equals(e.getType()))
            .mapToLong(e -> e.isActive() ? 1 : 0).sum();
        
        long chatRoomEntries = allEntries.stream()
            .filter(e -> "CHATROOM".equals(e.getType()))
            .mapToLong(e -> e.isActive() ? 1 : 0).sum();
        
        long keywordEntries = allEntries.stream()
            .filter(e -> "KEYWORD".equals(e.getType()))
            .mapToLong(e -> e.isActive() ? 1 : 0).sum();
        
        return WhitelistStatistics.builder()
            .totalEntries(totalEntries)
            .activeEntries(activeEntries)
            .userEntries(userEntries)
            .chatRoomEntries(chatRoomEntries)
            .keywordEntries(keywordEntries)
            .build();
    }
    
    /**
     * 캐시 클리어
     */
    private void clearWhitelistCache() {
        // Spring Cache 무효화 로직
        // 실제 구현에서는 CacheManager를 사용
        log.debug("Whitelist cache cleared");
    }
    
    /**
     * 화이트리스트 통계 DTO
     */
    public static class WhitelistStatistics {
        private final long totalEntries;
        private final long activeEntries;
        private final long userEntries;
        private final long chatRoomEntries;
        private final long keywordEntries;
        
        public WhitelistStatistics(long totalEntries, long activeEntries, long userEntries, 
                                 long chatRoomEntries, long keywordEntries) {
            this.totalEntries = totalEntries;
            this.activeEntries = activeEntries;
            this.userEntries = userEntries;
            this.chatRoomEntries = chatRoomEntries;
            this.keywordEntries = keywordEntries;
        }
        
        public static WhitelistStatisticsBuilder builder() {
            return new WhitelistStatisticsBuilder();
        }
        
        // Getters
        public long getTotalEntries() { return totalEntries; }
        public long getActiveEntries() { return activeEntries; }
        public long getUserEntries() { return userEntries; }
        public long getChatRoomEntries() { return chatRoomEntries; }
        public long getKeywordEntries() { return keywordEntries; }
        
        public static class WhitelistStatisticsBuilder {
            private long totalEntries;
            private long activeEntries;
            private long userEntries;
            private long chatRoomEntries;
            private long keywordEntries;
            
            public WhitelistStatisticsBuilder totalEntries(long totalEntries) {
                this.totalEntries = totalEntries;
                return this;
            }
            
            public WhitelistStatisticsBuilder activeEntries(long activeEntries) {
                this.activeEntries = activeEntries;
                return this;
            }
            
            public WhitelistStatisticsBuilder userEntries(long userEntries) {
                this.userEntries = userEntries;
                return this;
            }
            
            public WhitelistStatisticsBuilder chatRoomEntries(long chatRoomEntries) {
                this.chatRoomEntries = chatRoomEntries;
                return this;
            }
            
            public WhitelistStatisticsBuilder keywordEntries(long keywordEntries) {
                this.keywordEntries = keywordEntries;
                return this;
            }
            
            public WhitelistStatistics build() {
                return new WhitelistStatistics(totalEntries, activeEntries, userEntries, 
                                             chatRoomEntries, keywordEntries);
            }
        }
    }
}