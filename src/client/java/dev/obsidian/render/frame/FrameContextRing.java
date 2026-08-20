package dev.obsidian.render.frame;

/**
 * Fixed, allocation-free rotation of CPU frame metadata.
 *
 * <p>The ring size is intentionally small for Phase 1 scaffolding. It is not a
 * substitute for GPU completion tracking and must not be used to infer that a
 * resource is safe to reuse merely because its slot rotated around.</p>
 */
public final class FrameContextRing {
    public static final int DEFAULT_CONTEXT_COUNT = 3;

    private final FrameContext[] contexts;
    private int cursor = -1;

    public FrameContextRing() {
        this(DEFAULT_CONTEXT_COUNT);
    }

    public FrameContextRing(int contextCount) {
        if (contextCount < 2) {
            throw new IllegalArgumentException("Frame context count must be at least 2");
        }
        contexts = new FrameContext[contextCount];
        for (int i = 0; i < contextCount; i++) {
            contexts[i] = new FrameContext(i);
        }
    }

    public FrameContext begin(long serial, long beginNs) {
        int next = cursor + 1;
        if (next == contexts.length) {
            next = 0;
        }
        cursor = next;

        FrameContext context = contexts[next];
        context.begin(serial, beginNs);
        return context;
    }

    public int size() {
        return contexts.length;
    }
}
