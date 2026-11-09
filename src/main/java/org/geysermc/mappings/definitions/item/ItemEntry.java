package org.geysermc.mappings.definitions.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.geysermc.mappings.names.Renamers;

public record ItemEntry(Identifier bedrockIdentifier, int bedrockData, boolean isBlock,
                        int firstBlockRuntimeId, int lastBlockRuntimeId, boolean entityPlacer) {
    public static final Codec<ItemEntry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Identifier.CODEC.fieldOf("bedrock_identifier").forGetter(ItemEntry::bedrockIdentifier),
                    Codec.INT.optionalFieldOf("bedrock_data", 0).forGetter(ItemEntry::bedrockData),
                    Codec.BOOL.optionalFieldOf("is_block", false).forGetter(ItemEntry::isBlock),
                    Codec.INT.optionalFieldOf("firstBlockRuntimeId", -1).forGetter(ItemEntry::firstBlockRuntimeId),
                    Codec.INT.optionalFieldOf("lastBlockRuntimeId", -1).forGetter(ItemEntry::lastBlockRuntimeId),
                    Codec.BOOL.optionalFieldOf("is_entity_placer", false).forGetter(ItemEntry::entityPlacer)

            ).apply(instance, ItemEntry::new)
    );
    public static final ItemEntry UNKNOWN = new ItemEntry(Identifier.withDefaultNamespace("unknown"), 0);
    private static final Identifier BANNER = Identifier.withDefaultNamespace("banner");
    private static final Identifier BED = Identifier.withDefaultNamespace("bed");

    public ItemEntry(Identifier bedrockIdentifier, int bedrockData) {
        this(bedrockIdentifier, bedrockData, false, -1, -1, false);
    }

    /// @return the Bedrock item a Java item is sent as. Only banners and beds are told apart by their Bedrock data, everything else was flattened into items of their own
    public static ItemEntry of(Item item) {
        // Ignore items that require experiments
        if (FeatureFlags.isExperimental(item.requiredFeatures())) {
            return UNKNOWN;
        }

        Identifier bedrockIdentifier = Renamers.ITEMS.forType(item).apply(BuiltInRegistries.ITEM.getKey(item));
        Block block = Block.byItem(item);
        boolean isBlock = block != Blocks.AIR;
        int bedrockData = 0;
        int firstStateId = -1;
        int lastStateId = -1;
        boolean entityPlacer = false;

        if (bedrockIdentifier.equals(BANNER) && item instanceof BannerItem banner) {
            // The inverted dye colour: white is 15, black is 0
            bedrockData = 15 - banner.getColor().getId();
        } else if (bedrockIdentifier.equals(BED) && item instanceof BlockItem blockItem && blockItem.getBlock() instanceof BedBlock bed) {
            // The dye colour: white is 0, black is 15
            bedrockData = bed.getColor().getId();
        }

        if (isBlock) {
            for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
                if (state.getBlock() == block) {
                    int stateId = Block.getId(state);
                    if (firstStateId == -1) {
                        firstStateId = stateId;
                    }
                    lastStateId = stateId;
                } else if (lastStateId != -1) {
                    break;
                }
            }
        }

        if (item instanceof SpawnEggItem || item instanceof MinecartItem || item instanceof FireworkRocketItem || item instanceof BoatItem) {
            entityPlacer = true;
        }

        return new ItemEntry(bedrockIdentifier, bedrockData, isBlock, firstStateId, lastStateId, entityPlacer);
    }
}
