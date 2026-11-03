package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;
import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Segment;
import java.util.Locale;

/** Which GUI sprite shows a wheel segment: the path within the {@code heavymatica} namespace. */
public final class WheelIcons {

    public static final String FOLDER = "wheel/";

    private WheelIcons() {
    }

    public static String sprite(Segment segment) {
        return sprite(segment.entry(), segment.current());
    }

    /** @param current for Unknown blocks, whether they are shown; ignored for other entries */
    public static String sprite(Entry entry, boolean current) {
        String name = entry.name().toLowerCase(Locale.ROOT);
        if (entry == Entry.UNKNOWN_BLOCKS) {
            name += current ? "_shown" : "_hidden";
        }
        return FOLDER + name;
    }
}
