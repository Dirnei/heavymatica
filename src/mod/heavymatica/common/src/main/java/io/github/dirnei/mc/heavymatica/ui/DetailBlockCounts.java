package io.github.dirnei.mc.heavymatica.ui;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** A version's block counts as the details show them: by the block ID the game uses today, most common first, unknown ones marked. */
record DetailBlockCounts(List<Row> rows, int unknownTypes) {

    /** @param block the block ID as the game names it today, or as written when the game does not know it */
    record Row(String block, long count, boolean known) {
    }

    /**
     * @param counts  block ID as written in the file to its count
     * @param current the block ID as the game names it today for one as written
     * @param known   whether the game knows a block ID
     */
    static DetailBlockCounts of(Map<String, Long> counts, UnaryOperator<String> current, Predicate<String> known) {
        Map<String, Long> merged = new LinkedHashMap<>();
        counts.forEach((block, count) -> merged.merge(current.apply(block), count, Long::sum));
        List<Row> rows = merged.entrySet().stream()
                .map(entry -> new Row(entry.getKey(), entry.getValue(), known.test(entry.getKey())))
                .sorted(Comparator.comparingLong(Row::count).reversed().thenComparing(Row::block))
                .toList();
        return new DetailBlockCounts(rows, (int) rows.stream().filter(row -> !row.known()).count());
    }
}
