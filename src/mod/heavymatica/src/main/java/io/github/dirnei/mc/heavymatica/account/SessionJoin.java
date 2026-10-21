package io.github.dirnei.mc.heavymatica.account;

import java.util.UUID;

/**
 * Joins a server ID at Mojang's session service, the first half of the sign-in handshake. The Minecraft access token
 * must go to Mojang only.
 */
@FunctionalInterface
public interface SessionJoin {

    /** Blocks until Mojang answered. */
    void join(UUID profile, String serverId) throws JoinException;

    final class JoinException extends Exception {
        private final boolean unavailable;

        public JoinException(boolean unavailable, String message, Throwable cause) {
            super(message, cause);
            this.unavailable = unavailable;
        }

        /** True when Mojang could not be reached; false when it rejected the account. */
        public boolean unavailable() {
            return unavailable;
        }
    }
}
