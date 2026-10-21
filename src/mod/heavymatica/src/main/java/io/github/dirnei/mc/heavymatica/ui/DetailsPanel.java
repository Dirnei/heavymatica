package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.cloud.model.BlockCount;
import io.github.dirnei.mc.heavymatica.cloud.model.SchematicDetails;
import io.github.dirnei.mc.heavymatica.cloud.model.Version;
import io.github.dirnei.mc.heavymatica.library.LibraryEntry;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The selected entry: its versions (number and upload time, click to choose) and the chosen version's metadata with
 * block counts sorted from most to least common.
 */
final class DetailsPanel extends AbstractWidget {

    private static final int ROW = 10;
    static final int PLACE_WIDTH = 60;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final IntConsumer onVersion;
    private @Nullable LibraryEntry entry;
    private @Nullable SchematicDetails details;
    private int version;
    private @Nullable Component status;

    DetailsPanel(int x, int y, int width, int height, IntConsumer onVersion) {
        super(x, y, width, height, Component.translatable("heavymatica.library.details"));
        this.onVersion = onVersion;
    }

    void show(@Nullable LibraryEntry entry, @Nullable SchematicDetails details, int version, @Nullable Component status) {
        this.entry = entry;
        this.details = details;
        this.version = version;
        this.status = status;
    }

    int version() {
        return version;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x60000000);
        if (entry == null) {
            graphics.text(font, Component.translatable("heavymatica.library.details.none"), getX() + 6, getY() + 6, 0xFF909090, false);
            return;
        }
        int x = getX() + 6;
        int y = getY() + 6;
        // The Place button sits at the right of the name row.
        graphics.text(font, CardGrid.ellipsize(font, entry.name(), width - 12 - PLACE_WIDTH - 4), x, y, 0xFFFFFFFF, false);
        y += 12;
        if (status != null) {
            graphics.text(font, status, x, y, 0xFFC0C0C0, false);
            y += 12;
        }

        int columnWidth = width / 2 - 8;
        int versionsTop = y;
        if (details != null) {
            graphics.text(font, Component.translatable("heavymatica.library.details.versions"), x, y, 0xFFFFD060, false);
            y += ROW + 2;
            for (Version candidate : versions()) {
                if (y > getY() + height - ROW) {
                    break;
                }
                boolean chosen = candidate.getNumber() == version;
                if (chosen) {
                    graphics.fill(x - 2, y - 1, x + columnWidth, y + ROW - 1, 0xA0305080);
                }
                String label = "v" + candidate.getNumber() + "  " + WHEN.format(candidate.getCreatedAt());
                graphics.text(font, label, x, y, chosen ? 0xFFFFFFFF : 0xFFC0C0C0, false);
                y += ROW;
            }
        }

        Version shown = shownVersion();
        int bx = details != null ? x + columnWidth + 8 : x;
        int by = versionsTop;
        if (shown != null) {
            graphics.text(font, shown.getDimensions().getWidth() + " x " + shown.getDimensions().getHeight() + " x " + shown.getDimensions().getLength()
                    + "  " + shown.getBlockTotal() + " blocks", bx, by, 0xFFFFD060, false);
            by += ROW + 2;
            for (BlockCount count : shown.getBlockCounts().stream().sorted(Comparator.comparingLong(BlockCount::getCount).reversed()).toList()) {
                if (by > getY() + height - ROW) {
                    break;
                }
                String name = count.getBlock().startsWith("minecraft:") ? count.getBlock().substring("minecraft:".length()) : count.getBlock();
                graphics.text(font, CardGrid.ellipsize(font, count.getCount() + "  " + name, getX() + width - bx - 6), bx, by, 0xFFC0C0C0, false);
                by += ROW;
            }
        } else {
            graphics.text(font, entry.dimensions() + "  " + entry.blockTotal() + " blocks", bx, by, 0xFFC0C0C0, false);
        }
    }

    private List<Version> versions() {
        return details == null ? List.of() : details.getVersions().stream().sorted(Comparator.comparingInt(Version::getNumber).reversed()).toList();
    }

    private @Nullable Version shownVersion() {
        return versions().stream().filter(candidate -> candidate.getNumber() == version).findFirst().orElse(null);
    }

    /** Centre of the version row at {@code index}, in screen coordinates. */
    double[] versionPoint(int index) {
        int top = getY() + 6 + 12 + (status != null ? 12 : 0) + ROW + 2;
        return new double[] {getX() + 30, top + index * ROW + 4};
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (details == null || event.x() > getX() + width / 2.0) {
            return;
        }
        int top = getY() + 6 + 12 + (status != null ? 12 : 0) + ROW + 2;
        int index = (int) ((event.y() - top + 1) / ROW);
        List<Version> versions = versions();
        if (index >= 0 && index < versions.size()) {
            onVersion.accept(versions.get(index).getNumber());
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, entry == null ? getMessage() : Component.literal(entry.name()));
    }
}
