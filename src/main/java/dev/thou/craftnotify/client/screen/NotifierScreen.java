package dev.thou.craftnotify.client.screen;

import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import dev.thou.craftnotify.menu.NotifierMenu;
import dev.thou.craftnotify.network.TestNotifierPayload;
import dev.thou.craftnotify.network.UpdateNotifierPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NotifierScreen extends AbstractContainerScreen<NotifierMenu> {
    private EditBox labelInput;
    private EditBox channelInput;
    private EditBox titleInput;
    private MultiLineEditBox contentInput;
    private EditBox cooldownInput;

    public NotifierScreen(NotifierMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 332;
        imageHeight = 260;
        titleLabelX = 12;
        titleLabelY = 9;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 12;
        int inputX = leftPos + 108;
        int inputWidth = imageWidth - 120;
        int y = topPos + 31;

        labelInput = edit(inputX, y, inputWidth, NotifierBlockEntity.MAX_LABEL_LENGTH,
                menu.label(), "screen.craft_notify.label");
        y += 27;
        channelInput = edit(inputX, y, inputWidth, NotifierBlockEntity.MAX_CHANNEL_LENGTH,
                menu.channelId(), "screen.craft_notify.channel");
        y += 27;
        titleInput = edit(inputX, y, inputWidth, NotifierBlockEntity.MAX_TITLE_LENGTH,
                menu.titleTemplate(), "screen.craft_notify.title_template");
        y += 27;

        contentInput = new MultiLineEditBox(
                font, inputX, y, inputWidth, 75,
                Component.translatable("screen.craft_notify.content_placeholder"),
                Component.translatable("screen.craft_notify.content_template")
        );
        contentInput.setCharacterLimit(NotifierBlockEntity.MAX_CONTENT_LENGTH);
        contentInput.setValue(menu.contentTemplate());
        addRenderableWidget(contentInput);
        y += 82;

        cooldownInput = edit(inputX, y, 70, 5, Integer.toString(menu.cooldownSeconds()),
                "screen.craft_notify.cooldown");
        cooldownInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));

        int buttonY = topPos + imageHeight - 31;
        addRenderableWidget(Button.builder(Component.translatable("screen.craft_notify.save"), button -> save())
                .bounds(x, buttonY, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.craft_notify.test"), button -> test())
                .bounds(x + 104, buttonY, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(x + 208, buttonY, 100, 20).build());
    }

    private EditBox edit(int x, int y, int width, int maxLength, String value, String translationKey) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.translatable(translationKey));
        box.setMaxLength(maxLength);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private int cooldownSeconds() {
        try {
            return Math.clamp(Integer.parseInt(cooldownInput.getValue()), 5, 86400);
        } catch (NumberFormatException ignored) {
            return 30;
        }
    }

    private void save() {
        PacketDistributor.sendToServer(new UpdateNotifierPayload(
                menu.blockPos(), menu.revision(), labelInput.getValue(), channelInput.getValue(),
                titleInput.getValue(), contentInput.getValue(), cooldownSeconds()
        ));
        onClose();
    }

    private void test() {
        PacketDistributor.sendToServer(new TestNotifierPayload(
                menu.blockPos(), labelInput.getValue(), channelInput.getValue(),
                titleInput.getValue(), contentInput.getValue()
        ));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF010141A);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFF25303A);
        graphics.fill(leftPos + 5, topPos + 5, leftPos + imageWidth - 5, topPos + imageHeight - 5, 0xFF111820);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFFF, false);
        int y = 34;
        label(graphics, "screen.craft_notify.label", y);
        y += 27;
        label(graphics, "screen.craft_notify.channel", y);
        y += 27;
        label(graphics, "screen.craft_notify.title_template", y);
        y += 27;
        label(graphics, "screen.craft_notify.content_template", y);
        y += 82;
        label(graphics, "screen.craft_notify.cooldown", y);

        String channels = menu.availableChannels().isBlank()
                ? Component.translatable("screen.craft_notify.no_channels").getString()
                : menu.availableChannels();
        graphics.drawString(font,
                Component.translatable("screen.craft_notify.available_channels", channels),
                12, imageHeight - 42, 0xA9C7DA, false);
        graphics.drawString(font,
                Component.translatable("screen.craft_notify.status", menu.statusText()),
                12, 20, 0xB9C5CC, false);
        graphics.drawString(font,
                Component.translatable("screen.craft_notify.energy", menu.energyStored(), menu.energyCapacity()),
                174, 20, 0xFFD36A, false);
        graphics.drawString(font,
                Component.translatable(menu.antennaComplete()
                        ? "screen.craft_notify.antenna_ready"
                        : "screen.craft_notify.antenna_missing"),
                174, imageHeight - 42, menu.antennaComplete() ? 0x66DD88 : 0xFF7777, false);
    }

    private void label(GuiGraphics graphics, String key, int y) {
        graphics.drawString(font, Component.translatable(key), 12, y + 6, 0xDCE5EA, false);
    }
}
