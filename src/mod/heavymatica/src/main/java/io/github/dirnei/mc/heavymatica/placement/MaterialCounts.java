package io.github.dirnei.mc.heavymatica.placement;

/**
 * How many of each planned block, and of each block no item places, the positions that still need an item ask for,
 * and how many positions of each planned block need finishing, per layer. Kept in step with the comparison state, so
 * a layer range is summed without looking at any position.
 */
public final class MaterialCounts {

    /** Totals over a layer range: needed and finishing by planned block, can't-carry by block. */
    public record Totals(int[] needed, int[] cantCarry, int[] finishing) {
    }

    private final int layers;
    private final int planned;
    private final int blocks;
    private final int[] needed;
    private final int[] cantCarry;
    private final int[] finishing;

    public MaterialCounts(int layers, int planned, int blocks) {
        this.layers = layers;
        this.planned = planned;
        this.blocks = blocks;
        this.needed = new int[layers * planned];
        this.cantCarry = new int[layers * blocks];
        this.finishing = new int[layers * planned];
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
            case PLANNED -> needed[layer * planned + demand.index()] += amount;
            case CANT_CARRY -> cantCarry[layer * blocks + demand.index()] += amount;
        }
    }

    public void addFinishing(int layer, int block) {
        finishing[layer * planned + block]++;
    }

    public void removeFinishing(int layer, int block) {
        finishing[layer * planned + block]--;
    }

    /** Totals over layers {@code from} to {@code to} inclusive. */
    public Totals sum(int from, int to) {
        int[] neededSum = new int[planned];
        int[] cantCarrySum = new int[blocks];
        int[] finishingSum = new int[planned];
        for (int layer = Math.max(0, from); layer <= Math.min(to, layers - 1); layer++) {
            for (int block = 0; block < planned; block++) {
                neededSum[block] += needed[layer * planned + block];
                finishingSum[block] += finishing[layer * planned + block];
            }
            for (int block = 0; block < blocks; block++) {
                cantCarrySum[block] += cantCarry[layer * blocks + block];
            }
        }
        return new Totals(neededSum, cantCarrySum, finishingSum);
    }
}
