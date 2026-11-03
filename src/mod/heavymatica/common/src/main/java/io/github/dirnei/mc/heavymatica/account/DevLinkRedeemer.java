package io.github.dirnei.mc.heavymatica.account;

import io.github.dirnei.mc.heavymatica.cloud.model.LinkCode;
import io.github.dirnei.mc.heavymatica.cloud.model.RedeemRequest;
import io.github.dirnei.mc.heavymatica.cloud.model.SignInResult;
import io.github.dirnei.mc.heavymatica.cloud.model.VerifyRequest;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Development only: stands in for the website. Signs in a second Minecraft identity through DevMojang and redeems a
 * link code with it, as a pending sign-in when that identity has no account yet, or as its account otherwise. Nothing
 * is stored.
 */
public final class DevLinkRedeemer {

    /** What redeemed: {@code pending} or {@code account}, and the code as the cloud answered. */
    public record Redeemed(String principal, LinkCode code) {
    }

    private final URI cloudUrl;
    private final SessionJoin join;
    private final Executor completion;
    private final Executor background;

    public DevLinkRedeemer(URI cloudUrl, SessionJoin join, Executor completion, Executor background) {
        this.cloudUrl = cloudUrl;
        this.join = join;
        this.completion = completion;
        this.background = background;
    }

    public CompletableFuture<Redeemed> redeem(UUID uuid, String name, String code) {
        CloudClient auth = new CloudClient(cloudUrl, TokenSource.NONE, completion);
        return auth.call(apis -> apis.auth().createChallenge())
                .thenCompose(challenge -> CompletableFuture.runAsync(() -> {
                    try {
                        join.join(uuid, challenge.getServerId());
                    } catch (SessionJoin.JoinException e) {
                        throw new IllegalStateException("DevMojang join failed: " + e.getMessage(), e);
                    }
                }, background).thenCompose(ignored -> auth.call(apis -> apis.auth().signIn(
                        new VerifyRequest().challengeId(challenge.getChallengeId()).name(name)))))
                .thenCompose(result -> {
                    String principal = principal(result);
                    String token = result.getSession() != null ? result.getSession().getAccessToken() : result.getPending().getPendingToken();
                    CloudClient redeemer = new CloudClient(cloudUrl, TokenSource.fixed(() -> Optional.of(token)), completion);
                    return redeemer.call(apis -> apis.links().redeemLinkCode(new RedeemRequest().code(code)))
                            .thenApply(redeemed -> new Redeemed(principal, redeemed));
                });
    }

    private static String principal(SignInResult result) {
        if (result.getSession() != null) {
            return "account";
        }
        if (result.getPending() != null) {
            return "pending";
        }
        throw new IllegalStateException("The sign-in answer has neither a session nor a pending sign-in");
    }
}
