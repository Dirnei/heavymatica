package io.github.dirnei.mc.heavymatica.account;

import io.github.dirnei.mc.heavymatica.cloud.model.Account;
import io.github.dirnei.mc.heavymatica.cloud.model.Challenge;
import io.github.dirnei.mc.heavymatica.cloud.model.CompleteSignInRequest;
import io.github.dirnei.mc.heavymatica.cloud.model.LinkCode;
import io.github.dirnei.mc.heavymatica.cloud.model.PendingSignIn;
import io.github.dirnei.mc.heavymatica.cloud.model.RefreshRequest;
import io.github.dirnei.mc.heavymatica.cloud.model.Session;
import io.github.dirnei.mc.heavymatica.cloud.model.SignInResult;
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
 *
 * <p>A Minecraft account without a cloud account signs in to {@link Status#CHOOSING}: the cloud's pending sign-in
 * proves the identity for a few minutes, until the player creates an account or joins one with a link code. The
 * pending token is kept in memory only.
 */
public final class CloudAccount implements TokenSource {

    public enum Status {
        SIGNED_OUT,
        SIGNING_IN,
        SIGNED_IN,
        /** Signed in at Mojang, but no cloud account uses this Minecraft account yet; the player creates or joins one. */
        CHOOSING,
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
    private final CloudClient pendingClient;
    private final List<Consumer<CloudAccount>> listeners = new CopyOnWriteArrayList<>();

    private volatile Status status = Status.SIGNED_OUT;
    private volatile SessionStore.@Nullable StoredSession session;
    private volatile @Nullable Account account;
    private volatile @Nullable PendingSignIn pending;
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
        this.pendingClient = new CloudClient(cloudUrl,
                TokenSource.fixed(() -> Optional.ofNullable(pending).map(PendingSignIn::getPendingToken)), completion);
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

    /** The pending sign-in while {@link Status#CHOOSING}. */
    public Optional<PendingSignIn> pending() {
        return Optional.ofNullable(pending);
    }

    /** The name to show: the account's, the stored session's before the account was fetched, or the pending sign-in's. */
    public Optional<String> name() {
        Account current = account;
        SessionStore.StoredSession stored = session;
        PendingSignIn waiting = pending;
        if (current != null) {
            return Optional.of(current.getName());
        }
        if (stored != null) {
            return Optional.of(stored.name());
        }
        return Optional.ofNullable(waiting).map(value -> value.getIdentity().getName());
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
        pending = null;
        change(session != null ? Status.SIGNED_IN : Status.SIGNED_OUT);
    }

    /**
     * The handshake: challenge, join at Mojang, sign-in. Ends {@link Status#SIGNED_IN}, or {@link Status#CHOOSING} when no
     * cloud account uses this Minecraft account yet. Fails with a {@link SignInFailure.SignInException}.
     */
    public CompletableFuture<Void> signIn() {
        Player current = player.get();
        session = null;
        account = null;
        pending = null;
        change(Status.SIGNING_IN);
        return auth.call(apis -> apis.auth().createChallenge())
                .exceptionally(error -> {
                    throw failed(SignInFailure.of(CloudException.from(error, auth.json())), error);
                })
                .thenCompose(challenge -> joinAsync(current, challenge).thenApply(ignored -> challenge))
                .thenCompose(challenge -> auth.call(apis -> apis.auth().signIn(new VerifyRequest().challengeId(challenge.getChallengeId()).name(current.name())))
                        .exceptionally(error -> {
                            throw failed(SignInFailure.of(CloudException.from(error, auth.json())), error);
                        }))
                .thenAccept(this::signInAnswered)
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        change(Status.SIGNED_OUT);
                    }
                });
    }

    /** Creates a new cloud account for the pending sign-in and signs in to it. */
    public CompletableFuture<Void> createAccount() {
        return completeSignIn(true);
    }

    /**
     * Signs in to the account the pending sign-in joined through a confirmed link code. Fails with
     * {@code account_choice_required} while it has not joined any.
     */
    public CompletableFuture<Void> completeJoin() {
        return completeSignIn(false);
    }

    /** Leaves {@link Status#CHOOSING} without creating anything. */
    public void cancelChoice() {
        if (status == Status.CHOOSING) {
            pending = null;
            change(Status.SIGNED_OUT);
        }
    }

    /**
     * A new link code: for the signed-in account, so another sign-in can join it, or while {@link Status#CHOOSING} for the
     * pending sign-in, so it can join an existing account. Issuing cancels the previous open code.
     */
    public CompletableFuture<LinkCode> issueLinkCode() {
        return issuer().call(apis -> apis.links().createLinkCode());
    }

    public CompletableFuture<LinkCode> pollLinkCode(String codeId) {
        return issuer().call(apis -> apis.links().getLinkCode(codeId));
    }

    public CompletableFuture<LinkCode> confirmLinkCode(String codeId) {
        return issuer().call(apis -> apis.links().confirmLinkCode(codeId));
    }

    /** Cancels an open code, or declines a redeemed one. */
    public CompletableFuture<Void> cancelLinkCode(String codeId) {
        return issuer().call(apis -> apis.links().cancelLinkCode(codeId));
    }

    /**
     * Unlinks a sign-in from the account. Unlinking the sign-in in use ends its sessions at the cloud, so the player is
     * signed out here as well.
     */
    public CompletableFuture<Void> unlink(String identityId) {
        Account known = account;
        boolean inUse = known != null && known.getIdentities() != null && known.getIdentities().stream()
                .anyMatch(identity -> identity.getId().equals(identityId) && Boolean.TRUE.equals(identity.getCurrent()));
        return client.call(apis -> apis.account().unlinkIdentity(identityId)).thenCompose(ignored -> {
            if (inUse) {
                clearSession();
                change(Status.SIGNED_OUT);
                return CompletableFuture.<Void>completedFuture(null);
            }
            return refreshAccount().<Void>thenApply(fresh -> null).exceptionally(error -> null);
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
        clearSession();
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

    private void signInAnswered(SignInResult result) {
        if (result.getSession() != null) {
            signedIn(result.getSession());
        } else if (result.getPending() != null) {
            pending = result.getPending();
            change(Status.CHOOSING);
        } else {
            throw new CompletionException(new SignInFailure.SignInException(SignInFailure.UNEXPECTED,
                    new IllegalStateException("The sign-in answer has neither a session nor a pending sign-in")));
        }
    }

    private CompletableFuture<Void> completeSignIn(boolean createAccount) {
        if (pending == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No pending sign-in"));
        }
        return pendingClient.call(apis -> apis.auth().completeSignIn(new CompleteSignInRequest().createAccount(createAccount)))
                .thenAccept(this::signedIn);
    }

    /** The pending sign-in's client while choosing, the account's otherwise. */
    private CloudClient issuer() {
        return status == Status.CHOOSING ? pendingClient : client;
    }

    private void signedIn(Session fresh) {
        pending = null;
        keep(fresh);
        account = fresh.getAccount();
        change(Status.SIGNED_IN);
    }

    private void keep(Session fresh) {
        Account owner = fresh.getAccount();
        // Sessions stay keyed by the Minecraft account that signed in, which is the identity in use.
        UUID uuid = owner.getUuid() != null ? owner.getUuid() : player.get().uuid();
        SessionStore.StoredSession stored = new SessionStore.StoredSession(uuid, owner.getName(), fresh.getAccessToken(),
                fresh.getAccessTokenExpiresAt(), fresh.getRefreshToken());
        session = stored;
        store.save(stored);
    }

    private void clearSession() {
        Player current = player.get();
        session = null;
        account = null;
        pending = null;
        store.delete(current.uuid());
        onSignedOut.run();
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
