package org.geysermc.mappings.definitions.block;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mappings.util.MappingsCodecs;
import org.geysermc.mappings.util.MappingsUtil;
import org.geysermc.paletter.BlockExplanation;
import org.geysermc.paletter.Lookup;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class BlockMappings {
    public static final Codec<Map<BlockState, BlockEntry>> CODEC = BlockEntry.CODEC.listOf().fieldOf("bedrock_mappings")
            .xmap(BlockMappings::listToMappings, BlockMappings::mappingsToList)
            .codec();
    public static final Codec<Map<BlockState, BlockEntry>> DEBUG_CODEC = Codec.unboundedMap(MappingsCodecs.BLOCK_STATE_STRING_CODEC, BlockEntry.CODEC);
    /// The reduced form of the mappings, see {@link BlockMappingsTable}
    public static final Codec<Map<BlockState, BlockEntry>> TABLE_CODEC = Codec.STRING.xmap(BlockMappingsTable::decode, BlockMappingsTable::encode);
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<BlockState, BlockEntry> mappings = new Object2ObjectLinkedOpenHashMap<>();

    public Map<BlockState, BlockEntry> mappings() {
        return mappings;
    }

    public void map(BlockState state, BlockEntry entry) {
        mappings.put(state, entry);
    }

    /// Maps every Java block state, which must be mapped to a block state that {@link MappingsUtil#bedrockPalette()} accepts in the newest Bedrock data bundled with Paletter.
    public void mapAllStates(Function<BlockState, BlockEntry> mapper) {
        boolean success = true;
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            BlockEntry mapped = mapper.apply(state);
            NbtMap bedrockState = bedrockState(mapped, state.getBlock());
            if (MappingsUtil.bedrockPalette().blockState(bedrockState, Lookup.EXACT) != null) {
                map(state, mapped);
            } else {
                success = false;
                LOGGER.error("Unknown bedrock block state for Java state {}: {}", MappingsUtil.blockStateToString(state), MappingsUtil.bedrockPalette().explainUnknown(bedrockState, Lookup.EXACT));
                if (!mapped.state().isEmpty()) {
                    LOGGER.error("Bedrock block state NBT: {}", mapped.state());
                }
                NbtMap closest = closestBedrockState(bedrockState);
                if (closest != null) {
                    LOGGER.error("Closest Bedrock block state: {}", closest.getCompound("states").entrySet().stream()
                            .map(value -> value.getKey() + "=" + value.getValue())
                            .collect(Collectors.joining(", ", closest.getString("name") + " {", "}")));
                }
            }
        }

        if (!success) {
            throw new IllegalStateException("Errors occurred whilst mapping block states, block mappings are incomplete!");
        }
    }

    /// @return the state Paletter would send instead of a state of a known Bedrock block that the palette does not accept, which shows the state keys
    /// and values that are missing or wrong; null if the palette has no such block
    private static @Nullable NbtMap closestBedrockState(NbtMap bedrockState) {
        BlockExplanation explanation = MappingsUtil.bedrockPalette().explain(bedrockState);
        return explanation.kind() == BlockExplanation.Kind.ADAPTED ? explanation.sent() : null;
    }

    private static NbtMap bedrockState(BlockEntry entry, Block javaBlock) {
        NbtMap states = NbtMap.fromMap((Map) NbtOps.INSTANCE.convertTo(JavaOps.INSTANCE, entry.state()));
        return NbtMap.builder().putString("name", entry.getName(javaBlock).toString()).putCompound("states", states).build();
    }

    private static Map<BlockState, BlockEntry> listToMappings(List<BlockEntry> entries) {
        return IntStream.range(0, entries.size())
                .mapToObj(i -> Pair.of(Block.BLOCK_STATE_REGISTRY.byIdOrThrow(i), entries.get(i)))
                .collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond));
    }

    private static List<BlockEntry> mappingsToList(Map<BlockState, BlockEntry> mappings) {
        return mappings.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> Block.BLOCK_STATE_REGISTRY.getId(entry.getKey())))
                .map(Map.Entry::getValue)
                .toList();
    }
}
