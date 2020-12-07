package com.github.gekoh.yagen.util;

import org.junit.Test;

import java.sql.Timestamp;

import static org.junit.Assert.*;

public class NanoAwareTimestampUtilTest {


    @Test
    public void testConsecutiveInvocations() {
        for (int i = 0; i < 1000; i++) {
            Timestamp timestamp1 = NanoAwareTimestampUtil.getCurrentTimestamp();
            String timestamp1Str = timestamp1.toString();
            Timestamp timestamp2 = NanoAwareTimestampUtil.getCurrentTimestamp();
            String timestamp2Str = timestamp2.toString();
            if (!timestamp2.after(timestamp1)) {
                System.err.println(timestamp2Str + " is not after " + timestamp1Str);
            }
            assertTrue(timestamp2.after(timestamp1));
        }
    }

    @Test
    public void testValue() {
        for (int i = 0; i < 1000; i++) {
            Timestamp timestampMillis = new Timestamp(System.currentTimeMillis());
            Timestamp timestampNanos = NanoAwareTimestampUtil.getCurrentTimestamp();
            assertTrue(timestampNanos.getTime() - timestampMillis.getTime() < 100);
        }
    }
}
