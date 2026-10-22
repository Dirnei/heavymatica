package io.github.dirnei.mc.heavymatica.placement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import org.jetbrains.annotations.Nullable;

/**
 * What a placement still needs against what the player has: a row per item, most short first, then most needed,
 * then by item, and apart from those the blocks no item places. Built from planned blocks, it also keeps the numbers
 * of each planned block.
 *
 * @param <I> the item key
 * @param <B> the block key of the can't-carry part
 */
public final class MaterialList<I, B> {

    /** One item: how many are needed, how many the player has, and how many are short (never below 0). */
    public record Row<I>(I item, int needed, int had, int shortage) {
    }

    /** A block no item places, with how many positions still need it. */
    public record CantCarry<B>(B block, int count) {
    }

    /** The largest shortages, largest first, and how many more items are short. */
    public record Shortages<I>(List<Row<I>> top, int more) {

        public boolean none() {
            return top.isEmpty();
        }
    }

    /**
     * A planned block an item places, for {@link #shared}.
     *
     * @param item      the index of the item that places it
     * @param accepts   the indices of the items that count toward it, in the order it takes them
     * @param finishing how many of its positions need finishing
     */
    public record Need<B>(B block, int item, int[] accepts, int needed, int finishing, BlockEquivalents.@Nullable Finish finish) {
    }

    /**
     * The numbers of one planned block.
     *
     * @param item  the item that places it
     * @param given the carried items it was given
     * @param left  the carried items of its placing item that no planned block took
     */
    public record Planned<I>(I item, int needed, int given, int shortage, int left, int finishing, BlockEquivalents.@Nullable Finish finish) {
    }

    private final List<Row<I>> rows;
    private final List<CantCarry<B>> cantCarry;
    private final Map<B, Planned<I>> planned;

    private MaterialList(List<Row<I>> rows, List<CantCarry<B>> cantCarry, Map<B, Planned<I>> planned) {
        this.rows = rows;
        this.cantCarry = cantCarry;
        this.planned = planned;
    }

    /**
     * Shares the carried items out between the planned blocks and folds them into one row per placing item: needed
     * adds up, had is what its planned blocks were given plus its own carried items nobody took.
     *
     * @param planned the planned blocks in block ID order
     * @param items   the items {@link Need} indices point at
     * @param carried per item, how many the player carries
     */
    public static <I, B> MaterialList<I, B> shared(List<Need<B>> planned, List<I> items, int[] carried, Map<B, Integer> cantCarry,
            Comparator<I> itemOrder, Comparator<B> blockOrder) {
        int[] need = planned.stream().mapToInt(Need::needed).toArray();
        int[][] accepts = planned.stream().map(Need::accepts).toArray(int[][]::new);
        CarriedShares.Shares shares = CarriedShares.share(need, accepts, carried);
        Map<I, Integer> needed = new HashMap<>();
        Map<I, Integer> had = new HashMap<>();
        Map<B, Planned<I>> byBlock = new HashMap<>();
        for (int block = 0; block < planned.size(); block++) {
            Need<B> entry = planned.get(block);
            I item = items.get(entry.item());
            int given = shares.given()[block];
            int left = shares.left()[entry.item()];
            needed.merge(item, entry.needed(), Integer::sum);
            had.merge(item, given, Integer::sum);
            byBlock.put(entry.block(), new Planned<>(item, entry.needed(), given, entry.needed() - given, left, entry.finishing(), entry.finish()));
        }
        Map<I, Integer> leftByItem = new HashMap<>();
        planned.forEach(entry -> leftByItem.put(items.get(entry.item()), shares.left()[entry.item()]));
        MaterialList<I, B> list = of(needed, cantCarry, item -> had.getOrDefault(item, 0) + leftByItem.getOrDefault(item, 0), itemOrder, blockOrder);
        return new MaterialList<>(list.rows, list.cantCarry, Map.copyOf(byBlock));
    }

    /**
     * @param needed    how many of each item the counted positions need; items needed 0 times are left out
     * @param cantCarry how many positions need each block no item places; blocks needed 0 times are left out
     * @param had       how many of an item the player has
     */
    public static <I, B> MaterialList<I, B> of(Map<I, Integer> needed, Map<B, Integer> cantCarry, ToIntFunction<I> had, Comparator<I> itemOrder,
            Comparator<B> blockOrder) {
        List<Row<I>> rows = new ArrayList<>();
        needed.forEach((item, count) -> {
            if (count > 0) {
                int has = had.applyAsInt(item);
                rows.add(new Row<>(item, count, has, Math.max(0, count - has)));
            }
        });
        rows.sort(Comparator.<Row<I>>comparingInt(row -> -row.shortage())
                .thenComparingInt(row -> -row.needed())
                .thenComparing(Row::item, itemOrder));
        List<CantCarry<B>> blocks = new ArrayList<>();
        cantCarry.forEach((block, count) -> {
            if (count > 0) {
                blocks.add(new CantCarry<>(block, count));
            }
        });
        blocks.sort(Comparator.<CantCarry<B>>comparingInt(entry -> -entry.count()).thenComparing(CantCarry::block, blockOrder));
        return new MaterialList<>(List.copyOf(rows), List.copyOf(blocks), Map.of());
    }

    public List<Row<I>> rows() {
        return rows;
    }

    public List<CantCarry<B>> cantCarry() {
        return cantCarry;
    }

    /** The numbers of a planned block; null for blocks the list was not built from. */
    public @Nullable Planned<I> planned(B block) {
        return planned.get(block);
    }

    /** Up to {@code limit} short items, largest shortage first. */
    public Shortages<I> shortages(int limit) {
        List<Row<I>> top = new ArrayList<>();
        int more = 0;
        for (Row<I> row : rows) {
            if (row.shortage() == 0) {
                break;
            }
            if (top.size() < limit) {
                top.add(row);
            } else {
                more++;
            }
        }
        return new Shortages<>(List.copyOf(top), more);
    }
}
