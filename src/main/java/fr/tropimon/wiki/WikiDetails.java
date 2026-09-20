package fr.tropimon.wiki;

import com.google.gson.*;
import java.util.*;
import java.util.function.BiFunction;

/** Presentation rules independent of Minecraft, also used by the regression tests. */
final class WikiDetails {
  record Ability(String id, String name, String description, boolean hidden) {}

  static List<Ability> abilities(List<Ability> entries) {
    Map<String, Ability> unique = new LinkedHashMap<>();
    for (var entry : entries) {
      var existing = unique.get(entry.id());
      if (existing == null || (existing.hidden() && !entry.hidden())) unique.put(entry.id(), entry);
    }
    // A duplicate hidden slot is not a second talent. A sole talent is displayed as normal.
    if (unique.size() == 1) {
      var a = unique.values().iterator().next();
      return List.of(new Ability(a.id(), a.name(), a.description(), false));
    }
    return unique.values().stream().sorted(Comparator.comparing(Ability::hidden)).toList();
  }

  static int generation(int number) {
    int[] last = {151, 251, 386, 493, 649, 721, 809, 905, 1025};
    if (number <= 0) return 0;
    for (int i = 0; i < last.length; i++) if (number <= last[i]) return i + 1;
    return 0;
  }

  static String value(JsonObject object, String key, String fallback) {
    JsonElement value = object.get(key);
    return value == null || value.isJsonNull()
        ? fallback
        : value.isJsonPrimitive() ? value.getAsString() : value.toString();
  }

  static String number(double value) {
    return value == Math.rint(value)
        ? Long.toString((long) value)
        : String.format(Locale.FRANCE, "%.1f", value);
  }

  static List<String> evolution(JsonObject evolution, BiFunction<String, String, String> name) {
    List<String> lines = new ArrayList<>();
    String method = value(evolution, "variant", "unknown");
    lines.add(
        switch (method) {
          case "level_up" -> name.apply("ui", "evolution.level_up");
          case "item_interact" ->
              name.apply("ui", "evolution.use") + item(evolution.get("requiredContext"), name);
          case "trade" ->
              name.apply("ui", "evolution.trade")
                  + (evolution.has("requiredContext")
                          && !evolution.get("requiredContext").isJsonNull()
                          && !value(evolution, "requiredContext", "").isBlank()
                      ? name.apply("ui", "evolution.trade_for")
                          + name.apply("properties", value(evolution, "requiredContext", ""))
                      : "");
          default -> name.apply("ui", "evolution.special") + method;
        });
    var requirements = evolution.getAsJsonArray("requirements");
    if (requirements != null)
      for (var entry : requirements) {
        if (entry.isJsonObject()) lines.addAll(requirement(entry.getAsJsonObject(), name));
      }
    if (Boolean.parseBoolean(value(evolution, "consumeHeldItem", "false")))
      lines.add(name.apply("ui", "evolution.consume"));
    return lines;
  }

