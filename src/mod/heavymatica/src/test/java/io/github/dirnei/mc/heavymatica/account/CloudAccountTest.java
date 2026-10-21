package io.github.dirnei.mc.heavymatica.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.cloud.client.ApiClient;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** client/cloud-account against a stub cloud and a fake Mojang join. */
class CloudAccountTest {

    private static final UUID PLAYER = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final String MINECRAFT_TOKEN = "minecraft-access-token-never-for-the-cloud";

    @TempDir
    Path directory;

    private StubCloud cloud;
    private SessionStore store;
    private final AtomicReference<CloudAccount.Player> player = new AtomicReference<>(new CloudAccount.Player(PLAYER, "Dirnei"));
    private final AtomicInteger listingsRemoved = new AtomicInteger();
    private final AtomicReference<SessionJoin.JoinException> joinFailure = new AtomicReference<>();
    private final AtomicReference<String> joinedServerId = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        cloud = new StubCloud();
        store = new SessionStore(directory.resolve("sessions"), ApiClient.createDefaultObjectMapper());
    }

    @AfterEach
    void stop() {
        cloud.close();
    }

    @Test
    void nothing_is_sent_to_the_cloud_before_the_first_sign_in() {
        CloudAccount account = account(cloud.uri());

        account.restore();

        assertEquals(CloudAccount.Status.SIGNED_OUT, account.status());
        assertTrue(cloud.requests().isEmpty());
    }

    @Test
    void sign_in_runs_the_handshake_and_stores_the_session_per_uuid() throws Exception {
        cloud.answer(200, challenge()).answer(200, session("access-1", "refresh-1"));
        CloudAccount account = account(cloud.uri());

        account.signIn().get(10, TimeUnit.SECONDS);

        assertEquals(CloudAccount.Status.SIGNED_IN, account.status());
        assertEquals("server-1", joinedServerId.get());
        assertEquals("Dirnei", account.name().orElseThrow());
        assertEquals("access-1", store.load(PLAYER).orElseThrow().accessToken());
        assertTrue(cloud.requests().get(1).bodyText().contains("\"name\":\"Dirnei\""));
    }

    @Test
    void the_minecraft_token_never_reaches_the_cloud() throws Exception {
        cloud.answer(200, challenge()).answer(200, session("access-1", "refresh-1"));
        CloudAccount account = account(cloud.uri());

        account.signIn().get(10, TimeUnit.SECONDS);

        for (StubCloud.Request request : cloud.requests()) {
            assertFalse(request.bodyText().contains(MINECRAFT_TOKEN));
            assertFalse(String.valueOf(request.authorization()).contains(MINECRAFT_TOKEN));
            assertFalse(request.path().contains(MINECRAFT_TOKEN));
        }
    }

    @Test
    void a_restart_resumes_the_session_without_any_action() throws Exception {
        cloud.answer(200, challenge()).answer(200, session("access-1", "refresh-1"));
        account(cloud.uri()).signIn().get(10, TimeUnit.SECONDS);
        int requestsBefore = cloud.requests().size();

        CloudAccount restarted = account(cloud.uri());
        restarted.restore();

        assertEquals(CloudAccount.Status.SIGNED_IN, restarted.status());
        assertEquals("access-1", restarted.accessToken().orElseThrow());
        assertEquals(requestsBefore, cloud.requests().size());
    }

    @Test
    void another_minecraft_account_does_not_use_the_stored_session() throws Exception {
        cloud.answer(200, challenge()).answer(200, session("access-1", "refresh-1"));
        account(cloud.uri()).signIn().get(10, TimeUnit.SECONDS);

        player.set(new CloudAccount.Player(UUID.randomUUID(), "Sibling"));
        CloudAccount other = account(cloud.uri());
        other.restore();

        assertEquals(CloudAccount.Status.SIGNED_OUT, other.status());
        assertTrue(other.accessToken().isEmpty());
    }

    @Test
    void an_expired_access_token_is_refreshed_quietly() throws Exception {
        cloud.answer(200, challenge()).answer(200, session("access-1", "refresh-1"));
        CloudAccount account = account(cloud.uri());
        account.signIn().get(10, TimeUnit.SECONDS);
        cloud.problem(401, "unauthenticated").answer(200, session("access-2", "refresh-2")).answer(200, accountJson());

        account.client().call(apis -> apis.account().getMe()).get(10, TimeUnit.SECONDS);

        assertEquals(CloudAccount.Status.SIGNED_IN, account.status());
        assertEquals("refresh-2", store.load(PLAYER).orElseThrow().refreshToken());
        assertEquals("Bearer access-2", cloud.requests().getLast().authorization());
    }

    @Test
    void a_rejected_refresh_signs_out_quietly_and_keeps_the_listing() throws Exception {
        cloud.answer(200, challenge()).answer(200, session("access-1", "refresh-1"));
        CloudAccount account = account(cloud.uri());
        account.signIn().get(10, TimeUnit.SECONDS);
        cloud.problem(401, "unauthenticated").problem(401, "refresh_token_invalid");

        CompletableFuture<?> call = account.client().call(apis -> apis.account().getMe());

        assertThrows(ExecutionException.class, () -> call.get(10, TimeUnit.SECONDS));
        assertEquals(CloudAccount.Status.EXPIRED, account.status());
        assertTrue(store.load(PLAYER).isEmpty());
        assertEquals(0, listingsRemoved.get());
    }

    @Test
    void an_unreachable_cloud_during_refresh_keeps_the_session() throws Exception {
        cloud.answer(200, challenge()).answer(200, session("access-1", "refresh-1"));
        CloudAccount online = account(cloud.uri());
        online.signIn().get(10, TimeUnit.SECONDS);
        CloudAccount offline = account(URI.create("http://127.0.0.1:1"));
        offline.restore();

        assertFalse(offline.refresh().get(15, TimeUnit.SECONDS));

        assertEquals(CloudAccount.Status.SIGNED_IN, offline.status());
        assertTrue(store.load(PLAYER).isPresent());
    }

    @Test
    void a_game_mojang_cannot_verify_is_an_unverifiable_account() {
        cloud.answer(200, challenge());
        joinFailure.set(new SessionJoin.JoinException(false, "Invalid session", null));

        assertEquals(SignInFailure.UNVERIFIABLE_ACCOUNT, failure(account(cloud.uri()).signIn()));
    }

    @Test
    void mojang_not_confirming_the_player_is_an_unverifiable_account() {
        cloud.answer(200, challenge()).problem(401, "identity_not_confirmed");

        assertEquals(SignInFailure.UNVERIFIABLE_ACCOUNT, failure(account(cloud.uri()).signIn()));
    }

    @Test
    void mojang_being_unreachable_is_reported_as_such() {
        cloud.answer(200, challenge());
        joinFailure.set(new SessionJoin.JoinException(true, "Mojang down", null));

        assertEquals(SignInFailure.MOJANG_UNAVAILABLE, failure(account(cloud.uri()).signIn()));
    }

    @Test
    void the_cloud_reporting_mojang_down_is_reported_as_such() {
        cloud.answer(200, challenge()).problem(503, "identity_provider_unavailable");

        assertEquals(SignInFailure.MOJANG_UNAVAILABLE, failure(account(cloud.uri()).signIn()));
    }

    @Test
    void an_unreachable_cloud_is_reported_as_such() {
        CloudAccount account = account(URI.create("http://127.0.0.1:1"));

        assertEquals(SignInFailure.CLOUD_UNREACHABLE, failure(account.signIn()));
        assertEquals(CloudAccount.Status.SIGNED_OUT, account.status());
    }

    @Test
    void anything_else_is_unexpected() {
        cloud.answer(200, challenge()).problem(500, "unexpected");

        assertEquals(SignInFailure.UNEXPECTED, failure(account(cloud.uri()).signIn()));
    }

    @Test
    void signing_out_while_offline_removes_the_session_and_listing() throws Exception {
        cloud.answer(200, challenge()).answer(200, session("access-1", "refresh-1"));
        account(cloud.uri()).signIn().get(10, TimeUnit.SECONDS);
        CloudAccount offline = account(URI.create("http://127.0.0.1:1"));
        offline.restore();

        offline.signOut().get(15, TimeUnit.SECONDS);

        assertEquals(CloudAccount.Status.SIGNED_OUT, offline.status());
        assertTrue(store.load(PLAYER).isEmpty());
        assertEquals(1, listingsRemoved.get());
        assertFalse(Files.exists(directory.resolve("sessions").resolve(PLAYER + ".json")));
    }

    @Test
    void signing_out_online_revokes_the_session() throws Exception {
        cloud.answer(200, challenge()).answer(200, session("access-1", "refresh-1")).answer(204, "");
        CloudAccount account = account(cloud.uri());
        account.signIn().get(10, TimeUnit.SECONDS);

        account.signOut().get(10, TimeUnit.SECONDS);

        StubCloud.Request revoke = cloud.requests().getLast();
        assertEquals("/v1/auth/signout", revoke.path());
        assertTrue(revoke.bodyText().contains("refresh-1"));
    }

    private CloudAccount account(URI uri) {
        SessionJoin join = (profile, serverId) -> {
            // The fake Mojang is the only place the Minecraft token would go.
            assertEquals(PLAYER, profile);
            assertFalse(MINECRAFT_TOKEN.isEmpty());
            if (joinFailure.get() != null) {
                throw joinFailure.get();
            }
            joinedServerId.set(serverId);
        };
        return new CloudAccount(uri, Runnable::run, Runnable::run, player::get, join, store, listingsRemoved::incrementAndGet);
    }

    private static SignInFailure failure(CompletableFuture<Void> signIn) {
        ExecutionException thrown = assertThrows(ExecutionException.class, () -> signIn.get(15, TimeUnit.SECONDS));
        return assertInstanceOf(SignInFailure.SignInException.class, thrown.getCause()).failure();
    }

    private static String challenge() {
        return "{\"challengeId\":\"c1\",\"serverId\":\"server-1\",\"expiresAt\":\"2026-10-21T10:01:00Z\"}";
    }

    private static String accountJson() {
        return "{\"uuid\":\"" + PLAYER + "\",\"name\":\"Dirnei\",\"usedBytes\":10,\"quotaBytes\":100}";
    }

    private static String session(String access, String refresh) {
        return "{\"accessToken\":\"" + access + "\",\"accessTokenExpiresAt\":\"2026-10-21T10:15:00Z\",\"refreshToken\":\"" + refresh
                + "\",\"account\":" + accountJson() + "}";
    }
}
