package org.geysermc.mappings.definitions.item;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.geysermc.mappings.names.renamers.ItemNames;
import org.geysermc.mappings.util.MappingsUtil;
import org.geysermc.paletter.Lookup;
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

    /// Maps every Java item, which must be mapped to an item variant that {@link MappingsUtil#bedrockPalette()} accepts in the newest Bedrock data bundled with Paletter.
    public void mapAllItems(BiFunction<Identifier, Item, ItemEntry> mapper) {
        boolean success = true;
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier key = BuiltInRegistries.ITEM.getKey(item);
            ItemEntry entry = mapper.apply(key, item);
            if (MappingsUtil.bedrockPalette().itemVariant(entry.bedrockIdentifier().toString(), entry.bedrockData(), Lookup.EXACT) != null) {
                map(item, entry);
            } else {
                success = false;
                LOGGER.error("Java item {} was mapped to an unknown bedrock item: {}", key, MappingsUtil.bedrockPalette().explainUnknown(entry.bedrockIdentifier().toString(), entry.bedrockData(), Lookup.EXACT));
            }
        }
        if (!success) {
            throw new IllegalStateException("Errors occurred whilst mapping items, item mappings are incomplete!");
        }
    }

    /// Warns about Java-only items ({@link ItemNames#JAVA_ONLY}) whose identifier is a Bedrock item by now, according to
    /// {@link MappingsUtil#bedrockPalette()}: they probably do not need their stand-in anymore.
    public void checkStandIns() {
        for (Item item : ItemNames.JAVA_ONLY) {
            Identifier key = BuiltInRegistries.ITEM.getKey(item);
            if (MappingsUtil.bedrockPalette().itemType(key.toString(), Lookup.EXACT) != null) {
                LOGGER.warn("Java item {} is Java-only and sent as the stand-in {}, but Bedrock has an item {}: remove the stand-in from ItemNames if it is the same item", key, mappings.get(item).bedrockIdentifier(), key);
            }
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
