package io.github.dirnei.mc.heavymatica.account;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Supplies the bearer token for cloud calls and renews it when the cloud rejects it. */
public interface TokenSource {

    /** The current access token, or empty when signed out. */
    Optional<String> accessToken();

    /**
     * Tries to get a new access token. Completes with {@code true} when a new token is available,
     * {@code false} when the session is gone (the player is now signed out).
     */
    CompletableFuture<Boolean> refresh();

    /** Passes on whatever token the supplier holds and never renews it, for tokens that cannot be refreshed. */
    static TokenSource fixed(Supplier<Optional<String>> token) {
        return new TokenSource() {
            @Override
            public Optional<String> accessToken() {
                return token.get();
            }

            @Override
            public CompletableFuture<Boolean> refresh() {
                return CompletableFuture.completedFuture(false);
            }
        };
    }

    /** A token source for calls that need no sign-in. */
    TokenSource NONE = new TokenSource() {
        @Override
        public Optional<String> accessToken() {
            return Optional.empty();
        }

        @Override
        public CompletableFuture<Boolean> refresh() {
            return CompletableFuture.completedFuture(false);
        }
    };
}
