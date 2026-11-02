package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.placement.OpacitySteps;
import io.github.dirnei.mc.heavymatica.ui.glass.Glass;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassButton;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassChoice;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassEditBox;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassScreen;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassSlider;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassToggle;
import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * Heavymatica's client settings in sections, one section at a time, each setting with a short explanation. Changes
 * apply at once and are saved to the client config file. Opens from the mods list and from the library.
 */
public final class SettingsScreen extends GlassScreen {

    private static final int NAV_WIDTH = 120;
    private static final int CONTROL_WIDTH = 150;

    /** A setting row and the widgets of its control, placed per frame as the rows scroll. */
    private record Row(SettingsTable.Setting setting, List<AbstractWidget> widgets, int height, int textWidth) {
    }

    private static SettingsTable.Section lastSection = SettingsTable.Section.REMOTE;

    private final @Nullable Screen parent;
    private SettingsTable.Section section = lastSection;
    private final List<Row> rows = new ArrayList<>();
    private final List<GlassButton> sectionButtons = new ArrayList<>();
    private Rect nav = new Rect(0, 0, 0, 0);
    private Rect content = new Rect(0, 0, 0, 0);
    private double scroll;

    public SettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("heavymatica.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        sectionButtons.clear();
        int m = LibraryLayout.margin(width);
        int controlsY = m + (LibraryLayout.HEADER - LibraryLayout.BUTTON) / 2;
        int doneWidth = 52;
        addRenderableWidget(new GlassButton(width - m - doneWidth, controlsY, doneWidth, LibraryLayout.BUTTON, CommonComponents.GUI_DONE,
                GlassButton.Kind.PRIMARY, button -> onClose()));
        String reset = Component.translatable("heavymatica.settings.reset").getString();
        int resetWidth = font.width(reset) + 16;
        addRenderableWidget(new GlassButton(width - m - doneWidth - 6 - resetWidth, controlsY, resetWidth, LibraryLayout.BUTTON,
                Component.translatable("heavymatica.settings.reset"), GlassButton.Kind.SECONDARY, button -> resetSection()));
        int bodyTop = m + LibraryLayout.HEADER + 8;
        int navWidth = Math.min(NAV_WIDTH, width / 4);
        nav = new Rect(m, bodyTop, navWidth, height - bodyTop - m);
        content = new Rect(nav.right() + 8, bodyTop, width - m - nav.right() - 8, height - bodyTop - m);
        int y = nav.y() + 6;
        for (SettingsTable.Section candidate : SettingsTable.Section.values()) {
            GlassButton button = addRenderableWidget(new GlassButton(nav.x() + 6, y, nav.w() - 12, 22, Component.translatable(candidate.key()),
                    candidate == section ? GlassButton.Kind.ACCENT : GlassButton.Kind.QUIET, pressed -> {
                        section = candidate;
                        lastSection = candidate;
                        scroll = 0;
                        rebuildWidgets();
                    }));
            sectionButtons.add(button);
            y += 24;
        }
        int controlWidth = Math.min(CONTROL_WIDTH, content.w() / 2);
        for (SettingsTable.Setting setting : SettingsTable.of(section)) {
            List<AbstractWidget> widgets = control(setting, controlWidth);
            widgets.forEach(this::addRenderableWidget);
            rows.add(row(setting, widgets, setting.hintKey()));
        }
        if (section == SettingsTable.Section.LIBRARY) {
            GlassButton open = addRenderableWidget(new GlassButton(0, 0, font.width(Component.translatable("heavymatica.import.open_folder")) + 16, 18,
                    Component.translatable("heavymatica.import.open_folder"), GlassButton.Kind.SECONDARY, button -> openSchematicsFolder()));
            rows.addFirst(row(new SettingsTable.Setting(SettingsTable.Section.LIBRARY, "schematicsFolder", SettingsTable.Control.TOGGLE, 0, 0),
                    List.of(open), "heavymatica.settings.schematicsFolder.hint"));
        }
        placeRows();
    }

    /** A row whose explanation wraps in the room its controls leave. */
    private Row row(SettingsTable.Setting setting, List<AbstractWidget> widgets, String hintKey) {
        int controls = widgets.stream().mapToInt(widget -> widget instanceof GlassEditBox box ? box.fieldWidth() : widget.getWidth()).sum()
                + 4 * (widgets.size() - 1)
                + (setting.control() == SettingsTable.Control.ITEM ? 92 : 0);
        int textWidth = Math.max(80, content.w() - 24 - controls - 12);
        int hintLines = Glass.wrap(font, Component.translatable(hintKey), textWidth).size();
        return new Row(setting, widgets, Math.max(30, 16 + hintLines * Glass.LINE + 6), textWidth);
    }

    private void openSchematicsFolder() {
        Path folder = minecraft.gameDirectory.toPath().resolve("schematics");
        try {
            Files.createDirectories(folder);
        } catch (java.io.IOException e) {
            Heavymatica.LOGGER.warn("Could not create {}", folder, e);
        }
        Util.getPlatform().openPath(folder);
    }

