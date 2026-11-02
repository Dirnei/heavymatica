package io.github.dirnei.mc.heavymatica.account;

import java.net.URI;
import org.jetbrains.annotations.Nullable;

/** Which cloud address the game uses. */
public final class CloudAddress {

    /** Set by the dev run tasks, so a dev client never talks to the production cloud by accident. */
    public static final String PROPERTY = "heavymatica.cloud.url";

    private CloudAddress() {
    }

    /** The address from the system property when it is set, otherwise the configured one. */
    public static URI choose(@Nullable String property, String configured) {
        return URI.create(property == null || property.isBlank() ? configured : property.trim());
    }
}
