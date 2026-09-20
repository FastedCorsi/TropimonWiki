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
  int testedScale = 1;
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
                System.out.println(
                    "WIKI_GUI requested="
                        + client.options.getGuiScale().getValue()
                        + " effective="
                        + client.getWindow().getScaleFactor()
                        + " viewport="
                        + client.getWindow().getScaledWidth()
                        + "x"
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
                    wiki.scale <= 1F
                        && InstrumentScreen.H * wiki.scale <= screen.height - 15F
                        && InstrumentScreen.W * wiki.scale <= screen.width - 15F,
                    "frame stays inside screen margins");
                require(
                    screen.getTitle().getString().equals("Tropimon Wiki"), "Wiki product title");
                require(
                    ((List<?>) field(screen, "all")).size() > 1000, "species registry populated");
                auditEvYields(client);
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
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                search.setText("#5");
                click(400, 64);
                require((int) field(screen, "tab") == 6, "habitats button opens spawn view");
                stage = 9;
                ticks = 0;
              }
              case 9 -> {
                if ((boolean) field(screen, "spawnLoading")
                    || (boolean) field(screen, "previewLoading")) return;
                require(field(screen, "spawnCatalog") != null, "installed spawn catalogue loaded");
                var entries = (List<WikiSpawns.Entry>) field(screen, "spawnEntries");
                require(
                    !entries.isEmpty() && entries.stream().allMatch(WikiSpawns.Entry::habitat),
                    "Charmeleon has habitat entries");
                require(
                    field(screen, "habitatPreview") != null,
                    "habitat has actual structure preview");
                shot(client, "wiki-habitat");
                click(320, 236);
                require(!(boolean) field(screen, "habitatMode"), "switch to wild spawns");
                entries = (List<WikiSpawns.Entry>) field(screen, "spawnEntries");
                require(
                    entries.stream().anyMatch(e -> e.spawn().has("herdMember")),
                    "herd spawns included");
                require(
                    entries.stream().anyMatch(e -> e.spawn().has("condition")),
                    "wild conditions retained");
                stage = 10;
                ticks = 0;
              }
              case 10 -> {
                if (testedScale == 1) shot(client, "wiki-spawns");
                client.options.getGuiScale().setValue(testedScale);
                client.onResolutionChanged();
                screen = client.currentScreen;
                System.out.println(
                    "WIKI_GUI requested="
                        + testedScale
                        + " effective="
                        + client.getWindow().getScaleFactor()
                        + " framebuffer="
                        + client.getWindow().getFramebufferWidth()
                        + "x"
                        + client.getWindow().getFramebufferHeight());
                stage = 13;
                ticks = 0;
              }
              case 13 -> {
                screen = client.currentScreen;
                click(320, 206);
                require((int) field(screen, "tab") == 2, "moves tab at GUI " + testedScale);
                click(250, 236);
                var query =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "moveSearch");
                query.setText("scratch");
                require(
                    query.isFocused() && !((Map<?, ?>) field(screen, "moveRows")).isEmpty(),
                    "move search works at GUI " + testedScale);
                screen.resize(client, screen.width, screen.height);
                require(
                    ((net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "moveSearch"))
                        .getText()
                        .equals("scratch"),
                    "GUI resize preserves query");
                stage = 11;
                ticks = 0;
              }
              case 11 -> {
                require(
                    client.currentScreen == screen && (int) field(screen, "tab") == 2,
                    "same moves screen after scale settles " + testedScale);
                shot(client, "wiki-gui-" + testedScale);
                click(550, 236);
                require(
                    ((net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "moveSearch"))
                        .getText()
                        .isEmpty(),
                    "clear at GUI " + testedScale);
                scroll(400, 280, -1);
                require((int) field(screen, "detailOffset") > 0, "scroll at GUI " + testedScale);
                click(250, 206);
                hover(client, 330, 153);
                stage = 12;
                ticks = 0;
              }
              case 12 -> {
                shot(client, "wiki-gui-stats-" + testedScale);
                if (++testedScale <= 4) {
                  stage = 10;
                  ticks = 0;
                } else {
                  click(554, 32);
                  require(client.currentScreen == null, "close button after GUI matrix");
                  done(client);
                }
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

  void auditEvYields(MinecraftClient client) {
    var data = new WikiData();
    int audited = 0, forms = 0, unknown = 0;
    for (var species : com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getSpecies()) {
      var local = data.localEvYield(species, species.getStandardForm());
      if (!species.getResourceIdentifier().getNamespace().equals("cobblemon")) continue;
      if (!local.available()
          || local.values().values().stream().mapToInt(Integer::intValue).sum() <= 0)
        throw new AssertionError("Missing standard EV definition: " + species.getName());
      audited++;
      for (var form : species.getForms()) {
        var result = data.localEvYield(species, form);
        if (result.available()) forms++;
        else unknown++;
      }
    }
    require(
        audited >= 1025,
        "all standard species have nonzero local EV data: "
            + audited
            + "; forms="
            + forms
            + " unavailable forms="
            + unknown);
    var original =
        com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName("charmeleon");
    var buffer =
        new net.minecraft.network.RegistryByteBuf(
            io.netty.buffer.Unpooled.buffer(), client.world.getRegistryManager());
    try {
      original.encode(buffer);
      var network = new com.cobblemon.mod.common.pokemon.Species();
      network.decode(buffer);
      network.setResourceIdentifier(original.getResourceIdentifier());
      network.initialize();
      var yield = data.evYield(network, network.getStandardForm());
      require(
          yield.local()
              && yield.available()
              && yield.values().getOrDefault("spa", 0) == 1
              && yield.values().getOrDefault("spe", 0) == 1,
          "real species packet falls back to Charmeleon +1 SpA +1 Speed");
    } finally {
      buffer.release();
    }
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
