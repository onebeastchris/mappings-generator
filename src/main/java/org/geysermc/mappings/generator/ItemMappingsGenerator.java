package org.geysermc.mappings.generator;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import org.geysermc.mappings.definitions.item.ItemEntry;
import org.geysermc.mappings.definitions.item.ItemMappings;
import org.geysermc.mappings.FileType;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ItemMappingsGenerator extends MappingsGenerator<Map<Item, ItemEntry>> {
    public ItemMappingsGenerator(PackOutput output) {
        super(output, FileType.ITEM_MAPPINGS);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        ItemMappings mappings = new ItemMappings();
        mappings.mapAllItems((_, item) -> ItemEntry.of(item));
        mappings.checkForDuplicates();
        return saveFile(cache, mappings.mappings());
    }

    @Override
    public String getName() {
        return "Item Mappings Generator";
    }
}
