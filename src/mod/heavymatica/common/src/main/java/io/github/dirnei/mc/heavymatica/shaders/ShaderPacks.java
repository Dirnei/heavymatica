package io.github.dirnei.mc.heavymatica.shaders;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import net.neoforged.fml.ModList;

/** Whether a shader pack renders the world right now. Works without a shader mod installed. */
public final class ShaderPacks {

    private static final boolean IRIS = ModList.get().isLoaded("iris");
    private static boolean last;

    private ShaderPacks() {
    }

    /** True while Iris renders with a shader pack; false without Iris, with shaders off or after a pack failed to compile. */
    public static boolean inUse() {
        boolean inUse = IRIS && IrisBridge.shaderPackInUse();
        if (inUse != last) {
            last = inUse;
            Heavymatica.LOGGER.info("Shader pack {}", inUse ? "in use" : "not in use");
        }
        return inUse;
    }

    /** Development aid: turns Iris shaders on or off as its screen does; does nothing without Iris. */
    public static void setEnabled(boolean enabled) {
        if (IRIS) {
            IrisBridge.setShadersEnabled(enabled);
        }
    }

    /** Development aid: selects a shader pack by its name in the shaderpacks folder; does nothing without Iris. */
    public static void selectPack(String name) {
        if (IRIS) {
            IrisBridge.selectPack(name);
        }
    }
}
