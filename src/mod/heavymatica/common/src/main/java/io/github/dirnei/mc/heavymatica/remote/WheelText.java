package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Segment;
import java.util.Locale;

/** Translation keys for the wheel's centre text. */
public final class WheelText {

    private WheelText() {
    }

    /** What picking the segment does or, while it is greyed out, why it does nothing. */
    public static String hintKey(Segment segment) {
        String key = "heavymatica.wheel.hint." + segment.entry().name().toLowerCase(Locale.ROOT);
        return segment.enabled() ? key : key + ".disabled";
    }

    public static String stateKey(RadialMenu.State state) {
        return "heavymatica.wheel.state." + state.name().toLowerCase(Locale.ROOT);
    }
}
