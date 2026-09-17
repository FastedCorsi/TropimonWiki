package fr.tropimon.wiki;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WikiDetailsTest {
  private WikiDetails.Ability ability(String id, boolean hidden) {
    return new WikiDetails.Ability(id, id, "Description", hidden);
  }

  @Test
  void singleAbilityAndDuplicateHiddenSlotAreNormal() {
    assertFalse(WikiDetails.abilities(List.of(ability("levitate", false))).getFirst().hidden());
    assertFalse(WikiDetails.abilities(List.of(ability("levitate", true))).getFirst().hidden());
    var entries =
        WikiDetails.abilities(List.of(ability("shedskin", true), ability("shedskin", false)));
    assertEquals(1, entries.size());
    assertFalse(entries.getFirst().hidden());
  }

  @Test
  void hiddenStatusIsExplicitNotInferredFromPositionOrCount() {
    var ordinary =
        WikiDetails.abilities(List.of(ability("first", false), ability("second", false)));
    assertTrue(ordinary.stream().noneMatch(WikiDetails.Ability::hidden));
    var entries =
        WikiDetails.abilities(
            List.of(ability("hidden", true), ability("first", false), ability("second", false)));
    assertEquals(3, entries.size());
    assertEquals("hidden", entries.getLast().id());
    assertTrue(entries.getLast().hidden());
    assertFalse(entries.getFirst().hidden());
  }

  @Test
  void itemAndTradeIncludeRequiredActionAndHeldItem() {
    var stone =
        evolution(
            "{\"variant\":\"item_interact\",\"requiredContext\":\"cobblemon:thunder_stone\",\"requirements\":[]}");
    assertEquals(List.of("Utiliser : cobblemon:thunder_stone"), stone);
    var trade =
        evolution(
            "{\"variant\":\"trade\",\"consumeHeldItem\":true,\"requirements\":[{\"variant\":\"held_item\",\"itemCondition\":\"cobblemon:kings_rock\"}]}");
    assertEquals(
        List.of(
            "Échanger ce Pokémon", "Tenir : cobblemon:kings_rock", "L'objet tenu est consommé."),
        trade);
  }

  @Test
  void levelFriendshipAndTimeRemainCumulative() {
    var lines =
        evolution(
            "{\"variant\":\"level_up\",\"requirements\":[{\"variant\":\"level\",\"minLevel\":16},{\"variant\":\"friendship\",\"amount\":160},{\"variant\":\"time_range\",\"range\":\"night\"}]}");
    assertEquals(
        List.of("Monter d'un niveau", "Niveau 16 minimum", "Amitié : 160 minimum", "Moment : nuit"),
        lines);
    assertFalse(String.join(" ", lines).contains("2147483647"));
  }

  @Test
  void serializedItemPredicatesAndTradePartnersRemainReadable() {
    var stone =
        evolution(
            "{\"variant\":\"item_interact\",\"requiredContext\":{\"items\":\"cobblemon:thunder_stone\"}}");
    assertEquals(List.of("Utiliser : cobblemon:thunder_stone"), stone);
    var trade = evolution("{\"variant\":\"trade\",\"requiredContext\":\"shelmet\"}");
    assertEquals(List.of("Échanger ce Pokémon contre shelmet"), trade);
    var held =
        evolution(
            "{\"variant\":\"level_up\",\"requirements\":[{\"variant\":\"held_item\",\"itemCondition\":{\"items\":[\"one\",\"two\"]}}]}");
    assertTrue(held.contains("Tenir : one ou two"));
  }

  @Test
  void unknownConditionsAreNeverDropped() {
    var lines =
        evolution(
            "{\"variant\":\"custom_method\",\"requirements\":[{\"variant\":\"custom_requirement\",\"count\":7}]}");
    assertTrue(lines.contains("Méthode spéciale : custom_method"));
    assertTrue(lines.contains("Condition avancée : custom_requirement"));
    assertTrue(lines.contains("count : 7"));
  }

  @Test
  void negativePartyAndWeatherRequirementsKeepTheirMeaning() {
    var lines =
        evolution(
            "{\"variant\":\"level_up\",\"requirements\":[{\"variant\":\"party_member\",\"contains\":false,\"target\":\"pikachu\"},{\"variant\":\"weather\",\"isRaining\":false,\"isThundering\":true}]}");
    assertTrue(lines.contains("Ne pas avoir dans l'équipe : pikachu"));
    assertTrue(lines.contains("Sans pluie"));
    assertTrue(lines.contains("Pendant un orage"));
  }

  private List<String> evolution(String json) {
    return WikiDetails.evolution(JsonParser.parseString(json).getAsJsonObject(), (kind, id) -> id);
  }
}
