from pathlib import Path

path = Path(__file__).resolve().parents[1] / "src/client/java/dev/obsidian/render/terrain/PartialRemeshShadowResult.java"
text = path.read_text(encoding="utf-8")

def replace_once(old, new):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected one match, found {count}: {old!r}")
    text = text.replace(old, new, 1)

replace_once("package dev.obsidian.render.terrain;\n\nimport java.util.Arrays;\n",
             "package dev.obsidian.render.terrain;\n\nimport net.minecraft.core.Direction;\n\nimport java.util.Arrays;\n")
replace_once("public final class PartialRemeshShadowResult {\n",
             "public final class PartialRemeshShadowResult {\n    private static final Direction[] DIRECTIONS = Direction.values();\n")
replace_once("throw new NullPointerException(\"dev16 shadow inputs\");",
             "throw new NullPointerException(\"dev17 shadow inputs\");")
replace_once("                                || baked.direction(source) != (byte) direction\n",
             "                                || binaryDirection(baked.direction(source)) != direction\n")
replace_once(
    "    private static int sourceSlice(SectionBakedQuadSnapshot baked, int source) {\n"
    "        return ((baked.sourceBlock(source) >>> 4) & 0xF) >>> 2;\n"
    "    }\n",
    "    private static int sourceSlice(SectionBakedQuadSnapshot baked, int source) {\n"
    "        return ((baked.sourceBlock(source) >>> 4) & 0xF) >>> 2;\n"
    "    }\n"
    "    private static int binaryDirection(byte directionOrdinal) {\n"
    "        int ordinal = Byte.toUnsignedInt(directionOrdinal);\n"
    "        if (ordinal >= DIRECTIONS.length) return -1;\n"
    "        return switch (DIRECTIONS[ordinal]) {\n"
    "            case WEST -> BinarySectionVisibility.WEST;\n"
    "            case EAST -> BinarySectionVisibility.EAST;\n"
    "            case DOWN -> BinarySectionVisibility.DOWN;\n"
    "            case UP -> BinarySectionVisibility.UP;\n"
    "            case NORTH -> BinarySectionVisibility.NORTH;\n"
    "            case SOUTH -> BinarySectionVisibility.SOUTH;\n"
    "        };\n"
    "    }\n")
replace_once(
    "                && !permanentReferenceContractSatisfied(false, true, true)\n"
    "                && \"optimized-without-reference\".equals(failureName(FAILURE_OPTIMIZED_WITHOUT_REFERENCE));\n",
    "                && !permanentReferenceContractSatisfied(false, true, true)\n"
    "                && binaryDirection((byte) Direction.WEST.ordinal()) == BinarySectionVisibility.WEST\n"
    "                && binaryDirection((byte) Direction.EAST.ordinal()) == BinarySectionVisibility.EAST\n"
    "                && binaryDirection((byte) Direction.DOWN.ordinal()) == BinarySectionVisibility.DOWN\n"
    "                && binaryDirection((byte) Direction.UP.ordinal()) == BinarySectionVisibility.UP\n"
    "                && binaryDirection((byte) Direction.NORTH.ordinal()) == BinarySectionVisibility.NORTH\n"
    "                && binaryDirection((byte) Direction.SOUTH.ordinal()) == BinarySectionVisibility.SOUTH\n"
    "                && \"optimized-without-reference\".equals(failureName(FAILURE_OPTIMIZED_WITHOUT_REFERENCE));\n")

path.write_text(text, encoding="utf-8")
print("dev17 direction correction applied")
