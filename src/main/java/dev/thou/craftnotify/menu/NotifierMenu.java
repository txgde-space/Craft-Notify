package dev.thou.craftnotify.menu;

import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import dev.thou.craftnotify.preset.GuiPresetCatalog;
import dev.thou.craftnotify.preset.GuiPresetStore;
import dev.thou.craftnotify.registry.ModBlocks;
import dev.thou.craftnotify.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public final class NotifierMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final BlockPos blockPos;
    private final int revision;
    private final String label;
    private final String channelId;
    private final String titleTemplate;
    private final String contentTemplate;
    private final int cooldownSeconds;
    private final String availableChannels;
    private final String statusText;
    private final int energyStored;
    private final int energyCapacity;
    private final boolean antennaComplete;
    private final boolean enabled;
    private final GuiPresetCatalog presets;

    public NotifierMenu(int containerId, Inventory inventory, NotifierBlockEntity notifier) {
        super(ModMenus.NOTIFIER.get(), containerId);
        this.access = ContainerLevelAccess.create(notifier.getLevel(), notifier.getBlockPos());
        this.blockPos = notifier.getBlockPos();
        this.revision = notifier.configRevision();
        this.label = notifier.label();
        this.channelId = notifier.channelId();
        this.titleTemplate = notifier.titleTemplate();
        this.contentTemplate = notifier.contentTemplate();
        this.cooldownSeconds = notifier.cooldownSeconds();
        this.availableChannels = "";
        this.statusText = notifier.statusText();
        this.energyStored = notifier.energyStored();
        this.energyCapacity = notifier.energyCapacity();
        this.antennaComplete = notifier.hasCompleteAntenna();
        this.enabled = notifier.enabled();
        this.presets = GuiPresetStore.catalog();
    }

    private NotifierMenu(int containerId, Inventory inventory, BlockPos blockPos, int revision,
                         String label, String channelId, String titleTemplate, String contentTemplate,
                         int cooldownSeconds, String availableChannels, String statusText,
                         int energyStored, int energyCapacity, boolean antennaComplete, boolean enabled,
                         GuiPresetCatalog presets) {
        super(ModMenus.NOTIFIER.get(), containerId);
        this.access = ContainerLevelAccess.NULL;
        this.blockPos = blockPos;
        this.revision = revision;
        this.label = label;
        this.channelId = channelId;
        this.titleTemplate = titleTemplate;
        this.contentTemplate = contentTemplate;
        this.cooldownSeconds = cooldownSeconds;
        this.availableChannels = availableChannels;
        this.statusText = statusText;
        this.energyStored = energyStored;
        this.energyCapacity = energyCapacity;
        this.antennaComplete = antennaComplete;
        this.enabled = enabled;
        this.presets = presets;
    }

    public static NotifierMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new NotifierMenu(
                containerId,
                inventory,
                buf.readBlockPos(),
                buf.readVarInt(),
                buf.readUtf(NotifierBlockEntity.MAX_LABEL_LENGTH),
                buf.readUtf(NotifierBlockEntity.MAX_CHANNEL_LENGTH),
                buf.readUtf(NotifierBlockEntity.MAX_TITLE_LENGTH),
                buf.readUtf(NotifierBlockEntity.MAX_CONTENT_LENGTH),
                buf.readVarInt(),
                buf.readUtf(512),
                buf.readUtf(256),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                GuiPresetCatalog.read(buf)
        );
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) ->
                level.getBlockState(pos).is(ModBlocks.NOTIFIER.get())
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0,
                true);
    }

    public BlockPos blockPos() {
        return blockPos;
    }

    public int revision() {
        return revision;
    }

    public String label() {
        return label;
    }

    public String channelId() {
        return channelId;
    }

    public String titleTemplate() {
        return titleTemplate;
    }

    public String contentTemplate() {
        return contentTemplate;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public String availableChannels() {
        return availableChannels;
    }

    public String statusText() {
        return statusText;
    }

    public int energyStored() { return energyStored; }
    public int energyCapacity() { return energyCapacity; }
    public boolean antennaComplete() { return antennaComplete; }
    public boolean enabled() { return enabled; }
    public GuiPresetCatalog presets() { return presets; }
}
