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
          case "level_up" -> "Monter d'un niveau";
          case "item_interact" -> "Utiliser : " + item(evolution.get("requiredContext"), name);
          case "trade" ->
              "Échanger ce Pokémon"
                  + (evolution.has("requiredContext")
                          && !evolution.get("requiredContext").isJsonNull()
                          && !value(evolution, "requiredContext", "").isBlank()
                      ? " contre "
                          + name.apply("properties", value(evolution, "requiredContext", ""))
                      : "");
          default -> "Méthode spéciale : " + method;
        });
    var requirements = evolution.getAsJsonArray("requirements");
    if (requirements != null)
      for (var entry : requirements) {
        if (entry.isJsonObject()) lines.addAll(requirement(entry.getAsJsonObject(), name));
      }
    if (Boolean.parseBoolean(value(evolution, "consumeHeldItem", "false")))
      lines.add("L'objet tenu est consommé.");
    return lines;
  }

  static List<String> requirement(JsonObject r, BiFunction<String, String, String> name) {
    String variant = value(r, "variant", "inconnue");
    String line =
        switch (variant) {
          case "level" -> {
            String min = value(r, "minLevel", "1"), max = value(r, "maxLevel", "2147483647");
            yield max.equals("2147483647")
                ? "Niveau " + min + " minimum"
                : "Niveau entre " + min + " et " + max;
          }
          case "friendship" -> "Amitié : " + value(r, "amount", "?") + " minimum";
          case "held_item" -> "Tenir : " + item(r.get("itemCondition"), name);
          case "has_move" -> "Connaître : " + name.apply("move", value(r, "move", "?"));
          case "has_move_type" ->
              "Connaître une attaque de type " + name.apply("type", value(r, "type", "?"));
          case "time_range" ->
              "Moment : "
                  + switch (value(r, "range", "?")) {
                    case "day" -> "jour";
                    case "night" -> "nuit";
                    case "dusk" -> "crépuscule";
                    case "dawn" -> "aube";
                    default -> value(r, "range", "?");
                  };
          case "stat_compare" ->
              name.apply("stat", value(r, "highStat", "?"))
                  + " supérieure à "
                  + name.apply("stat", value(r, "lowStat", "?"));
          case "stat_equal" ->
              name.apply("stat", value(r, "statOne", "?"))
                  + " égale à "
                  + name.apply("stat", value(r, "statTwo", "?"));
          case "properties" ->
              "Caractéristiques requises : " + name.apply("properties", value(r, "target", "?"));
          case "party_member" ->
              (value(r, "contains", "true").equals("false")
                      ? "Ne pas avoir dans l'équipe : "
                      : "Dans l'équipe : ")
                  + name.apply("properties", value(r, "target", "?"));
          case "moon_phase" ->
              "Phase lunaire : "
                  + (value(r, "moonPhase", "?").equals("FULL_MOON")
                      ? "pleine lune"
                      : value(r, "moonPhase", "?"));
          case "property_range" ->
              "Progression " + value(r, "feature", "?") + " : " + value(r, "range", "?");
          case "use_move" ->
              "Utiliser "
                  + name.apply("move", value(r, "move", "?"))
                  + " : "
                  + value(r, "amount", "?")
                  + " fois";
          case "battle_critical_hits" ->
              "Réussir " + value(r, "amount", "?") + " coups critiques dans un combat";
          case "blocks_traveled" ->
              "Parcourir " + value(r, "amount", "?") + " blocs avec ce Pokémon";
          case "damage_taken" -> "Subir " + value(r, "amount", "?") + " points de dégâts";
          case "recoil" -> "Cumuler " + value(r, "amount", "?") + " points de dégâts de recul";
          case "defeat" ->
              "Vaincre "
                  + value(r, "amount", "?")
                  + " × "
                  + name.apply("properties", value(r, "target", "?"));
          case "advancement" -> "Accomplir le progrès : " + value(r, "requiredAdvancement", "?");
          default -> null;
        };
    if (line != null) return List.of(line);
    if (variant.equals("biome")) {
      List<String> lines = new ArrayList<>();
      if (r.has("biomeCondition")) lines.add("Biome requis : " + value(r, "biomeCondition", "?"));
      if (r.has("biomeAnticondition"))
        lines.add("Hors du biome : " + value(r, "biomeAnticondition", "?"));
      if (!lines.isEmpty()) return lines;
    }
    if (variant.equals("weather")) {
      List<String> weather = new ArrayList<>();
      if (r.has("isRaining"))
        weather.add(r.get("isRaining").getAsBoolean() ? "Sous la pluie" : "Sans pluie");
      if (r.has("isThundering"))
        weather.add(r.get("isThundering").getAsBoolean() ? "Pendant un orage" : "Sans orage");
      if (!weather.isEmpty()) return weather;
    }
    if (variant.equals("structure")) {
      List<String> structures = new ArrayList<>();
      if (r.has("structureCondition"))
        structures.add("Dans la structure : " + value(r, "structureCondition", "?"));
      if (r.has("structureAnticondition"))
        structures.add("Hors de la structure : " + value(r, "structureAnticondition", "?"));
      if (!structures.isEmpty()) return structures;
    }
    // Unknown extensions remain visible; never silently drop a condition or imply it is satisfied.
    List<String> fallback = new ArrayList<>();
    fallback.add("Condition avancée : " + variant);
    for (var e : r.entrySet())
      if (!e.getKey().equals("variant"))
        fallback.add(
            e.getKey()
                + " : "
                + (e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue()));
    return fallback;
  }

  static String item(JsonElement predicate, BiFunction<String, String, String> name) {
    if (predicate == null || predicate.isJsonNull()) return "objet non précisé";
    if (predicate.isJsonPrimitive()) return name.apply("item", predicate.getAsString());
    if (predicate.isJsonArray()) {
      List<String> alternatives = new ArrayList<>();
      predicate.getAsJsonArray().forEach(entry -> alternatives.add(item(entry, name)));
      return String.join(" ou ", alternatives);
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
    return "objet avec conditions : " + object;
  }
}
