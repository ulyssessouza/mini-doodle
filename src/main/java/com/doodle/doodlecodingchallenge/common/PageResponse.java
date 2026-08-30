package com.doodle.doodlecodingchallenge.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * The stable paginated envelope used by all list endpoints. Keeps Spring Data
 * types out of the API contract (and out of the OpenAPI schemas).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages());
    }
}
