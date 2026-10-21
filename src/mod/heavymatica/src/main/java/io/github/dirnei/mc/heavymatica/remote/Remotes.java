package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * What counts as a remote (D2): the configured item, or any item in {@link #TAG}. The core registers no items; the tag
 * comes from the server's data, so on servers without Heavymatica only the configured item works.
 */
public final class Remotes {

    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "remotes"));

    private static @Nullable String warnedAbout;

    private Remotes() {
    }

    public static boolean isRemote(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(configuredItem()) || stack.is(TAG));
    }

    /** True while the local player holds a remote in the main hand. */
    public static boolean held() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && isRemote(minecraft.player.getMainHandItem());
    }

    /** The configured item; a stick when the configured ID names no item (logged once). */
    public static Item configuredItem() {
        String id = HeavymaticaConfig.remoteItem();
        Identifier identifier = Identifier.tryParse(id);
        Item item = identifier == null ? null : BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
        if (item == null || item == Items.AIR) {
            if (!id.equals(warnedAbout)) {
                warnedAbout = id;
                Heavymatica.LOGGER.warn("The configured remote item {} does not exist; using a stick", id);
            }
            return Items.STICK;
        }
        return item;
    }
}
