package fr.tropimon.wiki;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.*;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

/** Installed definitions only; server datapacks are not advertised as synchronized. */
final class WikiSpawns {
  record Entry(
      String pool, String title, String pokemon, JsonObject spawn, List<JsonObject> presets) {
    boolean habitat() {
      return !pool.isEmpty();
    }

    boolean matches(Species species, FormData form) {
      String name = pokemon.split(" ", 2)[0].replace("cobblemon:", "");
      if (!name.equalsIgnoreCase(species.getResourceIdentifier().getPath())) return false;
      try {
        var renderable = PokemonProperties.Companion.parse(pokemon).asRenderablePokemon();
        return renderable != null
            && renderable.getForm().getName().equalsIgnoreCase(form.getName());
      } catch (RuntimeException failure) {
        return false;
      }
    }
  }

  record Catalog(List<Entry> entries, StructurePreview.Index structures, int skipped) {}

  static Catalog load() throws IOException {
    Map<String, JsonObject> files = new TreeMap<>();
    int skipped = 0;
    var mods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
    mods.sort(
        Comparator.comparing(
            m -> m.getMetadata().getId().equals("cobblemon") ? "" : m.getMetadata().getId()));
    for (var mod : mods)
      for (Path root : mod.getRootPaths()) {
        Path data = root.resolve("data");
        if (!Files.isDirectory(data)) continue;
        try (var namespaces = Files.list(data)) {
          for (Path namespace : namespaces.filter(Files::isDirectory).toList())
            for (String kind :
                List.of("habitat_pools", "spawn_pool_world", "spawn_detail_presets")) {
              Path dir = namespace.resolve(kind);
              if (!Files.isDirectory(dir)) continue;
              try (var paths = Files.walk(dir)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                  try {
                    if (Files.size(path) > 1_048_576) {
                      skipped++;
                      continue;
                    }
                    files.put(
                        namespace.getFileName()
                            + "/"
                            + kind
                            + "/"
                            + dir.relativize(path).toString().replace('\\', '/'),
                        JsonParser.parseString(Files.readString(path)).getAsJsonObject());
                  } catch (RuntimeException | IOException failure) {
                    skipped++;
                  }
                }
              }
            }
        }
      }
    List<Entry> entries = new ArrayList<>();
    for (var file : files.entrySet()) {
      JsonObject definition = file.getValue();
      if (!enabled(definition) || !definition.has("spawns")) continue;
      boolean habitat = file.getKey().contains("/habitat_pools/");
      for (var raw : definition.getAsJsonArray("spawns")) {
        try {
          JsonObject spawn = raw.getAsJsonObject();
          List<JsonObject> presets = new ArrayList<>();
          if (spawn.has("presets"))
            for (var preset : spawn.getAsJsonArray("presets")) {
              String id = preset.getAsString();
              String[] parts = (id.contains(":") ? id : "cobblemon:" + id).split(":", 2);
              var resolved = files.get(parts[0] + "/spawn_detail_presets/" + parts[1] + ".json");
              if (resolved != null) presets.add(resolved);
              else {
                JsonObject missing = new JsonObject();
                missing.addProperty("unresolvedPreset", id);
                presets.add(missing);
              }
            }
          String pool = habitat ? StructurePreview.poolKey(file.getKey()) : "";
          String title = habitat ? WikiDetails.value(definition, "name", pool) : "";
          if (spawn.has("herdablePokemon")) {
            for (var member : spawn.getAsJsonArray("herdablePokemon")) {
              JsonObject merged = spawn.deepCopy();
              merged.remove("herdablePokemon");
              merged.add("herdMember", member.deepCopy());
              merged.addProperty(
                  "level", WikiDetails.value(member.getAsJsonObject(), "levelRange", "?"));
              entries.add(
                  new Entry(
                      pool,
                      title,
                      WikiDetails.value(member.getAsJsonObject(), "pokemon", ""),
                      merged,
                      List.copyOf(presets)));
            }
          } else {
            String pokemon =
                WikiDetails.value(spawn, habitat ? "species" : "pokemon", "")
                    .toLowerCase(Locale.ROOT);
            if (habitat) pokemon += " " + WikiDetails.value(spawn, "modifiers", "");
            if (!pokemon.isBlank())
              entries.add(new Entry(pool, title, pokemon.strip(), spawn, List.copyOf(presets)));
          }
        } catch (RuntimeException malformed) {
          skipped++;
        }
      }
    }
    entries.sort(Comparator.comparing((Entry e) -> !e.habitat()).thenComparing(Entry::pool));
    var structures = StructurePreview.index();
    return new Catalog(List.copyOf(entries), structures, skipped + structures.skipped());
  }

  private static boolean enabled(JsonObject json) {
    if (json.has("enabled") && !json.get("enabled").getAsBoolean()) return false;
    for (String key : List.of("neededInstalledMods", "neededUninstalledMods"))
      if (json.has(key))
        for (var id : json.getAsJsonArray(key))
          if (key.equals("neededInstalledMods")
              != FabricLoader.getInstance().isModLoaded(id.getAsString())) return false;
    return true;
  }

  static List<String> describe(Entry entry, WikiData data) {
    List<String> result = new ArrayList<>();
    var spawn = entry.spawn();
    result.add(
        data.ui("level")
            + WikiDetails.value(spawn, "level", WikiDetails.value(spawn, "levelRange", "?"))
            + " · "
            + translated(data, "rarity", WikiDetails.value(spawn, "bucket", "?")));
    result.add(
        data.ui("spawn.position")
            + translated(data, "position", WikiDetails.value(spawn, "spawnablePositionType", "?")));
    if (spawn.has("phases"))
      result.add(data.ui("spawn.phases") + spawn.get("phases").getAsString());
    for (var field : spawn.entrySet()) {
      if (Set.of(
              "id",
              "type",
              "species",
              "pokemon",
              "level",
              "levelRange",
              "bucket",
              "phases",
              "spawnablePositionType",
              "presets",
              "herdMember")
          .contains(field.getKey())) continue;
      append(result, field.getKey(), field.getValue(), data, "");
    }
    for (var preset : entry.presets())
      for (var field : preset.entrySet())
        append(result, field.getKey(), field.getValue(), data, "");
    if (spawn.has("herdMember")) {
      result.add(data.ui("spawn.herd"));
      for (var field : spawn.getAsJsonObject("herdMember").entrySet())
        if (!Set.of("pokemon", "levelRange").contains(field.getKey()))
          append(result, field.getKey(), field.getValue(), data, "");
    }
    return result;
  }

  private static void append(
      List<String> result, String key, JsonElement value, WikiData data, String prefix) {
    if (value.isJsonObject()) {
      String next =
          key.equals("condition") ? prefix : prefix + translated(data, "spawnkey", key) + " · ";
      value
          .getAsJsonObject()
          .entrySet()
          .forEach(e -> append(result, e.getKey(), e.getValue(), data, next));
    } else if (value.isJsonArray()) {
      for (var item : value.getAsJsonArray()) append(result, key, item, data, prefix);
    } else result.add(prefix + translated(data, "spawnkey", key) + " : " + pretty(value, data));
  }

  private static String pretty(JsonElement value, WikiData data) {
    if (!value.isJsonPrimitive()) return value.toString();
    if (value.getAsJsonPrimitive().isBoolean()) return data.ui(value.getAsBoolean() ? "yes" : "no");
    String text = value.getAsString();
    if (text.equals("day") || text.equals("night")) return data.ui("spawn." + text);
    if (text.startsWith("#")) return translated(data, "biometag", text.substring(1));
    if (text.startsWith("minecraft:")) {
      String biome = data.tr("biome.minecraft." + text.substring(10));
      if (!biome.startsWith("biome.")) return biome;
      return data.name("item", text);
    }
    return text;
  }

  private static String translated(WikiData data, String kind, String value) {
    String key = "tropimon_wiki." + kind + "." + value;
    String translated = data.tr(key);
    return translated.equals(key) ? value.replace('_', ' ') : translated;
  }
}
