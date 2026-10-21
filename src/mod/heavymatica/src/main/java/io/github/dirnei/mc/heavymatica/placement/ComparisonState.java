package io.github.dirnei.mc.heavymatica.placement;

import java.util.Arrays;
import java.util.BitSet;

/**
 * The comparison result of one locked placement: a state per position, kept in placement-local (rotated) coordinates
 * with x fastest, then z, then y, plus the counters and dirty marks derived from it. Every change goes through
 * {@link #set}, which keeps progress, totals, the ghost mask (one bit per cell, set while the ghost shows) and the
 * dirty box sections up to date.
 */
public final class ComparisonState {

    public static final int SECTION = 16;

    private enum Section {
        PENDING,
        COMPARED,
        UNLOADED,
    }

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int sectionsX;
    private final int sectionsZ;
    private final byte[] cells;
    private final BitSet counted;
    private final int[] layerCounted;
    private final int[] layerMatching;
    private final int[] totals = new int[CellState.values().length];
    private final Section[] sections;
    private final byte[] mask;
    private final BitSet dirtyBoxSections = new BitSet();
    private int dirtyMaskFrom = Integer.MAX_VALUE;
    private int dirtyMaskTo = -1;
    private int pending;

    /** @param counted the positions that count toward progress, by index */
    public ComparisonState(int sizeX, int sizeY, int sizeZ, BitSet counted) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.sectionsX = Math.ceilDiv(sizeX, SECTION);
        this.sectionsZ = Math.ceilDiv(sizeZ, SECTION);
        int volume = sizeX * sizeY * sizeZ;
        this.cells = new byte[volume];
        this.counted = counted;
        this.layerCounted = new int[sizeY];
        this.layerMatching = new int[sizeY];
        int layer = sizeX * sizeZ;
        for (int index = counted.nextSetBit(0); index >= 0 && index < volume; index = counted.nextSetBit(index + 1)) {
            layerCounted[index / layer]++;
        }
        totals[CellState.UNKNOWN.ordinal()] = volume;
        this.sections = new Section[sectionsX * Math.ceilDiv(sizeY, SECTION) * sectionsZ];
        Arrays.fill(sections, Section.PENDING);
        this.pending = sections.length;
        this.mask = new byte[Math.max(1, Math.ceilDiv(volume, 8))];
        Arrays.fill(mask, (byte) 0xFF);
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int index(int x, int y, int z) {
        return x + sizeX * (z + sizeZ * y);
    }

    public int sectionCount() {
        return sections.length;
    }

    /** The section holding a position. */
    public int section(int x, int y, int z) {
        return x / SECTION + sectionsX * (z / SECTION + sectionsZ * (y / SECTION));
    }

    /** The minimum corner of a section, as {x, y, z}. */
    public int[] sectionMin(int section) {
        int x = section % sectionsX;
        int z = section / sectionsX % sectionsZ;
        int y = section / (sectionsX * sectionsZ);
        return new int[] {x * SECTION, y * SECTION, z * SECTION};
    }

    public CellState state(int index) {
        return CellState.of(cells[index]);
    }

    public boolean counted(int index) {
        return counted.get(index);
    }

    /** Sets a position's state and returns the state it had. */
    public CellState set(int x, int y, int z, CellState state) {
        return set(index(x, y, z), state);
    }

    /** Sets a position's state and returns the state it had. */
    public CellState set(int index, CellState state) {
        CellState old = CellState.of(cells[index]);
        if (old == state) {
            return old;
        }
        cells[index] = (byte) state.ordinal();
        totals[old.ordinal()]--;
        totals[state.ordinal()]++;
        int layer = sizeX * sizeZ;
        int y = index / layer;
        if (counted.get(index)) {
            if (old == CellState.MATCHING) {
                layerMatching[y]--;
            }
            if (state == CellState.MATCHING) {
                layerMatching[y]++;
            }
        }
        if (old.showsGhost() != state.showsGhost()) {
            int bit = index & 7;
            int at = index >>> 3;
            mask[at] = (byte) (state.showsGhost() ? mask[at] | (1 << bit) : mask[at] & ~(1 << bit));
            dirtyMaskFrom = Math.min(dirtyMaskFrom, at);
            dirtyMaskTo = Math.max(dirtyMaskTo, at + 1);
        }
        if (old.mistake() || state.mistake()) {
            int rest = index % layer;
            markBoxes(rest % sizeX, y, rest / sizeX);
        }
        return old;
    }

