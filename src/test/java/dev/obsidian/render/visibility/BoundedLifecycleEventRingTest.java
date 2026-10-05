package dev.obsidian.render.visibility;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BoundedLifecycleEventRingTest {
    @Test
    void overflowIsExplicitAndUnreadEventsAreNotOverwritten() {
        BoundedLifecycleEventRing ring = new BoundedLifecycleEventRing(3);
        assertTrue(ring.record((byte) 1, 11L));
        assertTrue(ring.record((byte) 2, 22L));
        assertTrue(ring.record((byte) 3, 33L));
        assertFalse(ring.record((byte) 4, 44L));
        assertEquals(4, ring.recordedEvents());
        assertEquals(1, ring.overflowEvents());
        assertTrue(ring.consumeOverflowed());
        assertFalse(ring.consumeOverflowed());
        byte[] types = new byte[3];
        long[] keys = new long[3];
        assertEquals(3, ring.drainTo(types, keys, 3));
        assertArrayEquals(new byte[] {1, 2, 3}, types);
        assertArrayEquals(new long[] {11L, 22L, 33L}, keys);
    }

    @Test
    void drainBudgetAndWraparoundRemainCoherent() {
        BoundedLifecycleEventRing ring = new BoundedLifecycleEventRing(3);
        ring.record((byte) 1, 1L);
        ring.record((byte) 2, 2L);
        assertEquals(1, ring.drainTo(new byte[1], new long[1], 1));
        ring.record((byte) 3, 3L);
        ring.record((byte) 4, 4L);
        assertEquals(3, ring.drainTo(new byte[3], new long[3], 3));
        assertEquals(0, ring.size());
    }
}
