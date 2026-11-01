package io.github.dirnei.mc.heavymatica.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.cloud.client.ApiClient;
import io.github.dirnei.mc.heavymatica.cloud.model.ProblemCode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

/** Runs against a stub cloud and a fake Mojang join. */
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
        cloud.answer(200, challenge()).answer(200, signedIn("access-1", "refresh-1"));
        CloudAccount account = account(cloud.uri());

        account.signIn().get(10, TimeUnit.SECONDS);

        assertEquals(CloudAccount.Status.SIGNED_IN, account.status());
        assertEquals("server-1", joinedServerId.get());
        assertEquals("Dirnei", account.name().orElseThrow());
        assertEquals("access-1", store.load(PLAYER).orElseThrow().accessToken());
        assertEquals("/v1/auth/sign-in", cloud.requests().get(1).path());
        assertTrue(cloud.requests().get(1).bodyText().contains("\"name\":\"Dirnei\""));
    }

    @Test
    void an_unknown_minecraft_account_waits_for_a_choice_and_stores_nothing() throws Exception {
        cloud.answer(200, challenge()).answer(200, pending(true));
        CloudAccount account = account(cloud.uri());

        account.signIn().get(10, TimeUnit.SECONDS);

        assertEquals(CloudAccount.Status.CHOOSING, account.status());
        assertFalse(account.signedIn());
        assertTrue(account.pending().orElseThrow().getJoinAvailable());
        assertEquals("Dirnei", account.name().orElseThrow());
        assertTrue(store.load(PLAYER).isEmpty());
        assertFalse(Files.exists(directory.resolve("sessions")));
        assertTrue(account.accessToken().isEmpty());
    }

    @Test
    void create_completes_the_pending_sign_in_with_a_new_account() throws Exception {
        cloud.answer(200, challenge()).answer(200, pending(true)).answer(200, session("access-1", "refresh-1"));
        CloudAccount account = account(cloud.uri());
        account.signIn().get(10, TimeUnit.SECONDS);

        account.createAccount().get(10, TimeUnit.SECONDS);

        StubCloud.Request complete = cloud.requests().getLast();
        assertEquals("/v1/auth/pending/session", complete.path());
        assertEquals("Bearer pending-1", complete.authorization());
        assertTrue(complete.bodyText().contains("\"createAccount\":true"));
        assertEquals(CloudAccount.Status.SIGNED_IN, account.status());
        assertTrue(account.pending().isEmpty());
        assertEquals("access-1", store.load(PLAYER).orElseThrow().accessToken());
    }

    @Test
    void cancelling_the_choice_signs_out_without_any_request() throws Exception {
        cloud.answer(200, challenge()).answer(200, pending(true));
        CloudAccount account = account(cloud.uri());
        account.signIn().get(10, TimeUnit.SECONDS);
        int requestsBefore = cloud.requests().size();

        account.cancelChoice();

        assertEquals(CloudAccount.Status.SIGNED_OUT, account.status());
        assertTrue(account.pending().isEmpty());
        assertEquals(requestsBefore, cloud.requests().size());
        assertTrue(store.load(PLAYER).isEmpty());
    }

    @Test
    void a_confirmed_join_signs_in_to_the_joined_account() throws Exception {
        cloud.answer(200, challenge()).answer(200, pending(true))
                .answer(201, linkCode("open")).answer(200, linkCode("redeemed")).answer(200, linkCode("linked"))
                .answer(200, session("access-joined", "refresh-joined"));
        CloudAccount account = account(cloud.uri());
        account.signIn().get(10, TimeUnit.SECONDS);

        String codeId = account.issueLinkCode().get(10, TimeUnit.SECONDS).getId();
        account.pollLinkCode(codeId).get(10, TimeUnit.SECONDS);
        account.confirmLinkCode(codeId).get(10, TimeUnit.SECONDS);
        account.completeJoin().get(10, TimeUnit.SECONDS);

        List<StubCloud.Request> requests = cloud.requests();
        assertEquals("POST /v1/link-codes", requests.get(2).method() + " " + requests.get(2).path());
        assertEquals("GET /v1/link-codes/code-1", requests.get(3).method() + " " + requests.get(3).path());
        assertEquals("POST /v1/link-codes/code-1/confirm", requests.get(4).method() + " " + requests.get(4).path());
        for (StubCloud.Request request : requests.subList(2, 6)) {
            assertEquals("Bearer pending-1", request.authorization());
        }
        assertTrue(requests.get(5).bodyText().contains("\"createAccount\":false"));
        assertEquals(CloudAccount.Status.SIGNED_IN, account.status());
        assertEquals("access-joined", store.load(PLAYER).orElseThrow().accessToken());
    }

    @Test
    void completing_a_join_before_it_was_confirmed_keeps_choosing() throws Exception {
        cloud.answer(200, challenge()).answer(200, pending(true)).problem(409, "account_choice_required");
        CloudAccount account = account(cloud.uri());
        account.signIn().get(10, TimeUnit.SECONDS);

        ExecutionException thrown = assertThrows(ExecutionException.class, () -> account.completeJoin().get(10, TimeUnit.SECONDS));

        assertEquals(ProblemCode.ACCOUNT_CHOICE_REQUIRED, CloudException.from(thrown, ApiClient.createDefaultObjectMapper()).code().orElseThrow());
        assertEquals(CloudAccount.Status.CHOOSING, account.status());
        assertTrue(store.load(PLAYER).isEmpty());
    }

    @Test
    void a_rejected_pending_token_is_never_refreshed() throws Exception {
        cloud.answer(200, challenge()).answer(200, pending(true)).problem(401, "unauthenticated");
        CloudAccount account = account(cloud.uri());
        account.signIn().get(10, TimeUnit.SECONDS);

        assertThrows(ExecutionException.class, () -> account.issueLinkCode().get(10, TimeUnit.SECONDS));

        assertEquals(3, cloud.requests().size());
        assertEquals(CloudAccount.Status.CHOOSING, account.status());
    }

    @Test
    void a_signed_in_account_issues_and_cancels_codes_with_its_session() throws Exception {
        cloud.answer(200, challenge()).answer(200, signedIn("access-1", "refresh-1")).answer(201, linkCode("open")).answer(204, "");
        CloudAccount account = account(cloud.uri());
        account.signIn().get(10, TimeUnit.SECONDS);

        String codeId = account.issueLinkCode().get(10, TimeUnit.SECONDS).getId();
        account.cancelLinkCode(codeId).get(10, TimeUnit.SECONDS);

        StubCloud.Request cancel = cloud.requests().getLast();
        assertEquals("DELETE /v1/link-codes/code-1", cancel.method() + " " + cancel.path());
        assertEquals("Bearer access-1", cancel.authorization());
        assertEquals("Bearer access-1", cloud.requests().get(2).authorization());
    }

    @Test
    void unlinking_an_alt_keeps_the_player_signed_in() throws Exception {
        cloud.answer(200, challenge()).answer(200, signedIn("access-1", "refresh-1")).answer(200, accountWithIdentities())
                .answer(204, "").answer(200, accountJson());
        CloudAccount account = account(cloud.uri());
        account.signIn().get(10, TimeUnit.SECONDS);
        account.refreshAccount().get(10, TimeUnit.SECONDS);

        account.unlink("id-alt").get(10, TimeUnit.SECONDS);

        StubCloud.Request unlink = cloud.requests().get(3);
        assertEquals("DELETE /v1/me/identities/id-alt", unlink.method() + " " + unlink.path());
        assertEquals("/v1/me", cloud.requests().getLast().path());
        assertEquals(CloudAccount.Status.SIGNED_IN, account.status());
        assertTrue(store.load(PLAYER).isPresent());
    }

    @Test
    void unlinking_the_sign_in_in_use_signs_out() throws Exception {
        cloud.answer(200, challenge()).answer(200, signedIn("access-1", "refresh-1")).answer(200, accountWithIdentities()).answer(204, "");
        CloudAccount account = account(cloud.uri());
        account.signIn().get(10, TimeUnit.SECONDS);
        account.refreshAccount().get(10, TimeUnit.SECONDS);

        account.unlink("id-main").get(10, TimeUnit.SECONDS);

        assertEquals(4, cloud.requests().size());
        assertEquals(CloudAccount.Status.SIGNED_OUT, account.status());
        assertTrue(store.load(PLAYER).isEmpty());
        assertEquals(1, listingsRemoved.get());
    }

    @Test
    void the_last_sign_in_cannot_be_unlinked() throws Exception {
        cloud.answer(200, challenge()).answer(200, signedIn("access-1", "refresh-1")).problem(409, "last_identity");
        CloudAccount account = account(cloud.uri());
        account.signIn().get(10, TimeUnit.SECONDS);

        ExecutionException thrown = assertThrows(ExecutionException.class, () -> account.unlink("id-main").get(10, TimeUnit.SECONDS));

        assertEquals(ProblemCode.LAST_IDENTITY, CloudException.from(thrown, ApiClient.createDefaultObjectMapper()).code().orElseThrow());
        assertEquals(CloudAccount.Status.SIGNED_IN, account.status());
    }

    @Test
    void the_dev_redeemer_signs_in_a_second_identity_and_redeems_as_pending() throws Exception {
        UUID alt = UUID.fromString("0b1c2d3e-4f50-6172-8394-a5b6c7d8e9f0");
        AtomicReference<UUID> joined = new AtomicReference<>();
        cloud.answer(200, challenge()).answer(200, pending(false)).answer(200, linkCode("redeemed"));
        DevLinkRedeemer redeemer = new DevLinkRedeemer(cloud.uri(), (profile, serverId) -> joined.set(profile), Runnable::run, Runnable::run);

        DevLinkRedeemer.Redeemed redeemed = redeemer.redeem(alt, "DirneiAlt", "K7QM-3XRP").get(10, TimeUnit.SECONDS);

        assertEquals("pending", redeemed.principal());
        assertEquals(alt, joined.get());
        StubCloud.Request redeem = cloud.requests().getLast();
        assertEquals("POST /v1/link-codes/redeem", redeem.method() + " " + redeem.path());
        assertEquals("Bearer pending-1", redeem.authorization());
        assertTrue(redeem.bodyText().contains("K7QM-3XRP"));
    }

    @Test
    void the_minecraft_token_never_reaches_the_cloud() throws Exception {
        cloud.answer(200, challenge()).answer(200, signedIn("access-1", "refresh-1"));
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
        cloud.answer(200, challenge()).answer(200, signedIn("access-1", "refresh-1"));
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
        cloud.answer(200, challenge()).answer(200, signedIn("access-1", "refresh-1"));
        account(cloud.uri()).signIn().get(10, TimeUnit.SECONDS);

        player.set(new CloudAccount.Player(UUID.randomUUID(), "Sibling"));
        CloudAccount other = account(cloud.uri());
        other.restore();

        assertEquals(CloudAccount.Status.SIGNED_OUT, other.status());
        assertTrue(other.accessToken().isEmpty());
    }

    @Test
    void an_expired_access_token_is_refreshed_quietly() throws Exception {
        cloud.answer(200, challenge()).answer(200, signedIn("access-1", "refresh-1"));
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
        cloud.answer(200, challenge()).answer(200, signedIn("access-1", "refresh-1"));
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
        cloud.answer(200, challenge()).answer(200, signedIn("access-1", "refresh-1"));
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
        cloud.answer(200, challenge()).answer(200, signedIn("access-1", "refresh-1"));
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
        cloud.answer(200, challenge()).answer(200, signedIn("access-1", "refresh-1")).answer(204, "");
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
        return "{\"id\":\"0123456789abcdef0123456789abcdef\",\"provider\":\"minecraft\",\"uuid\":\"" + PLAYER
                + "\",\"name\":\"Dirnei\",\"usedBytes\":10,\"quotaBytes\":100}";
    }

    private static String accountWithIdentities() {
        return "{\"id\":\"0123456789abcdef0123456789abcdef\",\"provider\":\"minecraft\",\"uuid\":\"" + PLAYER
                + "\",\"name\":\"Dirnei\",\"usedBytes\":10,\"quotaBytes\":100,\"linkingAvailable\":true,\"identities\":["
                + "{\"id\":\"id-main\",\"provider\":\"minecraft\",\"name\":\"Dirnei\",\"current\":true},"
                + "{\"id\":\"id-alt\",\"provider\":\"minecraft\",\"name\":\"DirneiAlt\",\"current\":false}]}";
    }

    private static String signedIn(String access, String refresh) {
        return "{\"session\":" + session(access, refresh) + "}";
    }

    private static String pending(boolean joinAvailable) {
        return "{\"pending\":{\"pendingToken\":\"pending-1\",\"expiresAt\":\"2026-10-21T10:10:00Z\",\"identity\":{\"provider\":\"minecraft\","
                + "\"name\":\"Dirnei\"},\"joinAvailable\":" + joinAvailable + "}}";
    }

    private static String linkCode(String status) {
        return "{\"id\":\"code-1\",\"code\":\"K7QM-3XRP\",\"linkUrl\":\"http://localhost:4321/link/?code=K7QM-3XRP\",\"status\":\"" + status
                + "\",\"expiresAt\":\"2026-10-21T10:05:00Z\",\"issuer\":{\"provider\":\"minecraft\",\"name\":\"Dirnei\"}}";
    }

    private static String session(String access, String refresh) {
        return "{\"accessToken\":\"" + access + "\",\"accessTokenExpiresAt\":\"2026-10-21T10:15:00Z\",\"refreshToken\":\"" + refresh
                + "\",\"account\":" + accountJson() + "}";
    }
}
