package org.geysermc.mappings.definitions.item;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.geysermc.mappings.util.MappingsUtil;
import org.geysermc.paletter.Paletter;
import org.slf4j.Logger;

import java.util.Map;
import java.util.function.BiFunction;

public final class ItemMappings {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Item, ItemEntry> mappings = new Object2ObjectOpenHashMap<>();

    public Map<Item, ItemEntry> mappings() {
        return mappings;
    }

    public void map(Item item, ItemEntry entry) {
        mappings.put(item, entry);
    }

    /// Maps every Java item, which must be mapped to an item variant that {@link MappingsUtil#bedrockLookup()} accepts in the Bedrock data bundled with Paletter.
    public void mapAllItems(BiFunction<Identifier, Item, ItemEntry> mapper) {
        boolean success = true;
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier key = BuiltInRegistries.ITEM.getKey(item);
            ItemEntry entry = mapper.apply(key, item);
            if (Paletter.bundled().item(entry.bedrockIdentifier().toString(), entry.bedrockData(), MappingsUtil.bedrockLookup()) != null) {
                map(item, entry);
            } else {
                success = false;
                LOGGER.error("Java item {} was mapped to bedrock item {} with data {}, which is not accepted by lookup \"{}\"!", key, entry.bedrockIdentifier(), entry.bedrockData(), MappingsUtil.bedrockLookup());
            }
        }
        if (!success) {
            throw new IllegalStateException("Errors occurred whilst mapping items, item mappings are incomplete!");
        }
    }

    public void checkForDuplicates() {
        Map<ItemEntry, Item> duplications = new Object2ObjectOpenHashMap<>();
        for (Map.Entry<Item, ItemEntry> mapping : mappings.entrySet()) {
            Item duplicate = duplications.get(mapping.getValue());
            if (duplicate != null) {
                LOGGER.warn("Possible duplicate item mapping ({} and {}) in mappings: {}", mapping.getKey(), duplicate, mapping.getValue());
            } else {
                duplications.put(mapping.getValue(), mapping.getKey());
            }
        }
    }
}
