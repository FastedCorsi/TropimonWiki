package fr.tropimon.wiki;

import com.google.gson.*;
import java.util.*;

/** Probability calculations for the supported, unconditional Cobblemon loot tables. */
final class WikiBaronRules {
  record Range(int min, int max) {
    @Override
    public String toString() {
      return min == max ? "" + min : min + "–" + max;
    }
  }

  record Drop(String item, Range count, double probability) {}

  record Pool(Range rolls, List<Drop> drops) {}

  static int tier(int level) {
    return level >= 66 ? 4 : level >= 51 ? 3 : level >= 31 ? 2 : 1;
  }

  static Range range(JsonElement value) {
    if (value.isJsonPrimitive()) {
      int n = value.getAsInt();
      if (value.getAsDouble() != n || n < 0 || n > 1000) throw new IllegalArgumentException();
      return new Range(n, n);
    }
    var object = value.getAsJsonObject();
    if (object.has("type") && !object.get("type").getAsString().equals("minecraft:uniform"))
      throw new IllegalArgumentException();
    var low = range(object.get("min"));
    var high = range(object.get("max"));
    if (low.min() > high.max()) throw new IllegalArgumentException();
    return new Range(low.min(), high.max());
  }

  static List<Pool> pools(JsonObject table) {
    var result = new ArrayList<Pool>();
    if (table.has("functions")) throw new IllegalArgumentException();
    for (var element : table.getAsJsonArray("pools")) {
      var pool = element.getAsJsonObject();
      if (pool.has("conditions")
          || pool.has("functions")
          || pool.has("bonus_rolls") && pool.get("bonus_rolls").getAsDouble() != 0)
        throw new IllegalArgumentException();
      var rolls = range(pool.get("rolls"));
      List<Drop> drops = new ArrayList<>();
      double total = 0;
      for (var entry : pool.getAsJsonArray("entries")) {
        var object = entry.getAsJsonObject();
        String type = object.get("type").getAsString();
        if (!Set.of("minecraft:item", "minecraft:empty").contains(type)
            || object.has("conditions")
            || object.has("quality")) throw new IllegalArgumentException();
        double weight = object.has("weight") ? object.get("weight").getAsDouble() : 1;
        if (!Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException();
        var count = new Range(1, 1);
        if (object.has("functions"))
          for (var f : object.getAsJsonArray("functions")) {
            var function = f.getAsJsonObject();
            if (!function
                    .get("function")
                    .getAsString()
                    .replace("minecraft:", "")
                    .equals("set_count")
                || function.has("conditions")
                || function.has("add")) throw new IllegalArgumentException();
            count = range(function.get("count"));
          }
        drops.add(
            new Drop(
                type.equals("minecraft:empty") ? "" : object.get("name").getAsString(),
                count,
                weight));
        total += weight;
      }
      if (total <= 0) throw new IllegalArgumentException();
      double denominator = total;
      result.add(
          new Pool(
              rolls,
              drops.stream()
                  .map(d -> new Drop(d.item(), d.count(), d.probability() / denominator))
                  .toList()));
    }
    return result;
  }

  /** At least one selection in a uniformly distributed, inclusive number of independent rolls. */
  static double atLeastOnce(double probability, Range rolls) {
    double sum = 0;
    for (int n = rolls.min(); n <= rolls.max(); n++) sum += 1 - Math.pow(1 - probability, n);
    return sum / (rolls.max() - rolls.min() + 1);
  }

  /** Two weighted choices without replacement, as used by the alpha TM slots. */
  static double[] twoChoices(double[] weights) {
    double total = Arrays.stream(weights).sum();
    if (!Double.isFinite(total)
        || total <= 0
        || Arrays.stream(weights).anyMatch(w -> w < 0 || !Double.isFinite(w)))
      throw new IllegalArgumentException();
    double[] probabilities = new double[weights.length];
    for (int i = 0; i < weights.length; i++) {
      probabilities[i] = weights[i] / total;
      for (int j = 0; j < weights.length; j++)
        if (i != j && weights[j] > 0 && total > weights[j])
          probabilities[i] += weights[j] / total * weights[i] / (total - weights[j]);
      probabilities[i] = Math.clamp(probabilities[i], 0, 1);
    }
    return probabilities;
  }
}
