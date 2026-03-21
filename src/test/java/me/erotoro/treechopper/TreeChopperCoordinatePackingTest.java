package me.erotoro.treechopper;

import me.erotoro.treechopper.util.CoordinatePacker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TreeChopperCoordinatePackingTest {

    @Test
    void packsAndUnpacksSignedCoordinates() {
        assertPackedRoundTrip(0, 64, 0);
        assertPackedRoundTrip(123, 255, -456);
        assertPackedRoundTrip(-33_000_000, -64, 33_000_000);
        assertPackedRoundTrip(29_999_984, 319, -29_999_984);
    }

    private static void assertPackedRoundTrip(int x, int y, int z) {
        long packed = CoordinatePacker.pack(x, y, z);
        assertEquals(x, CoordinatePacker.unpackX(packed));
        assertEquals(y, CoordinatePacker.unpackY(packed));
        assertEquals(z, CoordinatePacker.unpackZ(packed));
    }
}
