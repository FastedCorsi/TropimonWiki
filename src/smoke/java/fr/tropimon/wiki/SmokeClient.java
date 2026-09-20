package fr.tropimon.wiki;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Isolated synthetic world only; excluded from all delivery JARs. */
public final class SmokeClient implements ClientModInitializer {
  int ticks, stage = -1;
  long start;
  net.minecraft.client.gui.screen.Screen screen;
  BlockPos habitatPos;

  @Override
  public void onInitializeClient() {
    if (!Boolean.getBoolean("tropimon.smoke")) return;
    start = System.nanoTime();
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (stage == 99) return;
          try {
            if (System.nanoTime() - start > 240_000_000_000L)
              throw new AssertionError("Smoke timeout: " + stage);
            if (client.getOverlay() != null || ++ticks < 40) return;
            switch (stage) {
              case -1 -> {
                stage = 0;
                ticks = 0;
                client.options.getViewDistance().setValue(2);
                client
                    .options
                    .getGuiScale()
                    .setValue(Integer.getInteger("tropimon.smoke.guiScale", 2));
                client.onResolutionChanged();
                System.out.println("WIKI_GUI requested=" + client.options.getGuiScale().getValue()
                    + " effective=" + client.getWindow().getScaleFactor()
                    + " viewport=" + client.getWindow().getScaledWidth() + "x"
                    + client.getWindow().getScaledHeight());
                client.options.pauseOnLostFocus = false;
                client
                    .getTutorialManager()
                    .setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                client
                    .createIntegratedServerLoader()
                    .createAndStart(
                        "instrument-smoke-" + System.currentTimeMillis(),
                        new LevelInfo(
                            "Instrument verification",
                            GameMode.CREATIVE,
                            false,
                            Difficulty.PEACEFUL,
                            true,
                            new GameRules(),
                            DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(1L, false, false),
                        registries ->
                            registries
                                .get(RegistryKeys.WORLD_PRESET)
                                .get(WorldPresets.FLAT)
                                .createDimensionsRegistryHolder(),
                        client.currentScreen);
              }
              case 0 -> {
                if (client.player == null) return;
                client.getToastManager().clear();
                screen = new WikiScreen();
                client.setScreen(screen);
                var wiki = (WikiScreen) screen;
                require(
                    wiki.scale <= 1F && InstrumentScreen.H * wiki.scale <= screen.height - 15F && InstrumentScreen.W * wiki.scale <= screen.width - 15F,
                    "frame stays inside screen margins");
                require(
                    screen.getTitle().getString().equals("Tropimon Wiki"), "Wiki product title");
                require(
                    ((List<?>) field(screen, "all")).size() > 1000, "species registry populated");
                stage = 1;
                ticks = 0;
              }
              case 1 -> {
                shot(client, "wiki-profile");
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                click(40, 70);
                require(search.isFocused(), "scaled search field focus");
                search.setText(client.options.language.equals("fr_fr") ? "evoli" : "eevee");
                require(
                    ((List<?>) field(screen, "filtered")).size() == 1, "Search in player language");
                search.setText("eevee");
                require(((List<?>) field(screen, "filtered")).size() == 1, "English search");
                search.setText("no-such-pokemon");
                require(
                    field(screen, "selected") == null && field(screen, "portrait") == null,
                    "empty search clears model");
                search.setText("#1");
                var abilities = (List<WikiDetails.Ability>) field(screen, "abilities");
                require(
                    abilities.size() == 2
                        && !abilities.getFirst().hidden()
                        && abilities.getLast().hidden(),
                    "Bulbasaur normal and explicit HA");
                require(
                    abilities
                        .getFirst()
                        .name()
                        .equals(client.options.language.equals("fr_fr") ? "Engrais" : "Overgrow"),
                    "Ability follows player language");
                click(250, 206);
                require(((Map<?, ?>) field(screen, "statBars")).size() == 6, "all six base stats");
                require(
                    ((List<?>) field(screen, "lines")).size() <= WikiScreen.DETAIL_ROWS,
                    "all stats and EV visible without scroll");
                stage = 2;
                ticks = 0;
              }
              case 2 -> {
                shot(client, "wiki-stats");
                hover(client, 330, 153);
                require((int) field(screen, "tab") == 1, "ability hover does not change tabs");
                stage = 3;
                ticks = 0;
              }
              case 3 -> {
                shot(client, "wiki-abilities");
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                search.setText("#14");
                var abilities = (List<WikiDetails.Ability>) field(screen, "abilities");
                require(
                    abilities.size() == 1 && !abilities.getFirst().hidden(),
                    "Kakuna duplicate hidden slot remains one normal talent");
                var species = (com.cobblemon.mod.common.pokemon.Species) field(screen, "selected");
                var form = (com.cobblemon.mod.common.pokemon.FormData) field(screen, "form");
                var definitions = new ArrayList<>(form.getEvolutions());
                // Simulate missing network data only in this isolated synthetic world.
                form.getEvolutions().clear();
                try {
                  var local = new WikiData().evolutions(species, form);
                  require(
                      local.local() && local.available() && local.entries().size() == 1,
                      "missing network evolutions use installed Cobblemon reference");
                  require(
                      local.entries().toString().contains("beedrill")
                          && local.entries().toString().contains("minLevel"),
                      "fallback keeps destination and requirements");
                  click(388, 206);
                  require(
                      ((List<?>) field(screen, "lines"))
                          .stream()
                              .anyMatch(
                                  x ->
                                      x.toString()
                                          .contains(
                                              client.options.language.equals("fr_fr")
                                                  ? "Niveau 10 minimum"
                                                  : "Level 10 minimum")),
                      "level condition is readable");
                  require(
                      ((List<?>) field(screen, "lines"))
                          .stream()
                              .anyMatch(
                                  x ->
                                      x.toString()
                                          .contains(
                                              client.options.language.equals("fr_fr")
                                                  ? "Dardargnan"
                                                  : "Beedrill")),
                      "evolution destination translated");
                } finally {
                  form.getEvolutions().addAll(definitions);
                }
                require(
                    ((List<?>) field(screen, "evolutionPreviews")).size() == 1,
                    "evolution destination has a model");
                require(
                    ((List<?>) field(screen, "lines"))
                        .stream().noneMatch(x -> x.toString().contains("RÉFÉRENCE")),
                    "reference banner removed");
                hover(client, 550, 28);
                stage = 4;
                ticks = 0;
              }
              case 4 -> {
                shot(client, "wiki-evolution");
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                search.setText("#133");
                require(
                    ((List<?>) field(screen, "lines"))
                        .stream()
                            .anyMatch(
                                x ->
                                    x.toString()
                                        .contains(
                                            client.options.language.equals("fr_fr")
                                                ? "Utiliser : Pierre Foudre"
                                                : "Use: Thunder Stone")),
                    "live item evolution includes translated stone without JSON");
                require(
                    ((List<?>) field(screen, "lines"))
                        .stream()
                            .anyMatch(
                                x ->
                                    x.toString()
                                        .contains(
                                            client.options.language.equals("fr_fr")
                                                ? "Amitié : 160"
                                                : "Friendship: 160")),
                    "live friendship condition survives serialization");
                stage = 5;
                ticks = 0;
              }
              case 5 -> {
                shot(client, "wiki-eevee");
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                search.setText("#93");
                require(
                    ((List<?>) field(screen, "lines"))
                        .stream()
                            .anyMatch(
                                x ->
                                    x.toString()
                                        .contains(
                                            client.options.language.equals("fr_fr")
                                                ? "Échanger ce Pokémon"
                                                : "Trade this Pokémon")),
                    "live trade evolution method");
                search.setText("#52");
                click(281, 183);
                require((int) field(screen, "formIndex") == 1, "next regional form");
                var form = (com.cobblemon.mod.common.pokemon.FormData) field(screen, "form");
                var species = (com.cobblemon.mod.common.pokemon.Species) field(screen, "selected");
                var local = new WikiData().localEvolutions(species, form);
                require(local.available(), "regional form has its own fallback");
                require(
                    local.entries().toString().contains("alolan"),
                    "regional fallback is not the standard form");
                click(197, 183);
                require((int) field(screen, "formIndex") == 0, "previous form");
                search.setText("#1");
                for (int i = 0; i < 6; i++) {
                  click(197 + i * 63, 206);
                  require(
                      (int) field(screen, "tab") == i
                          && !((List<?>) field(screen, "lines")).isEmpty(),
                      "tab content " + i);
                }
                click(320, 206);
                scroll(400, 265, -1);
                require((int) field(screen, "detailOffset") == 3, "details scroll");
                screen.resize(client, screen.width, screen.height);
                require(
                    (int) field(screen, "detailOffset") == 3, "resize preserves reading position");
                var moveSearch =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "moveSearch");
                click(250, 236);
                require(
                    moveSearch.isFocused() && !search.isFocused(),
                    "move search has independent focus");
                screen.charTyped('v', 0);
                require(
                    moveSearch.getText().equals("v") && search.getText().equals("#1"),
                    "typing moves leaves species search unchanged");
                moveSearch.setText("not-a-real-move");
                require(
                    ((Map<?, ?>) field(screen, "moveRows")).isEmpty(),
                    "no matching move clears cards");
                moveSearch.setText("vinewhip");
                require(
                    !((Map<?, ?>) field(screen, "moveRows")).isEmpty(),
                    "technical move name is searchable");
                require(
                    ((Map<?, ?>) field(screen, "moveRows"))
                        .values().stream()
                            .allMatch(
                                m ->
                                    ((com.cobblemon.mod.common.api.moves.MoveTemplate) m)
                                        .getName()
                                        .equals("vinewhip")),
                    "only matching moves remain");
                require(
                    (int) field(screen, "detailOffset") == 0, "move query resets detail scroll");
                screen.resize(client, screen.width, screen.height);
                moveSearch =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "moveSearch");
                require(
                    moveSearch.getText().equals("vinewhip") && moveSearch.isFocused(),
                    "move filter survives resize");
                moveSearch.setText(
                    client.options.language.equals("fr_fr") ? "fouet lianes" : "vine whip");
                require(
                    !((Map<?, ?>) field(screen, "moveRows")).isEmpty(), "translated move search");
                hover(client, 350, 238);
                stage = 6;
                ticks = 0;
              }
              case 6 -> {
                shot(client, "wiki-moves");
                click(550, 236);
                require(
                    ((net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "moveSearch"))
                        .getText()
                        .isEmpty(),
                    "clear move filter");
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                search.setText("");
                scroll(90, 170, -1);
                require((int) field(screen, "listOffset") == 3, "species list scroll");
                screen.resize(client, screen.width, screen.height);
                require((int) field(screen, "listOffset") == 3, "resize preserves list position");
                click(447, 206);
                require(
                    !((Map<?, ?>) field(screen, "moveRows")).isEmpty(),
                    "egg moves share typed move cards");
                stage = 7;
                ticks = 0;
              }
              case 7 -> {
                require(
                    ((List<?>) field(screen, "lines"))
                        .stream()
                            .anyMatch(
                                x ->
                                    x.toString()
                                        .contains(
                                            client.options.language.equals("fr_fr")
                                                ? "Végétal"
                                                : "Grass")),
                    "egg groups follow player language");
                shot(client, "wiki-breeding");
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                search.setText("");
                click(157, 98);
                require((int) field(screen, "generation") == 1, "generation filter advances");
                require(
                    ((List<com.cobblemon.mod.common.pokemon.Species>) field(screen, "filtered"))
                        .stream().allMatch(p -> p.getNationalPokedexNumber() <= 151),
                    "generation filter excludes other generations");
                search.setText("#152");
                require(field(screen, "selected") == null, "search and generation compose");
                click(157, 98);
                require(
                    field(screen, "selected") != null,
                    "changing generation restores matching search");
                click(40, 98);
                click(40, 98);
                search.setText("#1");
                var dex =
                    com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getClientPokedexData();
                var species = (com.cobblemon.mod.common.pokemon.Species) field(screen, "selected");
                var record = dex.getOrCreateSpeciesRecord(species.getResourceIdentifier());
                var formRecord = record.getOrCreateFormRecord(species.getStandardForm().getName());
                formRecord.setKnowledgeProgress(
                    com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress.SEEN);
                require(
                    dex.getHighestKnowledgeForSpecies(species.getResourceIdentifier())
                        == com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress.SEEN,
                    "seen does not imply captured");
                formRecord.setKnowledgeProgress(
                    com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress.OWNED);
                require(
                    dex.getHighestKnowledgeForSpecies(species.getResourceIdentifier())
                        == com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress.OWNED,
                    "synthetic captured state is available");
                hover(client, 157, 132);
                stage = 8;
                ticks = 0;
              }
              case 8 -> {
                shot(client, "wiki-captured");
                click(554, 30);
                require(client.currentScreen == null, "close button");
                done(client);
              }
            }
          } catch (Throwable ex) {
            ex.printStackTrace();
            System.err.println("TROPIMON_SMOKE_FAILED");
            client.scheduleStop();
            stage = 99;
          }
        });
  }

  void hover(MinecraftClient client, int x, int y) {
    var wiki = (WikiScreen) screen;
    var window = client.getWindow();
    org.lwjgl.glfw.GLFW.glfwSetCursorPos(
        window.getHandle(),
        (wiki.left + (x + InstrumentScreen.CONTENT_OFFSET_X) * wiki.scale)
            * window.getWidth()
            / screen.width,
        (wiki.top + y * wiki.scale) * window.getHeight() / screen.height);
  }

  void click(int x, int y) {
    var wiki = (WikiScreen) screen;
    screen.mouseClicked(
        wiki.left + (x + 0.5 + InstrumentScreen.CONTENT_OFFSET_X) * wiki.scale,
        wiki.top + (y + 0.5) * wiki.scale,
        0);
  }

  void scroll(int x, int y, double amount) {
    var wiki = (WikiScreen) screen;
    screen.mouseScrolled(
        wiki.left + (x + 0.5 + InstrumentScreen.CONTENT_OFFSET_X) * wiki.scale,
        wiki.top + (y + 0.5) * wiki.scale,
        0,
        amount);
  }

  static Object field(Object instance, String name) throws Exception {
    var f = instance.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(instance);
  }

  static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    System.out.println("TROPIMON_CHECK: " + message);
  }

  static void shot(MinecraftClient client, String name) throws Exception {
    Path dir = client.runDirectory.toPath().resolve("verification");
    Files.createDirectories(dir);
    try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
      image.writeTo(dir.resolve(name + ".png"));
    }
  }

  void done(MinecraftClient client) {
    System.out.println("TROPIMON_SMOKE_OK");
    client.scheduleStop();
    stage = 99;
  }
}
