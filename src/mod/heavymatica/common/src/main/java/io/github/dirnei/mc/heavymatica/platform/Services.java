package io.github.dirnei.mc.heavymatica.platform;

import java.util.ServiceLoader;

/** Finds the implementation a loader project provides for a common interface. */
public final class Services {

    private Services() {
    }

    public static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, type.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation of " + type.getName()
                        + " found. The jar is missing the loader part that lists it in META-INF/services/" + type.getName() + "."));
    }
}
