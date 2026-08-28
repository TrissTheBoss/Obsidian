package dev.obsidian.render.terrain;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/** Public Blaze3D vertex layout for P3.4 dev11 repeat-aware merged quads. */
public final class RepeatAwareGreedyRenderFormat {
    public static final String REPEAT_BASIS0 = "RepeatBasis0";
    public static final String REPEAT_BASIS1 = "RepeatBasis1";

    public static final VertexFormat MERGED = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .addAttribute(REPEAT_BASIS0, GpuFormat.RGBA32_FLOAT)
            .addAttribute(REPEAT_BASIS1, GpuFormat.RGBA32_FLOAT)
            .build();

    static {
        if (MERGED.getVertexSize() != RepeatAwareGreedyMesh.MERGED_BYTES_PER_VERTEX) {
            throw new IllegalStateException("Dev11 merged vertex format byte-size mismatch");
        }
    }

    private RepeatAwareGreedyRenderFormat() { }
}
