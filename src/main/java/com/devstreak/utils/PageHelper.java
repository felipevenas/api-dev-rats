package com.devstreak.utils;

import com.devstreak.dto.PageResponse;
import org.springframework.data.domain.Page;

public final class PageHelper {

    private PageHelper() {
    }

    public static <T> PageResponse<T> toResponse(Page<T> page) {
        return PageResponse.from(page);
    }
}
