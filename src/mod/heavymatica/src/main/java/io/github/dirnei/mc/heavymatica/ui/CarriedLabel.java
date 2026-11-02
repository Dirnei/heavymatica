package io.github.dirnei.mc.heavymatica.ui;

/** What the block picker says about how many of a block the player carries for a row's need. */
record CarriedLabel(Kind kind, int have) {

    enum Kind {
        NONE,
        SOME,
        ENOUGH,
    }

    /** @param need how many the row needs: still needed while locked, every position otherwise */
    static CarriedLabel of(int have, int need) {
        if (have <= 0) {
            return new CarriedLabel(Kind.NONE, 0);
        }
        return new CarriedLabel(have >= need ? Kind.ENOUGH : Kind.SOME, have);
    }

    /** How many would be short building the row with this block. */
    static int shortage(int have, int need) {
        return Math.max(0, need - Math.max(0, have));
    }
}
