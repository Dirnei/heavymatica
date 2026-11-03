package io.github.dirnei.mc.heavymatica.paste;

/** Which positions a paste changes. */
public enum PasteMode {
    /** Every position becomes the planned block, air included. */
    REPLACE_ALL,
    /** Planned blocks go only where the world has air or a replaceable block; nothing is removed. */
    ONLY_INTO_AIR,
    /** Every planned block is placed; where the plan has air the world stays. */
    SKIP_AIR;

    /**
     * Whether a position changes. Positions that already hold the planned state stay, unless the plan has block-entity
     * data there.
     *
     * @param withBlockEntity whether the plan has block-entity data at the position
     */
    public <S> boolean changes(S planned, boolean withBlockEntity, S found, States<S> states) {
        if (planned.equals(found) && !withBlockEntity) {
            return false;
        }
        return switch (this) {
            case REPLACE_ALL -> true;
            case ONLY_INTO_AIR -> !states.air(planned) && (states.air(found) || states.replaceable(found));
            case SKIP_AIR -> !states.air(planned);
        };
    }

    /** What the mode needs to know about a block state. */
    public interface States<S> {

        boolean air(S state);

        /** Whether placing a block may replace it, like grass, snow layers or water. */
        boolean replaceable(S state);
    }
}
