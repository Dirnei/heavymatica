package io.github.dirnei.mc.heavymatica.account;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * Joins through the game's own session service, exactly as joining a multiplayer server does. The Minecraft access
 * token goes to Mojang here and nowhere else.
 */
public final class GameSessionJoin implements SessionJoin {

    @Override
    public void join(UUID profile, String serverId) throws JoinException {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            minecraft.services().sessionService().joinServer(profile, minecraft.getUser().getAccessToken(), serverId);
        } catch (AuthenticationUnavailableException e) {
            throw new JoinException(true, "Mojang's session service is unavailable.", e);
        } catch (AuthenticationException e) {
            throw new JoinException(false, "Mojang rejected the game's session.", e);
        }
    }
}
