package fr.tropimon.wiki;

import com.cobblemon.mod.common.api.moves.*;
import com.cobblemon.mod.common.api.tms.*;
import com.cobblemon.mod.common.pokemon.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

/** Local Alpha rules; TM recipes prefer Cobblemon's synchronized registry. No network access. */
final class WikiBarons {
  record Catalog(
      Map<String, JsonObject> loot,
      Map<String, JsonObject> recipes,
      boolean knownDrops,
      boolean knownMoves) {}

  record TM(MoveTemplate move, TechnicalMachine machine, double chance, boolean local) {}

  static Catalog load() throws IOException {
    var root =
        FabricLoader.getInstance()
            .getModContainer("cobblemon")
            .orElseThrow()
            .findPath("data/cobblemon")
            .orElseThrow();
    Path callbackFile = root.resolve("callbacks/battle_fainted/pokemon_alpha_drops.molang");
    String callback = Files.isRegularFile(callbackFile) ? Files.readString(callbackFile) : "";
    boolean knownDrops;
    try {
      knownDrops =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(callback.replaceAll("\\s+", "").getBytes(StandardCharsets.UTF_8)))
              .equals("127fa132fc4236247a7d24fb15a02b0c5128649263d85a15a00113940734e261");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
    Path builderFile = root.resolve("moveset_builders/alpha.json");
    JsonElement builder = JsonNull.INSTANCE;
    if (Files.isRegularFile(builderFile)) {
      try {
        builder = JsonParser.parseString(Files.readString(builderFile));
      } catch (RuntimeException unknownFormat) {
        /* Preserve recipes when the rules change. */
      }
    }
    var expected =
        JsonParser.parseString(
            "{\"type\":\"cobblemon:default\",\"slot1\":[\"last_suitable_offensive\",\"last_offensive\",\"last_levelup\"],\"slot2\":[\"last_offensive\",\"last_levelup\"],\"slot3\":[\"tm\",\"last_levelup\"],\"slot4\":[\"tm\",\"last_levelup\"]}");
    return new Catalog(
        readDirectory(root.resolve("loot_table/alpha")),
        readDirectory(root.resolve("tms")),
        knownDrops,
        builder.equals(expected));
  }

  private static Map<String, JsonObject> readDirectory(Path root) throws IOException {
    Map<String, JsonObject> result = new TreeMap<>();
    if (!Files.isDirectory(root)) return Map.of();
    try (var paths = Files.walk(root)) {
      for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
        if (Files.size(path) > 1_048_576) continue;
        try {
          result.put(
              root.relativize(path).toString().replace('\\', '/').replaceFirst("\\.json$", ""),
              JsonParser.parseString(Files.readString(path)).getAsJsonObject());
        } catch (RuntimeException ignored) {
          /* The selected missing definition is shown as unavailable. */
        }
      }
    }
    return Map.copyOf(result);
  }

  static boolean alpha(String pokemon) {
    return Arrays.stream(pokemon.toLowerCase(Locale.ROOT).split("\\s+"))
        .anyMatch(p -> p.equals("alpha") || p.equals("alpha=true"));
  }

  static List<TM> machines(Catalog catalog, FormData form, int level) {
    // The first two alpha slots contain level-up moves, which the TM selector excludes.
    Set<MoveTemplate> levelMoves = form.getMoves().getLevelUpMovesUpTo(level);
    Set<MoveTemplate> guaranteed = new HashSet<>(levelMoves);
    guaranteed.addAll(form.getMoves().getEvolutionMoves());
    Set<FormData> visited = new HashSet<>();
    for (var previous = form.getPreEvolution();
        previous != null && visited.add(previous.getForm());
        previous = previous.getForm().getPreEvolution()) {
      guaranteed.addAll(previous.getForm().getMoves().getLevelUpMovesUpTo(level));
      guaranteed.addAll(previous.getForm().getMoves().getEvolutionMoves());
    }
    var pool =
        form.getMoves().getTmMoves().stream()
            .filter(m -> !levelMoves.contains(m))
            .distinct()
            .toList();
    Map<MoveTemplate, Double> chances = new HashMap<>();
    if (!pool.isEmpty() && catalog.knownMoves()) {
      try {
        double[] probabilities =
            WikiBaronRules.twoChoices(
                pool.stream().mapToDouble(m -> m.getSelectionWeight(form)).toArray());
        for (int i = 0; i < pool.size(); i++) chances.put(pool.get(i), probabilities[i]);
      } catch (IllegalArgumentException unknown) {
        /* Never substitute zero for an unknown chance. */
      }
    }
    var registry = TechnicalMachines.INSTANCE.getMoveToTM();
    boolean local = registry.isEmpty();
    Map<MoveTemplate, TechnicalMachine> machines = new HashMap<>(registry);
    if (local)
      for (var entry : catalog.recipes().entrySet()) {
        try (var reader = new BufferedReader(new StringReader(entry.getValue().toString()))) {
          var machine =
              TechnicalMachines.INSTANCE.parse(reader, Identifier.of("cobblemon", entry.getKey()));
          var move = machine.getMoveName();
          if (move != null) machines.put(move, machine);
        } catch (IOException | RuntimeException ignored) {
          /* Missing recipes remain unavailable. */
        }
      }
    Set<MoveTemplate> candidates = new HashSet<>(guaranteed);
    candidates.addAll(pool);
    return candidates.stream()
        .filter(machines::containsKey)
        .map(
            m ->
                new TM(
                    m,
                    machines.get(m),
                    guaranteed.contains(m) ? 1 : chances.getOrDefault(m, Double.NaN),
                    local))
        .sorted(
            Comparator.comparing((TM tm) -> !Double.isFinite(tm.chance()) || tm.chance() < 1)
                .thenComparing(tm -> tm.move().getDisplayName().getString()))
        .toList();
  }
}
