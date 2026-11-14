package com.rideshare.common.util;

import com.rideshare.common.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Guards list endpoints against unbounded page sizes and sorting on arbitrary/internal properties.
 */
public final class PageableUtils {

    public static final int MAX_PAGE_SIZE = 100;

    private PageableUtils() {
    }

    public static Pageable sanitize(Pageable pageable, Set<String> allowedSortProperties, Sort defaultSort) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        int page = Math.max(pageable.getPageNumber(), 0);
        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = defaultSort;
        } else {
            for (Sort.Order order : sort) {
                if (!allowedSortProperties.contains(order.getProperty())) {
                    throw new BadRequestException("Sorting by '" + order.getProperty()
                            + "' is not supported. Allowed: " + allowedSortProperties);
                }
            }
        }
        return PageRequest.of(page, size, sort);
    }
}
