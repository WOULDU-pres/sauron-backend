package com.sauron.filter.controller;

import com.sauron.filter.entity.WhitelistWord;
import com.sauron.filter.service.WhitelistManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 화이트리스트 관리 REST API 컨트롤러
 * 화이트리스트 단어의 CRUD 및 관리 기능을 제공합니다.
 */
@RestController
@RequestMapping("/v1/whitelist")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Whitelist Management", description = "화이트리스트 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class WhitelistController {
    
    private final WhitelistManagementService whitelistService;
    
    /**
     * 화이트리스트 목록 조회 (페이징)
     */
    @GetMapping
    @Operation(summary = "화이트리스트 목록 조회", description = "페이징 및 검색 조건으로 화이트리스트를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<Page<WhitelistWord>> getWhitelists(
            @Parameter(description = "검색할 단어") @RequestParam(required = false) String word,
            @Parameter(description = "단어 타입") @RequestParam(required = false) String wordType,
            @Parameter(description = "활성화 상태") @RequestParam(required = false) Boolean isActive,
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "정렬 기준") @RequestParam(defaultValue = "priority") String sort,
            @Parameter(description = "정렬 방향") @RequestParam(defaultValue = "desc") String direction
    ) {
        try {
            Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? 
                Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
            
            Page<WhitelistWord> whitelists = whitelistService.searchWhitelists(word, wordType, isActive, pageable);
            
            log.debug("Retrieved {} whitelists on page {}", whitelists.getNumberOfElements(), page);
            return ResponseEntity.ok(whitelists);
            
        } catch (Exception e) {
            log.error("Failed to retrieve whitelists", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 화이트리스트 단일 조회
     */
    @GetMapping("/{id}")
    @Operation(summary = "화이트리스트 단일 조회", description = "ID로 특정 화이트리스트를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "화이트리스트를 찾을 수 없음"),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<WhitelistWord> getWhitelist(
            @Parameter(description = "화이트리스트 ID") @PathVariable Long id
    ) {
        try {
            return whitelistService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
                
        } catch (Exception e) {
            log.error("Failed to retrieve whitelist ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 화이트리스트 생성
     */
    @PostMapping
    @Operation(summary = "화이트리스트 생성", description = "새로운 화이트리스트 단어를 생성합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "생성 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
        @ApiResponse(responseCode = "409", description = "중복된 화이트리스트"),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<WhitelistWord> createWhitelist(
            @Valid @RequestBody CreateWhitelistRequest request,
            Authentication authentication
    ) {
        try {
            String createdBy = authentication != null ? authentication.getName() : "system";
            
            WhitelistWord created = whitelistService.createWhitelist(
                request.getWord(),
                request.getWordType(),
                request.getDescription(),
                request.getIsRegex(),
                request.getIsCaseSensitive(),
                request.getPriority(),
                createdBy
            );
            
            log.info("Whitelist created - ID: {}, Word: '{}'", created.getId(), created.getWord());
            return ResponseEntity.status(201).body(created);
            
        } catch (WhitelistManagementService.WhitelistManagementException e) {
            log.warn("Failed to create whitelist: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to create whitelist", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 화이트리스트 수정
     */
    @PutMapping("/{id}")
    @Operation(summary = "화이트리스트 수정", description = "기존 화이트리스트를 수정합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
        @ApiResponse(responseCode = "404", description = "화이트리스트를 찾을 수 없음"),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<WhitelistWord> updateWhitelist(
            @Parameter(description = "화이트리스트 ID") @PathVariable Long id,
            @Valid @RequestBody UpdateWhitelistRequest request
    ) {
        try {
            WhitelistWord updated = whitelistService.updateWhitelist(
                id,
                request.getWord(),
                request.getWordType(),
                request.getDescription(),
                request.getIsRegex(),
                request.getIsCaseSensitive(),
                request.getPriority(),
                request.getIsActive()
            );
            
            log.info("Whitelist updated - ID: {}, Word: '{}'", updated.getId(), updated.getWord());
            return ResponseEntity.ok(updated);
            
        } catch (WhitelistManagementService.WhitelistManagementException e) {
            log.warn("Failed to update whitelist ID {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to update whitelist ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 화이트리스트 삭제
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "화이트리스트 삭제", description = "화이트리스트를 삭제합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "화이트리스트를 찾을 수 없음"),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<Void> deleteWhitelist(
            @Parameter(description = "화이트리스트 ID") @PathVariable Long id
    ) {
        try {
            whitelistService.deleteWhitelist(id);
            
            log.info("Whitelist deleted - ID: {}", id);
            return ResponseEntity.noContent().build();
            
        } catch (WhitelistManagementService.WhitelistManagementException e) {
            log.warn("Failed to delete whitelist ID {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to delete whitelist ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 화이트리스트 통계 조회
     */
    @GetMapping("/statistics")
    @Operation(summary = "화이트리스트 통계", description = "화이트리스트 관련 통계를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "통계 조회 성공")
    public ResponseEntity<WhitelistManagementService.WhitelistStatistics> getStatistics() {
        try {
            WhitelistManagementService.WhitelistStatistics stats = whitelistService.getStatistics();
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Failed to retrieve whitelist statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 활성화된 화이트리스트 목록 조회
     */
    @GetMapping("/active")
    @Operation(summary = "활성화된 화이트리스트 목록", description = "현재 활성화된 모든 화이트리스트를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<List<WhitelistWord>> getActiveWhitelists() {
        try {
            List<WhitelistWord> activeWhitelists = whitelistService.findAllActive();
            return ResponseEntity.ok(activeWhitelists);
            
        } catch (Exception e) {
            log.error("Failed to retrieve active whitelists", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 화이트리스트 생성 요청 DTO
     */
    public static class CreateWhitelistRequest {
        private String word;
        private String wordType = "GENERAL";
        private String description;
        private Boolean isRegex = false;
        private Boolean isCaseSensitive = false;
        private Integer priority = 0;
        
        // Getters and Setters
        public String getWord() { return word; }
        public void setWord(String word) { this.word = word; }
        public String getWordType() { return wordType; }
        public void setWordType(String wordType) { this.wordType = wordType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Boolean getIsRegex() { return isRegex; }
        public void setIsRegex(Boolean isRegex) { this.isRegex = isRegex; }
        public Boolean getIsCaseSensitive() { return isCaseSensitive; }
        public void setIsCaseSensitive(Boolean isCaseSensitive) { this.isCaseSensitive = isCaseSensitive; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
    }
    
    /**
     * 화이트리스트 수정 요청 DTO
     */
    public static class UpdateWhitelistRequest {
        private String word;
        private String wordType;
        private String description;
        private Boolean isRegex;
        private Boolean isCaseSensitive;
        private Integer priority;
        private Boolean isActive;
        
        // Getters and Setters
        public String getWord() { return word; }
        public void setWord(String word) { this.word = word; }
        public String getWordType() { return wordType; }
        public void setWordType(String wordType) { this.wordType = wordType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Boolean getIsRegex() { return isRegex; }
        public void setIsRegex(Boolean isRegex) { this.isRegex = isRegex; }
        public Boolean getIsCaseSensitive() { return isCaseSensitive; }
        public void setIsCaseSensitive(Boolean isCaseSensitive) { this.isCaseSensitive = isCaseSensitive; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    }
} 