package dev.thou.craftnotify.block;

import dev.thou.craftnotify.registry.ModBlocks;
import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class AntennaBlock extends Block {
    public static final EnumProperty<AntennaPart> PART = EnumProperty.create("part", AntennaPart.class);
    public static final BooleanProperty TRANSMITTING = BooleanProperty.create("transmitting");
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final VoxelShape BASE_SHAPE = Shapes.or(
            box(1, 0, 1, 15, 3, 15),
            box(5.5, 3, 5.5, 10.5, 11, 10.5),
            box(6.5, 11, 6.5, 9.5, 16, 9.5)
    );
    private static final VoxelShape MIDDLE_SHAPE = Shapes.or(
            box(6.5, 0, 6.5, 9.5, 16, 9.5),
            box(1, 7.2, 7, 15, 8.8, 9),
            box(7, 7.2, 1, 9, 8.8, 15)
    );
    private static final VoxelShape TOP_SHAPE = Shapes.or(
            box(6.5, 0, 6.5, 9.5, 9, 9.5),
            box(0.5, 8, 7, 15.5, 10, 9),
            box(7, 8, 0.5, 9, 10, 15.5),
            box(6, 9.5, 6, 10, 16, 10)
    );

    public AntennaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(PART, AntennaPart.BASE)
                .setValue(TRANSMITTING, false)
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, TRANSMITTING, NORTH, EAST, SOUTH, WEST);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (!level.getBlockState(pos.above()).canBeReplaced(context)
                || !level.getBlockState(pos.above(2)).canBeReplaced(context)) {
            return null;
        }
        return withLinks(level, pos, defaultBlockState());
    }

    public static BlockState withLinks(LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.getValue(PART) != AntennaPart.BASE) {
            return state.setValue(NORTH, false).setValue(EAST, false)
                    .setValue(SOUTH, false).setValue(WEST, false);
        }
        return state
                .setValue(NORTH, isNotifier(level, pos.north()))
                .setValue(EAST, isNotifier(level, pos.east()))
                .setValue(SOUTH, isNotifier(level, pos.south()))
                .setValue(WEST, isNotifier(level, pos.west()));
    }

    public static void refreshLinks(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.ANTENNA.get()) || state.getValue(PART) != AntennaPart.BASE) {
            return;
        }
        BlockState next = withLinks(level, pos, state);
        if (next != state) {
            level.setBlock(pos, next, 3);
        }
    }

    private static boolean isNotifier(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.NOTIFIER.get());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide()) {
            level.setBlock(pos, withLinks(level, pos, state), 3);
            level.setBlock(pos.above(), defaultBlockState().setValue(PART, AntennaPart.MIDDLE), 3);
            level.setBlock(pos.above(2), defaultBlockState().setValue(PART, AntennaPart.TOP), 3);
            notifyNearbyTerminals(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            BlockPos basePos = pos.below(state.getValue(PART).offset());
            for (int offset = 0; offset < 3; offset++) {
                BlockPos partPos = basePos.above(offset);
                if (!partPos.equals(pos) && level.getBlockState(partPos).is(this)) {
                    level.removeBlock(partPos, false);
                }
            }
            notifyNearbyTerminals(level, basePos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative() && state.getValue(PART) != AntennaPart.BASE) {
            popResource(level, pos, new ItemStack(ModBlocks.ANTENNA_ITEM.get()));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    private static void notifyNearbyTerminals(Level level, BlockPos basePos) {
        for (var direction : Direction.Plane.HORIZONTAL) {
            BlockPos terminalPos = basePos.relative(direction);
            if (level.getBlockState(terminalPos).is(ModBlocks.NOTIFIER.get())) {
                NotifierBlock.refreshLinks(level, terminalPos);
                if (level.getBlockEntity(terminalPos) instanceof NotifierBlockEntity notifier) {
                    notifier.onAntennaChanged();
                }
            }
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return withLinks(level, pos, state);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        refreshLinks(level, pos);
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case BASE -> BASE_SHAPE;
            case MIDDLE -> MIDDLE_SHAPE;
            case TOP -> TOP_SHAPE;
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
