package dev.obsidian.render.visibility;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PersistentColumnHierarchyTest {
    @Test
    void tracksMembershipBoundsCapacityAndSlotReuse() {
        PersistentColumnHierarchy h = new PersistentColumnHierarchy(1, -4, 4);
        assertTrue(h.addSection(10, -2, 20));
        int slot = h.columnSlot(10, 20);
        int firstIdentity = h.identity(slot);
        assertTrue(h.addSection(10, 0, 20));
        assertTrue(h.addSection(10, 3, 20));
        assertEquals(-2, h.minLiveSectionY(slot));
        assertEquals(3, h.maxLiveSectionY(slot));
        assertTrue(h.auditColumn(slot));
        assertFalse(h.addSection(11, 0, 20));
        assertTrue(h.removeSection(10, -2, 20));
        assertEquals(0, h.minLiveSectionY(slot));
        assertTrue(h.removeSection(10, 3, 20));
        assertTrue(h.removeSection(10, 0, 20));
        assertTrue(h.addSection(11, 1, 21));
        int reused = h.columnSlot(11, 21);
        assertEquals(slot, reused);
        assertNotEquals(firstIdentity, h.identity(reused));
        assertEquals(1, h.slotReuses());
    }
}
