package com.kcpc.mkt.reporting.dto;

import java.util.UUID;

/**
 * Spring Data JPA interface projection for a "count active tasks, grouped by user" query - see
 * {@code AssigneeWorkloadCountService}. One row per user who has at least one currently-active
 * assignment; a user with zero active tasks simply has no row (callers default the missing lookup
 * to zero rather than the query producing a zero-count row for every candidate).
 */
public interface UserActiveTaskCount {

    UUID getUserId();

    long getActiveCount();
}
