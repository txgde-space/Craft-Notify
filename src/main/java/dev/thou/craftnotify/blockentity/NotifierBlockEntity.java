package dev.thou.craftnotify.blockentity;

import dev.thou.craftnotify.notification.NotificationDispatcher;
import dev.thou.craftnotify.notification.NotificationJob;
import dev.thou.craftnotify.notification.NotificationResult;
import dev.thou.craftnotify.notification.SecretChannelStore;
import dev.thou.craftnotify.block.AntennaBlock;
import dev.thou.craftnotify.block.AntennaPart;
import dev.thou.craftnotify.block.NotifierBlock;
import dev.thou.craftnotify.energy.TerminalEnergyStorage;
import dev.thou.craftnotify.registry.ModBlockEntities;
import dev.thou.craftnotify.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.time.Instant;
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
    public static final int CHARGE_TICKS = 40;
    public static final int BEAM_RISE_TICKS = 6;
    public static final int BEAM_SHRINK_TICKS = 54;
    public static final int SEND_ANIM_TICKS = CHARGE_TICKS + BEAM_RISE_TICKS + BEAM_SHRINK_TICKS;
    public static final int BEAM_HEIGHT = 48;

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
    private long sendAnimStart = -1L;
    private int lastChargeLit;
    private String pendingChannel;
    private String pendingTitle;
    private String pendingContent;
    private UUID pendingTestPlayer;
    private BlockPos cachedAntennaBase;
    private boolean antennaCacheValid;
    private long lastEnergySaveTick;

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
        invalidateAntennaCache();
        refreshReadyStatus();
        if (status == NotificationStatus.SENDING && sendAnimStart >= 0 && level != null) {
            setAntennaCharge(chargeLitForElapsed(level.getGameTime() - sendAnimStart));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        invalidateAntennaCache();
        if (level instanceof ServerLevel serverLevel) {
            syncVisualState();
            if (shouldKeepTicking()) {
                serverLevel.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
            }
        }
    }

    private void onEnergyChanged(int energy) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int band = energyBand(energy);
        applyEnergyBand(band);
        long now = serverLevel.getGameTime();
        if (now - lastEnergySaveTick >= 20L) {
            lastEnergySaveTick = now;
            setChanged();
        }
        if (status == NotificationStatus.NO_ENERGY && energy >= ENERGY_PER_NOTIFICATION) {
            refreshReadyStatus();
        }
    }

    public static int energyBand(int energy) {
        if (energy < ENERGY_PER_NOTIFICATION) {
            return 0;
        }
        if (energy < 2_500) {
            return 1;
        }
        if (energy < 5_000) {
            return 2;
        }
        if (energy < 7_500) {
            return 3;
        }
        return 4;
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

    public boolean shouldKeepTicking() {
        return status == NotificationStatus.SENDING && sendAnimStart >= 0;
    }

    public void serverTick(ServerLevel level) {
        if (!shouldKeepTicking()) {
            return;
        }
        if (antennaBasePos() == null) {
            cancelPending(false);
            setStatus(NotificationStatus.MISSING_ANTENNA, "A complete 3-block antenna must be adjacent");
            return;
        }
        long elapsed = level.getGameTime() - sendAnimStart;
        int lit = chargeLitForElapsed(elapsed);
        if (lit != lastChargeLit) {
            lastChargeLit = lit;
            setAntennaCharge(lit);
            spawnChargeParticles(level, lit);
        }
        if (elapsed >= SEND_ANIM_TICKS) {
            dispatchPending(level);
        }
    }

    private void trigger(ServerLevel level, int signalPower) {
        if (status == NotificationStatus.SENDING) {
            return;
        }
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
        int suppressed = suppressedTriggers;
        suppressedTriggers = 0;
        String serverName = level.getServer().getMotd();
        NotificationJob job = NotificationJob.from(
                channelId, titleTemplate, contentTemplate, label, serverName,
                level.dimension().location().toString(), worldPosition, signalPower, suppressed
        );
        beginTransmit(level, job, null);
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
        if (status == NotificationStatus.SENDING) {
            player.displayClientMessage(Component.translatable("message.craft_notify.busy"), false);
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
        beginTransmit(level, job, player.getUUID());
    }

    private void beginTransmit(ServerLevel level, NotificationJob job, UUID testPlayer) {
        pendingChannel = job.channelId();
        pendingTitle = job.title();
        pendingContent = job.content();
        pendingTestPlayer = testPlayer;
        sendAnimStart = level.getGameTime();
        lastChargeLit = 1;
        setStatus(NotificationStatus.SENDING, "Charging antenna");
        setAntennaCharge(1);
        spawnChargeParticles(level, 1);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    private void dispatchPending(ServerLevel level) {
        String channel = pendingChannel;
        String title = pendingTitle;
        String content = pendingContent;
        UUID testPlayer = pendingTestPlayer;
        clearPending();
        setAntennaCharge(0);
        lastChargeLit = 0;
        if (channel == null || title == null || content == null) {
            releaseReservedEnergy(false);
            setStatus(NotificationStatus.FAILED, "Transmit aborted");
            return;
        }
        long sequence = ++requestSequence;
        NotificationJob job = new NotificationJob(channel, title, content, Instant.now());
        setStatus(NotificationStatus.SENDING, "Sending notification");
        NotificationDispatcher.dispatch(job).whenComplete((result, error) -> level.getServer().execute(() -> {
            if (testPlayer != null) {
                applyTestResult(level, testPlayer, result, error);
            } else {
                applyResult(level, sequence, result, error);
            }
        }));
    }

    private void applyTestResult(ServerLevel level, UUID playerId, NotificationResult result, Throwable error) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (error != null) {
            releaseReservedEnergy(false);
            setStatus(NotificationStatus.FAILED, "Request failed: " + safeError(error));
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.craft_notify.test_failed", safeError(error)), false);
            }
        } else if (result != null && result.accepted()) {
            releaseReservedEnergy(true);
            setStatus(NotificationStatus.READY, result.message() + "; used " + ENERGY_PER_NOTIFICATION + " FE");
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.craft_notify.test_accepted"), false);
            }
        } else {
            releaseReservedEnergy(false);
            String message = result == null ? "No response" : result.message();
            setStatus(NotificationStatus.FAILED, message);
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.craft_notify.test_failed", message), false);
            }
        }
    }

    private void cancelPending(boolean consumeEnergy) {
        releaseReservedEnergy(consumeEnergy);
        clearPending();
        setAntennaCharge(0);
        lastChargeLit = 0;
    }

    private void clearPending() {
        pendingChannel = null;
        pendingTitle = null;
        pendingContent = null;
        pendingTestPlayer = null;
        sendAnimStart = -1L;
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
                setChanged();
            }
        }
    }

    private void refreshReadyStatus() {
        if (status == NotificationStatus.SENDING) {
            return;
        }
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
        if (level instanceof ServerLevel serverLevel) {
            boolean sending = newStatus == NotificationStatus.SENDING;
            BlockState state = getBlockState();
            if (state.hasProperty(NotifierBlock.SENDING) && state.getValue(NotifierBlock.SENDING) != sending) {
                serverLevel.setBlock(worldPosition, state.setValue(NotifierBlock.SENDING, sending), 2);
            }
            if (!sending) {
                setAntennaCharge(0);
                lastChargeLit = 0;
                if (pendingChannel != null || sendAnimStart >= 0) {
                    clearPending();
                }
            }
            serverLevel.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    private void syncVisualState() {
        applyEnergyBand(energyBand(energyStorage.getEnergyStored()));
        if (level instanceof ServerLevel serverLevel) {
            boolean sending = status == NotificationStatus.SENDING;
            BlockState state = getBlockState();
            if (state.hasProperty(NotifierBlock.SENDING) && state.getValue(NotifierBlock.SENDING) != sending) {
                serverLevel.setBlock(worldPosition, state.setValue(NotifierBlock.SENDING, sending), 2);
            }
        }
        if (status == NotificationStatus.SENDING && sendAnimStart >= 0 && level != null) {
            setAntennaCharge(chargeLitForElapsed(level.getGameTime() - sendAnimStart));
        } else {
            setAntennaCharge(0);
        }
    }

    private void applyEnergyBand(int band) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(NotifierBlock.ENERGY) && state.getValue(NotifierBlock.ENERGY) != band) {
            serverLevel.setBlock(worldPosition, state.setValue(NotifierBlock.ENERGY, band), 2);
        }
    }

    public void clearAntennaTransmitting() {
        setAntennaCharge(0);
    }

    private static int chargeLitForElapsed(long elapsed) {
        if (elapsed < 0 || elapsed >= SEND_ANIM_TICKS) {
            return 0;
        }
        if (elapsed >= 26) {
            return 3;
        }
        if (elapsed >= 13) {
            return 2;
        }
        return 1;
    }

    private void setAntennaCharge(int litParts) {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos base = worldPosition.relative(direction);
            if (!(isAntennaPart(base, AntennaPart.BASE)
                    && isAntennaPart(base.above(), AntennaPart.MIDDLE)
                    && isAntennaPart(base.above(2), AntennaPart.TOP))) {
                continue;
            }
            for (int offset = 0; offset < 3; offset++) {
                boolean transmitting = offset < litParts;
                BlockPos partPos = base.above(offset);
                BlockState partState = level.getBlockState(partPos);
                if (partState.is(ModBlocks.ANTENNA.get())
                        && partState.hasProperty(AntennaBlock.TRANSMITTING)
                        && partState.getValue(AntennaBlock.TRANSMITTING) != transmitting) {
                    level.setBlock(partPos, partState.setValue(AntennaBlock.TRANSMITTING, transmitting), 2);
                }
            }
        }
    }

    private void spawnChargeParticles(ServerLevel level, int litParts) {
        BlockPos base = antennaBasePos();
        if (base == null || litParts <= 0) {
            return;
        }
        BlockPos part = base.above(litParts - 1);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                part.getX() + 0.5, part.getY() + 0.6, part.getZ() + 0.5,
                8, 0.15, 0.25, 0.15, 0.02);
        level.sendParticles(ParticleTypes.GLOW,
                part.getX() + 0.5, part.getY() + 0.8, part.getZ() + 0.5,
                4, 0.1, 0.2, 0.1, 0.01);
    }

    public boolean isTransmitAnimating() {
        if (sendAnimStart < 0 || level == null || status != NotificationStatus.SENDING) {
            return false;
        }
        return level.getGameTime() - sendAnimStart < SEND_ANIM_TICKS;
    }

    public float sendElapsed(float partialTick) {
        if (sendAnimStart < 0 || level == null) {
            return -1.0F;
        }
        return (level.getGameTime() - sendAnimStart) + partialTick;
    }

    public static float beamHeightAt(float elapsed) {
        float local = elapsed - CHARGE_TICKS;
        if (local <= 0.0F) {
            return 0.0F;
        }
        if (local < BEAM_RISE_TICKS) {
            return easeOutExpo(local / BEAM_RISE_TICKS) * BEAM_HEIGHT;
        }
        float t = Mth.clamp((local - BEAM_RISE_TICKS) / BEAM_SHRINK_TICKS, 0.0F, 1.0F);
        return (1.0F - easeInQuart(t)) * BEAM_HEIGHT;
    }

    public static float beamRadiusAt(float elapsed) {
        float local = elapsed - CHARGE_TICKS;
        if (local <= 0.0F) {
            return 0.0F;
        }
        if (local < BEAM_RISE_TICKS) {
            float t = easeOutExpo(local / BEAM_RISE_TICKS);
            return 0.08F + 0.16F * t;
        }
        float t = Mth.clamp((local - BEAM_RISE_TICKS) / BEAM_SHRINK_TICKS, 0.0F, 1.0F);
        return 0.24F * (float) Math.pow(1.0F - t, 1.65);
    }

    private static float easeOutExpo(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, -10.0 * t);
    }

    private static float easeInQuart(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * t * t;
    }

    public BlockPos antennaBasePos() {
        if (level == null) {
            return null;
        }
        if (antennaCacheValid) {
            return cachedAntennaBase;
        }
        BlockPos found = null;
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos base = worldPosition.relative(direction);
            if (isAntennaPart(base, AntennaPart.BASE)
                    && isAntennaPart(base.above(), AntennaPart.MIDDLE)
                    && isAntennaPart(base.above(2), AntennaPart.TOP)) {
                found = base;
                break;
            }
        }
        cachedAntennaBase = found;
        antennaCacheValid = true;
        return found;
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
        return antennaBasePos() != null;
    }

    private void invalidateAntennaCache() {
        antennaCacheValid = false;
        cachedAntennaBase = null;
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
        tag.putLong("send_anim_start", sendAnimStart);
        if (pendingChannel != null) {
            tag.putString("pending_channel", pendingChannel);
            tag.putString("pending_title", pendingTitle == null ? "" : pendingTitle);
            tag.putString("pending_content", pendingContent == null ? "" : pendingContent);
        }
        if (pendingTestPlayer != null) {
            tag.putUUID("pending_test_player", pendingTestPlayer);
        }
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
        sendAnimStart = tag.contains("send_anim_start") ? tag.getLong("send_anim_start") : -1L;
        if (tag.contains("pending_channel")) {
            pendingChannel = tag.getString("pending_channel");
            pendingTitle = tag.getString("pending_title");
            pendingContent = tag.getString("pending_content");
        }
        if (tag.hasUUID("pending_test_player")) {
            pendingTestPlayer = tag.getUUID("pending_test_player");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
