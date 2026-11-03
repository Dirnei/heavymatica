package io.github.dirnei.mc.heavymatica.account;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dirnei.mc.heavymatica.cloud.client.ApiClient;
import io.github.dirnei.mc.heavymatica.cloud.model.Account;
import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Signs in against a running cloud (the Aspire AppHost) through its DevMojang. Opt-in: set HEAVYMATICA_CLOUD_URL,
 * for example {@code HEAVYMATICA_CLOUD_URL=http://localhost:5180 ./gradlew :heavymatica:test}.
 */
@EnabledIfEnvironmentVariable(named = "HEAVYMATICA_CLOUD_URL", matches = "https?://.+")
class AppHostSignInTest {

    @Test
    void signs_in_through_dev_mojang_and_reads_the_account(@TempDir Path directory) throws Exception {
        URI cloud = URI.create(System.getenv("HEAVYMATICA_CLOUD_URL"));
        UUID profile = UUID.randomUUID();
        CloudAccount account = new CloudAccount(cloud, Runnable::run, Runnable::run, () -> new CloudAccount.Player(profile, "AppHostTester"),
                new DevSessionJoin(URI.create(cloud + "/dev/mojang")), new SessionStore(directory, ApiClient.createDefaultObjectMapper()), () -> { });

        account.signIn().get(30, TimeUnit.SECONDS);
        if (account.status() == CloudAccount.Status.CHOOSING) {
            account.createAccount().get(30, TimeUnit.SECONDS);
        }
        Account me = account.refreshAccount().get(30, TimeUnit.SECONDS);

        assertEquals(CloudAccount.Status.SIGNED_IN, account.status());
        assertEquals(profile, me.getUuid());
        assertEquals("AppHostTester", me.getName());
    }
}
