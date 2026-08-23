package dev.thou.craftnotify.block;

import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import dev.thou.craftnotify.preset.GuiPresetStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public final class NotifierBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty SENDING = BooleanProperty.create("sending");
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");
    public static final IntegerProperty ENERGY = IntegerProperty.create("energy", 0, 4);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    public NotifierBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(POWERED, false)
                .setValue(SENDING, false)
                .setValue(ENABLED, false)
                .setValue(ENERGY, 0)
                .setValue(FACING, Direction.NORTH)
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(POWERED, SENDING, ENABLED, ENERGY, FACING, NORTH, EAST, SOUTH, WEST);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return withLinks(context.getLevel(), pos,
                defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()));
    }

    public static BlockState withLinks(LevelAccessor level, BlockPos pos, BlockState state) {
        return state
                .setValue(NORTH, isAntennaBase(level, pos.north()))
                .setValue(EAST, isAntennaBase(level, pos.east()))
                .setValue(SOUTH, isAntennaBase(level, pos.south()))
                .setValue(WEST, isAntennaBase(level, pos.west()));
    }

    public static void refreshLinks(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(dev.thou.craftnotify.registry.ModBlocks.NOTIFIER.get())) {
            return;
        }
        BlockState next = withLinks(level, pos, state);
        if (next != state) {
            level.setBlock(pos, next, 3);
        }
    }

    private static boolean isAntennaBase(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(dev.thou.craftnotify.registry.ModBlocks.ANTENNA.get())
                && state.hasProperty(AntennaBlock.PART)
                && state.getValue(AntennaBlock.PART) == AntennaPart.BASE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NotifierBlockEntity(pos, state);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof NotifierBlockEntity notifier) {
            notifier.serverTick(level);
            if (notifier.shouldKeepTicking()) {
                level.scheduleTick(pos, this, 1);
            }
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return withLinks(level, pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide()) {
            boolean powered = level.hasNeighborSignal(pos);
            level.setBlock(pos, withLinks(level, pos, state.setValue(POWERED, powered)), 3);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                AntennaBlock.refreshLinks(level, pos.relative(direction));
            }
            if (level.getBlockEntity(pos) instanceof NotifierBlockEntity notifier) {
                notifier.initializePowerBaseline(powered);
                if (placer instanceof Player player) {
                    notifier.setOwner(player.getUUID());
                }
            }
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                net.minecraft.world.level.block.Block neighborBlock,
                                BlockPos neighborPos,
                                boolean movedByPiston) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState next = withLinks(level, pos, state);
        boolean powered = level.hasNeighborSignal(pos);
        boolean wasPowered = state.getValue(POWERED);
        if (powered != wasPowered) {
            next = next.setValue(POWERED, powered);
        }
        if (next != state) {
            level.setBlock(pos, next, 3);
        }
        if (powered != wasPowered && level.getBlockEntity(pos) instanceof NotifierBlockEntity notifier) {
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
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide()
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof NotifierBlockEntity notifier) {
            if (!notifier.canPlayerEdit(serverPlayer)) {
                serverPlayer.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.craft_notify.no_permission"), false);
                return InteractionResult.CONSUME;
            }
            NetworkHooks.openScreen(serverPlayer, notifier, buf -> {
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
                buf.writeBoolean(notifier.enabled());
                GuiPresetStore.catalog().write(buf);
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof NotifierBlockEntity notifier
                ? notifier.comparatorSignal()
                : 0;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof NotifierBlockEntity notifier) {
                notifier.clearAntennaTransmitting();
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                AntennaBlock.refreshLinks(level, pos.relative(direction));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
