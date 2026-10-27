/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Read-only, bounded PLC observation, shared by all hosts through BatteryPresence. */
final class ColorOsCharge {
    static final String PLC_PATH = "/sys/class/oplus_chg/common/plc";
    static final long POLL_MILLIS = 1000L;
    private static final int MAX_BYTES = 64;

    interface Reader { String read(String path) throws IOException; }

    static final class Sample {
        final DuoPolicy.Charge charge;
        final Boolean bypass;

        Sample(DuoPolicy.Charge charge, Boolean bypass) {
            this.charge = charge;
            this.bypass = bypass;
        }
    }

    private final Reader reader;
    private long nextPoll = Long.MIN_VALUE;
    private Boolean bypass;

    ColorOsCharge() { this(ColorOsCharge::readNode); }
    ColorOsCharge(Reader reader) { this.reader = reader; }

    synchronized Sample sample(int status, int plugged, long now) {
        if ((plugged & 3) == 0) {
            bypass = null;
            nextPoll = Long.MIN_VALUE;
        } else if (nextPoll == Long.MIN_VALUE || now >= nextPoll) {
            nextPoll = now + POLL_MILLIS;
            // A failed observation replaces the previous value: never retain a stale grey state.
            try { bypass = parse(reader.read(PLC_PATH)); }
            catch (IOException | RuntimeException unavailable) { bypass = null; }
        }
        return new Sample(DuoPolicy.charge(status, plugged, bypass), bypass);
    }

    /** OnePlus 13 / ColorOS 17 measured values: enabled=status=3, disabled=status=2. */
    static Boolean parse(String text) {
        if (text == null || text.length() > MAX_BYTES) return null;
        String value = text.trim();
        if ("status=3".equals(value)) return Boolean.TRUE;
        if ("status=2".equals(value)) return Boolean.FALSE;
        return null;
    }

    private static String readNode(String path) throws IOException {
        try (InputStream stream = Files.newInputStream(Paths.get(path))) {
            byte[] data = new byte[MAX_BYTES];
            int size = 0;
            while (size < data.length) {
                int count = stream.read(data, size, data.length - size);
                if (count < 0) break;
                if (count == 0) return null;
                size += count;
            }
            if (size == data.length && stream.read() >= 0) return null;
            return new String(data, 0, size, StandardCharsets.UTF_8);
        }
    }
}
