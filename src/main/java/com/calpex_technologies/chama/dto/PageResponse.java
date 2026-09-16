package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic paginated response wrapper.
 * Used for all list endpoints that support pagination.
 *
 * @param <T> The type of items in the page
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    
    /** The items for the current page */
    private List<T> content;
    
    /** Current page number (0-indexed) */
    private int page;
    
    /** Number of items per page */
    private int size;
    
    /** Total number of items across all pages */
    private long totalElements;
    
    /** Total number of pages */
    private int totalPages;
    
    /** Whether this is the first page */
    private boolean first;
    
    /** Whether this is the last page */
    private boolean last;
    
    /**
     * Create a PageResponse from Spring Data's Page object.
     */
    public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
    
    /**
     * Create a PageResponse from a list with manual pagination info.
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return PageResponse.<T>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .build();
    }
}
