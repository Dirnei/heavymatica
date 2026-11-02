package io.github.dirnei.mc.heavymatica.targeting;

/** What the mistake pointer looks for: nothing, the boxed positions, or the boxed and the missing ones. */
public enum PointerMode {
    OFF,
    MISTAKES,
    MISTAKES_AND_MISSING,
}
