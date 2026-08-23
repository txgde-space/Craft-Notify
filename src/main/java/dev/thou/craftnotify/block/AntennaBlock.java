package dev.thou.craftnotify.block;

import com.mojang.serialization.MapCodec;
import dev.thou.craftnotify.registry.ModBlocks;
import dev.thou.craftnotify.blockentity.NotifierBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class AntennaBlock extends Block {
    public static final MapCodec<AntennaBlock> CODEC = simpleCodec(AntennaBlock::new);
    public static final EnumProperty<AntennaPart> PART = EnumProperty.create("part", AntennaPart.class);

    private static final VoxelShape BASE_SHAPE = Shapes.or(
            box(2, 0, 2, 14, 3, 14),
            box(6, 3, 6, 10, 16, 10)
    );
    private static final VoxelShape MIDDLE_SHAPE = box(6, 0, 6, 10, 16, 10);
    private static final VoxelShape TOP_SHAPE = Shapes.or(
            box(6, 0, 6, 10, 12, 10),
            box(3, 10, 3, 13, 14, 13),
            box(7, 14, 7, 9, 16, 9)
    );

    public AntennaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, AntennaPart.BASE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
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
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide()) {
            level.setBlock(pos.above(), defaultBlockState().setValue(PART, AntennaPart.MIDDLE), 3);
            level.setBlock(pos.above(2), defaultBlockState().setValue(PART, AntennaPart.TOP), 3);
            notifyNearbyTerminals(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
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
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative() && state.getValue(PART) != AntennaPart.BASE) {
            popResource(level, pos, new ItemStack(ModBlocks.ANTENNA_ITEM.get()));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static void notifyNearbyTerminals(Level level, BlockPos basePos) {
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos terminalPos = basePos.relative(direction);
            if (level.getBlockState(terminalPos).is(ModBlocks.NOTIFIER.get())) {
                if (level.getBlockEntity(terminalPos) instanceof NotifierBlockEntity notifier) {
                    notifier.onAntennaChanged();
                }
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case BASE -> BASE_SHAPE;
            case MIDDLE -> MIDDLE_SHAPE;
            case TOP -> TOP_SHAPE;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