  static List<String> requirement(JsonObject r, BiFunction<String, String, String> name) {
    String variant = value(r, "variant", "inconnue");
    String line =
        switch (variant) {
          case "level" -> {
            String min = value(r, "minLevel", "1"), max = value(r, "maxLevel", "2147483647");
            yield max.equals("2147483647")
                ? name.apply("ui", "level") + min + name.apply("ui", "evolution.minimum")
                : name.apply("ui", "evolution.level_range")
                    + min
                    + name.apply("ui", "evolution.and")
                    + max;
          }
          case "friendship" ->
              name.apply("ui", "evolution.friendship")
                  + value(r, "amount", "?")
                  + name.apply("ui", "evolution.minimum");
          case "held_item" ->
              name.apply("ui", "evolution.hold") + item(r.get("itemCondition"), name);
          case "has_move" ->
              name.apply("ui", "evolution.know") + name.apply("move", value(r, "move", "?"));
          case "has_move_type" ->
              name.apply("ui", "evolution.know_type") + name.apply("type", value(r, "type", "?"));
          case "time_range" ->
              name.apply("ui", "evolution.time")
                  + switch (value(r, "range", "?")) {
                    case "day" -> name.apply("ui", "time.day");
                    case "night" -> name.apply("ui", "time.night");
                    case "dusk" -> name.apply("ui", "time.dusk");
                    case "dawn" -> name.apply("ui", "time.dawn");
                    default -> value(r, "range", "?");
                  };
          case "stat_compare" ->
              name.apply("stat", value(r, "highStat", "?"))
                  + name.apply("ui", "evolution.higher")
                  + name.apply("stat", value(r, "lowStat", "?"));
          case "stat_equal" ->
              name.apply("stat", value(r, "statOne", "?"))
                  + name.apply("ui", "evolution.equal")
                  + name.apply("stat", value(r, "statTwo", "?"));
          case "properties" ->
              name.apply("ui", "evolution.properties")
                  + name.apply("properties", value(r, "target", "?"));
          case "party_member" ->
              (value(r, "contains", "true").equals("false")
                      ? name.apply("ui", "evolution.party_not")
                      : name.apply("ui", "evolution.party"))
                  + name.apply("properties", value(r, "target", "?"));
          case "moon_phase" ->
              name.apply("ui", "evolution.moon")
                  + (value(r, "moonPhase", "?").equals("FULL_MOON")
                      ? name.apply("ui", "evolution.fullmoon")
                      : value(r, "moonPhase", "?"));
          case "property_range" ->
              name.apply("ui", "evolution.progress")
                  + value(r, "feature", "?")
                  + " : "
                  + value(r, "range", "?");
          case "use_move" ->
              name.apply("ui", "evolution.use_move")
                  + name.apply("move", value(r, "move", "?"))
                  + " : "
                  + value(r, "amount", "?")
                  + name.apply("ui", "evolution.times");
          case "battle_critical_hits" ->
              name.apply("ui", "evolution.critical")
                  + value(r, "amount", "?")
                  + name.apply("ui", "evolution.critical_end");
          case "blocks_traveled" ->
              name.apply("ui", "evolution.walk")
                  + value(r, "amount", "?")
                  + name.apply("ui", "evolution.walk_end");
          case "damage_taken" ->
              name.apply("ui", "evolution.damage")
                  + value(r, "amount", "?")
                  + name.apply("ui", "evolution.damage_end");
          case "recoil" ->
              name.apply("ui", "evolution.recoil")
                  + value(r, "amount", "?")
                  + name.apply("ui", "evolution.recoil_end");
          case "defeat" ->
              name.apply("ui", "evolution.defeat")
                  + value(r, "amount", "?")
                  + " × "
                  + name.apply("properties", value(r, "target", "?"));
          case "advancement" ->
              name.apply("ui", "evolution.advancement") + value(r, "requiredAdvancement", "?");
          default -> null;
        };
    if (line != null) return List.of(line);
    if (variant.equals("biome")) {
      List<String> lines = new ArrayList<>();
      if (r.has("biomeCondition"))
        lines.add(name.apply("ui", "evolution.biome") + value(r, "biomeCondition", "?"));
      if (r.has("biomeAnticondition"))
        lines.add(name.apply("ui", "evolution.not_biome") + value(r, "biomeAnticondition", "?"));
      if (!lines.isEmpty()) return lines;
    }
    if (variant.equals("weather")) {
      List<String> weather = new ArrayList<>();
      if (r.has("isRaining"))
        weather.add(
            r.get("isRaining").getAsBoolean()
                ? name.apply("ui", "evolution.rain")
                : name.apply("ui", "evolution.no_rain"));
      if (r.has("isThundering"))
        weather.add(
            r.get("isThundering").getAsBoolean()
                ? name.apply("ui", "evolution.thunder")
                : name.apply("ui", "evolution.no_thunder"));
      if (!weather.isEmpty()) return weather;
    }
    if (variant.equals("structure")) {
      List<String> structures = new ArrayList<>();
      if (r.has("structureCondition"))
        structures.add(
            name.apply("ui", "evolution.structure") + value(r, "structureCondition", "?"));
      if (r.has("structureAnticondition"))
        structures.add(
            name.apply("ui", "evolution.not_structure") + value(r, "structureAnticondition", "?"));
      if (!structures.isEmpty()) return structures;
    }
    // Unknown extensions remain visible; never silently drop a condition or imply it is satisfied.
    List<String> fallback = new ArrayList<>();
    fallback.add(name.apply("ui", "evolution.advanced") + variant);
    for (var e : r.entrySet())
      if (!e.getKey().equals("variant"))
        fallback.add(
            e.getKey()
                + " : "
                + (e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue()));
    return fallback;
  }

  static String item(JsonElement predicate, BiFunction<String, String, String> name) {
    if (predicate == null || predicate.isJsonNull())
      return name.apply("ui", "evolution.item_unknown");
    if (predicate.isJsonPrimitive()) return name.apply("item", predicate.getAsString());
    if (predicate.isJsonArray()) {
      List<String> alternatives = new ArrayList<>();
      predicate.getAsJsonArray().forEach(entry -> alternatives.add(item(entry, name)));
      return String.join(name.apply("ui", "evolution.or"), alternatives);
    }
    JsonObject object = predicate.getAsJsonObject();
    if (object.has("items")) {
      String label = item(object.get("items"), name);
      List<String> extra = new ArrayList<>();
      object.entrySet().stream()
          .filter(e -> !e.getKey().equals("items"))
          .forEach(e -> extra.add(e.getKey() + " : " + e.getValue()));
      return label + (extra.isEmpty() ? "" : " (" + String.join(", ", extra) + ")");
    }
    return name.apply("ui", "evolution.item_conditions") + object;
  }
}
