package io.github.dirnei.mc.heavymatica.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.cloud.model.Account;
import io.github.dirnei.mc.heavymatica.cloud.model.ProblemCode;
import io.github.dirnei.mc.heavymatica.cloud.model.UploadResult;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CloudClientTest {

    private static final String ACCOUNT = "{\"uuid\":\"8667ba71-b85a-4004-af54-457a9734eed7\",\"name\":\"Dirnei\",\"usedBytes\":10,\"quotaBytes\":100}";
    private static final String VERSION = "{\"number\":1,\"sha256\":\"ab\",\"format\":\"litematica\",\"sizeBytes\":5,"
            + "\"dimensions\":{\"width\":1,\"height\":1,\"length\":1},\"dataVersion\":null,\"blockTotal\":1,"
            + "\"blockCounts\":[{\"block\":\"minecraft:stone\",\"count\":1}],\"skippedEntities\":0,\"hasThumbnail\":false,"
            + "\"createdAt\":\"2026-10-21T10:00:00Z\"}";

    private StubCloud cloud;
    private ExecutorService renderThread;
    private final AtomicReference<Thread> renderThreadRef = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        cloud = new StubCloud();
        renderThread = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "render");
            renderThreadRef.set(thread);
            return thread;
        });
    }

    @AfterEach
    void stop() {
        cloud.close();
        renderThread.shutdownNow();
    }

    @Test
    void calls_send_the_bearer_token_and_complete_on_the_render_thread() throws Exception {
        cloud.answer(200, ACCOUNT);
        CloudClient client = new CloudClient(cloud.uri(), new FixedTokens("token-1"), renderThread);
        AtomicReference<Thread> completedOn = new AtomicReference<>();

        Account account = client.call(apis -> apis.account().getMe())
                .whenComplete((value, failure) -> completedOn.set(Thread.currentThread()))
                .get(10, TimeUnit.SECONDS);

        assertEquals("Dirnei", account.getName());
        assertEquals("Bearer token-1", cloud.requests().getFirst().authorization());
        assertEquals(renderThreadRef.get(), completedOn.get());
    }

    @Test
    void a_401_refreshes_once_and_retries_with_the_new_token() throws Exception {
        cloud.problem(401, "unauthenticated").answer(200, ACCOUNT);
        FixedTokens tokens = new FixedTokens("old").renewTo("new");
        CloudClient client = new CloudClient(cloud.uri(), tokens, renderThread);

        Account account = client.call(apis -> apis.account().getMe()).get(10, TimeUnit.SECONDS);

        assertEquals("Dirnei", account.getName());
        assertEquals(1, tokens.refreshes.get());
        assertEquals(List.of("Bearer old", "Bearer new"), cloud.requests().stream().map(StubCloud.Request::authorization).toList());
    }

    @Test
    void a_second_401_after_refreshing_fails_without_refreshing_again() {
        cloud.problem(401, "unauthenticated").problem(401, "unauthenticated");
        FixedTokens tokens = new FixedTokens("old").renewTo("new");
        CloudClient client = new CloudClient(cloud.uri(), tokens, renderThread);

        CloudException error = failure(client.call(apis -> apis.account().getMe()));

        assertEquals(401, error.status());
        assertEquals(1, tokens.refreshes.get());
        assertEquals(2, cloud.requests().size());
    }

    @Test
    void a_401_without_a_renewable_session_fails_as_unauthenticated() {
        cloud.problem(401, "unauthenticated");
        CloudClient client = new CloudClient(cloud.uri(), new FixedTokens("old"), renderThread);

        CloudException error = failure(client.call(apis -> apis.account().getMe()));

        assertEquals(401, error.status());
        assertEquals(Optional.of(ProblemCode.UNAUTHENTICATED), error.code());
        assertEquals(1, cloud.requests().size());
    }

    @Test
    void uploads_send_the_raw_file_and_read_the_generated_model(@TempDir Path dir) throws Exception {
        Path file = Files.write(dir.resolve("tower.litematic"), new byte[] {1, 2, 3, 4, 5});
        cloud.answer(201, "{\"schematicId\":\"s1\",\"version\":" + VERSION + "}");
        CloudClient client = new CloudClient(cloud.uri(), new FixedTokens("t"), renderThread);

        UploadResult result = client.uploadSchematic("My Tower", file).get(10, TimeUnit.SECONDS);

        StubCloud.Request request = cloud.requests().getFirst();
        assertEquals("POST", request.method());
        assertEquals("/v1/schematics?name=My+Tower", request.path());
        assertEquals("application/octet-stream", request.contentType());
        assertEquals("Bearer t", request.authorization());
        assertTrue(java.util.Arrays.equals(new byte[] {1, 2, 3, 4, 5}, request.body()));
        assertEquals("s1", result.getSchematicId());
        assertNull(result.getVersion().getDataVersion());
    }

    @Test
    void problem_details_become_reason_codes(@TempDir Path dir) throws Exception {
        Path file = Files.write(dir.resolve("big.schem"), new byte[] {1});
        cloud.problem(422, "over_quota");
        CloudClient client = new CloudClient(cloud.uri(), new FixedTokens("t"), renderThread);

        CloudException error = failure(client.uploadSchematic("big", file));

        assertEquals(422, error.status());
        assertEquals(Optional.of(ProblemCode.OVER_QUOTA), error.code());
    }

    @Test
    void an_unreachable_cloud_is_reported_as_status_zero() {
        CloudClient client = new CloudClient(URI.create("http://127.0.0.1:1"), TokenSource.NONE, renderThread);

        CloudException error = failure(client.call(apis -> apis.account().getMe()));

        assertTrue(error.unreachable());
    }

    private static CloudException failure(CompletableFuture<?> future) {
        ExecutionException thrown = assertThrows(ExecutionException.class, () -> future.get(15, TimeUnit.SECONDS));
        return assertInstanceOf(CloudException.class, thrown.getCause());
    }

    private static final class FixedTokens implements TokenSource {
        private volatile String token;
        private String renewed;
        final AtomicInteger refreshes = new AtomicInteger();

        FixedTokens(String token) {
            this.token = token;
        }

        FixedTokens renewTo(String next) {
            this.renewed = next;
            return this;
        }

        @Override
        public Optional<String> accessToken() {
            return Optional.ofNullable(token);
        }

        @Override
        public CompletableFuture<Boolean> refresh() {
            refreshes.incrementAndGet();
            if (renewed == null) {
                return CompletableFuture.completedFuture(false);
            }
            token = renewed;
            return CompletableFuture.completedFuture(true);
        }
    }
}
