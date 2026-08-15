package com.kcpc.mkt.identity.domain;

/**
 * The exactly-3 internal access classes (ERD-TBL-003 base_roles). These are the actual
 * authorization boundary; a {@link BusinessRole} is the visible organizational designation
 * that resolves to exactly one of these.
 */
public enum AccessClass {
    CEO_OWNER,
    MARKETING_MANAGER,
    EMPLOYEE
}
