package me.erotoro.treechopper.util;

public final class CoordinatePacker {

    private CoordinatePacker() {
    }

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
    }

    public static int unpackX(long packed) {
        int raw = (int) (packed >> 38) & 0x3FFFFFF;
        return (raw & 0x2000000) != 0 ? raw | ~0x3FFFFFF : raw;
    }

    public static int unpackY(long packed) {
        int raw = (int) (packed >> 26) & 0xFFF;
        return (raw & 0x800) != 0 ? raw | ~0xFFF : raw;
    }

    public static int unpackZ(long packed) {
        int raw = (int) packed & 0x3FFFFFF;
        return (raw & 0x2000000) != 0 ? raw | ~0x3FFFFFF : raw;
    }
}
