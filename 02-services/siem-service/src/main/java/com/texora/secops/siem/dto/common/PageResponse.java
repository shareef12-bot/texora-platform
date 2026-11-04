package com.texora.secops.siem.dto.common;

import java.util.List;

public class PageResponse<T> {

    private List<T> items;
    private int page;
    private int pageSize;
    private long totalElements;
    private boolean degraded;

    public PageResponse() {
        // required for JSON deserialization
    }

    public PageResponse(List<T> items, int page, int pageSize, long totalElements, boolean degraded) {
        this.items = items;
        this.page = page;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.degraded = degraded;
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int pageSize, long totalElements) {
        return new PageResponse<>(items, page, pageSize, totalElements, false);
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

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    /** True when this result came back from a degraded search path (backend impaired) rather
     *  than a full query - see TenantFilteredSearchService. */
    public boolean isDegraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }
}
