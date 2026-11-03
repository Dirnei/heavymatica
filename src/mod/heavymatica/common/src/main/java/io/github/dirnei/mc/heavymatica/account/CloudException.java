package io.github.dirnei.mc.heavymatica.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dirnei.mc.heavymatica.cloud.client.ApiException;
import io.github.dirnei.mc.heavymatica.cloud.model.Problem;
import io.github.dirnei.mc.heavymatica.cloud.model.ProblemCode;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.Nullable;

/**
 * A failed cloud call. {@link #status()} is the HTTP status, or 0 when the cloud could not be reached;
 * {@link #code()} is the contract's reason code when the cloud sent problem details.
 */
public final class CloudException extends RuntimeException {

    private final int status;
    private final @Nullable ProblemCode code;

    public CloudException(int status, @Nullable ProblemCode code, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public Optional<ProblemCode> code() {
        return Optional.ofNullable(code);
    }

    /** True when no HTTP answer arrived at all (DNS, connection refused, timeout). */
    public boolean unreachable() {
        return status == 0;
    }

    /** Unwraps the async wrappers around a failure and turns it into a {@link CloudException}. */
    public static CloudException from(Throwable failure, ObjectMapper json) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof CloudException cloud) {
            return cloud;
        }
        if (cause instanceof ApiException api) {
            if (api.getCode() == 0) {
                return new CloudException(0, null, "Heavymatica Cloud is unreachable", api);
            }
            return fromResponse(api.getCode(), api.getResponseBody(), json, api);
        }
        return new CloudException(0, null, "Heavymatica Cloud is unreachable", cause);
    }

    static CloudException fromResponse(int status, @Nullable String body, ObjectMapper json, @Nullable Throwable cause) {
        ProblemCode code = null;
        String message = "Heavymatica Cloud answered " + status;
        if (body != null && !body.isBlank()) {
            try {
                Problem problem = json.readValue(body, Problem.class);
                code = problem.getCode();
                if (problem.getDetail() != null) {
                    message = problem.getDetail();
                } else if (problem.getTitle() != null) {
                    message = problem.getTitle();
                }
            } catch (Exception ignored) {
                // Not problem details (for example a proxy error page); keep the status only.
            }
        }
        return new CloudException(status, code, message, cause);
    }
}
