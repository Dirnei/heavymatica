package io.github.dirnei.mc.heavymatica.build;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Whether a block class acts when used: it, or a class between it and the base block class, declares one of the use
 * methods. The methods are never called, since several change client state. Cached per class.
 */
public final class ActsOnUse {

    private final Class<?> base;
    private final Set<String> methods;
    private final ClassValue<Boolean> cache = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return overrides(type);
        }
    };

    /** @param methods the names of the base class's use methods */
    public ActsOnUse(Class<?> base, Set<String> methods) {
        this.base = base;
        this.methods = methods;
    }

    public boolean acts(Class<?> type) {
        return cache.get(type);
    }

    private boolean overrides(Class<?> type) {
        for (Class<?> current = type; current != null && current != base && base.isAssignableFrom(current); current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (methods.contains(method.getName()) && !method.isBridge() && !method.isSynthetic()) {
                    return true;
                }
            }
        }
        return false;
    }
}
