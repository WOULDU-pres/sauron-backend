package com.sauron.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 페이지네이션 응답 DTO
 * 페이징된 데이터를 포함하는 표준화된 응답 구조
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaginatedResponse<T> {
    
    /**
     * 페이지 데이터
     */
    private List<T> content;
    
    /**
     * 페이지 메타데이터
     */
    private PageMetadata page;
    
    /**
     * 페이지 메타데이터 클래스
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PageMetadata {
        
        /**
         * 현재 페이지 번호 (0부터 시작)
         */
        private int number;
        
        /**
         * 페이지 크기
         */
        private int size;
        
        /**
         * 전체 요소 수
         */
        private long totalElements;
        
        /**
         * 전체 페이지 수
         */
        private int totalPages;
        
        /**
         * 첫 번째 페이지 여부
         */
        private boolean first;
        
        /**
         * 마지막 페이지 여부
         */
        private boolean last;
        
        /**
         * 다음 페이지 존재 여부
         */
        private boolean hasNext;
        
        /**
         * 이전 페이지 존재 여부
         */
        private boolean hasPrevious;
    }
    
    /**
     * 페이지네이션 응답 생성 헬퍼 메서드
     */
    public static <T> PaginatedResponse<T> of(List<T> content, PageMetadata page) {
        return PaginatedResponse.<T>builder()
                .content(content)
                .page(page)
                .build();
    }
    
    /**
     * 빈 페이지네이션 응답 생성 헬퍼 메서드
     */
    public static <T> PaginatedResponse<T> empty(int pageNumber, int pageSize) {
        PageMetadata page = PageMetadata.builder()
                .number(pageNumber)
                .size(pageSize)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .hasNext(false)
                .hasPrevious(false)
                .build();
        
        return PaginatedResponse.<T>builder()
                .content(List.of())
                .page(page)
                .build();
    }
    
    /**
     * Spring Data Page 객체에서 변환하는 헬퍼 메서드
     */
    public static <T> PaginatedResponse<T> fromSpringDataPage(org.springframework.data.domain.Page<T> springPage) {
        PageMetadata page = PageMetadata.builder()
                .number(springPage.getNumber())
                .size(springPage.getSize())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .first(springPage.isFirst())
                .last(springPage.isLast())
                .hasNext(springPage.hasNext())
                .hasPrevious(springPage.hasPrevious())
                .build();
        
        return PaginatedResponse.<T>builder()
                .content(springPage.getContent())
                .page(page)
                .build();
    }
}