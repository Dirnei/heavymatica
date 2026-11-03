package io.github.dirnei.mc.heavymatica.ui.glass;

import java.util.function.ToIntFunction;

/** Text shortened to a width with {@code …} at the end, keeping the full text for a hover tooltip. */
public record TextFit(String shown, String full) {

    public static final String ELLIPSIS = "…";

    public boolean shortened() {
        return !shown.equals(full);
    }

    /** @param width the width of a string as drawn, in the same units as {@code maxWidth} */
    public static TextFit of(String text, int maxWidth, ToIntFunction<String> width) {
        if (width.applyAsInt(text) <= maxWidth) {
            return new TextFit(text, text);
        }
        int keep = text.length();
        while (keep > 0 && width.applyAsInt(text.substring(0, keep).stripTrailing() + ELLIPSIS) > maxWidth) {
            keep--;
        }
        return new TextFit(text.substring(0, keep).stripTrailing() + ELLIPSIS, text);
    }
}
