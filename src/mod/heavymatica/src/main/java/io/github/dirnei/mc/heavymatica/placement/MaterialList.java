package io.github.dirnei.mc.heavymatica.placement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * What a placement still needs against what the player has: a row per item, most short first, then most needed,
 * then by item, and apart from those the blocks no item places.
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

    private final List<Row<I>> rows;
    private final List<CantCarry<B>> cantCarry;

    private MaterialList(List<Row<I>> rows, List<CantCarry<B>> cantCarry) {
        this.rows = rows;
        this.cantCarry = cantCarry;
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
        return new MaterialList<>(List.copyOf(rows), List.copyOf(blocks));
    }

    public List<Row<I>> rows() {
        return rows;
    }

    public List<CantCarry<B>> cantCarry() {
        return cantCarry;
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
