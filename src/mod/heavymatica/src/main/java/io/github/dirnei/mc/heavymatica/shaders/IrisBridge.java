package io.github.dirnei.mc.heavymatica.shaders;

import java.io.IOException;
import java.io.UncheckedIOException;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;

/** The only class that touches Iris; loaded only when Iris is installed. */
final class IrisBridge {

    private IrisBridge() {
    }

    static boolean shaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    static void setShadersEnabled(boolean enabled) {
        IrisApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);
    }

    /** Selects a pack from the shaderpacks folder and reloads, as applying it in the Iris screen does. Not public API. */
    static void selectPack(String name) {
        try {
            Iris.getIrisConfig().setShaderPackName(name);
            Iris.getIrisConfig().save();
            Iris.reload();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
