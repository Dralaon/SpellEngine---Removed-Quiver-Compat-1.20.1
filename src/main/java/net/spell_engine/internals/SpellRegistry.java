package net.spell_engine.internals;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.api.spell.SpellPool;
import net.spell_engine.utils.WeaponCompatibility;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellSchools;

import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class SpellRegistry {
    public static class SpellEntry { public SpellEntry() { }
        public Spell spell;
        public int rawId;
        public SpellEntry(Spell spell, int rawId) {
            this.spell = spell;
            this.rawId = rawId;
        }
    }
    private static final Map<Identifier, SpellEntry> spells = new HashMap<>();
    private static final Map<Identifier, SpellPool> pools = new HashMap<>();
    public static final Map<Identifier, SpellContainer> book_containers = new HashMap<>();
    public static final Map<Identifier, SpellContainer> containers = new HashMap<>();
    private static final Map<SpellSchool, Integer> spellCount = new HashMap<>();

    public static Map<Identifier, SpellEntry> all() {
        return spells;
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(SpellRegistry::load);
    }

    private static void load(MinecraftServer minecraftServer) {
        loadSpells(minecraftServer.getResourceManager());
        loadPools(minecraftServer.getResourceManager());
        loadContainers(minecraftServer.getResourceManager());
        WeaponCompatibility.initialize();
        encodeContent();
    }

    public static void loadSpells(ResourceManager resourceManager) {
        var gson = new Gson();
        Map<Identifier, SpellEntry> parsed = new HashMap<>();
        // Reading all attribute files
        int rawId = 1;
        var directory = "spells";
        for (var entry : resourceManager.findResources(directory, fileName -> fileName.getPath().endsWith(".json")).entrySet()) {
            var identifier = entry.getKey();
            var resource = entry.getValue();
            try {
                // System.out.println("Checking resource: " + identifier);
                JsonReader reader = new JsonReader(new InputStreamReader(resource.getInputStream()));
                Spell container = gson.fromJson(reader, Spell.class);
                var id = identifier
                        .toString().replace(directory + "/", "");
                id = id.substring(0, id.lastIndexOf('.'));
                Validator.validate(container);
                parsed.put(new Identifier(id), new SpellEntry(container, rawId++));
                // System.out.println("loaded spell - id: " + id +  " spell: " + gson.toJson(container));
            } catch (Exception e) {
                System.err.println("Spell Engine: Failed to parse spell: " + identifier + " | Reason: " + e.getMessage());
            }
        }
        spells.clear();
        spells.putAll(parsed);
        spellsUpdated();
    }

    public static void loadPools(ResourceManager resourceManager) {
        var gson = new Gson();
        Map<Identifier, SpellPool.DataFormat> parsed = new HashMap<>();
        // Reading all attribute files
        var directory = "spell_pools";
        for (var entry : resourceManager.findResources(directory, fileName -> fileName.getPath().endsWith(".json")).entrySet()) {
            var identifier = entry.getKey();
            var resource = entry.getValue();
            try {
                // System.out.println("Checking resource: " + identifier);
                JsonReader reader = new JsonReader(new InputStreamReader(resource.getInputStream()));
                SpellPool.DataFormat pool = gson.fromJson(reader, SpellPool.DataFormat.class);
                var id = identifier
                        .toString().replace(directory + "/", "");
                id = id.substring(0, id.lastIndexOf('.'));
                parsed.put(new Identifier(id), pool);
                // System.out.println("loaded pool - " + id +  " ids: " + pool.spell_ids);
            } catch (Exception e) {
                System.err.println("Spell Engine: Failed to parse spell pool: " + identifier + " | Reason: " + e.getMessage());
            }
        }
        Map<Identifier, Spell> spellFlat = spells.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().spell));
        pools.clear();
        for (var entry: parsed.entrySet()) {
            pools.put(entry.getKey(), SpellPool.fromData(entry.getValue(), spellFlat));
        }
    }

    public static void loadContainers(ResourceManager resourceManager) {
        var gson = new Gson();
        Map<Identifier, SpellContainer> parsed = new HashMap<>();
        // Reading all attribute files
        var directory = "spell_assignments";
        for (var entry : resourceManager.findResources(directory, fileName -> fileName.getPath().endsWith(".json")).entrySet()) {
            var identifier = entry.getKey();
            var resource = entry.getValue();
            try {
                // System.out.println("Checking resource: " + identifier);
                JsonReader reader = new JsonReader(new InputStreamReader(resource.getInputStream()));
                SpellContainer container = gson.fromJson(reader, SpellContainer.class);
                var id = identifier
                        .toString().replace(directory + "/", "");
                id = id.substring(0, id.lastIndexOf('.'));
<<<<<<< HEAD
                parsed.put(new Identifier(id), container);
                // System.out.println("loaded assignment - id: " + id +  " assignment: " + container.spell);
=======
                parsed.put(Identifier.of(id), container);
                // System.out.println("loaded assignment - id: " + id +  " assignment: " + contaisner);
>>>>>>> 6677eb2 (Improve spell projectile stability)
            } catch (Exception e) {
                System.err.println("Spell Engine: Failed to parse spell_assignment: " + identifier + " | Reason: " + e.getMessage());
            }
        }
        containers.clear();
        containers.putAll(parsed);
        containers.putAll(book_containers);
    }

    private static void spellsUpdated() {
        updateReverseMaps();
        spellCount.clear();
        for(var school: SpellSchools.all()) {
            spellCount.put(school, 0);
        }
        for(var spell: spells.entrySet()) {
            var school = spell.getValue().spell.school;
            var current = spellCount.get(school);
            spellCount.put(school, current + 1);
        }
    }

    public static int numberOfSpells(SpellSchool school) {
        return spellCount.get(school);
    }

    public static SpellContainer containerForItem(Identifier itemId) {
        if (itemId == null) {
            return null;
        }
        return containers.get(itemId);
    }

    public static Spell getSpell(Identifier spellId) {
        var entry = spells.get(spellId);
        if (entry != null) {
            return entry.spell;
        }
        return null;
    }

    public static SpellPool spellPool(Identifier id) {
        var pool = pools.get(id);
        return pool != null ? pool : SpellPool.empty;
    }

    public static PacketByteBuf encoded = PacketByteBufs.create();

    public static class SyncFormat { public SyncFormat() { }
        public Map<String, SpellEntry> spells = new HashMap<>();
        public Map<String, SpellPool.SyncFormat> pools = new HashMap<>();
        public Map<String, SpellContainer> containers = new HashMap<>();
    }

    private static void encodeContent() {
        var gson = new Gson();
        var buffer = PacketByteBufs.create();

        var sync = new SyncFormat();
        spells.forEach((key, value) -> {
            sync.spells.put(key.toString(), value);
        });
        pools.forEach((key, value) -> {
            sync.pools.put(key.toString(), value.toSync());
        });
        containers.forEach((key, value) -> {
            sync.containers.put(key.toString(), value);
        });
        var json = gson.toJson(sync);

        List<String> chunks = new ArrayList<>();
        var chunkSize = 10000;
        for (int i = 0; i < json.length(); i += chunkSize) {
            chunks.add(json.substring(i, Math.min(json.length(), i + chunkSize)));
        }
        buffer.writeInt(chunks.size());
        for (var chunk: chunks) {
            buffer.writeString(chunk);
        }

        System.out.println("Encoded SpellRegistry size (with package overhead): " + buffer.readableBytes()
                + " bytes (in " + chunks.size() + " string chunks with the size of "  + chunkSize + ")");

        encoded = buffer;
    }

    public static void decodeContent(PacketByteBuf buffer) {
        var chunkCount = buffer.readInt();
        String json = "";
        for (int i = 0; i < chunkCount; ++i) {
            json = json.concat(buffer.readString());
        }
        var gson = new Gson();
        SyncFormat sync = gson.fromJson(json, SyncFormat.class);
        spells.clear();
        sync.spells.forEach((key, value) -> {
            spells.put(new Identifier(key), value);
        });
        sync.pools.forEach((key, value) -> {
            pools.put(new Identifier(key), SpellPool.fromSync(value));
        });
        sync.containers.forEach((key, value) -> {
            containers.put(new Identifier(key), value);
        });
        spellsUpdated();
    }

    private record ReverseEntry(Identifier identifier, Spell spell) { }
    private static final Map<Integer, ReverseEntry> reverseSpells = new HashMap<>();

    private static void updateReverseMaps() {
        reverseSpells.clear();
        for (var entry: spells.entrySet()) {
            var id = entry.getKey();
            var spell = entry.getValue().spell;
            var rawId = entry.getValue().rawId;
            reverseSpells.put(rawId, new ReverseEntry(id, spell));
        }
    }

    public static int rawSpellId(Identifier identifier) {
        return spells.get(identifier).rawId;
    }

    public static Optional<Identifier> fromRawSpellId(int rawId) {
        var reverseEntry = reverseSpells.get(rawId);
        if (reverseEntry != null) {
            return Optional.of(reverseEntry.identifier);
        }
        return Optional.empty();
    }
}
