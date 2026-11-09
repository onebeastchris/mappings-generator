package org.geysermc.mappings.generator;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.geysermc.mappings.definitions.block.BlockEntry;
import org.geysermc.mappings.definitions.block.BlockMappings;
import org.geysermc.mappings.definitions.block.state.BlockMapper;
import org.geysermc.mappings.definitions.block.state.BlockMappers;
import org.geysermc.mappings.FileType;
import org.geysermc.mappings.names.Renamers;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class BlockMappingsGenerator extends MappingsGenerator<Map<BlockState, BlockEntry>> {
    public BlockMappingsGenerator(PackOutput output) {
        super(output, FileType.BLOCK_MAPPINGS);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        BlockMappers.registerMappers();
        BlockMappings mappings = new BlockMappings();
        mappings.mapAllStates(javaState -> {
            String bedrockName = Renamers.BLOCKS.forType(javaState.getBlock()).apply(javaState);
            CompoundTag bedrockState = new CompoundTag();

            for (BlockMapper mapper : BlockMapper.ALL_MAPPERS) {
                mapper.apply(javaState, bedrockState);
            }
            return BlockEntry.of(javaState.getBlock(), bedrockName, bedrockState);
        });
        return CompletableFuture.allOf(saveFile(cache, mappings.mappings()), saveFile(cache, FileType.BLOCK_MAPPINGS_DEBUG, mappings.mappings()));
    }

    @Override
    public String getName() {
        return "Block Mappings Generator";
    }
}
