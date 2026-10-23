package com.texora.secops.cgi.common;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;

/** Simple page envelope for list endpoints. */
public class PageResponse<T> {

    public static final int MAX_PAGE_SIZE = 200;

    private List<T> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public PageResponse() {
        // JSON
    }

    public PageResponse(List<T> items, int page, int size, long totalElements, int totalPages) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public static <E, T> PageResponse<T> of(Page<E> source, Function<E, T> mapper) {
        List<T> mapped = source.getContent().stream().map(mapper).toList();
        return new PageResponse<>(mapped, source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages());
    }

    /** Clamps client-supplied paging so a caller cannot request an unbounded page. */
    public static Pageable pageable(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
