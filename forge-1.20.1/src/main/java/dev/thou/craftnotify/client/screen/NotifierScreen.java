package dev.thou.craftnotify.client.screen;

import dev.thou.craftnotify.CraftNotify;
import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import dev.thou.craftnotify.menu.NotifierMenu;
import dev.thou.craftnotify.network.TestNotifierPayload;
import dev.thou.craftnotify.network.UpdateNotifierPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import dev.thou.craftnotify.network.ModNetworking;

import java.util.ArrayList;
import java.util.List;

public final class NotifierScreen extends AbstractContainerScreen<NotifierMenu> {
    private static final ResourceLocation TEXTURE = CraftNotify.id("textures/gui/notifier.png");
    private static final int ENERGY_X = 8;
    private static final int ENERGY_Y = 19;
    private static final int ENERGY_W = 12;
    private static final int ENERGY_H = 72;
    private static final int LAMP_X = 9;
    private static final int ANTENNA_Y = 101;
    private static final int STATUS_Y = 119;
    private static final int LAMP_SIZE = 12;
    private static final int PREVIEW_X = 28;
    private static final int PREVIEW_Y = 132;
    private static final int PREVIEW_W = 202;

    private final List<DropdownWidget<?>> dropdowns = new ArrayList<>();
    private DropdownWidget<TerminalPresets.DeviceOption> deviceButton;
    private DropdownWidget<String> channelButton;
    private DropdownWidget<TerminalPresets.TemplateOption> titleButton;
    private DropdownWidget<TerminalPresets.TemplateOption> messageButton;
    private DropdownWidget<Integer> cooldownButton;
    private Button enabledButton;
    private boolean enabled;

