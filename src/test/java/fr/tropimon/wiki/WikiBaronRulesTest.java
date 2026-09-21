package fr.tropimon.wiki;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class WikiBaronRulesTest {
  @Test
  void levelBoundaries() {
    for (int level = 1; level <= 100; level++)
      assertEquals(
          level <= 30 ? 1 : level <= 50 ? 2 : level <= 65 ? 3 : 4, WikiBaronRules.tier(level));
  }

  @Test
  void emptyEntriesStayInDenominatorAndUniformRollsAreInclusive() {
    var table =
        JsonParser.parseString(
                """
{"pools":[{"rolls":{"type":"minecraft:uniform","min":1,"max":2},"entries":[
{"type":"minecraft:empty","weight":30},
{"type":"minecraft:item","name":"example:candy","weight":70,"functions":[{"function":"minecraft:set_count","count":2}]}]}]}
""")
            .getAsJsonObject();
    var pool = WikiBaronRules.pools(table).getFirst();
    assertEquals(.7, pool.drops().get(1).probability(), 1e-12);
    assertEquals(.805, WikiBaronRules.atLeastOnce(.7, pool.rolls()), 1e-12);
    assertEquals(2, pool.drops().get(1).count().min());
  }

  @Test
  void conditionalTablesNeverMasqueradeAsCertainDrops() {
    var table =
        JsonParser.parseString("{\"pools\":[{\"conditions\":[],\"rolls\":1,\"entries\":[]}]}")
            .getAsJsonObject();
    assertThrows(IllegalArgumentException.class, () -> WikiBaronRules.pools(table));
  }

  @Test
  void legacyImplicitUniformCountsRemainSupported() {
    assertEquals(
        new WikiBaronRules.Range(1, 3),
        WikiBaronRules.range(JsonParser.parseString("{\"min\":1,\"max\":3}")));
  }

  @Test
  void weightedTmChoicesAreNotUniformOrIndependent() {
    var probabilities = WikiBaronRules.twoChoices(new double[] {1, 2, 3});
    assertArrayEquals(
        new double[] {.4166666666666667, .7333333333333333, .85}, probabilities, 1e-12);
    assertEquals(2, java.util.Arrays.stream(probabilities).sum(), 1e-12);
  }

  @Test
  void smallAndZeroWeightTmPools() {
    assertArrayEquals(new double[] {1}, WikiBaronRules.twoChoices(new double[] {8}), 1e-12);
    assertArrayEquals(new double[] {1, 1}, WikiBaronRules.twoChoices(new double[] {8, 2}), 1e-12);
    assertArrayEquals(new double[] {1, 0}, WikiBaronRules.twoChoices(new double[] {8, 0}), 1e-12);
    assertThrows(
        IllegalArgumentException.class, () -> WikiBaronRules.twoChoices(new double[] {0, 0}));
  }
}
