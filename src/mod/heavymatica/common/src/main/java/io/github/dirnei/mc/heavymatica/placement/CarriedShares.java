package io.github.dirnei.mc.heavymatica.placement;

import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * Shares the carried items out between planned blocks, so each carried item counts toward at most one. Planned blocks
 * are served one after another, those accepting the fewest items first, ties by index; each takes its accepted items
 * in order, up to its need.
 */
public final class CarriedShares {

    /**
     * @param given per planned block, how many carried items it took
     * @param left  per item, how many carried items no planned block took
     */
    public record Shares(int[] given, int[] left) {
    }

    private CarriedShares() {
    }

    /**
     * @param need    per planned block, how many it still needs; indices in block ID order
     * @param accepts per planned block, the items that count toward it, in the order it takes them
     * @param carried per item, how many the player carries
     */
    public static Shares share(int[] need, int[][] accepts, int[] carried) {
        int[] given = new int[need.length];
        int[] left = carried.clone();
        int[] order = IntStream.range(0, need.length).boxed()
                .sorted(Comparator.<Integer>comparingInt(block -> accepts[block].length).thenComparingInt(block -> block))
                .mapToInt(Integer::intValue).toArray();
        for (int block : order) {
            int wanted = need[block];
            for (int item : accepts[block]) {
                if (wanted == 0) {
                    break;
                }
                int taken = Math.min(wanted, left[item]);
                left[item] -= taken;
                given[block] += taken;
                wanted -= taken;
            }
        }
        return new Shares(given, left);
    }
}
