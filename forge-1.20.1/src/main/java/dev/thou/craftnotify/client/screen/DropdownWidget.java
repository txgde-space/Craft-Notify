package dev.thou.craftnotify.client.screen;

import dev.thou.craftnotify.MoreMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

final class DropdownWidget<T> extends AbstractWidget {
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    static final int ROW_HEIGHT = 14;
    static final int MAX_VISIBLE = 7;

    private final List<T> options;
    private final Function<T, Component> stringify;
    private final Function<T, Tooltip> tooltip;
    private final Consumer<T> onChange;
    private final Consumer<DropdownWidget<?>> onToggle;
    private T value;
    private boolean expanded;
    private int scroll;

    DropdownWidget(int x, int y, int width, List<T> options, T value,
                   Function<T, Component> stringify, Function<T, Tooltip> tooltip,
                   Consumer<T> onChange, Consumer<DropdownWidget<?>> onToggle) {
        super(x, y, width, 20, stringify.apply(value));
        if (options.isEmpty()) {
            throw new IllegalArgumentException("Dropdown needs at least one option");
        }
        this.options = List.copyOf(options);
        this.stringify = stringify;
        this.tooltip = tooltip;
        this.onChange = onChange;
        this.onToggle = onToggle;
        this.value = value;
        refreshTooltip();
    }

    T getValue() {
        return value;
    }

    boolean isExpanded() {
        return expanded;
    }

    void setExpanded(boolean expanded) {
        this.expanded = expanded;
        if (expanded) {
            int index = options.indexOf(value);
            if (index >= 0) {
                scroll = Math.max(0, Math.min(index - 2, maxScroll()));
            }
        }
    }

    void collapse() {
        setExpanded(false);
    }

    private int maxScroll() {
        return Math.max(0, options.size() - MAX_VISIBLE);
    }

    private int visibleRows() {
        return Math.min(MAX_VISIBLE, options.size());
    }

    private int listHeight() {
        return visibleRows() * ROW_HEIGHT + 2;
    }

    boolean expandsUp(int screenHeight) {
        int below = screenHeight - (getY() + getHeight());
        return listHeight() > below && getY() >= listHeight();
    }

    int listTop(int screenHeight) {
        return expandsUp(screenHeight) ? getY() - listHeight() : getY() + getHeight();
    }

    boolean isOverHeader(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + width
                && mouseY >= getY() && mouseY < getY() + getHeight();
    }

    boolean isOverList(double mouseX, double mouseY, int screenHeight) {
        if (!expanded) {
            return false;
        }
        int top = listTop(screenHeight);
        return mouseX >= getX() && mouseX < getX() + width
                && mouseY >= top && mouseY < top + listHeight();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY)
                || isOverList(mouseX, mouseY, Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onToggle.accept(this);
    }

    boolean mouseClickedList(double mouseX, double mouseY, int screenHeight) {
        if (!expanded || !isOverList(mouseX, mouseY, screenHeight)) {
            return false;
        }
        int top = listTop(screenHeight) + 1;
        int row = (int) ((mouseY - top) / ROW_HEIGHT);
        int index = row + scroll;
        if (index >= 0 && index < options.size()) {
            select(options.get(index));
            playDownSound(Minecraft.getInstance().getSoundManager());
            collapse();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (!expanded || !isOverList(mouseX, mouseY, screenHeight) || maxScroll() == 0) {
            return false;
        }
        scroll = MoreMath.clamp(scroll + (delta > 0 ? -1 : 1), 0, maxScroll());
        return true;
    }

    private void select(T next) {
        value = next;
        setMessage(stringify.apply(next));
        refreshTooltip();
        onChange.accept(next);
    }

    private void refreshTooltip() {
        setTooltip(tooltip.apply(value));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int v = !this.active ? 46 : (this.isHoveredOrFocused() && !expanded ? 86 : 66);
        int half = getWidth() / 2;
        graphics.blit(WIDGETS, getX(), getY(), 0, v, half, getHeight());
        graphics.blit(WIDGETS, getX() + half, getY(), 200 - (getWidth() - half), v, getWidth() - half, getHeight());
        Font font = Minecraft.getInstance().font;
        int textColor = this.active ? 0xE0E0E0 : 0xA0A0A0;
        String text = stringify.apply(value).getString();
        int maxWidth = getWidth() - 18;
        if (font.width(text) > maxWidth) {
            text = font.plainSubstrByWidth(text, maxWidth - 6) + "…";
        }
        graphics.drawString(font, text, getX() + 5, getY() + 6, textColor, false);
        drawChevron(graphics, getX() + getWidth() - 11, getY() + 8, textColor, expanded);
    }

    void renderMenu(GuiGraphics graphics, int mouseX, int mouseY, int screenHeight) {
        if (!expanded) {
            return;
        }
        int x = getX();
        int y = listTop(screenHeight);
        int w = getWidth();
        int h = listHeight();
        graphics.fill(x, y, x + w, y + h, 0xF0101010);
        graphics.fill(x, y, x + w, y + 1, 0xFF000000);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF000000);
        graphics.fill(x, y, x + 1, y + h, 0xFF000000);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF000000);

        Font font = Minecraft.getInstance().font;
        int rows = visibleRows();
        for (int row = 0; row < rows; row++) {
            int index = row + scroll;
            T option = options.get(index);
            int rowY = y + 1 + row * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean selected = option.equals(value);
            if (selected) {
                graphics.fill(x + 1, rowY, x + w - 1, rowY + ROW_HEIGHT, 0xFF5A4030);
            } else if (hovered) {
                graphics.fill(x + 1, rowY, x + w - 1, rowY + ROW_HEIGHT, 0xFF3A5A78);
            }
            String text = stringify.apply(option).getString();
            int maxWidth = w - (maxScroll() > 0 ? 12 : 6);
            if (font.width(text) > maxWidth) {
                text = font.plainSubstrByWidth(text, maxWidth - 6) + "…";
            }
            graphics.drawString(font, text, x + 4, rowY + 3, 0xE0E0E0, false);
        }
        if (maxScroll() > 0) {
            int barX = x + w - 4;
            int track = h - 4;
            int barH = Math.max(8, track * rows / options.size());
            int barY = y + 2 + (track - barH) * scroll / maxScroll();
            graphics.fill(barX, y + 2, barX + 2, y + h - 2, 0xFF2A2A2A);
            graphics.fill(barX, barY, barX + 2, barY + barH, 0xFFC8A070);
        }
    }

    private static void drawChevron(GuiGraphics graphics, int x, int y, int color, boolean up) {
        if (up) {
            graphics.fill(x - 3, y + 3, x + 4, y + 4, color);
            graphics.fill(x - 2, y + 2, x + 3, y + 3, color);
            graphics.fill(x - 1, y + 1, x + 2, y + 2, color);
            graphics.fill(x, y, x + 1, y + 1, color);
        } else {
            graphics.fill(x, y, x + 1, y + 1, color);
            graphics.fill(x - 1, y + 1, x + 2, y + 2, color);
            graphics.fill(x - 2, y + 2, x + 3, y + 3, color);
            graphics.fill(x - 3, y + 3, x + 4, y + 4, color);
        }
    }

    @Override
    public void playDownSound(SoundManager handler) {
        handler.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, stringify.apply(value));
    }
}
