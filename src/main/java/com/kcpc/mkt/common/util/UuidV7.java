package com.kcpc.mkt.common.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates application-side UUIDv7 (RFC 9562) surrogate keys, as required by the ERD
 * ("Application UUIDv7" default) for every physical table primary key in this schema.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        long timestampMillis = System.currentTimeMillis();
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);

        long mostSigBits = (timestampMillis & 0xFFFFFFFFFFFFL) << 16;
        mostSigBits |= 0x7000L; // version 7
        mostSigBits |= (randomBytes[0] & 0x0F) << 8;
        mostSigBits |= (randomBytes[1] & 0xFF);

        long leastSigBits = 0L;
        leastSigBits |= 0x8000000000000000L; // variant 10xx
        leastSigBits |= ((long) (randomBytes[2] & 0x3F)) << 56;
        for (int i = 3; i < 10; i++) {
            leastSigBits |= ((long) (randomBytes[i] & 0xFF)) << (8 * (9 - i));
        }

        return new UUID(mostSigBits, leastSigBits);
    }
}
