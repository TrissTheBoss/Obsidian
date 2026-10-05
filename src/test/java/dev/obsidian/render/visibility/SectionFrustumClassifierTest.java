package dev.obsidian.render.visibility;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class SectionFrustumClassifierTest {
    @Test
    void classifiesVisibleBoundaryAndCulledSections() {
        float e = 32.0f;
        float[] p = {
                1,0,0,e, -1,0,0,e,
                0,1,0,e, 0,-1,0,e,
                0,0,1,e, 0,0,-1,e
        };
        assertEquals(SectionFrustumClassifier.VISIBLE, classify(0, p));
        assertEquals(SectionFrustumClassifier.AMBIGUOUS, classify(2, p));
        assertEquals(SectionFrustumClassifier.CULLED, classify(3, p));
        assertEquals(SectionFrustumClassifier.CULLED, classify(-4, p));
    }

    private static int classify(int sectionX, float[] planes) {
        return SectionFrustumClassifier.classifySection(
                sectionX, 0, 0, 0, 0, 0,
                0, 0, 0, planes, 1.0e-3f);
    }
}
