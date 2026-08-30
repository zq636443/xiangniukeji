package com.xniu.rental.externalorder.model;

/**
 * The source of a verification amount used when calculating an external-order
 * renewal period.  INITIAL is deliberately scoped to the first rental period;
 * ORDER_EDIT starts a new amount timeline at the exact edit timestamp.
 */
public enum ExternalOrderVerificationRevisionType {
    INITIAL,
    ORDER_EDIT,
    BACKFILL
}
