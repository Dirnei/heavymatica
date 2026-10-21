package io.github.dirnei.mc.heavymatica.account;

import io.github.dirnei.mc.heavymatica.cloud.model.Account;
import io.github.dirnei.mc.heavymatica.cloud.model.Challenge;
import io.github.dirnei.mc.heavymatica.cloud.model.RefreshRequest;
import io.github.dirnei.mc.heavymatica.cloud.model.Session;
import io.github.dirnei.mc.heavymatica.cloud.model.VerifyRequest;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * The player's cloud account, one session per Minecraft UUID. Makes no network call until the player signs in for the
 * first time.
 *
 * <p>Sign-in and refresh must go through a separate client without a token, so a 401 from them can never trigger
 * another refresh.
 */
public final class CloudAccount implements TokenSource {

    public enum Status {
        SIGNED_OUT,
        SIGNING_IN,
        SIGNED_IN,
        /** The session can no longer be refreshed; the player is signed out and asked to sign in again. */
        EXPIRED,
    }

    /** The Minecraft account the game is running with. */
    public record Player(UUID uuid, String name) {
    }

    private final Supplier<Player> player;
    private final SessionJoin join;
    private final SessionStore store;
    private final Runnable onSignedOut;
    private final Executor background;
    private final CloudClient client;
    private final CloudClient auth;
    private final List<Consumer<CloudAccount>> listeners = new CopyOnWriteArrayList<>();

    private volatile Status status = Status.SIGNED_OUT;
    private volatile SessionStore.@Nullable StoredSession session;
    private volatile @Nullable Account account;
    private @Nullable CompletableFuture<Boolean> refreshing;

    /**
     * @param completion  where results are delivered, the render thread in game
     * @param background  where the blocking Mojang join runs
     * @param onSignedOut removes what the player's sign-out must remove besides the session (the stored listing)
     */
    public CloudAccount(URI cloudUrl, Executor completion, Executor background, Supplier<Player> player, SessionJoin join,
                        SessionStore store, Runnable onSignedOut) {
        this.player = player;
        this.join = join;
        this.store = store;
        this.onSignedOut = onSignedOut;
        this.background = background;
        this.client = new CloudClient(cloudUrl, this, completion);
        this.auth = new CloudClient(cloudUrl, TokenSource.NONE, completion);
    }

    /** The client for every cloud call besides sign-in. */
    public CloudClient client() {
        return client;
    }

    public Status status() {
        return status;
    }

    public boolean signedIn() {
        return status == Status.SIGNED_IN;
    }

    public Optional<Account> account() {
        return Optional.ofNullable(account);
    }

    /** The name to show: the account's, or the stored session's before the account was fetched. */
    public Optional<String> name() {
        Account current = account;
        SessionStore.StoredSession stored = session;
        return current != null ? Optional.of(current.getName()) : Optional.ofNullable(stored).map(SessionStore.StoredSession::name);
    }

    /** Subscribes to changes; run the returned handle to unsubscribe. */
    public Runnable onChange(Consumer<CloudAccount> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Resumes the stored session of the current Minecraft account, if any. Makes no network call. */
    public void restore() {
        Player current = player.get();
        session = store.load(current.uuid()).orElse(null);
        account = null;
        change(session != null ? Status.SIGNED_IN : Status.SIGNED_OUT);
    }

    /** The handshake: challenge, join at Mojang, verify. Fails with a {@link SignInFailure.SignInException}. */
    public CompletableFuture<Void> signIn() {
        Player current = player.get();
        session = null;
        account = null;
        change(Status.SIGNING_IN);
        return auth.call(apis -> apis.auth().createChallenge())
                .exceptionally(error -> {
                    throw failed(SignInFailure.of(CloudException.from(error, auth.json())), error);
                })
                .thenCompose(challenge -> joinAsync(current, challenge).thenApply(ignored -> challenge))
                .thenCompose(challenge -> auth.call(apis -> apis.auth().verify(new VerifyRequest().challengeId(challenge.getChallengeId()).name(current.name())))
                        .exceptionally(error -> {
                            throw failed(SignInFailure.of(CloudException.from(error, auth.json())), error);
                        }))
                .thenAccept(this::signedIn)
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        change(Status.SIGNED_OUT);
                    }
                });
    }

    public CompletableFuture<Account> refreshAccount() {
        return client.call(apis -> apis.account().getMe()).thenApply(fresh -> {
            account = fresh;
            change(status);
            return fresh;
        });
    }

    /**
     * Signs out: the local session and listing are removed at once, whatever happens; the session is revoked at the
     * cloud when it can be reached.
     */
    public CompletableFuture<Void> signOut() {
        SessionStore.StoredSession stored = session;
        Player current = player.get();
        session = null;
        account = null;
        store.delete(current.uuid());
        onSignedOut.run();
        change(Status.SIGNED_OUT);
        if (stored == null) {
            return CompletableFuture.completedFuture(null);
        }
        return auth.call(apis -> apis.auth().revokeSession(new RefreshRequest().refreshToken(stored.refreshToken())))
                .exceptionally(ignored -> null);
    }

    @Override
    public Optional<String> accessToken() {
        SessionStore.StoredSession stored = session;
        return stored == null ? Optional.empty() : Optional.of(stored.accessToken());
    }

    @Override
    public synchronized CompletableFuture<Boolean> refresh() {
        SessionStore.StoredSession stored = session;
        if (stored == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (refreshing != null) {
            return refreshing;
        }
        CompletableFuture<Boolean> attempt = auth.call(apis -> apis.auth().refresh(new RefreshRequest().refreshToken(stored.refreshToken())))
                .thenApply(fresh -> {
                    keep(fresh);
                    return true;
                })
                .exceptionally(error -> {
                    CloudException cloud = CloudException.from(error, auth.json());
                    if (cloud.status() == 401) {
                        expire();
                    }
                    // Unreachable: keep the session for later, the call that needed the token fails for now.
                    return false;
                });
        refreshing = attempt;
        attempt.whenComplete((ignored, failure) -> {
            synchronized (this) {
                refreshing = null;
            }
        });
        return attempt;
    }

    private CompletableFuture<Void> joinAsync(Player current, Challenge challenge) {
        return CompletableFuture.runAsync(() -> {
            try {
                join.join(current.uuid(), challenge.getServerId());
            } catch (SessionJoin.JoinException e) {
                throw failed(e.unavailable() ? SignInFailure.MOJANG_UNAVAILABLE : SignInFailure.UNVERIFIABLE_ACCOUNT, e);
            }
        }, background);
    }

    private void signedIn(Session fresh) {
        keep(fresh);
        account = fresh.getAccount();
        change(Status.SIGNED_IN);
    }

    private void keep(Session fresh) {
        Account owner = fresh.getAccount();
        SessionStore.StoredSession stored = new SessionStore.StoredSession(owner.getUuid(), owner.getName(), fresh.getAccessToken(),
                fresh.getAccessTokenExpiresAt(), fresh.getRefreshToken());
        session = stored;
        store.save(stored);
    }

    private void expire() {
        Player current = player.get();
        session = null;
        account = null;
        store.delete(current.uuid());
        change(Status.EXPIRED);
    }

    private void change(Status next) {
        status = next;
        listeners.forEach(listener -> listener.accept(this));
    }

    private static CompletionException failed(SignInFailure failure, Throwable cause) {
        Throwable root = cause instanceof CompletionException completion && completion.getCause() != null ? completion.getCause() : cause;
        if (root instanceof SignInFailure.SignInException signIn) {
            return new CompletionException(signIn);
        }
        return new CompletionException(new SignInFailure.SignInException(failure, root));
    }
}
