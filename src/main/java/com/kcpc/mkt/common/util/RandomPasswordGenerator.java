package com.kcpc.mkt.common.util;

import java.security.SecureRandom;

/**
 * Generates a random initial password for CSV-imported users (User CSV Import spec: passwords are
 * never read from the CSV - this app has no password-reset/email-delivery mechanism, so the
 * generated value is shown to the CEO exactly once on the Import Result Summary screen so it can be
 * handed to the employee out-of-band, then discarded - never logged, never persisted anywhere
 * except as the bcrypt hash {@code UserAdminService#createUser} already produces for any password).
 */
public final class RandomPasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // no I/O - avoids visual ambiguity
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz"; // no l
    private static final String DIGITS = "23456789"; // no 0/1
    private static final String SYMBOLS = "!@#$%&*";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final int LENGTH = 14;

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomPasswordGenerator() {
    }

    /** Always contains at least one uppercase, lowercase, digit, and symbol character. */
    public static String generate() {
        char[] password = new char[LENGTH];
        password[0] = UPPER.charAt(RANDOM.nextInt(UPPER.length()));
        password[1] = LOWER.charAt(RANDOM.nextInt(LOWER.length()));
        password[2] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
        password[3] = SYMBOLS.charAt(RANDOM.nextInt(SYMBOLS.length()));
        for (int i = 4; i < LENGTH; i++) {
            password[i] = ALL.charAt(RANDOM.nextInt(ALL.length()));
        }
        // Shuffle so the guaranteed-category characters aren't always in positions 0-3.
        for (int i = password.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = password[i];
            password[i] = password[j];
            password[j] = tmp;
        }
        return new String(password);
    }
}
