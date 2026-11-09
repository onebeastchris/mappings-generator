package org.geysermc.mappings.definitions.block;

import net.minecraft.data.PackOutput;
import org.geysermc.mappings.FileType;
import org.geysermc.mappings.MinecraftBootstrap;
import org.geysermc.mappings.generator.BlockMappingsGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockMappingsTest {
    /// Every Java block state is mapped to a Bedrock block state of Paletter's newest data, or the generator throws
    @Test
    void mapsEveryBlockState() {
        MinecraftBootstrap.bootstrap();
        Map<Path, byte[]> files = new HashMap<>();
        BlockMappingsGenerator generator = new BlockMappingsGenerator(new PackOutput(Path.of("unused")));

        generator.run((path, data, _) -> files.put(path, data)).join();

        assertTrue(files.containsKey(generator.path(FileType.BLOCK_MAPPINGS)));
    }
}
