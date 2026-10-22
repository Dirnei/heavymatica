package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.formats.SchematicModel.BlockCount;
import io.github.dirnei.mc.heavymatica.placement.MaterialList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.jetbrains.annotations.Nullable;

/**
 * The rows of the materials screen: one per block type of the schematic with its total and swap target and, given a
 * material list, the needed, had and short of the item placing the block the row shows as. Sorted by most short,
 * most needed, then most used with a list, and by most used without; ties by block ID.
 */
final class MaterialRows {

    /** @param numbers the shown-as item's counts; null without a material list or when no item places the block */
    record Row<I>(String block, long total, @Nullable String target, MaterialList.@Nullable Row<I> numbers) {

        int shortage() {
            return numbers == null ? 0 : numbers.shortage();
        }

        int needed() {
            return numbers == null ? 0 : numbers.needed();
        }
    }

    private MaterialRows() {
    }

    /**
     * @param known  whether the game knows a block ID; an unknown target shows as the original block
     * @param itemOf the item placing a block, or null when none does
     * @param list   the placement's material list, or null while it is not locked
     * @param had    how many of an item the player carries, for items the list does not need
     */
    static <I> List<Row<I>> of(List<BlockCount> counts, Map<String, String> swaps, Predicate<String> known, Function<String, I> itemOf,
            @Nullable MaterialList<I, ?> list, ToIntFunction<I> had) {
        Map<I, MaterialList.Row<I>> byItem = new HashMap<>();
        if (list != null) {
            list.rows().forEach(row -> byItem.put(row.item(), row));
        }
        List<Row<I>> rows = new ArrayList<>(counts.size());
        for (BlockCount count : counts) {
            String target = swaps.get(count.block());
            MaterialList.Row<I> numbers = null;
            if (list != null) {
                I item = itemOf.apply(target != null && known.test(target) ? target : count.block());
                if (item != null) {
                    numbers = byItem.getOrDefault(item, new MaterialList.Row<>(item, 0, had.applyAsInt(item), 0));
                }
            }
            rows.add(new Row<>(count.block(), count.count(), target, numbers));
        }
        Comparator<Row<I>> byTotal = Comparator.<Row<I>>comparingLong(row -> -row.total()).thenComparing(Row::block);
        rows.sort(list == null ? byTotal
                : Comparator.<Row<I>>comparingInt(row -> -row.shortage()).thenComparingInt(row -> -row.needed()).thenComparing(byTotal));
        return List.copyOf(rows);
    }
}
