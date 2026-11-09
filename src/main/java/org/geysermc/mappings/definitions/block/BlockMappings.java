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
import org.geysermc.paletter.Paletter;
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
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<BlockState, BlockEntry> mappings = new Object2ObjectLinkedOpenHashMap<>();

    public Map<BlockState, BlockEntry> mappings() {
        return mappings;
    }

    public void map(BlockState state, BlockEntry entry) {
        mappings.put(state, entry);
    }

    /// Maps every Java block state, which must be mapped to a block state that {@link MappingsUtil#BEDROCK_LOOKUP} accepts in the Bedrock data bundled with Paletter.
    public void mapAllStates(Function<BlockState, BlockEntry> mapper) {
        boolean success = true;
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            BlockEntry mapped = mapper.apply(state);
            NbtMap bedrockState = bedrockState(mapped, state.getBlock());
            if (Paletter.bundled().block(bedrockState, MappingsUtil.BEDROCK_LOOKUP) != null) {
                map(state, mapped);
            } else {
                success = false;
                LOGGER.error("Unknown bedrock block state: {} for Java state {}, not accepted by lookup \"{}\": {}", mapped.getName(state.getBlock()), MappingsUtil.blockStateToString(state),
                        MappingsUtil.BEDROCK_LOOKUP, Paletter.bundled().explainUnknown(bedrockState));
                if (!mapped.state().isEmpty()) {
                    LOGGER.error("Bedrock block state NBT: {}", mapped.state());
                }
            }
        }

        if (!success) {
            throw new IllegalStateException("Errors occurred whilst mapping block states, block mappings are incomplete!");
        }
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
