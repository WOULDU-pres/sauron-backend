package com.sauron.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 검색 요청 DTO
 * 페이징, 정렬, 필터링을 포함한 통합 검색 기능 제공
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchRequest {
    
    /**
     * 검색 쿼리
     */
    private String query;
    
    /**
     * 페이지 번호 (0부터 시작)
     */
    @Min(value = 0, message = "Page number must be 0 or greater")
    @Builder.Default
    private int page = 0;
    
    /**
     * 페이지 크기
     */
    @Min(value = 1, message = "Page size must be 1 or greater")
    @Builder.Default
    private int size = 20;
    
    /**
     * 정렬 옵션들
     */
    @Builder.Default
    private List<SortOption> sort = List.of();
    
    /**
     * 검색 필터들
     */
    @Builder.Default
    private Map<String, Object> filters = Map.of();
    
    /**
     * 검색 대상 필드들 (지정하지 않으면 전체 필드 검색)
     */
    private List<String> searchFields;
    
    /**
     * 대소문자 구분 여부
     */
    @Builder.Default
    private boolean caseSensitive = false;
    
    /**
     * 정확한 일치 검색 여부
     */
    @Builder.Default
    private boolean exactMatch = false;
    
    /**
     * 정렬 옵션 클래스
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SortOption {
        
        /**
         * 정렬 필드명
         */
        @NotNull(message = "Sort field is required")
        private String field;
        
        /**
         * 정렬 방향
         */
        @NotNull(message = "Sort direction is required")
        @Builder.Default
        private SortDirection direction = SortDirection.ASC;
    }
    
    /**
     * 정렬 방향 열거형
     */
    public enum SortDirection {
        ASC, DESC
    }
    
    /**
     * 기본 검색 요청 생성 헬퍼 메서드
     */
    public static SearchRequest defaultSearch() {
        return SearchRequest.builder().build();
    }
    
    /**
     * 쿼리 기반 검색 요청 생성 헬퍼 메서드
     */
    public static SearchRequest withQuery(String query) {
        return SearchRequest.builder()
                .query(query)
                .build();
    }
    
    /**
     * 페이징 기반 검색 요청 생성 헬퍼 메서드
     */
    public static SearchRequest withPaging(int page, int size) {
        return SearchRequest.builder()
                .page(page)
                .size(size)
                .build();
    }
    
    /**
     * 정렬 추가 헬퍼 메서드
     */
    public SearchRequest addSort(String field, SortDirection direction) {
        List<SortOption> newSort = new java.util.ArrayList<>(this.sort);
        newSort.add(SortOption.builder()
                .field(field)
                .direction(direction)
                .build());
        this.sort = newSort;
        return this;
    }
    
    /**
     * 필터 추가 헬퍼 메서드
     */
    public SearchRequest addFilter(String key, Object value) {
        Map<String, Object> newFilters = new java.util.HashMap<>(this.filters);
        newFilters.put(key, value);
        this.filters = newFilters;
        return this;
    }
}