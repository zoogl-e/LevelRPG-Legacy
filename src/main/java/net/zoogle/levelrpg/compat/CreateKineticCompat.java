package net.zoogle.levelrpg.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public final class CreateKineticCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CREATE_MOD_ID = "create";
    private static final String KINETIC_BLOCK_ENTITY_CLASS_NAME = "com.simibubi.create.content.kinetics.base.KineticBlockEntity";
    private static final String WATER_WHEEL_BLOCK_ENTITY_CLASS_NAME = "com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity";
    private static final String LARGE_WATER_WHEEL_BLOCK_ENTITY_CLASS_NAME = "com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlockEntity";
    private static final String WATER_WHEEL_STRUCTURAL_BLOCK_CLASS_NAME = "com.simibubi.create.content.kinetics.waterwheel.WaterWheelStructuralBlock";
    private static final String ADVANCEMENT_BEHAVIOUR_CLASS_NAME = "com.simibubi.create.foundation.advancement.AdvancementBehaviour";
    private static final int LARGE_WATER_WHEEL_SCAN_PADDING = 3;

    private CreateKineticCompat() {
    }

    public static void refreshGeneratedKinetics(ServerLevel level, BoundingBox bounds) {
        if (!ModList.get().isLoaded(CREATE_MOD_ID)) {
            return;
        }
        try {
            KineticReflection reflection = KineticReflection.load();
            BoundingBox scanBounds = expanded(bounds, LARGE_WATER_WHEEL_SCAN_PADDING);
            Set<BlockPos> refreshedPositions = new HashSet<>();
            int refreshed = 0;
            int refreshedWaterWheels = 0;
            int refreshedLargeWaterWheels = 0;
            int structuralBlocks = 0;
            int resolvedStructuralMasters = 0;
            for (BlockPos cursor : BlockPos.betweenClosed(scanBounds.minX(), scanBounds.minY(), scanBounds.minZ(), scanBounds.maxX(), scanBounds.maxY(), scanBounds.maxZ())) {
                BlockPos pos = cursor.immutable();
                BlockState state = level.getBlockState(pos);
                if (reflection.isWaterWheelStructuralBlock(state)) {
                    structuralBlocks++;
                    BlockPos masterPos = reflection.resolveWaterWheelStructuralMaster(level, pos, state);
                    if (masterPos != null) {
                        BlockEntity masterBlockEntity = level.getBlockEntity(masterPos);
                        if (reflection.isWaterWheel(masterBlockEntity) && refreshedPositions.add(masterPos)) {
                            refreshKinetic(level, reflection, masterPos, masterBlockEntity);
                            refreshWaterWheelFlow(reflection, masterBlockEntity);
                            refreshed++;
                            refreshedWaterWheels++;
                            if (reflection.isLargeWaterWheel(masterBlockEntity)) {
                                refreshedLargeWaterWheels++;
                            }
                        }
                        resolvedStructuralMasters++;
                    }
                }
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (!reflection.isKinetic(blockEntity)) {
                    continue;
                }
                if (!refreshedPositions.add(pos)) {
                    continue;
                }
                refreshKinetic(level, reflection, pos, blockEntity);
                if (reflection.isWaterWheel(blockEntity)) {
                    refreshWaterWheelFlow(reflection, blockEntity);
                    refreshedWaterWheels++;
                    if (reflection.isLargeWaterWheel(blockEntity)) {
                        refreshedLargeWaterWheels++;
                    }
                }
                refreshed++;
            }
            LOGGER.info(
                    "Refreshed {} generated Create kinetic block entit(ies), including {} water wheel(s) and {} large water wheel(s), in {} using scan bounds {}. Found {} water wheel structural block(s), resolved {} master position(s).",
                    refreshed,
                    refreshedWaterWheels,
                    refreshedLargeWaterWheels,
                    bounds,
                    scanBounds,
                    structuralBlocks,
                    resolvedStructuralMasters
            );
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to refresh generated Create kinetics in {}", bounds, throwable);
        }
    }

    private static BoundingBox expanded(BoundingBox bounds, int amount) {
        return new BoundingBox(
                bounds.minX() - amount,
                bounds.minY() - amount,
                bounds.minZ() - amount,
                bounds.maxX() + amount,
                bounds.maxY() + amount,
                bounds.maxZ() + amount
        );
    }

    private static void refreshKinetic(ServerLevel level, KineticReflection reflection, BlockPos pos, BlockEntity blockEntity) throws ReflectiveOperationException {
        reflection.detachKinetics(blockEntity);
        reflection.clearKineticInformation(blockEntity);
        reflection.removeSource(blockEntity);
        blockEntity.setChanged();
        BlockState state = level.getBlockState(pos);
        level.updateNeighborsAt(pos, state.getBlock());
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            level.updateNeighborsAt(neighbor, level.getBlockState(neighbor).getBlock());
        }
        reflection.initialize(blockEntity);
        reflection.attachKinetics(blockEntity);
    }

    private static void refreshWaterWheelFlow(KineticReflection reflection, BlockEntity blockEntity) throws ReflectiveOperationException {
        reflection.removeAdvancementBehaviour(blockEntity);
        int flowScore = reflection.calculateWaterWheelFlowScore(blockEntity);
        reflection.setWaterWheelFlowScore(blockEntity, flowScore);
    }

    private record KineticReflection(
            Class<?> kineticClass,
            Class<?> waterWheelClass,
            Class<?> largeWaterWheelClass,
            Class<?> waterWheelStructuralBlockClass,
            Object advancementBehaviourType,
            Method detachKineticsMethod,
            Method clearKineticInformationMethod,
            Method removeSourceMethod,
            Method initializeMethod,
            Method attachKineticsMethod,
            Method removeBehaviourMethod,
            Method getAxisMethod,
            Method getOffsetsToCheckMethod,
            Method getFlowVectorAtPositionMethod,
            Method setFlowScoreAndUpdateMethod,
            Method getWaterWheelStructuralMasterMethod
    ) {
        static KineticReflection load() throws ReflectiveOperationException {
            Class<?> kineticClass = Class.forName(KINETIC_BLOCK_ENTITY_CLASS_NAME);
            Class<?> waterWheelClass = Class.forName(WATER_WHEEL_BLOCK_ENTITY_CLASS_NAME);
            Class<?> largeWaterWheelClass = Class.forName(LARGE_WATER_WHEEL_BLOCK_ENTITY_CLASS_NAME);
            Class<?> waterWheelStructuralBlockClass = Class.forName(WATER_WHEEL_STRUCTURAL_BLOCK_CLASS_NAME);
            Class<?> advancementBehaviourClass = Class.forName(ADVANCEMENT_BEHAVIOUR_CLASS_NAME);
            Object advancementBehaviourType = advancementBehaviourClass.getField("TYPE").get(null);
            Class<?> behaviourTypeClass = Class.forName("com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType");
            Method getAxisMethod = waterWheelClass.getDeclaredMethod("getAxis");
            Method getOffsetsToCheckMethod = waterWheelClass.getDeclaredMethod("getOffsetsToCheck");
            getAxisMethod.setAccessible(true);
            getOffsetsToCheckMethod.setAccessible(true);
            return new KineticReflection(
                    kineticClass,
                    waterWheelClass,
                    largeWaterWheelClass,
                    waterWheelStructuralBlockClass,
                    advancementBehaviourType,
                    kineticClass.getMethod("detachKinetics"),
                    kineticClass.getMethod("clearKineticInformation"),
                    kineticClass.getMethod("removeSource"),
                    kineticClass.getMethod("initialize"),
                    kineticClass.getMethod("attachKinetics"),
                    kineticClass.getMethod("removeBehaviour", behaviourTypeClass),
                    getAxisMethod,
                    getOffsetsToCheckMethod,
                    waterWheelClass.getMethod("getFlowVectorAtPosition", BlockPos.class),
                    waterWheelClass.getMethod("setFlowScoreAndUpdate", int.class),
                    waterWheelStructuralBlockClass.getMethod("getMaster", BlockGetter.class, BlockPos.class, BlockState.class)
            );
        }

        boolean isKinetic(BlockEntity blockEntity) {
            return blockEntity != null && kineticClass.isInstance(blockEntity);
        }

        boolean isWaterWheel(BlockEntity blockEntity) {
            return blockEntity != null && waterWheelClass.isInstance(blockEntity);
        }

        boolean isLargeWaterWheel(BlockEntity blockEntity) {
            return blockEntity != null && largeWaterWheelClass.isInstance(blockEntity);
        }

        boolean isWaterWheelStructuralBlock(BlockState state) {
            return waterWheelStructuralBlockClass.isInstance(state.getBlock());
        }

        BlockPos resolveWaterWheelStructuralMaster(ServerLevel level, BlockPos pos, BlockState state) throws ReflectiveOperationException {
            Object result = getWaterWheelStructuralMasterMethod.invoke(null, level, pos, state);
            return result instanceof BlockPos masterPos ? masterPos : null;
        }

        void detachKinetics(Object blockEntity) throws ReflectiveOperationException {
            detachKineticsMethod.invoke(blockEntity);
        }

        void clearKineticInformation(Object blockEntity) throws ReflectiveOperationException {
            clearKineticInformationMethod.invoke(blockEntity);
        }

        void removeSource(Object blockEntity) throws ReflectiveOperationException {
            removeSourceMethod.invoke(blockEntity);
        }

        void initialize(Object blockEntity) throws ReflectiveOperationException {
            initializeMethod.invoke(blockEntity);
        }

        void attachKinetics(Object blockEntity) throws ReflectiveOperationException {
            attachKineticsMethod.invoke(blockEntity);
        }

        void removeAdvancementBehaviour(Object blockEntity) throws ReflectiveOperationException {
            removeBehaviourMethod.invoke(blockEntity, advancementBehaviourType);
        }

        int calculateWaterWheelFlowScore(Object blockEntity) throws ReflectiveOperationException {
            Direction.Axis axis = (Direction.Axis) getAxisMethod.invoke(blockEntity);
            Vec3 mask = Vec3.atLowerCornerOf(new Vec3i(1, 1, 1).subtract(Direction.get(Direction.AxisDirection.POSITIVE, axis).getNormal()));
            int flowScore = 0;
            @SuppressWarnings("unchecked")
            Set<BlockPos> offsets = (Set<BlockPos>) getOffsetsToCheckMethod.invoke(blockEntity);
            BlockPos wheelPos = ((BlockEntity) blockEntity).getBlockPos();
            for (BlockPos offset : offsets) {
                BlockPos samplePos = wheelPos.offset(offset);
                Vec3 flow = ((Vec3) getFlowVectorAtPositionMethod.invoke(blockEntity, samplePos)).multiply(mask);
                if (flow.lengthSqr() == 0.0D) {
                    continue;
                }
                Vec3 normalizedFlow = flow.normalize();
                Vec3 tangent = rotate90(Vec3.atLowerCornerOf(offset).normalize(), axis);
                double dot = normalizedFlow.dot(tangent);
                if (Math.abs(dot) > 0.5D) {
                    flowScore += (int) Math.signum(dot);
                }
            }
            return flowScore;
        }

        void setWaterWheelFlowScore(Object blockEntity, int flowScore) throws ReflectiveOperationException {
            setFlowScoreAndUpdateMethod.invoke(blockEntity, flowScore);
        }

        private static Vec3 rotate90(Vec3 vec, Direction.Axis axis) {
            return switch (axis) {
                case X -> new Vec3(vec.x, -vec.z, vec.y);
                case Y -> new Vec3(vec.z, vec.y, -vec.x);
                case Z -> new Vec3(-vec.y, vec.x, vec.z);
            };
        }
    }
}
