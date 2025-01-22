package org.geysermc.generator.interactions;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.geysermc.generator.Util;
import org.geysermc.generator.javaclass.FieldConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.geysermc.generator.BlockGenerator.blockStateToString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InteractionGenerator {

    // TODO:
    /*
    decorated pot block?
     */

    public static final List<Block> manuallyImplemented  = new ArrayList<>();

    // e.g. furnace
    public List<Block> ALWAYS_SUCCESS = new ArrayList<>();
    // e.g. comparator
    public List<Block> SUCCESS_MAY_BUILD = new ArrayList<>();
    // daylight detector
    public List<Block> ALWAYS_CONSUME = new ArrayList<>();

    public String getClassName(Block block, String clazz) {
        if (manuallyImplemented.contains(block)) {
            if (block instanceof AbstractCauldronBlock) {
                if (block.equals(Blocks.WATER_CAULDRON)) {
                    return "WaterCauldronBlock";
                } else if (block.equals(Blocks.POWDER_SNOW_CAULDRON)) {
                    return "PowderedSnowCauldronBlock";
                } else if (block.equals(Blocks.LAVA_CAULDRON)) {
                    return "LavaCauldronBlock";
                } else {
                    return "CauldronBlock";
                }
            }
            return block.getClass().getSimpleName();
        }

        return clazz;
    }

    public void addInteractionData(FieldConstructor constructor, Block block) {
        if (ALWAYS_SUCCESS.contains(block)) {
            constructor.addMethod("interactionSuccess");
            return;
        }

        if (ALWAYS_CONSUME.contains(block)) {
            constructor.addMethod("interactionConsume");
            return;
        }

        if (SUCCESS_MAY_BUILD.contains(block)) {
            constructor.addMethod("interactionSuccessMayBuild");
        }
    }

    static {
        Util.initialize(); // safety measure

        manuallyImplemented.add(Blocks.CAULDRON);
        manuallyImplemented.add(Blocks.WATER_CAULDRON);
        manuallyImplemented.add(Blocks.LAVA_CAULDRON);
        manuallyImplemented.add(Blocks.POWDER_SNOW_CAULDRON);
        manuallyImplemented.add(Blocks.BEEHIVE);
        manuallyImplemented.add(Blocks.BELL);
        manuallyImplemented.add(Blocks.CAMPFIRE);
        manuallyImplemented.add(Blocks.SOUL_CAMPFIRE);
        manuallyImplemented.add(Blocks.CHISELED_BOOKSHELF);
        manuallyImplemented.add(Blocks.COMPOSTER);
        manuallyImplemented.add(Blocks.VAULT);
        manuallyImplemented.add(Blocks.TNT);
        manuallyImplemented.add(Blocks.SCULK_VEIN);
        manuallyImplemented.add(Blocks.RESPAWN_ANCHOR);
        manuallyImplemented.add(Blocks.REDSTONE_ORE);
        manuallyImplemented.add(Blocks.PUMPKIN);
        manuallyImplemented.add(Blocks.NOTE_BLOCK);
        manuallyImplemented.add(Blocks.LECTERN);
        manuallyImplemented.add(Blocks.JUKEBOX);
        manuallyImplemented.add(Blocks.FLOWER_POT);
        manuallyImplemented.add(Blocks.DECORATED_POT);
        manuallyImplemented.add(Blocks.REDSTONE_WIRE);
    }

    public void generateInteractionData() {
        ClientLevel mockClientLevel = mock(ClientLevel.class);
        mockClientLevel.isClientSide = true;
        mockClientLevel.random = RandomSource.create(); // Used by cave_vines and doors
        when(mockClientLevel.getRandom()).thenReturn(mockClientLevel.random);

        when(mockClientLevel.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());

        Abilities abilities = new Abilities();
        abilities.mayBuild = true;

        LocalPlayer mockPlayer = mock(LocalPlayer.class);

        // Used by bee_hive
        mockPlayer.level = mockClientLevel;
        mockPlayer.position = Vec3.ZERO;
        when(mockPlayer.getInventory()).thenReturn(new Inventory(mockPlayer));

        AtomicBoolean requiresAbilities = new AtomicBoolean(false);
        when(mockPlayer.getAbilities()).then(invocationOnMock -> {
            requiresAbilities.set(true);
            return abilities;
        });

        when(mockPlayer.mayBuild()).then(invocationOnMock -> {
            requiresAbilities.set(true);
            return abilities.mayBuild;
        });

        when(mockPlayer.getDirection()).thenReturn(Direction.UP); // Used by fence_gates

        AtomicReference<ItemStack> item = new AtomicReference<>(ItemStack.EMPTY);
        AtomicBoolean requiresItem = new AtomicBoolean(false);
        when(mockPlayer.getItemInHand(InteractionHand.MAIN_HAND)).then(invocationOnMock -> {
            requiresItem.set(true);
            return item.get();
        });
        when(mockPlayer.getItemInHand(InteractionHand.OFF_HAND)).thenReturn(ItemStack.EMPTY);

        AtomicBoolean checksStates = new AtomicBoolean(false);

        when(mockClientLevel.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);

        BlockHitResult blockHitResult = new BlockHitResult(Vec3.ZERO, Direction.DOWN, BlockPos.ZERO, true);

        for (BlockState state : getAllStates()) {
            try {
                Block block = state.getBlock();
                if (block instanceof AbstractCandleBlock || block instanceof RedStoneOreBlock || block instanceof SignBlock ||
                        block instanceof CakeBlock ) {
                    continue;
                }

                if (manuallyImplemented.contains(state.getBlock())) {
                    continue;
                }

                // todo figure out if we can catch these; they depend on block state properties
                if (block instanceof ButtonBlock) {
                    continue;
                }

                if (block.equals(Blocks.DRAGON_EGG)) {
                    ALWAYS_SUCCESS.add(block); // always teleports
                    continue;
                }

                requiresAbilities.set(false);
                abilities.mayBuild = true;

                requiresItem.set(false);
                item.set(ItemStack.EMPTY);

                if (state.getBlock() instanceof BaseEntityBlock baseEntityBlock) {
                    when(mockClientLevel.getBlockEntity(BlockPos.ZERO)).thenReturn(baseEntityBlock.newBlockEntity(BlockPos.ZERO, state));
                } else {
                    when(mockClientLevel.getBlockEntity(BlockPos.ZERO)).thenReturn(null);
                }
                when(mockClientLevel.getBlockState(new BlockPos(0, 0, 0))).thenReturn(state);

                // First: make sure we didn't forget any state#useWithItem
                InteractionResult result = state.useItemOn(ItemStack.EMPTY, mockClientLevel, mockPlayer, InteractionHand.MAIN_HAND, blockHitResult);
                if (result != InteractionResult.TRY_WITH_EMPTY_HAND) {
                    throw new RuntimeException("Forgot to implement (or mark) useItemOn (geyser: interactWithItem) for block: " + blockStateToString(state));
                }

                if (block instanceof LightBlock) {
                    ALWAYS_CONSUME.add(block);
                    continue;
                }

                InteractionResult noItemResult = state.useWithoutItem(mockClientLevel, mockPlayer, blockHitResult);
                if (!requiresItem.get()) {
                    if (requiresAbilities.get() && noItemResult instanceof InteractionResult.Success) {
                        abilities.mayBuild = false;
                        InteractionResult noMoreMayBuild = state.useWithoutItem(mockClientLevel, mockPlayer, blockHitResult);
                        if (noMoreMayBuild.equals(InteractionResult.PASS)) {
                            SUCCESS_MAY_BUILD.add(block);
                            continue;
                        }
                    }

                    if (noItemResult.equals(InteractionResult.PASS)) {
                        continue; // shouldn't be interesting for us
                    }

                    if (noItemResult instanceof InteractionResult.Success) {
                        ALWAYS_SUCCESS.add(state.getBlock());
                    } else if (noItemResult.equals(InteractionResult.CONSUME)) {
                        ALWAYS_CONSUME.add(state.getBlock());
                    } else {
                        throw new RuntimeException("not impld result: %s, %s".formatted(noItemResult.getClass().getName(), blockStateToString(state)));
                    }
                } else {
                    throw new RuntimeException("Blockstate %s requires item, but does not appear to be manually implemented!".formatted(blockStateToString(state)));
                }
            } catch (Throwable e) {
                // Ignore; this means the block has extended behavior we have to implement manually
                System.out.println("Failed to test interactions for " + blockStateToString(state) + " due to");
                e.printStackTrace(System.out);
            }
        }

        ALWAYS_SUCCESS.forEach(block -> System.out.println(block.getDescriptionId() + " is always a success!"));
        SUCCESS_MAY_BUILD.forEach(block -> System.out.println(block.getDescriptionId() + " is always a success when may build!"));
        ALWAYS_CONSUME.forEach(block -> System.out.println(block.getDescriptionId() + " is always a consume!"));
    }

    public List<BlockState> getAllStates() {
        List<BlockState> states = new ArrayList<>();
        BuiltInRegistries.BLOCK.forEach(block -> states.addAll(block.getStateDefinition().getPossibleStates()));
        return states.stream().sorted(Comparator.comparingInt(Block::getId)).collect(Collectors.toList());
    }
}
