package io.github.dirnei.mc.heavymatica.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dirnei.mc.heavymatica.cloud.api.AccountApi;
import io.github.dirnei.mc.heavymatica.cloud.api.AuthApi;
import io.github.dirnei.mc.heavymatica.cloud.api.SchematicsApi;
import io.github.dirnei.mc.heavymatica.cloud.client.ApiClient;
import io.github.dirnei.mc.heavymatica.cloud.client.ApiException;
import io.github.dirnei.mc.heavymatica.cloud.model.UploadResult;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The only way feature code talks to the Heavymatica Cloud (D4). Wraps the client generated from the contract:
 * adds the bearer token, refreshes it once when the cloud answers 401, turns failures into {@link CloudException}
 * and completes every call on the given executor (the render thread in game), so callers never block a frame.
 *
 * <p>The generated client cannot send raw binary bodies (it serializes them as JSON), so the three binary
 * operations are sent here by hand while still using the generated models for their answers.
 */
public final class CloudClient {

    /** The generated APIs, sharing one HTTP client and one token interceptor. */
    public record Apis(AuthApi auth, AccountApi account, SchematicsApi schematics) {
    }

    @FunctionalInterface
    public interface Call<T> {
        CompletableFuture<T> send(Apis apis) throws ApiException;
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(5);

    private final URI baseUri;
    private final TokenSource tokens;
    private final Executor completion;
    private final HttpClient http;
    private final ObjectMapper json;
    private final Apis apis;

    public CloudClient(URI baseUri, TokenSource tokens, Executor completion) {
        this.baseUri = trimSlash(baseUri);
        this.tokens = tokens;
        this.completion = completion;
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        this.http = builder.build();
        this.json = ApiClient.createDefaultObjectMapper();
        ApiClient client = new ApiClient(builder, json, this.baseUri.toString());
        client.setReadTimeout(TIMEOUT);
        client.setRequestInterceptor(request -> tokens.accessToken().ifPresent(token -> request.setHeader("Authorization", "Bearer " + token)));
        this.apis = new Apis(new AuthApi(client), new AccountApi(client), new SchematicsApi(client));
    }

    public URI baseUri() {
        return baseUri;
    }

    public ObjectMapper json() {
        return json;
    }

    /** Sends a call through the generated APIs. */
    public <T> CompletableFuture<T> call(Call<T> call) {
        return deliver(withRefresh(() -> {
            try {
                return call.send(apis);
            } catch (ApiException e) {
                return CompletableFuture.failedFuture(e);
            }
        }));
    }

    /** Uploads a file as a new schematic (operation {@code uploadSchematic}). */
    public CompletableFuture<UploadResult> uploadSchematic(String name, Path file) {
        return deliver(withRefresh(() -> sendBinary("POST",
                "/v1/schematics?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8), "application/octet-stream", publisher(file))
                .thenApply(body -> read(body, UploadResult.class))));
    }

    /** Uploads a file as a new version of an existing schematic (operation {@code uploadVersion}). */
    public CompletableFuture<UploadResult> uploadVersion(String schematicId, Path file) {
        return deliver(withRefresh(() -> sendBinary("POST",
                "/v1/schematics/" + segment(schematicId) + "/versions", "application/octet-stream", publisher(file))
                .thenApply(body -> read(body, UploadResult.class))));
    }

    /** Attaches a PNG thumbnail to a version (operation {@code attachThumbnail}). */
    public CompletableFuture<Void> attachThumbnail(String schematicId, int versionNumber, byte[] png) {
        return deliver(withRefresh(() -> sendBinary("PUT",
                "/v1/schematics/" + segment(schematicId) + "/versions/" + versionNumber + "/thumbnail", "image/png",
                HttpRequest.BodyPublishers.ofByteArray(png))
                .thenApply(body -> (Void) null)));
    }

    private CompletableFuture<String> sendBinary(String method, String pathAndQuery, String contentType, HttpRequest.BodyPublisher body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUri + pathAndQuery))
                .timeout(UPLOAD_TIMEOUT)
                .header("Content-Type", contentType)
                .header("Accept", "application/json")
                .method(method, body);
        tokens.accessToken().ifPresent(token -> request.header("Authorization", "Bearer " + token));
        return http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() / 100 != 2) {
                throw CloudException.fromResponse(response.statusCode(), response.body(), json, null);
            }
            return response.body();
        });
    }

    /** Runs the call; when the cloud answers 401 and the token can be renewed, runs it exactly once more. */
    private <T> CompletableFuture<T> withRefresh(Supplier<CompletableFuture<T>> attempt) {
        return start(attempt).handle((value, failure) -> {
            if (failure == null) {
                return CompletableFuture.completedFuture(value);
            }
            CloudException error = CloudException.from(failure, json);
            if (error.status() != 401) {
                return CompletableFuture.<T>failedFuture(error);
            }
            return tokens.refresh().thenCompose(renewed -> renewed
                    ? start(attempt).exceptionallyCompose(retry -> CompletableFuture.failedFuture(CloudException.from(retry, json)))
                    : CompletableFuture.<T>failedFuture(error));
        }).thenCompose(future -> future);
    }

    private static <T> CompletableFuture<T> start(Supplier<CompletableFuture<T>> attempt) {
        try {
            return attempt.get();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Development aid: holds every answer back this long, to check that slow calls never block the game. */
    private static volatile long developmentDelayMillis;

    public static void developmentDelay(long millis) {
        developmentDelayMillis = millis;
    }

    private <T> CompletableFuture<T> deliver(CompletableFuture<T> answer) {
        long delay = developmentDelayMillis;
        CompletableFuture<T> future = delay <= 0 ? answer : answer.handleAsync((value, failure) -> {
            if (failure != null) {
                throw failure instanceof java.util.concurrent.CompletionException completion ? completion : new java.util.concurrent.CompletionException(failure);
            }
            return value;
        }, CompletableFuture.delayedExecutor(delay, java.util.concurrent.TimeUnit.MILLISECONDS));
        CompletableFuture<T> delivered = new CompletableFuture<>();
        future.whenComplete((value, failure) -> completion.execute(() -> {
            if (failure != null) {
                delivered.completeExceptionally(CloudException.from(failure, json));
            } else {
                delivered.complete(value);
            }
        }));
        return delivered;
    }

    private <T> T read(String body, Class<T> type) {
        try {
            return json.readValue(body, type);
        } catch (IOException e) {
            throw new CloudException(200, null, "Unreadable answer from Heavymatica Cloud", e);
        }
    }

    private static HttpRequest.BodyPublisher publisher(Path file) {
        try {
            return HttpRequest.BodyPublishers.ofFile(file);
        } catch (IOException e) {
            throw new CloudException(0, null, "Cannot read " + file, e);
        }
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static URI trimSlash(URI uri) {
        String text = uri.toString();
        return text.endsWith("/") ? URI.create(text.substring(0, text.length() - 1)) : uri;
    }
}