    public NotifierScreen(NotifierMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.enabled = menu.enabled();
        imageWidth = 240;
        imageHeight = 186;
        inventoryLabelY = 10000;
        titleLabelX = 8;
        titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        dropdowns.clear();
        int x = leftPos + 84;
        int y = topPos + 18;
        int width = 148;

        List<TerminalPresets.DeviceOption> devices = TerminalPresets.devicesFor(menu.presets().devices(), menu.label());
        deviceButton = dropdown(x, y, width, devices,
                TerminalPresets.selectedDevice(devices, menu.label()),
                TerminalPresets.DeviceOption::label,
                option -> Tooltip.create(Component.translatable("screen.craft_notify.dropdown_hint")));

        y += 22;
        List<String> channels = TerminalPresets.channelsFor(menu.availableChannels(), menu.channelId());
        String channel = channels.contains(menu.channelId()) ? menu.channelId() : channels.get(0);
        channelButton = dropdown(x, y, width, channels, channel,
                TerminalPresets::channelLabel,
                id -> Tooltip.create(id.isBlank()
                        ? Component.translatable("screen.craft_notify.no_channels")
                        : Component.translatable("screen.craft_notify.dropdown_hint")));
        channelButton.active = !channel.isBlank() || channels.size() > 1;

        y += 22;
        List<TerminalPresets.TemplateOption> titles = TerminalPresets.titlesFor(menu.presets().titles(), menu.titleTemplate());
        titleButton = dropdown(x, y, width, titles,
                TerminalPresets.selectedTemplate(titles, menu.titleTemplate()),
                TerminalPresets.TemplateOption::label,
                option -> Tooltip.create(Component.literal(option.template())));

        y += 22;
        List<TerminalPresets.TemplateOption> messages = TerminalPresets.messagesFor(menu.presets().messages(), menu.contentTemplate());
        messageButton = dropdown(x, y, width, messages,
                TerminalPresets.selectedTemplate(messages, menu.contentTemplate()),
                TerminalPresets.TemplateOption::label,
                option -> Tooltip.create(Component.literal(option.template())));

        y += 22;
        List<Integer> cooldowns = TerminalPresets.cooldownsFor(menu.presets().cooldowns(), menu.cooldownSeconds());
        cooldownButton = dropdown(x, y, width, cooldowns, menu.cooldownSeconds(),
                TerminalPresets::cooldownLabel,
                seconds -> Tooltip.create(Component.translatable("screen.craft_notify.dropdown_hint")));

        enabledButton = addRenderableWidget(Button.builder(enabledLabel(), button -> {
            enabled = !enabled;
            button.setMessage(enabledLabel());
            button.setTooltip(Tooltip.create(Component.translatable("screen.craft_notify.enabled_tooltip")));
        }).bounds(leftPos + 168, topPos + 4, 64, 16).build());
        enabledButton.setTooltip(Tooltip.create(Component.translatable("screen.craft_notify.enabled_tooltip")));

        int buttonY = topPos + 160;
        addRenderableWidget(Button.builder(Component.translatable("screen.craft_notify.save"), button -> save())
                .bounds(leftPos + 28, buttonY, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.craft_notify.test"), button -> test())
                .bounds(leftPos + 96, buttonY, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(leftPos + 164, buttonY, 60, 20).build());
    }

    private <T> DropdownWidget<T> dropdown(int x, int y, int width, List<T> options, T value,
                                          java.util.function.Function<T, Component> names,
                                          java.util.function.Function<T, Tooltip> tooltip) {
        DropdownWidget<T> widget = new DropdownWidget<>(x, y, width, options, value, names, tooltip,
                ignored -> {}, this::toggleDropdown);
        dropdowns.add(addRenderableWidget(widget));
        return widget;
    }

    private void toggleDropdown(DropdownWidget<?> widget) {
        boolean open = !widget.isExpanded();
        for (DropdownWidget<?> dropdown : dropdowns) {
            dropdown.setExpanded(dropdown == widget && open);
        }
    }

    private void collapseDropdowns() {
        for (DropdownWidget<?> dropdown : dropdowns) {
            dropdown.collapse();
        }
    }

    private DropdownWidget<?> expandedDropdown() {
        for (DropdownWidget<?> dropdown : dropdowns) {
            if (dropdown.isExpanded()) {
                return dropdown;
            }
        }
        return null;
    }

    private String selectedLabel() {
        return deviceButton.getValue().stored();
    }

    private String selectedChannel() {
        return channelButton.getValue();
    }

    private String selectedTitle() {
        return titleButton.getValue().template();
    }

    private String selectedMessage() {
        return messageButton.getValue().template();
    }

    private void save() {
        ModNetworking.CHANNEL.sendToServer(new UpdateNotifierPayload(
                menu.blockPos(), menu.revision(), selectedLabel(), selectedChannel(),
                selectedTitle(), selectedMessage(), cooldownButton.getValue(), enabled
        ));
        onClose();
    }

    private void test() {
        ModNetworking.CHANNEL.sendToServer(new TestNotifierPayload(
                menu.blockPos(), selectedLabel(), selectedChannel(),
                selectedTitle(), selectedMessage()
        ));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int filled = Math.round(ENERGY_H * (float) menu.energyStored() / Math.max(1, menu.energyCapacity()));
        filled = dev.thou.craftnotify.MoreMath.clamp(filled, 0, ENERGY_H);
        if (filled > 0) {
            int skip = ENERGY_H - filled;
            graphics.blit(TEXTURE, leftPos + ENERGY_X, topPos + ENERGY_Y + skip, 240, skip, ENERGY_W, filled);
        }
        graphics.blit(TEXTURE, leftPos + LAMP_X, topPos + ANTENNA_Y,
                240, menu.antennaComplete() ? 72 : 84, LAMP_SIZE, LAMP_SIZE);
        int statusV = 84;
        if (menu.statusText().startsWith("SENDING")) {
            statusV = 108;
        } else if (menu.statusText().startsWith("FAILED")) {
            statusV = 96;
        }
        graphics.blit(TEXTURE, leftPos + LAMP_X, topPos + STATUS_Y,
                240, statusV, LAMP_SIZE, LAMP_SIZE);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        int y = 24;
        label(graphics, "screen.craft_notify.label", y);
        y += 22;
        label(graphics, "screen.craft_notify.channel", y);
        y += 22;
        label(graphics, "screen.craft_notify.title_template", y);
        y += 22;
        label(graphics, "screen.craft_notify.content_template", y);
        y += 22;
        label(graphics, "screen.craft_notify.cooldown", y);

        String preview = TerminalPresets.preview(selectedTitle(), selectedMessage(), selectedLabel());
        if (font.width(preview) > PREVIEW_W - 4) {
            preview = font.plainSubstrByWidth(preview, PREVIEW_W - 12) + "…";
        }
        graphics.drawString(font, preview, PREVIEW_X + 2, PREVIEW_Y + 6, 0xC8C8C8, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        DropdownWidget<?> expanded = expandedDropdown();
        if (expanded != null) {
            expanded.renderMenu(graphics, mouseX, mouseY, height);
            return;
        }
        this.renderTooltip(graphics, mouseX, mouseY);
        if (inside(mouseX, mouseY, ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H)) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.craft_notify.energy_tooltip",
                    menu.energyStored(), menu.energyCapacity(),
                    NotifierBlockEntity.ENERGY_PER_NOTIFICATION), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, LAMP_X, ANTENNA_Y, LAMP_SIZE, LAMP_SIZE)) {
            graphics.renderTooltip(font, Component.translatable(menu.antennaComplete()
                    ? "screen.craft_notify.antenna_ready"
                    : "screen.craft_notify.antenna_missing"), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, LAMP_X, STATUS_Y, LAMP_SIZE, LAMP_SIZE)) {
            graphics.renderTooltip(font, Component.literal(menu.statusText()), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        DropdownWidget<?> expanded = expandedDropdown();
        if (expanded != null && expanded.mouseClickedList(mouseX, mouseY, height)) {
            return true;
        }
        boolean onHeader = false;
        for (DropdownWidget<?> dropdown : dropdowns) {
            if (dropdown.isOverHeader(mouseX, mouseY)) {
                onHeader = true;
                break;
            }
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!onHeader) {
            collapseDropdowns();
        }
        return handled || expanded != null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        DropdownWidget<?> expanded = expandedDropdown();
        if (expanded != null && expanded.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + w
                && mouseY >= topPos + y && mouseY < topPos + y + h;
    }

    private void label(GuiGraphics graphics, String key, int y) {
        graphics.drawString(font, Component.translatable(key), 26, y, 0x404040, false);
    }

    private Component enabledLabel() {
        return Component.translatable(enabled
                ? "screen.craft_notify.enabled_on"
                : "screen.craft_notify.enabled_off");
    }
}
