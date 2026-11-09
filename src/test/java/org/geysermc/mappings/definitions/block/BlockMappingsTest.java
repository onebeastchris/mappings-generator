package org.geysermc.mappings.definitions.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.state.BlockState;
import org.geysermc.mappings.FileType;
import org.geysermc.mappings.MinecraftBootstrap;
import org.geysermc.mappings.generator.BlockMappingsGenerator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockMappingsTest {
    /// Every Java block state is mapped to a Bedrock block state of Paletter's data, or the generator throws, and blocks.json holds the same mapping as blocks_debug.json
    @Test
    void mapsEveryBlockState() {
        MinecraftBootstrap.bootstrap();
        Map<Path, byte[]> files = new HashMap<>();
        BlockMappingsGenerator generator = new BlockMappingsGenerator(new PackOutput(Path.of("unused")));

        generator.run((path, data, _) -> files.put(path, data)).join();

        String table = new String(files.get(generator.path(FileType.BLOCK_MAPPINGS_TABLE)), StandardCharsets.UTF_8);
        String debug = new String(files.get(generator.path(FileType.BLOCK_MAPPINGS_DEBUG)), StandardCharsets.UTF_8);
        Map<BlockState, BlockEntry> fromTable = BlockMappings.TABLE_CODEC.parse(JavaOps.INSTANCE, table).getOrThrow();
        Map<BlockState, BlockEntry> fromDebug = BlockMappings.DEBUG_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(debug)).getOrThrow();
        // Compared as JSON, as that is how the debug file holds the Bedrock state values
        assertEquals(json(fromDebug), json(fromTable));
    }

    private static JsonElement json(Map<BlockState, BlockEntry> mappings) {
        return BlockMappings.DEBUG_CODEC.encodeStart(JsonOps.INSTANCE, mappings).getOrThrow();
    }
}
