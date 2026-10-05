package dev.obsidian.render.visibility;

public final class SectionFrustumClassifier {
    public static final int CULLED = -1;
    public static final int AMBIGUOUS = 0;
    public static final int VISIBLE = 1;

    private SectionFrustumClassifier() {}

    public static int classifySection(
            int sectionX, int sectionY, int sectionZ,
            int cameraSectionX, int cameraSectionY, int cameraSectionZ,
            float cameraLocalX, float cameraLocalY, float cameraLocalZ,
            float[] planes, float epsilon) {
        float minX = (sectionX - cameraSectionX) * 16.0f - cameraLocalX;
        float minY = (sectionY - cameraSectionY) * 16.0f - cameraLocalY;
        float minZ = (sectionZ - cameraSectionZ) * 16.0f - cameraLocalZ;
        float maxX = minX + 16.0f;
        float maxY = minY + 16.0f;
        float maxZ = minZ + 16.0f;
        boolean ambiguous = false;

        for (int i = 0; i < 6; i++) {
            int p = i * 4;
            float a = planes[p];
            float b = planes[p + 1];
            float c = planes[p + 2];
            float d = planes[p + 3];
            float x = a >= 0.0f ? maxX : minX;
            float y = b >= 0.0f ? maxY : minY;
            float z = c >= 0.0f ? maxZ : minZ;
            float maxDistance = a * x + b * y + c * z + d;
            if (maxDistance < -epsilon) return CULLED;
            if (maxDistance <= epsilon) ambiguous = true;
        }
        return ambiguous ? AMBIGUOUS : VISIBLE;
    }
}
