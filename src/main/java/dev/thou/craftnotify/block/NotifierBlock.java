package dev.thou.craftnotify.block;

import com.mojang.serialization.MapCodec;
import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class NotifierBlock extends BaseEntityBlock {
    public static final MapCodec<NotifierBlock> CODEC = simpleCodec(NotifierBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public NotifierBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(POWERED, FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NotifierBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide()) {
            boolean powered = level.hasNeighborSignal(pos);
            level.setBlock(pos, state.setValue(POWERED, powered), 2);
            if (level.getBlockEntity(pos) instanceof NotifierBlockEntity notifier) {
                notifier.initializePowerBaseline(powered);
                if (placer instanceof Player player) {
                    notifier.setOwner(player.getUUID());
                }
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   net.minecraft.world.level.block.Block neighborBlock,
                                   BlockPos neighborPos,
                                   boolean movedByPiston) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean powered = level.hasNeighborSignal(pos);
        boolean wasPowered = state.getValue(POWERED);
        if (powered == wasPowered) {
            return;
        }

        level.setBlock(pos, state.setValue(POWERED, powered), 2);
        if (level.getBlockEntity(pos) instanceof NotifierBlockEntity notifier) {
            notifier.onPowerChanged(serverLevel, powered, strongestSignal(level, pos));
        }
    }

    private static int strongestSignal(Level level, BlockPos pos) {
        int result = 0;
        for (Direction direction : Direction.values()) {
            result = Math.max(result, level.getSignal(pos.relative(direction), direction));
        }
        return result;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof NotifierBlockEntity notifier) {
            if (!notifier.canPlayerEdit(serverPlayer)) {
                serverPlayer.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.craft_notify.no_permission"), false);
                return InteractionResult.CONSUME;
            }
            serverPlayer.openMenu(notifier, buf -> {
                buf.writeBlockPos(pos);
                buf.writeVarInt(notifier.configRevision());
                buf.writeUtf(notifier.label(), NotifierBlockEntity.MAX_LABEL_LENGTH);
                buf.writeUtf(notifier.channelId(), NotifierBlockEntity.MAX_CHANNEL_LENGTH);
                buf.writeUtf(notifier.titleTemplate(), NotifierBlockEntity.MAX_TITLE_LENGTH);
                buf.writeUtf(notifier.contentTemplate(), NotifierBlockEntity.MAX_CONTENT_LENGTH);
                buf.writeVarInt(notifier.cooldownSeconds());
                buf.writeUtf(dev.thou.craftnotify.notification.SecretChannelStore.channelIdsForGui(), 512);
                buf.writeUtf(notifier.statusText(), 256);
                buf.writeVarInt(notifier.energyStored());
                buf.writeVarInt(notifier.energyCapacity());
                buf.writeBoolean(notifier.hasCompleteAntenna());
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof NotifierBlockEntity notifier
                ? notifier.comparatorSignal()
                : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return false;
    }
}
