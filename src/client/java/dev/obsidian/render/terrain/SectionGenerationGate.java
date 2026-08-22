package dev.obsidian.render.terrain;

/** Renderer-owned monotonically increasing section generation gate. */
public final class SectionGenerationGate {
    private long currentGeneration = 1L;
    private long installedGeneration;
    private long advances;
    private long acceptedInstalls;
    private long rejectedInstalls;

    public long currentGeneration() { return currentGeneration; }
    public long installedGeneration() { return installedGeneration; }
    public long advances() { return advances; }
    public long acceptedInstalls() { return acceptedInstalls; }
    public long rejectedInstalls() { return rejectedInstalls; }

    public long advance() {
        if (currentGeneration == Long.MAX_VALUE) {
            throw new IllegalStateException("P2.6 section generation exhausted");
        }
        currentGeneration++;
        advances++;
        return currentGeneration;
    }

    public boolean tryInstall(long generation) {
        if (generation != currentGeneration) {
            rejectedInstalls++;
            return false;
        }
        installedGeneration = generation;
        acceptedInstalls++;
        return true;
    }

    public static boolean staleSelfTest() {
        SectionGenerationGate gate = new SectionGenerationGate();
        long stale = gate.currentGeneration();
        gate.advance();
        return !gate.tryInstall(stale)
                && gate.rejectedInstalls() == 1L
                && gate.installedGeneration() == 0L;
    }
}
