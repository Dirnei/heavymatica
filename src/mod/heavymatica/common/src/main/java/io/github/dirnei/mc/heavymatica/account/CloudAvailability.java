package io.github.dirnei.mc.heavymatica.account;

import io.github.dirnei.mc.heavymatica.cloud.model.ServiceStatus;
import io.github.dirnei.mc.heavymatica.cloud.model.SignInState;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Whether the game shows the cloud and may contact it. Asks the cloud's status only when the library opens or is
 * refreshed, never at game start, and keeps a real answer for the rest of the game session.
 */
public final class CloudAvailability {

    public enum Answer {
        /** Not asked yet, or the answer is still on its way. */
        UNKNOWN,
        OPEN,
        CLOSED,
        /** No answer: asked again the next time the library opens. */
        UNREACHABLE,
        /** The cloud needs a newer mod. */
        TOO_OLD,
    }

    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Supplier<CompletableFuture<ServiceStatus>> fetcher;
    private final String modVersion;
    private final Supplier<CloudAccess> access;
    private final Executor completion;
    private final Duration timeout;
    private final List<Consumer<CloudAvailability>> listeners = new CopyOnWriteArrayList<>();

    private volatile Answer answer = Answer.UNKNOWN;
    private volatile boolean asking;

    /**
     * @param fetcher    the anonymous status request
     * @param completion where answers are applied, the render thread in game
     */
    public CloudAvailability(Supplier<CompletableFuture<ServiceStatus>> fetcher, String modVersion, Supplier<CloudAccess> access,
                             Executor completion, Duration timeout) {
        this.fetcher = fetcher;
        this.modVersion = modVersion;
        this.access = access;
        this.completion = completion;
        this.timeout = timeout;
    }

    /** No restriction: the cloud always shows and may always be contacted, without asking its status. */
    public static CloudAvailability unrestricted() {
        return new CloudAvailability(() -> CompletableFuture.failedFuture(new IllegalStateException("No status")), "0",
                () -> CloudAccess.ON, Runnable::run, TIMEOUT);
    }

    public Answer answer() {
        return answer;
    }

    public CloudAccess access() {
        return access.get();
    }

    /** Subscribes to changes; run the returned handle to unsubscribe. */
    public Runnable onChange(Consumer<CloudAvailability> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** The library opened: asks the status unless an answer is already kept or a request is running. */
    public void libraryOpened() {
        if (access.get() != CloudAccess.AUTO || asking) {
            return;
        }
        if (answer == Answer.UNKNOWN || answer == Answer.UNREACHABLE) {
            ask();
        }
    }

    /** The player asked for a refresh: asks the status again. */
    public void refresh() {
        if (access.get() == CloudAccess.AUTO && !asking) {
            ask();
        }
    }

    /** Whether the library shows its cloud parts, given whether the player has a stored session. */
    public boolean showCloud(boolean hasSession) {
        return switch (access.get()) {
            case OFF -> false;
            case ON -> true;
            case AUTO -> hasSession || answer == Answer.OPEN || answer == Answer.TOO_OLD;
        };
    }

    public boolean offerSignIn(boolean hasSession) {
        return showCloud(hasSession) && !hasSession && !tooOld();
    }

    /** False only when the player turned the cloud off: then nothing may reach it. */
    public boolean mayContact() {
        return access.get() != CloudAccess.OFF;
    }

    public boolean tooOld() {
        return access.get() == CloudAccess.AUTO && answer == Answer.TOO_OLD;
    }

    private void ask() {
        asking = true;
        CompletableFuture<ServiceStatus> request;
        try {
            request = fetcher.get();
        } catch (RuntimeException e) {
            request = CompletableFuture.failedFuture(e);
        }
        request.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).whenCompleteAsync((status, failure) -> {
            asking = false;
            answer = failure != null || status == null || status.getSignIn() == null ? Answer.UNREACHABLE : answerOf(status);
            listeners.forEach(listener -> listener.accept(this));
        }, completion);
    }

    private Answer answerOf(ServiceStatus status) {
        if (isOlder(modVersion, status.getMinClient())) {
            return Answer.TOO_OLD;
        }
        return status.getSignIn() == SignInState.OPEN ? Answer.OPEN : Answer.CLOSED;
    }

    /**
     * True when {@code running} is older than {@code minimum}, compared by dotted numbers. A suffix such as
     * {@code -alpha} is ignored, and a minimum that is no version means no minimum, so a typo on the server cannot
     * lock every player out.
     */
    static boolean isOlder(String running, @Nullable String minimum) {
        int[] min = parse(minimum);
        int[] own = parse(running);
        if (min == null || own == null) {
            return false;
        }
        for (int i = 0; i < Math.max(min.length, own.length); i++) {
            int a = i < own.length ? own[i] : 0;
            int b = i < min.length ? min[i] : 0;
            if (a != b) {
                return a < b;
            }
        }
        return false;
    }

    private static int @Nullable [] parse(@Nullable String version) {
        if (version == null) {
            return null;
        }
        String core = version.trim();
        for (char separator : new char[] {'-', '+', ' '}) {
            int at = core.indexOf(separator);
            if (at >= 0) {
                core = core.substring(0, at);
            }
        }
        if (!core.matches("\\d{1,9}(\\.\\d{1,9})*")) {
            return null;
        }
        String[] parts = core.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = Integer.parseInt(parts[i]);
        }
        return numbers;
    }
}
