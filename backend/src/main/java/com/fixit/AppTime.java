package com.fixit;

import java.time.ZoneId;

/**
 * Fix It runs on Indian Standard Time. Timestamps are stored as IST wall-clock values and every API timestamp carries
 * the explicit +05:30 offset, so clients never have to guess the zone.
 */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private AppTime() {
    }
}