    @SuppressWarnings("unchecked")
    private static <T> ModConfigSpec.ConfigValue<T> value(String path) {
        return (ModConfigSpec.ConfigValue<T>) HeavymaticaConfig.SPEC.getValues().get(path);
    }

    private static <T> void set(String path, T newValue) {
        ModConfigSpec.ConfigValue<T> value = value(path);
        if (!Objects.equals(value.get(), newValue)) {
            value.set(newValue);
            value.save();
        }
    }

    /** The widgets that change a setting; they set and save it at once. */
    private List<AbstractWidget> control(SettingsTable.Setting setting, int controlWidth) {
        String path = setting.path();
        Component name = Component.translatable(setting.nameKey());
        return switch (setting.control()) {
            case TOGGLE -> {
                ModConfigSpec.ConfigValue<Boolean> value = value(path);
                yield List.of(new GlassToggle(0, 0, name, value.get(), on -> set(path, on)));
            }
            case CHOICE -> List.of(choice(path, name));
            case PERCENT -> {
                ModConfigSpec.ConfigValue<Double> value = value(path);
                yield List.of(new GlassSlider(0, 0, controlWidth, name, setting.min(), setting.max(), 0.01, value.get(),
                        share -> Math.round(share * 100) + "%", share -> set(path, Math.round(share * 100) / 100.0)));
            }
            case SLIDER -> {
                ModConfigSpec.ConfigValue<Integer> value = value(path);
                yield List.of(new GlassSlider(0, 0, controlWidth, name, setting.min(), setting.max(), 1, value.get(),
                        number -> Component.translatable("heavymatica.settings.blocks", Math.round(number)).getString(),
                        number -> set(path, (int) Math.round(number))));
            }
            case NUMBER -> List.of(number(setting, name, controlWidth));
            case ITEM -> remoteItem(controlWidth);
            case STEPS -> opacitySteps(controlWidth);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private AbstractWidget choice(String path, Component name) {
        ModConfigSpec.ConfigValue<Enum> value = value(path);
        Enum current = value.get();
        List<Enum> options = Arrays.asList((Enum[]) current.getDeclaringClass().getEnumConstants());
        List<Component> labels = options.stream()
                .map(option -> (Component) Component.translatable("heavymatica.settings." + path + "." + option.name().toLowerCase(Locale.ROOT)))
                .toList();
        return new GlassChoice<Enum>(0, 0, name, options, labels, current, chosen -> set(path, chosen));
    }

    private AbstractWidget number(SettingsTable.Setting setting, Component name, int controlWidth) {
        ModConfigSpec.ConfigValue<Integer> value = value(setting.path());
        GlassEditBox box = new GlassEditBox(font, 0, 0, Math.min(controlWidth, 90), GlassEditBox.HEIGHT, name, false);
        box.setValue(Integer.toString(value.get()));
        box.setFilter(text -> text.chars().allMatch(Character::isDigit) && text.length() <= 9);
        box.setResponder(text -> {
            if (text.isEmpty()) {
                return;
            }
            long typed = Long.parseLong(text);
            boolean ok = SettingsTable.accepts(setting, typed);
            box.setTextColor(ok ? GlassStyle.TEXT : GlassStyle.DANGER_TEXT);
            if (ok) {
                set(setting.path(), (int) typed);
            }
        });
        return box;
    }

    private List<AbstractWidget> remoteItem(int controlWidth) {
        GlassButton use = new GlassButton(0, 0, Math.min(controlWidth - 22, 96), 18, Component.translatable("heavymatica.settings.remote.use_held"),
                GlassButton.Kind.SECONDARY, button -> {
                    ItemStack held = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
                    if (!held.isEmpty()) {
                        set("remote.item", BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
                        rebuildWidgets();
                    }
                });
        boolean holding = minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty();
        use.enable(holding, Component.translatable(minecraft.player == null ? "heavymatica.settings.remote.no_world" : "heavymatica.settings.remote.hold_first"));
        return List.of(use);
    }

    private List<AbstractWidget> opacitySteps(int controlWidth) {
        List<AbstractWidget> widgets = new ArrayList<>();
        List<Double> steps = HeavymaticaConfig.ghostOpacitySteps();
        for (Double step : steps) {
            String label = Math.round(step * 100) + "%";
            GlassButton chip = new GlassButton(0, 0, font.width(label) + 12, 16, Component.literal(label), GlassButton.Kind.ACCENT, button -> {
                List<Double> next = new ArrayList<>(HeavymaticaConfig.ghostOpacitySteps());
                next.remove(step);
                set("ghost.opacitySteps", List.copyOf(next));
                rebuildWidgets();
            });
            chip.enable(steps.size() > OpacitySteps.MIN_STEPS, Component.translatable("heavymatica.settings.ghost.opacitySteps.minimum"));
            if (steps.size() > OpacitySteps.MIN_STEPS) {
                chip.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("heavymatica.settings.ghost.opacitySteps.remove")));
            }
            widgets.add(chip);
        }
        GlassButton add = new GlassButton(0, 0, 16, 16, Component.literal("+"), GlassButton.Kind.SECONDARY, button -> minecraft.setScreen(
                new TextPromptScreen(this, Component.translatable("heavymatica.settings.ghost.opacitySteps.add"),
                        Component.translatable("heavymatica.settings.ghost.opacitySteps.add_hint"), "", null, text -> addStep(text), () -> { })));
        add.enable(steps.size() < OpacitySteps.MAX_STEPS, Component.translatable("heavymatica.settings.ghost.opacitySteps.maximum"));
        widgets.add(add);
        return widgets;
    }

    private void addStep(String text) {
        try {
            double percent = Double.parseDouble(text.replace("%", "").trim());
            List<Double> next = new ArrayList<>(HeavymaticaConfig.ghostOpacitySteps());
            next.add(percent / 100.0);
            List<Double> sanitized = OpacitySteps.sanitize(next);
            if (sanitized.stream().anyMatch(step -> OpacitySteps.matches(step, percent / 100.0))) {
                set("ghost.opacitySteps", List.copyOf(sanitized));
            }
        } catch (NumberFormatException ignored) {
            // Not a number: nothing is added.
        }
    }

    private void resetSection() {
        for (SettingsTable.Setting setting : SettingsTable.of(section)) {
            resetToDefault(setting.path());
        }
        rebuildWidgets();
    }

    private static <T> void resetToDefault(String path) {
        ModConfigSpec.ConfigValue<T> value = value(path);
        set(path, value.getDefault());
    }

    /** Puts each row's widgets at its row's place in the scrolled list, and hides the ones out of view. */
    private void placeRows() {
        int total = rows.stream().mapToInt(Row::height).sum();
        scroll = Math.clamp(scroll, 0, Math.max(0, total - (content.h() - 12)));
        int y = content.y() + 6 - (int) scroll;
        int right = content.right() - 12;
        for (Row row : rows) {
            int centreY = y + row.height() / 2;
            int x = right;
            List<AbstractWidget> widgets = row.widgets();
            for (int i = widgets.size() - 1; i >= 0; i--) {
                AbstractWidget widget = widgets.get(i);
                boolean visible = y >= content.y() && y + row.height() <= content.bottom();
                if (widget instanceof GlassEditBox box) {
                    x -= box.fieldWidth();
                    box.moveTo(x, centreY - box.fieldHeight() / 2);
                } else {
                    x -= widget.getWidth();
                    widget.setX(x);
                    widget.setY(centreY - widget.getHeight() / 2);
                }
                widget.visible = visible;
                x -= 4;
            }
            y += row.height();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (content.contains(mouseX, mouseY)) {
            scroll -= scrollY * 20;
            placeRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        int m = LibraryLayout.margin(width);
        Glass.text(graphics, font, title.getString(), m, m + 2, GlassStyle.TEXT, width / 2);
        Glass.text(graphics, font, Component.translatable("heavymatica.settings.subtitle").getString(), m, m + 13, GlassStyle.HINT, width / 2);
        Glass.panel(graphics, nav.x(), nav.y(), nav.w(), nav.h());
        Glass.panel(graphics, content.x(), content.y(), content.w(), content.h());
        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        int y = content.y() + 6 - (int) scroll;
        int textX = content.x() + 12;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (i > 0) {
                graphics.fill(textX, y, content.right() - 12, y + 1, 0x0FFFFFFF);
            }
            String key = row.setting().path().equals("schematicsFolder") ? "heavymatica.settings.schematicsFolder" : row.setting().nameKey();
            Glass.text(graphics, font, Component.translatable(key + (key.endsWith(".name") ? "" : ".name")).getString(), textX, y + 6, GlassStyle.TEXT,
                    row.textWidth());
            Glass.paragraph(graphics, font, Component.translatable(key.endsWith(".name") ? row.setting().hintKey() : key + ".hint"), textX, y + 17,
                    row.textWidth(), GlassStyle.HINT);
            if (row.setting().control() == SettingsTable.Control.ITEM) {
                remoteItemPreview(graphics, row, y);
            }
            y += row.height();
        }
        graphics.disableScissor();
        super.extractRenderState(graphics, mouseX, mouseY, partial);
    }

    /** The current remote item's icon and name, left of the Use held item button. */
    private void remoteItemPreview(GuiGraphicsExtractor graphics, Row row, int y) {
        AbstractWidget use = row.widgets().getFirst();
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(HeavymaticaConfig.remoteItem())).orElse(Items.AIR);
        int x = use.getX() - 92;
        Glass.box(graphics, x, y + row.height() / 2 - 10, 20, 20, 4, 0x66000000, 0x29FFFFFF);
        if (item != Items.AIR) {
            graphics.item(item.getDefaultInstance(), x + 2, y + row.height() / 2 - 8);
        }
        Glass.text(graphics, font, item == Items.AIR ? HeavymaticaConfig.remoteItem() : item.getName(item.getDefaultInstance()).getString(), x + 24,
                y + row.height() / 2 - 4, GlassStyle.LABEL, 64);
    }

    /** The section shown, for development checks. */
    void showSectionForDevelopment(String name) {
        section = SettingsTable.Section.valueOf(name.toUpperCase(Locale.ROOT));
        lastSection = section;
        scroll = 0;
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
