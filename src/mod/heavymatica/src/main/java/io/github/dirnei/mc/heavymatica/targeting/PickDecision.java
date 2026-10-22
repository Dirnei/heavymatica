package io.github.dirnei.mc.heavymatica.targeting;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/** What picking a ghost does with the player's items. */
public final class PickDecision {

    /**
     * An item that places or counts toward the planned block, and where the player carries it.
     *
     * @param hotbar    hotbar slots holding it, 0 to 8, ascending
     * @param main      main inventory slots holding it, 9 to 35, ascending
     * @param container the first carried container holding it, or null
     */
    public record Candidate<T>(T item, List<Integer> hotbar, List<Integer> main, @Nullable T container) {
    }

    public sealed interface Action<T> permits Vanilla, Select, Swap, CreativeAdd, InContainer, NotCarried {
    }

    /** Leave pick block to vanilla. */
    public record Vanilla<T>() implements Action<T> {
    }

    /** Select a hotbar slot. */
    public record Select<T>(int slot) implements Action<T> {
    }

    /** Swap a main inventory slot with a hotbar slot, then select the hotbar slot. */
    public record Swap<T>(int inventorySlot, int hotbarSlot) implements Action<T> {
    }

    /** Put the item into a hotbar slot, creative only, then select it. */
    public record CreativeAdd<T>(T item, int hotbarSlot) implements Action<T> {
    }

    /** Tell the player the item is only inside a container. */
    public record InContainer<T>(T item, T container) implements Action<T> {
    }

    /** Tell the player they carry none of the item. */
    public record NotCarried<T>(T item) implements Action<T> {
    }

    private PickDecision() {
    }

    /**
     * The first candidate in the hotbar or main inventory is picked, in candidate order; without one, creative adds the
     * first candidate, survival names a container holding one or the first candidate.
     *
     * @param suitableSlot the hotbar slot vanilla's pick block would fill
     */
    public static <T> Action<T> decide(List<Candidate<T>> candidates, boolean creative, int selectedSlot, int suitableSlot) {
        if (candidates.isEmpty()) {
            return new Vanilla<>();
        }
        for (Candidate<T> candidate : candidates) {
            if (!candidate.hotbar().isEmpty()) {
                return new Select<>(candidate.hotbar().contains(selectedSlot) ? selectedSlot : candidate.hotbar().getFirst());
            }
            if (!candidate.main().isEmpty()) {
                return new Swap<>(candidate.main().getFirst(), suitableSlot);
            }
        }
        T first = candidates.getFirst().item();
        if (creative) {
            return new CreativeAdd<>(first, suitableSlot);
        }
        for (Candidate<T> candidate : candidates) {
            if (candidate.container() != null) {
                return new InContainer<>(candidate.item(), candidate.container());
            }
        }
        return new NotCarried<>(first);
    }
}
