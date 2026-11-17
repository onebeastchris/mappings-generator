package org.geysermc.mappings.definitions.block;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.geysermc.mappings.util.MappingsUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// The reduced form of the block mappings, `blocks.json` (format 1, specified in the mappings repository's `FORMAT.md`): per Java block, how its
/// Bedrock name and each Bedrock state value follow from the Java properties they depend on, instead of one Bedrock state per Java state.
///
/// A value is a constant, or a table `{"by": [properties], "values": {"java values joined by a comma": value}}` over the smallest set of
/// properties it depends on. Tables and blocks that are used more than once are written once under `tables` and referred to as `$n`.
final class BlockMappingsTable {
    private static final int FORMAT = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private BlockMappingsTable() {}

    /// @return the text of `blocks.json`, one block per line so that changes to a block are one line in a diff
    static String encode(Map<BlockState, BlockEntry> mappings) {
        Map<Block, JsonObject> blocks = new LinkedHashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            JsonObject json = reduce(block, mappings);
            if (!json.isEmpty()) {
                blocks.put(block, json);
            }
        }

        Map<String, Integer> tableUses = new LinkedHashMap<>();
        blocks.values().forEach(block -> mapValues(block, value -> {
            if (value.isJsonObject()) {
                tableUses.merge(GSON.toJson(value), 1, Integer::sum);
            }
            return value;
        }));
        Map<String, String> ids = new LinkedHashMap<>();
        tableUses.forEach((table, uses) -> {
            if (uses > 1) {
                ids.put(table, "$" + (ids.size() + 1));
            }
        });
        // Blocks that map the same way (all saplings, all signs, ...) share an entry as well
        Map<String, Integer> blockUses = new LinkedHashMap<>();
        blocks.values().forEach(block -> {
            mapValues(block, value -> value.isJsonObject() && ids.containsKey(GSON.toJson(value)) ? new JsonPrimitive(ids.get(GSON.toJson(value))) : value);
            blockUses.merge(GSON.toJson(block), 1, Integer::sum);
        });
        blockUses.forEach((block, uses) -> {
            if (uses > 1 && block.length() > 12) {
                ids.put(block, "$" + (ids.size() + 1));
            }
        });

