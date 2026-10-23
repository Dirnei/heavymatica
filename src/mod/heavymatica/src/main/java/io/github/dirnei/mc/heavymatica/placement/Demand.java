package io.github.dirnei.mc.heavymatica.placement;

/**
 * What one position of a placement takes to build: nothing, an amount of a planned block an item places, or an
 * amount of a block no item places. Planned blocks and can't-carry blocks are numbered per comparison.
 */
public record Demand(Kind kind, int index, int amount) {

    public enum Kind {
        NONE,
        PLANNED,
        CANT_CARRY,
    }

    public static final Demand NONE = new Demand(Kind.NONE, 0, 0);

    public static Demand planned(int block, int amount) {
        return new Demand(Kind.PLANNED, block, amount);
    }

    public static Demand cantCarry(int block, int amount) {
        return new Demand(Kind.CANT_CARRY, block, amount);
    }

    /** What a position in {@code state} asks for, {@code found} being the amount it holds when incomplete. */
    public int amountFor(CellState state, int found) {
        if (kind == Kind.NONE || !state.needsItem()) {
            return 0;
        }
        return state == CellState.INCOMPLETE ? Math.max(0, amount - found) : amount;
    }
}
