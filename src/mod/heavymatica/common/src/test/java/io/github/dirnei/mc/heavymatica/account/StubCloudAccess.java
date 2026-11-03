package io.github.dirnei.mc.heavymatica.account;

import io.github.dirnei.mc.heavymatica.cloud.client.ApiClient;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Gives tests outside this package a stub cloud and an account signed in against it. */
public final class StubCloudAccess implements AutoCloseable {

    public static final UUID PLAYER = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    private final StubCloud stub;

    public StubCloudAccess() throws java.io.IOException {
        stub = new StubCloud();
    }

    public Stub stub() {
        return new Stub(stub);
    }

    /** An account for {@link #PLAYER}; when {@code signedIn}, a stored session makes it signed in without any request. */
    public CloudAccount account(Path directory, boolean signedIn) {
        SessionStore store = new SessionStore(directory.resolve("sessions"), ApiClient.createDefaultObjectMapper());
        if (signedIn) {
            store.save(new SessionStore.StoredSession(PLAYER, "Dirnei", "access", OffsetDateTime.now().plusMinutes(15), "refresh"));
        }
        CloudAccount account = new CloudAccount(stub.uri(), Runnable::run, Runnable::run, () -> new CloudAccount.Player(PLAYER, "Dirnei"),
                (profile, serverId) -> { }, store, () -> { });
        account.restore();
        return account;
    }

    @Override
    public void close() {
        stub.close();
    }

    /** The stub cloud's public surface for other packages. */
    public static final class Stub {
        private final StubCloud stub;

        Stub(StubCloud stub) {
            this.stub = stub;
        }

        public java.net.URI uri() {
            return stub.uri();
        }

        public Stub answer(int status, String json) {
            stub.answer(status, json);
            return this;
        }

        public Stub answerBytes(int status, byte[] body) {
            stub.answerBytes(status, body);
            return this;
        }

        public Stub problem(int status, String code) {
            stub.problem(status, code);
            return this;
        }

        public java.util.List<String> requests() {
            return stub.requests().stream().map(request -> request.method() + " " + request.path()).toList();
        }
    }
}
