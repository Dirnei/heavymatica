package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.formats.SchematicModel.BlockCount;
import io.github.dirnei.mc.heavymatica.placement.BlockEquivalents;
import io.github.dirnei.mc.heavymatica.placement.MaterialList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.jetbrains.annotations.Nullable;

/**
 * The rows of the materials screen: one per block type of the schematic with its total and swap target and, given a
 * material list, the needed, had, short and finishing of the planned block the row shows as. Sorted by most short,
 * most needed, then most used with a list, and by most used without; ties by block ID.
 */
final class MaterialRows {

    /**
     * @param numbers   the shown-as block's counts under its placing item; null without a material list or when no item
     *                  places the block
     * @param finishing how many positions of the shown-as block need {@code finish}
     */
    record Row<I>(String block, long total, @Nullable String target, MaterialList.@Nullable Row<I> numbers, int finishing,
            BlockEquivalents.@Nullable Finish finish) {

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
     * @param known     whether the game knows a block ID; an unknown target shows as the original block
     * @param itemOf    the item placing a block, or null when none does
     * @param plannedOf the list's block key for a block ID, or null when the game does not know it
     * @param list      the placement's material list, or null while it is not locked
     * @param had       how many of an item the player carries, for blocks the list was not built from
     */
    static <I, B> List<Row<I>> of(List<BlockCount> counts, Map<String, String> swaps, Predicate<String> known, Function<String, I> itemOf,
            Function<String, B> plannedOf, @Nullable MaterialList<I, B> list, ToIntFunction<I> had) {
        List<Row<I>> rows = new ArrayList<>(counts.size());
        for (BlockCount count : counts) {
            String target = swaps.get(count.block());
            String shownAs = target != null && known.test(target) ? target : count.block();
            MaterialList.Row<I> numbers = null;
            int finishing = 0;
            BlockEquivalents.Finish finish = null;
            if (list != null) {
                B planned = plannedOf.apply(shownAs);
                MaterialList.Planned<I> shown = planned == null ? null : list.planned(planned);
                if (shown != null) {
                    numbers = new MaterialList.Row<>(shown.item(), shown.needed(), shown.given() + shown.left(), shown.shortage());
                    finishing = shown.finishing();
                    finish = shown.finish();
                } else {
                    I item = itemOf.apply(shownAs);
                    if (item != null) {
                        numbers = new MaterialList.Row<>(item, 0, had.applyAsInt(item), 0);
                    }
                }
            }
            rows.add(new Row<>(count.block(), count.count(), target, numbers, finishing, finish));
        }
        Comparator<Row<I>> byTotal = Comparator.<Row<I>>comparingLong(row -> -row.total()).thenComparing(Row::block);
        rows.sort(list == null ? byTotal
                : Comparator.<Row<I>>comparingInt(row -> -row.shortage()).thenComparingInt(row -> -row.needed()).thenComparing(byTotal));
        return List.copyOf(rows);
    }
}
