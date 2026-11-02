package io.github.dirnei.mc.heavymatica.placement;

/**
 * What one position of a placement takes to build: nothing, an amount of a planned block an item places, or an
 * amount of a block no item places. A two-item block takes one of its base and one of its content, both planned
 * blocks; {@code content} is -1 for every other position. Planned blocks and can't-carry blocks are numbered per
 * comparison.
 */
public record Demand(Kind kind, int index, int amount, int content) {

    public enum Kind {
        NONE,
        PLANNED,
        CANT_CARRY,
    }

    public static final Demand NONE = new Demand(Kind.NONE, 0, 0, -1);

    public static Demand planned(int block, int amount) {
        return new Demand(Kind.PLANNED, block, amount, -1);
    }

    public static Demand twoItem(int base, int content) {
        return new Demand(Kind.PLANNED, base, 1, content);
    }

    public static Demand cantCarry(int block, int amount) {
        return new Demand(Kind.CANT_CARRY, block, amount, -1);
    }

    /**
     * What a position in {@code state} asks for of {@link #index}, {@code found} being the amount it holds when
     * incomplete. An incomplete two-item block already has its base.
     */
    public int amountFor(CellState state, int found) {
        if (kind == Kind.NONE || !state.needsItem()) {
            return 0;
        }
        if (state == CellState.INCOMPLETE) {
            return content >= 0 ? 0 : Math.max(0, amount - found);
        }
        return amount;
    }

    /** What a position in {@code state} asks for of {@link #content}: one until the content is in. */
    public int contentFor(CellState state) {
        return content >= 0 && state.needsItem() ? 1 : 0;
    }
}
