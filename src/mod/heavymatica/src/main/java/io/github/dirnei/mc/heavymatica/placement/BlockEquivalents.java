package io.github.dirnei.mc.heavymatica.placement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Planned blocks that are not placed as themselves: the block that places each, the blocks that already count as it,
 * and the blocks that only need a finishing step. Rules are block ID templates where {@code *} matches a non-empty run
 * without {@code :}; the captures of the planned template fill the other templates' {@code *} in order. The first rule
 * that matches wins.
 */
public final class BlockEquivalents {

    /** The step that turns a block into the planned one. */
    public enum Finish {
        SHOVEL,
        HOE,
        AXE,
        WATER,
    }

    public record Rule(String planned, String placedWith, List<String> same, List<String> finishFrom, @Nullable Finish finish) {
    }

    /**
     * What a planned block takes and accepts.
     *
     * @param placedWith the block whose item places it
     * @param same       blocks that count as the planned block
     * @param finishFrom blocks that become the planned block after {@code finish}
     */
    public record Equivalence(String placedWith, List<String> same, List<String> finishFrom, @Nullable Finish finish) {

        public static Equivalence self(String plannedId) {
            return new Equivalence(plannedId, List.of(), List.of(), null);
        }

        /** The blocks whose items count toward the planned block: itself, the placing block, the same, then the finishing blocks. */
        public List<String> accepted(String plannedId) {
            Set<String> accepted = new LinkedHashSet<>();
            accepted.add(plannedId);
            accepted.add(placedWith);
            accepted.addAll(same);
            accepted.addAll(finishFrom);
            return List.copyOf(accepted);
        }
    }

    private static final String DIRT = "minecraft:dirt";
    private static final String GRASS = "minecraft:grass_block";
    private static final String PATH = "minecraft:dirt_path";

    static final List<Rule> RULES = List.of(
            new Rule(GRASS, DIRT, List.of(DIRT), List.of(), null),
            new Rule(DIRT, DIRT, List.of(GRASS), List.of(), null),
            new Rule(PATH, DIRT, List.of(), List.of(DIRT, GRASS, "minecraft:podzol", "minecraft:mycelium", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
                    Finish.SHOVEL),
            new Rule("minecraft:farmland", DIRT, List.of(), List.of(DIRT, GRASS, PATH), Finish.HOE),
            stripped("log"),
            stripped("wood"),
            stripped("stem"),
            stripped("hyphae"),
            new Rule("minecraft:stripped_bamboo_block", "minecraft:bamboo_block", List.of(), List.of("minecraft:bamboo_block"), Finish.AXE),
            new Rule("minecraft:*_concrete", "minecraft:*_concrete_powder", List.of(), List.of("minecraft:*_concrete_powder"), Finish.WATER));

    private static final List<Pattern> PATTERNS = RULES.stream().map(rule -> pattern(rule.planned())).toList();
    private static final Map<String, Equivalence> CACHE = new ConcurrentHashMap<>();

    private BlockEquivalents() {
    }

    private static Rule stripped(String suffix) {
        String unstripped = "*:*_" + suffix;
        return new Rule("*:stripped_*_" + suffix, unstripped, List.of(), List.of(unstripped), Finish.AXE);
    }

    public static Equivalence of(String plannedId) {
        return CACHE.computeIfAbsent(plannedId, BlockEquivalents::lookup);
    }

    private static Equivalence lookup(String plannedId) {
        for (int i = 0; i < RULES.size(); i++) {
            Matcher matcher = PATTERNS.get(i).matcher(plannedId);
            if (matcher.matches()) {
                List<String> captures = new ArrayList<>();
                for (int group = 1; group <= matcher.groupCount(); group++) {
                    captures.add(matcher.group(group));
                }
                Rule rule = RULES.get(i);
                return new Equivalence(fill(rule.placedWith(), captures), fill(rule.same(), captures), fill(rule.finishFrom(), captures), rule.finish());
            }
        }
        return Equivalence.self(plannedId);
    }

    /** The planned blocks, in their order, whose items the held block counts toward. */
    public static List<String> highlights(String heldId, Collection<String> plannedIds) {
        return plannedIds.stream().filter(planned -> of(planned).accepted(planned).contains(heldId)).toList();
    }

    private static Pattern pattern(String template) {
        StringBuilder regex = new StringBuilder();
        String[] parts = template.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append("([^:]+)");
            }
            regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(regex.toString());
    }

    private static List<String> fill(List<String> templates, List<String> captures) {
        return templates.stream().map(template -> fill(template, captures)).toList();
    }

    private static String fill(String template, List<String> captures) {
        StringBuilder filled = new StringBuilder();
        int capture = 0;
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '*') {
                filled.append(captures.get(capture++));
            } else {
                filled.append(c);
            }
        }
        return filled.toString();
    }
}
