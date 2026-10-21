package io.github.dirnei.mc.heavymatica.placement;

/**
 * How many of each item, and of each block no item places, the positions that still need one ask for, per layer.
 * Kept in step with the comparison state, so a layer range is summed without looking at any position.
 */
public final class MaterialCounts {

    /** Totals over a layer range, indexed by item and by block. */
    public record Totals(int[] needed, int[] cantCarry) {
    }

    private final int layers;
    private final int items;
    private final int blocks;
    private final int[] needed;
    private final int[] cantCarry;

    public MaterialCounts(int layers, int items, int blocks) {
        this.layers = layers;
        this.items = items;
        this.blocks = blocks;
        this.needed = new int[layers * items];
        this.cantCarry = new int[layers * blocks];
    }

    public void add(int layer, Demand demand) {
        change(layer, demand, demand.amount());
    }

    public void remove(int layer, Demand demand) {
        change(layer, demand, -demand.amount());
    }

    private void change(int layer, Demand demand, int amount) {
        switch (demand.kind()) {
            case NONE -> {
            }
            case ITEM -> needed[layer * items + demand.index()] += amount;
            case CANT_CARRY -> cantCarry[layer * blocks + demand.index()] += amount;
        }
    }

    /** Totals over layers {@code from} to {@code to} inclusive. */
    public Totals sum(int from, int to) {
        int[] neededSum = new int[items];
        int[] cantCarrySum = new int[blocks];
        for (int layer = Math.max(0, from); layer <= Math.min(to, layers - 1); layer++) {
            for (int item = 0; item < items; item++) {
                neededSum[item] += needed[layer * items + item];
            }
            for (int block = 0; block < blocks; block++) {
                cantCarrySum[block] += cantCarry[layer * blocks + block];
            }
        }
        return new Totals(neededSum, cantCarrySum);
    }
}
