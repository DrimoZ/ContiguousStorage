package dev.drimoz.contiguousstorage.block;

import com.buuz135.functionalstorage.util.IWoodType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.drimoz.contiguousstorage.CSConfig;
import dev.drimoz.contiguousstorage.ContiguousStorage;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Everything the trims need besides code, generated in memory from the registered trims - one pack
 * for the client (blockstates, models, names) and one for the server (recipes, loot tables, the axe
 * tag). Nothing is written per wood, and a Functional Storage addon's wood gets its files like the
 * vanilla ones. No texture is drawn either: a wood trim is that wood's log as a frame around its
 * planks, read from Functional Storage's own wood type.
 *
 * <p>The content is built each time the pack is opened, so the recipes follow
 * {@link CSConfig#TRIMS_ENABLED} on {@code /reload}.
 */
public final class TrimPack implements PackResources {

    private static final String ID = ContiguousStorage.MOD_ID + "_trims";

    private final PackLocationInfo location;
    private final PackType type;
    private final Map<ResourceLocation, byte[]> files;

    private TrimPack(PackLocationInfo location, PackType type, Map<ResourceLocation, byte[]> files) {
        this.location = location;
        this.type = type;
        this.files = files;
    }

    /** Adds the pack, always on and on top, to both the client resources and the server data. */
    public static void addPacks(AddPackFindersEvent event) {
        PackType type = event.getPackType();
        PackLocationInfo info = new PackLocationInfo(ID, Component.literal("Contiguous Storage trims"), PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new TrimPack(location, type, contents(type, CSConfig.TRIMS_ENABLED.get()));
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return openPrimary(location);
            }
        };
        Pack pack = Pack.readMetaAndCreate(info, resources, type, new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    // --- Contents --------------------------------------------------------------------------------

    /** The files of the pack of {@code type}, as they would be with trims {@code enabled} or not. */
    public static Map<ResourceLocation, byte[]> contents(PackType type, boolean enabled) {
        Map<ResourceLocation, JsonElement> json = new LinkedHashMap<>();
        if (type == PackType.CLIENT_RESOURCES) {
            client(json);
        } else {
            server(json, enabled);
        }
        Map<ResourceLocation, byte[]> bytes = new LinkedHashMap<>();
        json.forEach((path, content) -> bytes.put(path, content.toString().getBytes(StandardCharsets.UTF_8)));
        return bytes;
    }

    private static void client(Map<ResourceLocation, JsonElement> out) {
        JsonObject lang = new JsonObject();
        out.put(CSBlocks.id("models/block/trim_base.json"), model(true, true));
        out.put(CSBlocks.id("models/block/trim_frame.json"), model(true, false));
        out.put(CSBlocks.id("models/block/trim_core.json"), model(false, true));
        CSBlocks.WOOD_TRIMS.forEach((trim, wood) -> {
            String name = name(trim);
            JsonObject model = new JsonObject();
            model.addProperty("parent", CSBlocks.id("block/trim_base").toString());
            JsonObject textures = new JsonObject();
            textures.addProperty("frame", texture(wood.getWood()));
            textures.addProperty("core", texture(wood.getPlanks()));
            textures.addProperty("particle", texture(wood.getPlanks()));
            model.add("textures", textures);
            out.put(CSBlocks.id("models/block/" + name + ".json"), model);
            lang.addProperty("block." + ContiguousStorage.MOD_ID + "." + name, title(wood) + " Trim");
        });
        if (CSBlocks.FRAMED_TRIM != null) {
            out.put(CSBlocks.id("models/block/framed_trim.json"), framedModel());
            lang.addProperty("block." + ContiguousStorage.MOD_ID + ".framed_trim", "Framed Trim");
        }
        for (TrimBlock trim : CSBlocks.all()) {
            String name = name(trim);
            JsonObject variant = new JsonObject();
            variant.addProperty("model", CSBlocks.id("block/" + name).toString());
            JsonObject variants = new JsonObject();
            variants.add("", variant);
            JsonObject blockstate = new JsonObject();
            blockstate.add("variants", variants);
            out.put(CSBlocks.id("blockstates/" + name + ".json"), blockstate);
            JsonObject item = new JsonObject();
            item.addProperty("parent", CSBlocks.id("block/" + name).toString());
            out.put(CSBlocks.id("models/item/" + name + ".json"), item);
        }
        out.put(CSBlocks.id("lang/en_us.json"), lang);
    }

    private static void server(Map<ResourceLocation, JsonElement> out, boolean enabled) {
        JsonArray axe = new JsonArray();
        for (TrimBlock trim : CSBlocks.all()) {
            String name = name(trim);
            String id = CSBlocks.id(name).toString();
            axe.add(id);
            out.put(CSBlocks.id("loot_table/blocks/" + name + ".json"), lootTable(id, trim == CSBlocks.FRAMED_TRIM));
        }
        JsonObject tag = new JsonObject();
        tag.add("values", axe);
        out.put(ResourceLocation.withDefaultNamespace("tags/block/mineable/axe.json"), tag);
        if (!enabled) {
            return;
        }
        CSBlocks.WOOD_TRIMS.forEach((trim, wood) -> out.put(CSBlocks.id("recipe/" + name(trim) + ".json"),
                ringRecipe(item(BuiltInRegistries.BLOCK.getKey(wood.getPlanks()).toString()), CSBlocks.id(name(trim)).toString())));
        if (CSBlocks.FRAMED_TRIM != null) {
            JsonObject nugget = new JsonObject();
            nugget.addProperty("tag", "c:nuggets/iron");
            out.put(CSBlocks.id("recipe/framed_trim.json"), ringRecipe(nugget, CSBlocks.id("framed_trim").toString()));
        }
    }

    /** Eight sticks around one ingredient: four trims. */
    private static JsonObject ringRecipe(JsonObject centre, String result) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shaped");
        recipe.addProperty("category", "building");
        JsonArray pattern = new JsonArray();
        pattern.add("SSS");
        pattern.add("SCS");
        pattern.add("SSS");
        recipe.add("pattern", pattern);
        JsonObject key = new JsonObject();
        key.add("S", item("minecraft:stick"));
        key.add("C", centre);
        recipe.add("key", key);
        JsonObject output = new JsonObject();
        output.addProperty("id", result);
        output.addProperty("count", 4);
        recipe.add("result", output);
        return recipe;
    }

    /** The block drops itself; the framed trim keeps its look. */
    private static JsonObject lootTable(String id, boolean framed) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", id);
        if (framed) {
            JsonObject copy = new JsonObject();
            copy.addProperty("function", "minecraft:copy_components");
            copy.addProperty("source", "block_entity");
            JsonArray include = new JsonArray();
            include.add("functionalstorage:style");
            copy.add("include", include);
            entry.add("functions", array(copy));
        }
        JsonObject survives = new JsonObject();
        survives.addProperty("condition", "minecraft:survives_explosion");
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.add("entries", array(entry));
        pool.add("conditions", array(survives));
        JsonObject table = new JsonObject();
        table.addProperty("type", "minecraft:block");
        table.add("pools", array(pool));
        return table;
    }

    /**
     * The framed trim: Functional Storage's framed loader, one child per part it dresses - the frame
     * takes the recipe's first block ({@code side}), the core its second ({@code front}).
     */
    private static JsonObject framedModel() {
        String placeholder = "functionalstorage:block/framed_side";
        JsonObject frame = new JsonObject();
        frame.addProperty("parent", CSBlocks.id("block/trim_frame").toString());
        frame.add("textures", textures("frame", placeholder));
        JsonObject core = new JsonObject();
        core.addProperty("parent", CSBlocks.id("block/trim_core").toString());
        core.add("textures", textures("core", placeholder));
        JsonObject children = new JsonObject();
        children.add("side", frame);
        children.add("front", core);
        JsonArray order = new JsonArray();
        order.add("side");
        order.add("front");
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.addProperty("loader", "functionalstorage:framedblock");
        model.add("children", children);
        model.add("item_render_order", order);
        model.add("textures", textures("particle", placeholder));
        return model;
    }

    /**
     * A trim's geometry: twelve 2 x 2 bars along the edges ({@code #frame}), around a core set half a
     * pixel back ({@code #core}).
     */
    private static JsonObject model(boolean frame, boolean core) {
        JsonArray elements = new JsonArray();
        if (frame) {
            for (int a : new int[]{0, 14}) {
                for (int b : new int[]{0, 14}) {
                    elements.add(element(new double[]{0, a, b}, new double[]{16, a + 2, b + 2}, "#frame"));
                    elements.add(element(new double[]{a, 2, b}, new double[]{a + 2, 14, b + 2}, "#frame"));
                    elements.add(element(new double[]{a, b, 2}, new double[]{a + 2, b + 2, 14}, "#frame"));
                }
            }
        }
        if (core) {
            elements.add(element(new double[]{0.5, 0.5, 0.5}, new double[]{15.5, 15.5, 15.5}, "#core"));
        }
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures("particle", core ? "#core" : "#frame"));
        model.add("elements", elements);
        return model;
    }

    /** One cuboid, every face drawn, culled against the neighbour where it lies on (or next to) the block's side. */
    private static JsonObject element(double[] from, double[] to, String texture) {
        JsonObject faces = new JsonObject();
        String[][] sides = {{"west", "0", "min"}, {"east", "0", "max"}, {"down", "1", "min"}, {"up", "1", "max"}, {"north", "2", "min"}, {"south", "2", "max"}};
        for (String[] side : sides) {
            int axis = Integer.parseInt(side[1]);
            boolean min = side[2].equals("min");
            double at = min ? from[axis] : to[axis];
            JsonObject face = new JsonObject();
            face.addProperty("texture", texture);
            if (min ? at <= 0.5 : at >= 15.5) {
                face.addProperty("cullface", side[0]);
            }
            faces.add(side[0], face);
        }
        JsonObject element = new JsonObject();
        element.add("from", array(from));
        element.add("to", array(to));
        element.add("faces", faces);
        return element;
    }

    private static String name(Block trim) {
        return BuiltInRegistries.BLOCK.getKey(trim).getPath();
    }

    /** {@code dark_oak} to {@code Dark Oak}. */
    private static String title(IWoodType wood) {
        return Arrays.stream(wood.getName().split("_"))
                .map(word -> word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    /** The texture a block's own model most likely uses: {@code ns:block/path}. */
    private static String texture(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id.getNamespace() + ":block/" + id.getPath();
    }

    private static JsonObject item(String id) {
        JsonObject item = new JsonObject();
        item.addProperty("item", id);
        return item;
    }

    private static JsonObject textures(String key, String value) {
        JsonObject textures = new JsonObject();
        textures.addProperty(key, value);
        return textures;
    }

    private static JsonArray array(JsonElement... elements) {
        JsonArray array = new JsonArray();
        for (JsonElement element : elements) {
            array.add(element);
        }
        return array;
    }

    private static JsonArray array(double[] values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            array.add(value);
        }
        return array;
    }

    // --- PackResources -----------------------------------------------------------------------------

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        byte[] bytes = packType == type ? files.get(location) : null;
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, ResourceOutput output) {
        if (packType != type) {
            return;
        }
        files.forEach((location, bytes) -> {
            if (location.getNamespace().equals(namespace) && location.getPath().startsWith(path + "/")) {
                output.accept(location, () -> new ByteArrayInputStream(bytes));
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType packType) {
        return packType == type ? files.keySet().stream().map(ResourceLocation::getNamespace).collect(Collectors.toSet()) : Set.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        if (!deserializer.getMetadataSectionName().equals(PackMetadataSection.TYPE.getMetadataSectionName())) {
            return null;
        }
        return (T) new PackMetadataSection(Component.literal("Contiguous Storage trims, generated"),
                SharedConstants.getCurrentVersion().getPackVersion(type), Optional.empty());
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public void close() {
    }
}
