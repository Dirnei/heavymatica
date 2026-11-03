package io.github.dirnei.mc.heavymatica.account;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Development only: joins at the cloud's DevMojang instead of Mojang, for dev clients that have no Microsoft
 * session. Sends a placeholder instead of any real token.
 */
public final class DevSessionJoin implements SessionJoin {

    private final URI joinUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** @param devMojang base address of DevMojang, for example {@code http://localhost:5180/dev/mojang} */
    public DevSessionJoin(URI devMojang) {
        String base = devMojang.toString().endsWith("/") ? devMojang.toString() : devMojang + "/";
        this.joinUrl = URI.create(base + "session/minecraft/join");
    }

    @Override
    public void join(UUID profile, String serverId) throws JoinException {
        String body = "{\"accessToken\":\"dev\",\"selectedProfile\":\"" + profile.toString().replace("-", "") + "\",\"serverId\":\"" + serverId + "\"}";
        HttpRequest request = HttpRequest.newBuilder(joinUrl)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                throw new JoinException(false, "DevMojang answered " + response.statusCode(), null);
            }
        } catch (IOException e) {
            throw new JoinException(true, "DevMojang is unreachable.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JoinException(true, "Interrupted while joining.", e);
        }
    }
}
