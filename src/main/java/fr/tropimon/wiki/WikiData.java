package fr.tropimon.wiki;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.pokemon.*;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.text.*;
import net.minecraft.util.*;

/** Reads the loaded registry first. Local evolution references never masquerade as server rules. */
final class WikiData {
  private final Map<String, String> french = new HashMap<>();
  private Map<String, Path> speciesFiles;

  record Evolutions(JsonArray entries, boolean local, boolean available) {}

  WikiData() {
    for (String namespace : List.of("minecraft", "cobblemon")) {
      var resource =
          MinecraftClient.getInstance()
              .getResourceManager()
              .getResource(Identifier.of(namespace, "lang/fr_fr.json"));
      if (resource.isEmpty()) continue;
      try (var reader = resource.get().getReader()) {
        JsonParser.parseReader(reader)
            .getAsJsonObject()
            .entrySet()
            .forEach(
                e -> {
                  if (e.getValue().isJsonPrimitive())
                    french.put(e.getKey(), e.getValue().getAsString());
                });
      } catch (IOException | RuntimeException ignored) {
        /* Loaded language remains the fallback. */
      }
    }
  }

  String tr(String key) {
    return french.getOrDefault(key, Language.getInstance().get(key));
  }

  String text(Text text) {
    if (text.getContent() instanceof TranslatableTextContent translated)
      return tr(translated.getKey());
    return text.getString();
  }

  String species(Species species) {
    return text(species.getTranslatedName());
  }

  String name(String kind, String id) {
    if (kind.equals("properties")) {
      List<String> parts = new ArrayList<>();
      for (String part : id.split(" ")) {
        String[] property = part.split("=", 2);
        if (property.length == 1)
          parts.add(
              switch (part) {
                case "alolan" -> "Alola";
                case "galarian" -> "Galar";
                case "hisuian" -> "Hisui";
                default -> name("species", part);
              });
        else
          parts.add(
              switch (property[0]) {
                case "species" -> name("species", property[1]);
                case "gender" ->
                    property[1].equals("male")
                        ? "mâle"
                        : property[1].equals("female") ? "femelle" : property[1];
                case "held_item" -> "tenant " + name("item", property[1]);
                case "nickname" -> "surnom « " + property[1] + " »";
                case "gimmighoul_coins" -> property[1] + " pièces de Mordudor";
                case "form", "wolf_form" -> "forme " + property[1];
                default -> property[0].replace('_', ' ') + " : " + property[1];
              });
      }
      return String.join(" · ", parts);
    }
    if (kind.equals("item")) {
      Identifier item = Identifier.tryParse(id);
      if (item != null && Registries.ITEM.containsId(item))
        return tr(Registries.ITEM.get(item).getTranslationKey());
      return id;
    }
    if (kind.equals("stat"))
      id =
          switch (id) {
            case "atk" -> "attack";
            case "def" -> "defence";
            case "spa" -> "special_attack";
            case "spd" -> "special_defence";
            case "spe" -> "speed";
            default -> id;
          };
    String key =
        "cobblemon."
            + kind
            + "."
            + id.replace("cobblemon:", "")
            + (kind.equals("species") || kind.equals("stat") ? ".name" : "");
    String result = tr(key);
    return result.equals(key) ? id : result;
  }

  List<WikiDetails.Ability> abilities(FormData form) {
    var entries = new ArrayList<WikiDetails.Ability>();
    for (var ability : form.getAbilities()) {
      var a = ability.getTemplate();
      entries.add(
          new WikiDetails.Ability(
              a.getName(),
              tr(a.getDisplayName()),
              tr(a.getDescription()),
              ability instanceof HiddenAbility));
    }
    return WikiDetails.abilities(entries);
  }

  Evolutions evolutions(Species species, FormData form) {
    if (!form.getEvolutions().isEmpty()) {
      JsonArray entries = new JsonArray();
      for (var evolution : form.getEvolutions()) {
        try {
          entries.add(PokemonSpecies.INSTANCE.getGson().toJsonTree(evolution, Evolution.class));
        } catch (RuntimeException failure) {
          JsonObject unknown = new JsonObject();
          unknown.addProperty("result", evolution.getResult().asString(" "));
          unknown.addProperty("variant", "conditions non lisibles");
          entries.add(unknown);
        }
      }
      return new Evolutions(entries, false, true);
    }
    return localEvolutions(species, form);
  }

  Evolutions localEvolutions(Species species, FormData form) {
    // Index paths once per opened Wiki; only the selected species JSON is read, never per frame.
    if (speciesFiles == null) {
      speciesFiles = new HashMap<>();
      FabricLoader.getInstance()
          .getModContainer("cobblemon")
          .ifPresent(
              container -> {
                for (Path root : container.getRootPaths()) {
                  Path directory = root.resolve("data/cobblemon/species");
                  if (!Files.isDirectory(directory)) continue;
                  try (var paths = Files.walk(directory)) {
                    paths
                        .filter(p -> p.toString().endsWith(".json"))
                        .forEach(
                            p ->
                                speciesFiles.put(
                                    p.getFileName().toString().replace(".json", ""), p));
                  } catch (IOException ignored) {
                    /* Unavailable is shown explicitly. */
                  }
                }
              });
    }
    if (!species.getResourceIdentifier().getNamespace().equals("cobblemon"))
      return new Evolutions(new JsonArray(), true, false);
    Path path = speciesFiles.get(species.getResourceIdentifier().getPath());
    if (path == null) return new Evolutions(new JsonArray(), true, false);
    try (var reader = Files.newBufferedReader(path)) {
      JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
      if (form != species.getStandardForm()) {
        JsonObject matching = null;
        if (json.has("forms"))
          for (var candidate : json.getAsJsonArray("forms")) {
            var object = candidate.getAsJsonObject();
            if (WikiDetails.value(object, "name", "").equalsIgnoreCase(form.getName()))
              matching = object;
          }
        if (matching == null) return new Evolutions(new JsonArray(), true, false);
        json = matching;
      }
      return new Evolutions(
          json.has("evolutions") ? json.getAsJsonArray("evolutions") : new JsonArray(), true, true);
    } catch (IOException | RuntimeException failure) {
      return new Evolutions(new JsonArray(), true, false);
    }
  }
}
