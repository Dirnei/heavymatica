package io.github.dirnei.mc.heavymatica.placement;

/**
 * What one position of a placement takes to build: nothing, an amount of an item, or an amount of a block no item
 * places. Items and blocks are numbered per comparison.
 */
public record Demand(Kind kind, int index, int amount) {

    public enum Kind {
        NONE,
        ITEM,
        CANT_CARRY,
    }

    public static final Demand NONE = new Demand(Kind.NONE, 0, 0);

    public static Demand item(int item, int amount) {
        return new Demand(Kind.ITEM, item, amount);
    }

    public static Demand cantCarry(int block, int amount) {
        return new Demand(Kind.CANT_CARRY, block, amount);
    }
}
