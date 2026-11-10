package org.geysermc.mappings.definitions.item;

import net.minecraft.data.PackOutput;
import org.geysermc.mappings.FileType;
import org.geysermc.mappings.MinecraftBootstrap;
import org.geysermc.mappings.generator.ItemMappingsGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemMappingsTest {
    /// Every Java item is mapped to an item variant of Paletter's data, or the generator throws
    @Test
    void mapsEveryItem() {
        MinecraftBootstrap.bootstrap();
        Map<Path, byte[]> files = new HashMap<>();
        ItemMappingsGenerator generator = new ItemMappingsGenerator(new PackOutput(Path.of("unused")));

        generator.run((path, data, _) -> files.put(path, data)).join();

        assertTrue(files.containsKey(generator.path(FileType.ITEM_MAPPINGS)));
    }
}
