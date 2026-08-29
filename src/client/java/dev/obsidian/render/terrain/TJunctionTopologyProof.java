package dev.obsidian.render.terrain;

import java.util.Arrays;

/**
 * P3.6 immutable summary proof over the actual dev10-safe merged candidates
 * emitted by {@link RepeatAwareGreedyMesh}.
 *
 * <p>All topology is evaluated in exact section-local integer coordinates.
 * A strict T-junction exists when an emitted rectangle corner lies strictly
 * inside another emitted rectangle edge on the same facing direction and face
 * plane. The common directional 1/512 comparison offset is therefore irrelevant
 * to the integer incidence test and cannot introduce an epsilon decision.</p>
 */
public final class TJunctionTopologyProof {
    public static final int EDGE_COORD_SIZE = SectionSnapshot.INTERIOR_SIZE + 1;
    public static final int PLANES = SectionSnapshot.INTERIOR_SIZE;
    public static final int PLANE_DIRECTION_COUNT = BinarySectionVisibility.DIRECTION_COUNT * PLANES;
    public static final int GRID_ROWS = PLANE_DIRECTION_COUNT * EDGE_COORD_SIZE;

    private static final int EDGE_COORD_MASK = (1 << EDGE_COORD_SIZE) - 1;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Fixed primitive workspace intended to be retained and reused by one worker. */
    public static final class BuildScratch {
        private final int[] endpointRows = new int[GRID_ROWS];
        private final int[] strictInteriorRows = new int[GRID_ROWS];
        private final boolean[] seenCandidates = new boolean[RenderMergeCandidates.MAX_CANDIDATES];
        private final int[] junctionsByDirection = new int[BinarySectionVisibility.DIRECTION_COUNT];
        private long uses;
        private int highWaterRecords;

        private void begin(int candidateCount, int records) {
            uses++;
            highWaterRecords = Math.max(highWaterRecords, records);
            Arrays.fill(endpointRows, 0);
            Arrays.fill(strictInteriorRows, 0);
            Arrays.fill(seenCandidates, 0, candidateCount, false);
            Arrays.fill(junctionsByDirection, 0);
        }

        public long uses() { return uses; }
        public int highWaterRecords() { return highWaterRecords; }
        public int retainedScratchBytes() {
            return endpointRows.length * Integer.BYTES
                    + strictInteriorRows.length * Integer.BYTES
                    + seenCandidates.length
                    + junctionsByDirection.length * Integer.BYTES;
        }
    }

    private final long sourceCandidateFingerprint;
    private final long sourceTransportFingerprint;
    private final int emittedCandidates;
    private final int emittedEdges;
    private final int strictInteriorEdgeLatticePoints;
    private final int strictTJunctionPoints;
    private final int[] junctionsByDirection;
    private final int boundsChecks;
    private final int boundsMatches;
    private final int planeDirectionChecks;
    private final int planeDirectionMatches;
    private final int integerLatticeChecks;
    private final int integerLatticeMatches;
    private final long fingerprint;
    private final long buildTimeNs;

