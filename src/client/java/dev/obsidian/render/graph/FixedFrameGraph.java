package dev.obsidian.render.graph;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Fixed-capacity pass/dependency metadata for Obsidian-owned GPU work.
 *
 * <p>Passes are defined during initialization. Executing the graph only
 * mutates primitive arrays and bit masks; it does not allocate graph nodes on
 * the render-thread hot path.</p>
 */
public final class FixedFrameGraph {
    public static final int MAX_PASSES = 16;

    private final String[] names = new String[MAX_PASSES];
    private final long[] dependencyMasks = new long[MAX_PASSES];
    private final long[] cpuBeginNs = new long[MAX_PASSES];
    private final long[] lastCpuNs = new long[MAX_PASSES];
    private final long[] totalCpuNs = new long[MAX_PASSES];
    private final long[] executions = new long[MAX_PASSES];

    private int passCount;
    private int activePass = -1;
    private long definedMask;
    private long executedMask;
    private boolean executing;

    public void definePass(int index, String name, long dependencyMask) {
        RenderSystem.assertOnRenderThread();
        if (executing) {
            throw new IllegalStateException("Cannot define frame-graph passes while executing");
        }
        if (index < 0 || index >= MAX_PASSES) {
            throw new IndexOutOfBoundsException("Frame-graph pass index " + index + " is out of range");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Frame-graph pass name is required");
        }
        long bit = 1L << index;
        if ((definedMask & bit) != 0L) {
            throw new IllegalStateException("Frame-graph pass " + index + " is already defined");
        }
        if ((dependencyMask & bit) != 0L) {
            throw new IllegalArgumentException("Frame-graph pass cannot depend on itself");
        }
        long unknownDependencies = dependencyMask & ~definedMask;
        if (unknownDependencies != 0L) {
            throw new IllegalArgumentException(
                    "Frame-graph dependencies must reference passes defined earlier: mask="
                            + Long.toUnsignedString(unknownDependencies));
        }

        names[index] = name;
        dependencyMasks[index] = dependencyMask;
        definedMask |= bit;
        if (index + 1 > passCount) {
            passCount = index + 1;
        }
    }

    public void beginExecution() {
        RenderSystem.assertOnRenderThread();
        if (executing) {
            throw new IllegalStateException("Frame graph is already executing");
        }
        if (definedMask == 0L) {
            throw new IllegalStateException("Frame graph has no defined passes");
        }
        executing = true;
        activePass = -1;
        executedMask = 0L;
    }

    public void beginPass(int index) {
        RenderSystem.assertOnRenderThread();
        ensureExecuting();
        if (activePass != -1) {
            throw new IllegalStateException("Frame-graph pass " + activePass + " is still active");
        }
        long bit = passBit(index);
        if ((executedMask & bit) != 0L) {
            throw new IllegalStateException("Frame-graph pass " + index + " already executed");
        }
        long missing = dependencyMasks[index] & ~executedMask;
        if (missing != 0L) {
            throw new IllegalStateException(
                    "Frame-graph pass " + index + " started before dependencies completed: mask="
                            + Long.toUnsignedString(missing));
        }
        activePass = index;
        cpuBeginNs[index] = System.nanoTime();
    }

    public void endPass(int index) {
        RenderSystem.assertOnRenderThread();
        ensureExecuting();
        if (activePass != index) {
            throw new IllegalStateException(
                    "Ending frame-graph pass " + index + " while active pass is " + activePass);
        }
        long elapsed = System.nanoTime() - cpuBeginNs[index];
        if (elapsed < 0L) {
            elapsed = 0L;
        }
        lastCpuNs[index] = elapsed;
        totalCpuNs[index] += elapsed;
        executions[index]++;
        executedMask |= 1L << index;
        activePass = -1;
    }

    public void endExecution() {
        RenderSystem.assertOnRenderThread();
        ensureExecuting();
        if (activePass != -1) {
            throw new IllegalStateException("Cannot finish frame graph while a pass is active");
        }
        if ((executedMask & definedMask) != definedMask) {
            throw new IllegalStateException(
                    "Frame graph finished with unexecuted passes: mask="
                            + Long.toUnsignedString(definedMask & ~executedMask));
        }
        executing = false;
    }

    public void abortExecution() {
        RenderSystem.assertOnRenderThread();
        executing = false;
        activePass = -1;
        executedMask = 0L;
    }

    public int passCount() {
        return passCount;
    }

    public String passName(int index) {
        passBit(index);
        return names[index];
    }

    public long dependencyMask(int index) {
        passBit(index);
        return dependencyMasks[index];
    }

    public long lastCpuNs(int index) {
        passBit(index);
        return lastCpuNs[index];
    }

    public long totalCpuNs(int index) {
        passBit(index);
        return totalCpuNs[index];
    }

    public long executions(int index) {
        passBit(index);
        return executions[index];
    }

    public long executedMask() {
        return executedMask;
    }

    private long passBit(int index) {
        if (index < 0 || index >= MAX_PASSES) {
            throw new IndexOutOfBoundsException("Frame-graph pass index " + index + " is out of range");
        }
        long bit = 1L << index;
        if ((definedMask & bit) == 0L) {
            throw new IllegalStateException("Frame-graph pass " + index + " is not defined");
        }
        return bit;
    }

    private void ensureExecuting() {
        if (!executing) {
            throw new IllegalStateException("Frame graph is not executing");
        }
    }
}
