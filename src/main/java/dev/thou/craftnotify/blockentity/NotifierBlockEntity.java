package dev.thou.craftnotify.blockentity;

import dev.thou.craftnotify.notification.NotificationDispatcher;
import dev.thou.craftnotify.notification.NotificationJob;
import dev.thou.craftnotify.notification.NotificationResult;
import dev.thou.craftnotify.notification.SecretChannelStore;
import dev.thou.craftnotify.block.AntennaBlock;
import dev.thou.craftnotify.block.AntennaPart;
import dev.thou.craftnotify.energy.TerminalEnergyStorage;
import dev.thou.craftnotify.registry.ModBlockEntities;
import dev.thou.craftnotify.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class NotifierBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_LABEL_LENGTH = 32;
    public static final int MAX_CHANNEL_LENGTH = 64;
    public static final int MAX_TITLE_LENGTH = 64;
    public static final int MAX_CONTENT_LENGTH = 1024;
    public static final long DEFAULT_COOLDOWN_TICKS = 20L * 30L;
    public static final int ENERGY_CAPACITY = 10_000;
    public static final int ENERGY_PER_NOTIFICATION = 1_000;
    public static final int MAX_ENERGY_RECEIVE = 2_000;

    private String label = "Redstone notifier";
    private String channelId = "default";
    private String titleTemplate = "[{server}] {label}";
    private String contentTemplate = "{label} triggered at {dimension} ({x}, {y}, {z}), power {power}.";
    private long cooldownTicks = DEFAULT_COOLDOWN_TICKS;
    private long lastTriggeredGameTime = Long.MIN_VALUE / 2;
    private int suppressedTriggers;
    private boolean powerBaselineInitialized;
    private NotificationStatus status = NotificationStatus.UNCONFIGURED;
    private String lastMessage = "Not configured";
    private UUID instanceId = UUID.randomUUID();
    private UUID ownerId;
    private long requestSequence;
    private int configRevision;
    private final TerminalEnergyStorage energyStorage = new TerminalEnergyStorage(
            ENERGY_CAPACITY, MAX_ENERGY_RECEIVE, this::onEnergyChanged);
    private int reservedEnergy;

    public NotifierBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NOTIFIER.get(), pos, state);
    }

    public void initializePowerBaseline(boolean powered) {
        powerBaselineInitialized = true;
        refreshReadyStatus();
        setChanged();
    }

    public void setOwner(UUID ownerId) {
        if (this.ownerId == null) {
            this.ownerId = ownerId;
            setChanged();
        }
    }

    public void onAntennaChanged() {
        refreshReadyStatus();
    }

    private void onEnergyChanged(int energy) {
        setChanged();
        if (status == NotificationStatus.NO_ENERGY && energy >= ENERGY_PER_NOTIFICATION) {
            refreshReadyStatus();
        }
    }

    public void onPowerChanged(ServerLevel level, boolean powered, int signalPower) {
        if (!powerBaselineInitialized) {
            initializePowerBaseline(powered);
            return;
        }
        if (!powered) {
            refreshReadyStatus();
            return;
        }
        trigger(level, signalPower);
    }

    private void trigger(ServerLevel level, int signalPower) {
        if (!hasCompleteAntenna()) {
            setStatus(NotificationStatus.MISSING_ANTENNA, "A complete 3-block antenna must be adjacent");
            return;
        }
        if (!reserveNotificationEnergy()) {
            setStatus(NotificationStatus.NO_ENERGY,
                    "Needs " + ENERGY_PER_NOTIFICATION + " FE; available " + availableEnergy() + " FE");
            return;
        }
        if (!SecretChannelStore.hasChannel(channelId)) {
            releaseReservedEnergy(false);
            setStatus(NotificationStatus.UNCONFIGURED, "Channel '" + channelId + "' is not configured");
            return;
        }

        long now = level.getGameTime();
        if (now - lastTriggeredGameTime < cooldownTicks) {
            releaseReservedEnergy(false);
            suppressedTriggers++;
            setStatus(NotificationStatus.COOLDOWN, "Cooldown; suppressed " + suppressedTriggers + " trigger(s)");
            return;
        }

        lastTriggeredGameTime = now;
        long sequence = ++requestSequence;
        int suppressed = suppressedTriggers;
        suppressedTriggers = 0;
        setStatus(NotificationStatus.SENDING, "Sending notification");

        String serverName = level.getServer().getMotd();
        NotificationJob job = NotificationJob.from(
                channelId, titleTemplate, contentTemplate, label, serverName,
                level.dimension().location().toString(), worldPosition, signalPower, suppressed
        );
        NotificationDispatcher.dispatch(job).whenComplete((result, error) ->
                level.getServer().execute(() -> applyResult(level, sequence, result, error)));
    }

    public void test(ServerLevel level, ServerPlayer player, String candidateLabel, String candidateChannel,
                     String candidateTitle, String candidateContent) {
        if (!canPlayerEdit(player)) {
            player.displayClientMessage(Component.translatable("message.craft_notify.too_far"), false);
            return;
        }
        if (!SecretChannelStore.hasChannel(candidateChannel)) {
            player.displayClientMessage(Component.translatable("message.craft_notify.unknown_channel", candidateChannel), false);
            return;
        }
        if (!hasCompleteAntenna()) {
            player.displayClientMessage(Component.translatable("message.craft_notify.missing_antenna"), false);
            return;
        }
        if (!reserveNotificationEnergy()) {
            player.displayClientMessage(Component.translatable(
                    "message.craft_notify.no_energy", ENERGY_PER_NOTIFICATION, availableEnergy()), false);
            return;
        }
        NotificationJob job = NotificationJob.from(
                candidateChannel, limit(candidateTitle, MAX_TITLE_LENGTH), limit(candidateContent, MAX_CONTENT_LENGTH),
                limit(candidateLabel, MAX_LABEL_LENGTH), level.getServer().getMotd(),
                level.dimension().location().toString(), worldPosition, 0, 0
        );
        player.displayClientMessage(Component.translatable("message.craft_notify.test_sending"), false);
        NotificationDispatcher.dispatch(job).whenComplete((result, error) -> level.getServer().execute(() -> {
            if (error != null) {
                releaseReservedEnergy(false);
                player.displayClientMessage(Component.translatable("message.craft_notify.test_failed", safeError(error)), false);
            } else if (result.accepted()) {
                releaseReservedEnergy(true);
                player.displayClientMessage(Component.translatable("message.craft_notify.test_accepted"), false);
            } else {
                releaseReservedEnergy(false);
                player.displayClientMessage(Component.translatable("message.craft_notify.test_failed", result.message()), false);
            }
        }));
    }

    private void applyResult(ServerLevel level, long sequence, NotificationResult result, Throwable error) {
        if (isRemoved() || sequence != requestSequence || level.getBlockEntity(worldPosition) != this) {
            return;
        }
        if (error != null) {
            releaseReservedEnergy(false);
            setStatus(NotificationStatus.FAILED, "Request failed: " + safeError(error));
        } else if (result.accepted()) {
            releaseReservedEnergy(true);
            setStatus(NotificationStatus.READY,
                    result.message() + "; used " + ENERGY_PER_NOTIFICATION + " FE");
        } else {
            releaseReservedEnergy(false);
            setStatus(NotificationStatus.FAILED, result.message());
        }
    }

    private static String safeError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    private int availableEnergy() {
        return Math.max(0, energyStorage.getEnergyStored() - reservedEnergy);
    }

    private boolean reserveNotificationEnergy() {
        if (availableEnergy() < ENERGY_PER_NOTIFICATION) {
            return false;
        }
        reservedEnergy += ENERGY_PER_NOTIFICATION;
        return true;
    }

    private void releaseReservedEnergy(boolean consume) {
        if (reservedEnergy >= ENERGY_PER_NOTIFICATION) {
            reservedEnergy -= ENERGY_PER_NOTIFICATION;
            if (consume) {
                energyStorage.consume(ENERGY_PER_NOTIFICATION);
            }
        }
    }

    private void refreshReadyStatus() {
        if (!hasCompleteAntenna()) {
            setStatus(NotificationStatus.MISSING_ANTENNA, "A complete 3-block antenna must be adjacent");
        } else if (availableEnergy() < ENERGY_PER_NOTIFICATION) {
            setStatus(NotificationStatus.NO_ENERGY,
                    "Needs " + ENERGY_PER_NOTIFICATION + " FE; available " + availableEnergy() + " FE");
        } else if (SecretChannelStore.hasChannel(channelId)) {
            setStatus(NotificationStatus.READY, "Ready on channel '" + channelId + "'");
        } else {
            setStatus(NotificationStatus.UNCONFIGURED, "Channel '" + channelId + "' is not configured");
        }
    }

    public boolean configure(Player player, int expectedRevision, String label, String channelId,
                             String title, String content, int cooldownSeconds) {
        if (!canPlayerEdit(player) || expectedRevision != configRevision) {
            return false;
        }
        configure(label, channelId, title, content, cooldownSeconds);
        return true;
    }

    public void configure(String label, String channelId, String title, String content, int cooldownSeconds) {
        this.label = limit(label, MAX_LABEL_LENGTH);
        this.channelId = limit(channelId, MAX_CHANNEL_LENGTH);
        this.titleTemplate = limit(title, MAX_TITLE_LENGTH);
        this.contentTemplate = limit(content, MAX_CONTENT_LENGTH);
        this.cooldownTicks = Math.clamp(cooldownSeconds, 5, 86400) * 20L;
        configRevision++;
        refreshReadyStatus();
        setChanged();
    }

    public boolean canPlayerEdit(Player player) {
        return !isRemoved()
                && player.level() == level
                && (ownerId == null || ownerId.equals(player.getUUID()) || player.hasPermissions(2))
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0;
    }

    private static String limit(String value, int maxLength) {
        String clean = value.strip();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
    }

    private void setStatus(NotificationStatus newStatus, String message) {
        this.status = newStatus;
        this.lastMessage = message;
        setChanged();
        if (level != null) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public int comparatorSignal() {
        return status.comparatorSignal();
    }

    public Component statusMessage() {
        return Component.literal("[Craft Notify] " + status + ": " + lastMessage);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.craft_notify.notifier");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new dev.thou.craftnotify.menu.NotifierMenu(containerId, inventory, this);
    }

    public String label() { return label; }
    public String channelId() { return channelId; }
    public String titleTemplate() { return titleTemplate; }
    public String contentTemplate() { return contentTemplate; }
    public int cooldownSeconds() { return (int) (cooldownTicks / 20L); }
    public int configRevision() { return configRevision; }
    public String statusText() { return status + ": " + lastMessage; }
    public int energyStored() { return energyStorage.getEnergyStored(); }
    public int energyCapacity() { return ENERGY_CAPACITY; }
    public TerminalEnergyStorage energyStorage() { return energyStorage; }

    public boolean hasCompleteAntenna() {
        if (level == null) {
            return false;
        }
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos base = worldPosition.relative(direction);
            if (isAntennaPart(base, AntennaPart.BASE)
                    && isAntennaPart(base.above(), AntennaPart.MIDDLE)
                    && isAntennaPart(base.above(2), AntennaPart.TOP)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAntennaPart(BlockPos pos, AntennaPart part) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.ANTENNA.get()) && state.getValue(AntennaBlock.PART) == part;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("label", label);
        tag.putString("channel_id", channelId);
        tag.putString("title_template", titleTemplate);
        tag.putString("content_template", contentTemplate);
        tag.putLong("cooldown_ticks", cooldownTicks);
        tag.putLong("last_triggered", lastTriggeredGameTime);
        tag.putInt("suppressed", suppressedTriggers);
        tag.putBoolean("baseline_initialized", powerBaselineInitialized);
        tag.putString("status", status.name());
        tag.putString("last_message", lastMessage);
        tag.putUUID("instance_id", instanceId);
        if (ownerId != null) {
            tag.putUUID("owner_id", ownerId);
        }
        tag.putLong("request_sequence", requestSequence);
        tag.putInt("config_revision", configRevision);
        tag.putInt("energy", energyStorage.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        label = tag.getString("label");
        channelId = tag.getString("channel_id");
        titleTemplate = tag.getString("title_template");
        contentTemplate = tag.getString("content_template");
        cooldownTicks = tag.getLong("cooldown_ticks");
        lastTriggeredGameTime = tag.getLong("last_triggered");
        suppressedTriggers = tag.getInt("suppressed");
        powerBaselineInitialized = tag.getBoolean("baseline_initialized");
        lastMessage = tag.getString("last_message");
        requestSequence = tag.getLong("request_sequence");
        configRevision = tag.getInt("config_revision");
        energyStorage.setEnergy(tag.getInt("energy"));
        if (tag.hasUUID("instance_id")) {
            instanceId = tag.getUUID("instance_id");
        }
        if (tag.hasUUID("owner_id")) {
            ownerId = tag.getUUID("owner_id");
        }
        try {
            status = NotificationStatus.valueOf(tag.getString("status"));
        } catch (IllegalArgumentException ignored) {
            status = NotificationStatus.UNCONFIGURED;
        }
        if (cooldownTicks <= 0) {
            cooldownTicks = DEFAULT_COOLDOWN_TICKS;
        }
    }
}