        String tables = ids.entrySet().stream().map(id -> "\n    \"" + id.getValue() + "\": " + id.getKey()).collect(Collectors.joining(","));
        String entries = blocks.entrySet().stream().map(entry -> {
            String json = GSON.toJson(entry.getValue());
            return "\n    \"" + BuiltInRegistries.BLOCK.getKey(entry.getKey()).getPath() + "\": " + (ids.containsKey(json) ? "\"" + ids.get(json) + "\"" : json);
        }).collect(Collectors.joining(","));
        return "{\n  \"format\": " + FORMAT + ",\n  \"tables\": {" + tables + "\n  },\n  \"blocks\": {" + entries + "\n  }\n}\n";
    }

    /// @return the reduced mapping of a Java block, empty if the Bedrock block has the same name and no states
    private static JsonObject reduce(Block block, Map<BlockState, BlockEntry> mappings) {
        List<String> properties = block.getStateDefinition().getProperties().stream().map(Property::getName).toList();
        List<BlockState> states = block.getStateDefinition().getPossibleStates();
        String javaName = BuiltInRegistries.BLOCK.getKey(block).getPath();

        JsonObject json = new JsonObject();
        JsonElement name = reduce(properties, states, state -> new JsonPrimitive(mappings.get(state).bedrockIdentifier().orElse(javaName)));
        if (!name.equals(new JsonPrimitive(javaName))) {
            json.add("name", name);
        }

        // Every Bedrock state key the block uses, in the order of the first Java state that has it
        Set<String> keys = new LinkedHashSet<>();
        states.forEach(state -> keys.addAll(mappings.get(state).state().keySet()));
        JsonObject bedrockStates = new JsonObject();
        keys.forEach(key -> bedrockStates.add(key, reduce(properties, states, state -> {
            Tag value = mappings.get(state).state().get(key);
            if (value == null) {
                throw new IllegalStateException("The Bedrock block of " + javaName + " has no state " + key + " for " + MappingsUtil.blockStateToString(state));
            }
            return toJson(value);
        })));
        if (!bedrockStates.isEmpty()) {
            json.add("states", bedrockStates);
        }
        return json;
    }

    /// @return a value over the smallest set of properties it depends on (the first such set in property order, among those of that size)
    private static JsonElement reduce(List<String> properties, List<BlockState> states, Function<BlockState, JsonElement> value) {
        for (int size = 0; size <= properties.size(); size++) {
            for (List<String> subset : subsets(properties, size)) {
                Map<String, JsonElement> values = new LinkedHashMap<>();
                boolean function = true;
                for (BlockState state : states) {
                    JsonElement previous = values.putIfAbsent(key(state, subset), value.apply(state));
                    if (previous != null && !previous.equals(value.apply(state))) {
                        function = false;
                        break;
                    }
                }
                if (function) {
                    return subset.isEmpty() ? values.get("") : table(subset, values);
                }
            }
        }
        throw new IllegalStateException("A value is always a function of all properties");
    }

    private static List<List<String>> subsets(List<String> properties, int size) {
        if (size == 0) {
            return List.of(List.of());
        }
        List<List<String>> subsets = new ArrayList<>();
        for (int i = 0; i + size <= properties.size(); i++) {
            for (List<String> rest : subsets(properties.subList(i + 1, properties.size()), size - 1)) {
                subsets.add(Stream.concat(Stream.of(properties.get(i)), rest.stream()).toList());
            }
        }
        return subsets;
    }

    private static JsonObject table(List<String> properties, Map<String, JsonElement> values) {
        JsonArray by = new JsonArray();
        properties.forEach(by::add);
        JsonObject tableValues = new JsonObject();
        values.forEach(tableValues::add);
        JsonObject table = new JsonObject();
        table.add("by", by);
        table.add("values", tableValues);
        return table;
    }

    /// Applies the mapper to the name and every state value of a block
    private static void mapValues(JsonObject block, UnaryOperator<JsonElement> mapper) {
        if (block.has("name")) {
            block.add("name", mapper.apply(block.get("name")));
        }
        if (block.has("states")) {
            block.getAsJsonObject("states").asMap().replaceAll((_, value) -> mapper.apply(value));
        }
    }

    /// @return the Java values of the properties of a state, joined by a comma
    private static String key(BlockState state, List<String> properties) {
        return properties.stream().map(name -> valueName(state, state.getBlock().getStateDefinition().getProperty(name))).collect(Collectors.joining(","));
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /// Bedrock state values are bytes (written as booleans), ints and strings
    private static JsonElement toJson(Tag value) {
        return switch (value) {
            case ByteTag byteTag when byteTag.byteValue() == 0 || byteTag.byteValue() == 1 -> new JsonPrimitive(byteTag.byteValue() == 1);
            case IntTag intTag -> new JsonPrimitive(intTag.intValue());
            case StringTag stringTag -> new JsonPrimitive(stringTag.value());
            default -> throw new IllegalArgumentException("Unsupported Bedrock state value " + value);
        };
    }

    private static Tag toTag(JsonPrimitive value) {
        if (value.isBoolean()) {
            return ByteTag.valueOf(value.getAsBoolean());
        }
        return value.isNumber() ? IntTag.valueOf(value.getAsInt()) : StringTag.valueOf(value.getAsString());
    }

    static Map<BlockState, BlockEntry> decode(String text) {
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        if (root.get("format").getAsInt() != FORMAT) {
            throw new IllegalArgumentException("Unsupported blocks.json format " + root.get("format") + ", expected " + FORMAT);
        }
        JsonObject tables = root.getAsJsonObject("tables");
        JsonObject blocks = root.getAsJsonObject("blocks");

        Map<BlockState, BlockEntry> mappings = new Object2ObjectLinkedOpenHashMap<>();
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            String javaName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            JsonObject block = blocks.has(javaName) ? resolve(blocks.get(javaName), tables).getAsJsonObject() : new JsonObject();
            String name = block.has("name") ? value(block.get("name"), tables, state).getAsString() : javaName;
            CompoundTag bedrockState = new CompoundTag();
            if (block.has("states")) {
                block.getAsJsonObject("states").asMap().forEach((key, value) -> bedrockState.put(key, toTag(value(value, tables, state))));
            }
            mappings.put(state, BlockEntry.of(state.getBlock(), name, bedrockState));
        }
        return mappings;
    }

    /// @return the element a `$n` reference stands for, or the element itself
    private static JsonElement resolve(JsonElement json, JsonObject tables) {
        return json.isJsonPrimitive() && json.getAsString().startsWith("$") ? tables.get(json.getAsString()) : json;
    }

    /// @return the value of a constant or table (possibly shared) for a Java block state
    private static JsonPrimitive value(JsonElement json, JsonObject tables, BlockState state) {
        JsonElement value = resolve(json, tables);
        if (value.isJsonObject()) {
            JsonObject table = value.getAsJsonObject();
            List<String> by = table.getAsJsonArray("by").asList().stream().map(JsonElement::getAsString).toList();
            value = table.getAsJsonObject("values").get(key(state, by));
        }
        return value.getAsJsonPrimitive();
    }
}
