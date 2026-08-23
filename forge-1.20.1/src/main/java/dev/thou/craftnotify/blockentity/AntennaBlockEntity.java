package dev.thou.craftnotify.blockentity;

import dev.thou.craftnotify.block.AntennaBlock;
import dev.thou.craftnotify.registry.ModBlockEntities;
import dev.thou.craftnotify.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class AntennaBlockEntity extends BlockEntity {
    public static final int DEPLOY_TICKS = 14;

    private boolean extended;
    private long animStart;
    private float animFrom;
    private boolean animReady;

    public AntennaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANTENNA.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AntennaBlockEntity antenna) {
        if (level.getGameTime() % 20L == 0L) {
            antenna.refreshDeploy();
        }
    }

    public void refreshDeploy() {
        if (level == null || level.isClientSide()) {
            return;
        }
        setExtended(hasLinkedPower());
    }

    private boolean hasLinkedPower() {
        if (level == null) {
            return false;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof NotifierBlockEntity notifier
                    && notifier.energyStored() > 0) {
                return true;
            }
        }
        return false;
    }

    private void setExtended(boolean next) {
        if (extended == next) {
            return;
        }
        animFrom = deployAmount(0.0F);
        animStart = level == null ? 0L : level.getGameTime();
        extended = next;
        animReady = true;
        applyExtendedState();
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockState state = getBlockState();
            serverLevel.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    private void applyExtendedState() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (int offset = 1; offset <= 2; offset++) {
            BlockPos partPos = worldPosition.above(offset);
            BlockState state = serverLevel.getBlockState(partPos);
            if (state.is(ModBlocks.ANTENNA.get())
                    && state.hasProperty(AntennaBlock.EXTENDED)
                    && state.getValue(AntennaBlock.EXTENDED) != extended) {
                serverLevel.setBlock(partPos, state.setValue(AntennaBlock.EXTENDED, extended), 3);
            }
        }
    }

    public boolean isExtended() {
        return extended;
    }

    public float deployAmount(float partialTick) {
        float target = extended ? 1.0F : 0.0F;
        if (level == null || !animReady) {
            return target;
        }
        float t = Mth.clamp((level.getGameTime() - animStart + partialTick) / DEPLOY_TICKS, 0.0F, 1.0F);
        t = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        return Mth.lerp(t, animFrom, target);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            refreshDeploy();
        } else {
            animFrom = extended ? 1.0F : 0.0F;
            animStart = 0L;
            animReady = true;
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0.0, 3.0, 0.0).inflate(1.25);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("extended", extended);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boolean next = tag.getBoolean("extended");
        if (level != null && level.isClientSide()) {
            if (animReady && next != extended) {
                animFrom = deployAmount(0.0F);
                animStart = level.getGameTime();
            } else if (!animReady) {
                animFrom = next ? 1.0F : 0.0F;
                animStart = 0L;
                animReady = true;
            }
        } else {
            animFrom = next ? 1.0F : 0.0F;
            animStart = 0L;
        }
        extended = next;
    }
}
