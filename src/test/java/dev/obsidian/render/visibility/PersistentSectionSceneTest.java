package dev.obsidian.render.visibility;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PersistentSectionSceneTest {
    @Test
    void reusesFreedSlotWithFreshIdentityAndHonorsCapacity() {
        PersistentSectionScene scene = new PersistentSectionScene(2);
        assertTrue(scene.add(1, 2, 3));
        int firstSlot = scene.sectionSlot(1, 2, 3);
        int firstIdentity = scene.identity(firstSlot);
        assertTrue(scene.add(-4, 5, 6));
        assertFalse(scene.add(7, 8, 9));
        assertEquals(1, scene.capacityFailures());
        assertTrue(scene.remove(1, 2, 3));
        assertTrue(scene.add(7, 8, 9));
        int reused = scene.sectionSlot(7, 8, 9);
        assertEquals(firstSlot, reused);
        assertNotEquals(firstIdentity, scene.identity(reused));
        assertEquals(1, scene.slotReuses());
    }
}
