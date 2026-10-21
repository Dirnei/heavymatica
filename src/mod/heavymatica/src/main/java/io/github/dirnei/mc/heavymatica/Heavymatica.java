package io.github.dirnei.mc.heavymatica;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Heavymatica.MOD_ID)
public final class Heavymatica {

    public static final String MOD_ID = "heavymatica";
    public static final Logger LOGGER = LoggerFactory.getLogger(Heavymatica.class);

    public Heavymatica(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("Heavymatica loaded");
    }
}
