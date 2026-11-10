package org.geysermc.mappings.generator.javaclass;

import net.minecraft.data.PackOutput;
import org.geysermc.mappings.FileType;
import org.geysermc.mappings.MinecraftBootstrap;
import org.geysermc.mappings.util.RegistryAccessUtil;
import org.geysermc.paletter.vanilla.BedrockItems;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemsGeneratorTest {
    private static final Pattern CONSTANT = Pattern.compile("BedrockItems\\.(\\w+)");

    /// Every constant Items.java refers to exists in BedrockItems
    @Test
    void refersToExistingConstants() {
        MinecraftBootstrap.bootstrapWithComponents();
        Map<Path, byte[]> files = new HashMap<>();
        ItemsGenerator generator = new ItemsGenerator(new PackOutput(Path.of("unused")), RegistryAccessUtil.create());

        generator.run((path, data, _) -> files.put(path, data)).join();

        Matcher constants = CONSTANT.matcher(new String(files.get(generator.path(FileType.ITEMS)), StandardCharsets.UTF_8));
        int count = 0;
        while (constants.find()) {
            String name = constants.group(1);
            assertDoesNotThrow(() -> BedrockItems.class.getField(name), name);
            count++;
        }
        assertTrue(count > 0);
    }
}
