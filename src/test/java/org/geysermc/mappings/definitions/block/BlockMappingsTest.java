package org.geysermc.mappings.definitions.block;

import com.mojang.serialization.JavaOps;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.state.BlockState;
import org.geysermc.mappings.FileType;
import org.geysermc.mappings.MinecraftBootstrap;
import org.geysermc.mappings.generator.BlockMappingsGenerator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockMappingsTest {
    /// Every Java block state is mapped to a Bedrock block state of Paletter's data, or the generator throws, and blocks.json holds the same mapping as blocks.nbt, including the types of the Bedrock state values
    @Test
    void mapsEveryBlockState() throws IOException {
        MinecraftBootstrap.bootstrap();
        Map<Path, byte[]> files = new HashMap<>();
        BlockMappingsGenerator generator = new BlockMappingsGenerator(new PackOutput(Path.of("unused")));

        generator.run((path, data, _) -> files.put(path, data)).join();

        String table = new String(files.get(generator.path(FileType.BLOCK_MAPPINGS_TABLE)), StandardCharsets.UTF_8);
        CompoundTag nbt = NbtIo.readCompressed(new ByteArrayInputStream(files.get(generator.path(FileType.BLOCK_MAPPINGS))), NbtAccounter.unlimitedHeap());
        Map<BlockState, BlockEntry> fromTable = BlockMappings.TABLE_CODEC.parse(JavaOps.INSTANCE, table).getOrThrow();
        Map<BlockState, BlockEntry> fromNbt = BlockMappings.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(fromNbt, fromTable);
    }
}
