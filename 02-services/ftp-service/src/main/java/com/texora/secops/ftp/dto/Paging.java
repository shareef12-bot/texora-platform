package com.texora.secops.ftp.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Bounds page parameters so a caller cannot request an unbounded result set. */
public final class Paging {

    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), MAX_SIZE);
        return PageRequest.of(p, s, sort);
    }
}
