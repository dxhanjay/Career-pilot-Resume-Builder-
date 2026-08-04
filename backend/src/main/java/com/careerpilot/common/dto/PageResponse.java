package com.careerpilot.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Pagination envelope returned by every paged endpoint.
 *
 * <p><strong>Why this exists instead of returning Spring's {@code Page}.</strong>
 * Serialising {@code Page} directly leaks Spring Data's internal structure into
 * the public API — including a nested {@code pageable} object and a {@code sort}
 * block that most clients ignore. That shape has changed between Spring Data
 * versions, which means a framework upgrade becomes a breaking API change for
 * every consumer. Spring itself warns about this at startup.
 *
 * <p>Owning the DTO makes the contract ours: the API changes when we decide it
 * changes, not when a transitive dependency does.
 *
 * @param <T>           the element type
 * @param content       the page's elements
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total matching rows across all pages
 * @param totalPages    total number of pages
 * @param last          whether this is the final page
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    /**
     * Wraps a Spring Data {@link Page} whose elements are already DTOs.
     *
     * @param page the source page
     * @param <T>  element type
     * @return an API-owned page envelope
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    /**
     * Wraps a {@link Page} of entities, mapping each element to a DTO.
     *
     * <p>This overload exists so that the mapping happens here rather than in
     * the controller. NFR-SEC-05 forbids serialising entities; keeping the
     * mapper in the signature makes forgetting it a compile error rather than a
     * data leak.
     *
     * @param page   the source page of entities
     * @param mapper entity-to-DTO function
     * @param <E>    entity type
     * @param <T>    DTO type
     * @return an API-owned page envelope of DTOs
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
