package io.github.dirnei.mc.heavymatica.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/** What the block picker lists for a search: blocks of the original's kind first, then the rest, each sorted by name. */
record BlockChoices<T>(List<T> suggested, List<T> others) {

    static <T> BlockChoices<T> of(List<T> blocks, Predicate<T> sameKind, Function<T, String> name, Function<T, String> id, String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<T> suggested = new ArrayList<>();
        List<T> others = new ArrayList<>();
        for (T block : blocks) {
            if (!needle.isEmpty() && !name.apply(block).toLowerCase(Locale.ROOT).contains(needle) && !id.apply(block).contains(needle)) {
                continue;
            }
            (sameKind.test(block) ? suggested : others).add(block);
        }
        Comparator<T> byName = Comparator.comparing(block -> name.apply(block).toLowerCase(Locale.ROOT));
        suggested.sort(byName);
        others.sort(byName);
        return new BlockChoices<>(List.copyOf(suggested), List.copyOf(others));
    }

    int size() {
        return suggested.size() + others.size();
    }
}
