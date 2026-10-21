package io.github.dirnei.mc.heavymatica.account;

import java.util.UUID;

/**
 * Joins a server ID at Mojang's session service, the first half of the sign-in handshake (D3). Implementations use
 * the game's own session (the Minecraft access token goes to Mojang only) or, in development, the cloud's DevMojang.
 */
@FunctionalInterface
public interface SessionJoin {

    /** Blocks until Mojang answered. */
    void join(UUID profile, String serverId) throws JoinException;

    /** Mojang refused the join or could not be asked. */
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