    private TJunctionTopologyProof(
            long sourceCandidateFingerprint,
            long sourceTransportFingerprint,
            int emittedCandidates,
            int emittedEdges,
            int strictInteriorEdgeLatticePoints,
            int strictTJunctionPoints,
            int[] junctionsByDirection,
            int boundsChecks,
            int boundsMatches,
            int planeDirectionChecks,
            int planeDirectionMatches,
            int integerLatticeChecks,
            int integerLatticeMatches,
            long fingerprint,
            long buildTimeNs) {
        this.sourceCandidateFingerprint = sourceCandidateFingerprint;
        this.sourceTransportFingerprint = sourceTransportFingerprint;
        this.emittedCandidates = emittedCandidates;
        this.emittedEdges = emittedEdges;
        this.strictInteriorEdgeLatticePoints = strictInteriorEdgeLatticePoints;
        this.strictTJunctionPoints = strictTJunctionPoints;
        this.junctionsByDirection = junctionsByDirection;
        this.boundsChecks = boundsChecks;
        this.boundsMatches = boundsMatches;
        this.planeDirectionChecks = planeDirectionChecks;
        this.planeDirectionMatches = planeDirectionMatches;
        this.integerLatticeChecks = integerLatticeChecks;
        this.integerLatticeMatches = integerLatticeMatches;
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static TJunctionTopologyProof build(
            RenderMergeCandidates candidates,
            RepeatAwareTransportProof transport) {
        return build(candidates, transport, new BuildScratch());
    }

    public static TJunctionTopologyProof build(
            RenderMergeCandidates candidates,
            RepeatAwareTransportProof transport,
            BuildScratch scratch) {
        if (candidates == null || transport == null || scratch == null) {
            throw new NullPointerException("candidates, transport and scratch are required");
        }
        if (transport.sourceCandidateFingerprint() != candidates.fingerprint()) {
            throw new IllegalArgumentException("P3.6 T-junction proof source identity mismatch");
        }

        long startNs = System.nanoTime();
        int records = transport.recordCount();
        scratch.begin(candidates.candidateCount(), records);

        int edges = 0;
        int boundsChecks = 0;
        int boundsMatches = 0;
        int planeChecks = 0;
        int planeMatches = 0;
        int latticeChecks = 0;
        int latticeMatches = 0;

        for (int record = 0; record < records; record++) {
            int candidate = transport.candidateIndex(record);
            if (candidate < 0 || candidate >= candidates.candidateCount() || scratch.seenCandidates[candidate]) {
                throw new IllegalStateException("P3.6 emitted candidate identity is missing or duplicated");
            }
            scratch.seenCandidates[candidate] = true;

            int packed = candidates.packedCandidate(candidate);
            int direction = RenderMergeCandidates.direction(packed);
            int plane = RenderMergeCandidates.plane(packed);
            int u0 = RenderMergeCandidates.u(packed);
            int v0 = RenderMergeCandidates.v(packed);
            int u1 = u0 + RenderMergeCandidates.width(packed);
            int v1 = v0 + RenderMergeCandidates.height(packed);

            planeChecks++;
            if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT
                    || plane < 0 || plane >= PLANES) {
                throw new IllegalStateException("P3.6 emitted candidate has invalid direction/plane");
            }
            planeMatches++;

            boundsChecks += 4;
            if (u0 < 0 || v0 < 0 || u1 > SectionSnapshot.INTERIOR_SIZE
                    || v1 > SectionSnapshot.INTERIOR_SIZE || u0 >= u1 || v0 >= v1) {
                throw new IllegalStateException("P3.6 emitted candidate edge is outside the 0..16 section lattice");
            }
            boundsMatches += 4;

            latticeChecks += 4;
            latticeMatches += 4;

            int row0 = rowIndex(direction, plane, v0);
            int row1 = rowIndex(direction, plane, v1);
            scratch.endpointRows[row0] |= bit(u0) | bit(u1);
            scratch.endpointRows[row1] |= bit(u0) | bit(u1);

            int horizontalInterior = strictMask(u0, u1);
            scratch.strictInteriorRows[row0] |= horizontalInterior;
            scratch.strictInteriorRows[row1] |= horizontalInterior;

            if (v1 - v0 > 1) {
                int verticalInteriorMask = bit(u0) | bit(u1);
                for (int v = v0 + 1; v < v1; v++) {
                    scratch.strictInteriorRows[rowIndex(direction, plane, v)] |= verticalInteriorMask;
                }
            }
            edges += 4;
        }

        int interiorPoints = 0;
        int junctionPoints = 0;
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            int directionJunctions = 0;
            for (int plane = 0; plane < PLANES; plane++) {
                for (int v = 0; v < EDGE_COORD_SIZE; v++) {
                    int row = rowIndex(direction, plane, v);
                    int interiors = scratch.strictInteriorRows[row] & EDGE_COORD_MASK;
                    int junctions = scratch.endpointRows[row] & interiors;
                    interiorPoints += Integer.bitCount(interiors);
                    int count = Integer.bitCount(junctions);
                    junctionPoints += count;
                    directionJunctions += count;
                }
            }
            scratch.junctionsByDirection[direction] = directionJunctions;
        }

        int[] retainedByDirection = Arrays.copyOf(
                scratch.junctionsByDirection, scratch.junctionsByDirection.length);

        long hash = FNV_OFFSET_BASIS;
        hash = hashLong(hash, candidates.fingerprint());
        hash = hashLong(hash, transport.fingerprint());
        hash = hashInt(hash, records);
        hash = hashInt(hash, edges);
        hash = hashInt(hash, interiorPoints);
        hash = hashInt(hash, junctionPoints);
        hash = hashInt(hash, boundsChecks);
        hash = hashInt(hash, boundsMatches);
        hash = hashInt(hash, planeChecks);
        hash = hashInt(hash, planeMatches);
        hash = hashInt(hash, latticeChecks);
        hash = hashInt(hash, latticeMatches);
        for (int direction = 0; direction < retainedByDirection.length; direction++) {
            hash = hashInt(hash, retainedByDirection[direction]);
        }