    /** Marks the box sections of a cell and of its neighbours, whose shared faces may appear or disappear. */
    private void markBoxes(int x, int y, int z) {
        dirtyBoxSections.set(section(x, y, z));
        if (x % SECTION == 0 && x > 0) {
            dirtyBoxSections.set(section(x - 1, y, z));
        }
        if (x % SECTION == SECTION - 1 && x < sizeX - 1) {
            dirtyBoxSections.set(section(x + 1, y, z));
        }
        if (y % SECTION == 0 && y > 0) {
            dirtyBoxSections.set(section(x, y - 1, z));
        }
        if (y % SECTION == SECTION - 1 && y < sizeY - 1) {
            dirtyBoxSections.set(section(x, y + 1, z));
        }
        if (z % SECTION == 0 && z > 0) {
            dirtyBoxSections.set(section(x, y, z - 1));
        }
        if (z % SECTION == SECTION - 1 && z < sizeZ - 1) {
            dirtyBoxSections.set(section(x, y, z + 1));
        }
    }

    /** The state at a position, or {@link CellState#UNKNOWN} outside the placement. */
    public CellState stateOrUnknown(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return CellState.UNKNOWN;
        }
        return CellState.of(cells[index(x, y, z)]);
    }

    /** Records that a section was compared; {@code loaded} is false when part of it lies in an unloaded chunk. */
    public void compared(int section, boolean loaded) {
        if (sections[section] == Section.PENDING) {
            pending--;
        }
        sections[section] = loaded ? Section.COMPARED : Section.UNLOADED;
    }

    /** True for sections never compared or last found partly unloaded; they are scanned first. */
    public boolean needsScan(int section) {
        return sections[section] != Section.COMPARED;
    }

    /** True until every section has been compared once. */
    public boolean scanning() {
        return pending > 0;
    }

    public int total(CellState state) {
        return totals[state.ordinal()];
    }

    /** Matching positions that count toward progress, over layers {@code from} to {@code to} inclusive. */
    public int matching(int from, int to) {
        int sum = 0;
        for (int y = Math.max(0, from); y <= Math.min(to, sizeY - 1); y++) {
            sum += layerMatching[y];
        }
        return sum;
    }

    /** Positions that count toward progress, over layers {@code from} to {@code to} inclusive. */
    public int counted(int from, int to) {
        int sum = 0;
        for (int y = Math.max(0, from); y <= Math.min(to, sizeY - 1); y++) {
            sum += layerCounted[y];
        }
        return sum;
    }

    /** The ghost mask, one bit per position, set where the ghost shows. Owned by this state; do not modify. */
    public byte[] mask() {
        return mask;
    }

    /** The range of mask bytes changed since the last call, as {from, to} with {@code to} exclusive; null if none. */
    public int[] takeDirtyMask() {
        if (dirtyMaskTo < 0) {
            return null;
        }
        int[] range = {dirtyMaskFrom, dirtyMaskTo};
        dirtyMaskFrom = Integer.MAX_VALUE;
        dirtyMaskTo = -1;
        return range;
    }

    /** Sections whose mistake boxes changed since the last call. */
    public BitSet takeDirtyBoxSections() {
        BitSet dirty = (BitSet) dirtyBoxSections.clone();
        dirtyBoxSections.clear();
        return dirty;
    }
}
