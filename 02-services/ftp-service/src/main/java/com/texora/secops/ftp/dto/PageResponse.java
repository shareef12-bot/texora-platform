package com.texora.secops.ftp.dto;

import java.util.List;
import java.util.Objects;

/** Simple page envelope so Spring Data's Page type is never serialised directly. */
public class PageResponse<T> {

    private List<T> items;
    private int page;
    private int size;
    private long totalElements;

    public PageResponse() {
    }

    public PageResponse(List<T> items, int page, int size, long totalElements) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PageResponse)) {
            return false;
        }
        PageResponse<?> that = (PageResponse<?>) other;
        return page == that.page && size == that.size && totalElements == that.totalElements
                && Objects.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, page, size, totalElements);
    }

    @Override
    public String toString() {
        return "PageResponse{page=" + page + ", size=" + size + ", totalElements=" + totalElements + "}";
    }
}