        TJunctionTopologyProof result = new TJunctionTopologyProof(
                candidates.fingerprint(), transport.fingerprint(), records, edges,
                interiorPoints, junctionPoints, retainedByDirection,
                boundsChecks, boundsMatches, planeChecks, planeMatches,
                latticeChecks, latticeMatches, hash, System.nanoTime() - startNs);
        result.validate();
        return result;
    }

    private void validate() {
        if (emittedCandidates < 0
                || emittedEdges != emittedCandidates * 4
                || strictInteriorEdgeLatticePoints < 0
                || strictTJunctionPoints < 0
                || strictTJunctionPoints > strictInteriorEdgeLatticePoints
                || boundsChecks != emittedCandidates * 4
                || boundsMatches != boundsChecks
                || planeDirectionChecks != emittedCandidates
                || planeDirectionMatches != planeDirectionChecks
                || integerLatticeChecks != emittedCandidates * 4
                || integerLatticeMatches != integerLatticeChecks) {
            throw new IllegalStateException("P3.6 T-junction proof accounting mismatch");
        }
        int directionSum = 0;
        for (int count : junctionsByDirection) {
            if (count < 0) throw new IllegalStateException("P3.6 negative directional T-junction count");
            directionSum += count;
        }
        if (directionSum != strictTJunctionPoints) {
            throw new IllegalStateException("P3.6 directional T-junction accounting mismatch");
        }
    }

    public boolean contentEquals(TJunctionTopologyProof other) {
        return other != null
                && sourceCandidateFingerprint == other.sourceCandidateFingerprint
                && sourceTransportFingerprint == other.sourceTransportFingerprint
                && emittedCandidates == other.emittedCandidates
                && emittedEdges == other.emittedEdges
                && strictInteriorEdgeLatticePoints == other.strictInteriorEdgeLatticePoints
                && strictTJunctionPoints == other.strictTJunctionPoints
                && boundsChecks == other.boundsChecks
                && boundsMatches == other.boundsMatches
                && planeDirectionChecks == other.planeDirectionChecks
                && planeDirectionMatches == other.planeDirectionMatches
                && integerLatticeChecks == other.integerLatticeChecks
                && integerLatticeMatches == other.integerLatticeMatches
                && fingerprint == other.fingerprint
                && Arrays.equals(junctionsByDirection, other.junctionsByDirection);
    }

    public long sourceCandidateFingerprint() { return sourceCandidateFingerprint; }
    public long sourceTransportFingerprint() { return sourceTransportFingerprint; }
    public int emittedCandidates() { return emittedCandidates; }
    public int emittedEdges() { return emittedEdges; }
    public int strictInteriorEdgeLatticePoints() { return strictInteriorEdgeLatticePoints; }
    public int strictTJunctionPoints() { return strictTJunctionPoints; }
    public int boundsChecks() { return boundsChecks; }
    public int boundsMatches() { return boundsMatches; }
    public int planeDirectionChecks() { return planeDirectionChecks; }
    public int planeDirectionMatches() { return planeDirectionMatches; }
    public int integerLatticeChecks() { return integerLatticeChecks; }
    public int integerLatticeMatches() { return integerLatticeMatches; }
    public int junctionsByDirection(int direction) {
        if (direction < 0 || direction >= junctionsByDirection.length) throw new IndexOutOfBoundsException(direction);
        return junctionsByDirection[direction];
    }
    public long fingerprint() { return fingerprint; }
    public long buildTimeNs() { return buildTimeNs; }

    private static int rowIndex(int direction, int plane, int v) {
        return ((direction * PLANES + plane) * EDGE_COORD_SIZE) + v;
    }

    private static int bit(int coordinate) {
        if (coordinate < 0 || coordinate >= EDGE_COORD_SIZE) {
            throw new IllegalArgumentException("P3.6 edge coordinate outside 0..16: " + coordinate);
        }
        return 1 << coordinate;
    }

    private static int strictMask(int start, int end) {
        if (end - start <= 1) return 0;
        int width = end - start - 1;
        return (((1 << width) - 1) << (start + 1)) & EDGE_COORD_MASK;
    }

    private static long hashInt(long hash, int value) {
        hash ^= Integer.toUnsignedLong(value);
        return hash * FNV_PRIME;
    }

    private static long hashLong(long hash, long value) {
        hash = hashInt(hash, (int) value);
        return hashInt(hash, (int) (value >>> 32));
    }
}
