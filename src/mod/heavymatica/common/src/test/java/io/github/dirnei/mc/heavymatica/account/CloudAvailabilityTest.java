package io.github.dirnei.mc.heavymatica.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.cloud.model.ServiceStatus;
import io.github.dirnei.mc.heavymatica.cloud.model.SignInState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CloudAvailabilityTest {

    /** Hands out one pending request per fetch, so a test decides when and how each one answers. */
    private final List<CompletableFuture<ServiceStatus>> fetches = new ArrayList<>();
    private CloudAccess access = CloudAccess.AUTO;

    private CloudAvailability availability(String modVersion, Duration timeout) {
        return new CloudAvailability(() -> {
            CompletableFuture<ServiceStatus> fetch = new CompletableFuture<>();
            fetches.add(fetch);
            return fetch;
        }, modVersion, () -> access, Runnable::run, timeout);
    }

    private CloudAvailability availability() {
        return availability("0.2.0", Duration.ofSeconds(5));
    }

    private static ServiceStatus status(SignInState signIn, String minClient) {
        return new ServiceStatus().signIn(signIn).minClient(minClient);
    }

    @Test
    void nothing_is_asked_until_the_library_opens() {
        CloudAvailability availability = availability();

        assertEquals(0, fetches.size());
        assertEquals(CloudAvailability.Answer.UNKNOWN, availability.answer());
        assertFalse(availability.showCloud(false));
    }

    @Test
    void no_session_sees_the_cloud_only_while_it_is_open() {
        CloudAvailability open = availability();
        open.libraryOpened();
        assertFalse(open.offerSignIn(false), "still pending");
        fetches.getFirst().complete(status(SignInState.OPEN, null));
        assertTrue(open.showCloud(false));
        assertTrue(open.offerSignIn(false));

        CloudAvailability closed = availability();
        closed.libraryOpened();
        fetches.getLast().complete(status(SignInState.CLOSED, null));
        assertFalse(closed.showCloud(false));
        assertFalse(closed.offerSignIn(false));

        CloudAvailability unreachable = availability();
        unreachable.libraryOpened();
        fetches.getLast().completeExceptionally(new java.net.ConnectException("refused"));
        assertEquals(CloudAvailability.Answer.UNREACHABLE, unreachable.answer());
        assertFalse(unreachable.showCloud(false));
    }

    @Test
    void a_stored_session_keeps_the_cloud_whatever_the_status() {
        CloudAvailability closed = availability();
        closed.libraryOpened();
        fetches.getLast().complete(status(SignInState.CLOSED, null));
        assertTrue(closed.showCloud(true));
        assertFalse(closed.offerSignIn(true));

        CloudAvailability unreachable = availability();
        unreachable.libraryOpened();
        fetches.getLast().completeExceptionally(new java.net.ConnectException("refused"));
        assertTrue(unreachable.showCloud(true));
        assertTrue(unreachable.mayContact());
    }

    @Test
    void a_timeout_counts_as_unreachable() {
        CloudAvailability availability = availability("0.2.0", Duration.ofMillis(50));

        availability.libraryOpened();
        CompletableFuture<Void> settled = new CompletableFuture<>();
        availability.onChange(changed -> settled.complete(null));
        settled.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();

        assertEquals(CloudAvailability.Answer.UNREACHABLE, availability.answer());
    }

    @Test
    void an_answer_is_kept_and_refresh_asks_again() {
        CloudAvailability availability = availability();
        availability.libraryOpened();
        fetches.getFirst().complete(status(SignInState.CLOSED, null));

        availability.libraryOpened();
        assertEquals(1, fetches.size());

        availability.refresh();
        assertEquals(2, fetches.size());
        fetches.getLast().complete(status(SignInState.OPEN, null));
        assertEquals(CloudAvailability.Answer.OPEN, availability.answer());
    }

    @Test
    void a_failure_is_asked_again_on_the_next_opening() {
        CloudAvailability availability = availability();
        availability.libraryOpened();
        fetches.getFirst().completeExceptionally(new java.net.ConnectException("refused"));

        availability.libraryOpened();

        assertEquals(2, fetches.size());
    }

    @Test
    void a_running_request_is_not_started_twice() {
        CloudAvailability availability = availability();
        availability.libraryOpened();
        availability.libraryOpened();
        availability.refresh();

        assertEquals(1, fetches.size());
    }

    @Test
    void on_shows_the_cloud_without_asking() {
        access = CloudAccess.ON;
        CloudAvailability availability = availability();

        availability.libraryOpened();
        availability.refresh();

        assertEquals(0, fetches.size());
        assertTrue(availability.showCloud(false));
        assertTrue(availability.offerSignIn(false));
        assertTrue(availability.mayContact());
    }

    @Test
    void off_never_asks_and_hides_the_cloud_even_with_a_session() {
        access = CloudAccess.OFF;
        CloudAvailability availability = availability();

        availability.libraryOpened();
        availability.refresh();

        assertEquals(0, fetches.size());
        assertFalse(availability.showCloud(true));
        assertFalse(availability.offerSignIn(false));
        assertFalse(availability.mayContact());
    }

    @Test
    void an_outdated_mod_offers_no_sign_in_but_keeps_its_session_view() {
        CloudAvailability availability = availability("0.2.0", Duration.ofSeconds(5));
        availability.libraryOpened();
        fetches.getFirst().complete(status(SignInState.OPEN, "0.3.0"));

        assertTrue(availability.tooOld());
        assertTrue(availability.showCloud(false), "the update hint needs the cloud parts");
        assertFalse(availability.offerSignIn(false));
        assertTrue(availability.showCloud(true));

        availability.libraryOpened();
        assertEquals(1, fetches.size(), "too old is an answer and is kept");
    }

    @Test
    void versions_compare_by_dotted_numbers() {
        assertTrue(CloudAvailability.isOlder("0.2.0", "0.3.0"));
        assertTrue(CloudAvailability.isOlder("0.9.9", "0.10.0"));
        assertFalse(CloudAvailability.isOlder("0.3.0", "0.3.0"));
        assertFalse(CloudAvailability.isOlder("0.3.0-alpha", "0.3.0"));
        assertFalse(CloudAvailability.isOlder("0.3", "0.3.0"));
        assertFalse(CloudAvailability.isOlder("1.0.0", "0.3.0"));
        assertFalse(CloudAvailability.isOlder("0.2.0", "not a version"));
        assertFalse(CloudAvailability.isOlder("0.2.0", null));
    }
}
