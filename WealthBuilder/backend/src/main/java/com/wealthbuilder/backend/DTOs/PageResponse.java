package com.wealthbuilder.backend.DTOs;

import lombok.Value;
import org.springframework.data.domain.Page;

import java.util.List;


/**
 * Stable, explicit pagination envelope for the SPA — preferred over serializing Spring Data's
 * {@code Page} directly, whose JSON shape is not a guaranteed contract.
 */
@Value
public class PageResponse<T> {

    List<T> content;

    int page;

    int size;

    long totalElements;

    int totalPages;

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
