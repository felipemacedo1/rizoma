package io.github.felipemacedo1.rizoma.core;

/** Fixed 1,024-register HyperLogLog used internally for bounded cardinality. */
final class HyperLogLogSketch {
    static final int PRECISION = 10;
    static final int REGISTERS = 1 << PRECISION;
    static final double EXPECTED_RELATIVE_ERROR = 1.04 / Math.sqrt(REGISTERS);
    private final byte[] registers = new byte[REGISTERS];

    void add(String value) {
        long hash = mix64(fnv1a(value));
        int index = (int) (hash >>> (64 - PRECISION));
        long remaining = hash << PRECISION;
        int rank = Math.min(64 - PRECISION + 1, Long.numberOfLeadingZeros(remaining) + 1);
        if (rank > registers[index]) registers[index] = (byte) rank;
    }

    long estimate() {
        double sum = 0;
        int zeros = 0;
        for (byte register : registers) {
            sum += Math.scalb(1.0, -Byte.toUnsignedInt(register));
            if (register == 0) zeros++;
        }
        double alpha = 0.7213 / (1 + 1.079 / REGISTERS);
        double raw = alpha * REGISTERS * REGISTERS / sum;
        double corrected = raw <= 2.5 * REGISTERS && zeros > 0
                ? REGISTERS * Math.log((double) REGISTERS / zeros) : raw;
        return Math.max(0, Math.round(corrected));
    }

    private static long fnv1a(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }
}
