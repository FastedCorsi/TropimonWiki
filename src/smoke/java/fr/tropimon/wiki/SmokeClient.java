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
                    wiki.scale <= 1F && InstrumentScreen.H * wiki.scale <= screen.height * 0.781F,
                    "compact window keeps vertical margins");
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
                search.setText("evoli");
                require(
                    ((List<?>) field(screen, "filtered")).size() == 1,
                    "French search regardless of game language");
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
                    abilities.getFirst().name().equals("Engrais"),
                    "French ability text with English game setting");
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
                click(330, 153);
                require((int) field(screen, "tab") == 2, "ability summary opens full description");
                require(
                    ((List<?>) field(screen, "lines"))
                        .stream().anyMatch(x -> x.toString().contains("HA / Talent caché")),
                    "hidden ability explained");
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
                  click(415, 206);
                  require(
                      ((List<?>) field(screen, "lines"))
                          .stream().anyMatch(x -> x.toString().contains("Niveau 10 minimum")),
                      "level condition is readable");
                  require(
                      ((List<?>) field(screen, "lines"))
                          .stream().anyMatch(x -> x.toString().contains("Dardargnan")),
                      "evolution destination translated");
                } finally {
                  form.getEvolutions().addAll(definitions);
                }
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
                        .stream().anyMatch(x -> x.toString().contains("Utiliser : Pierre Foudre")),
                    "live item evolution includes translated stone without JSON");
                require(
                    ((List<?>) field(screen, "lines"))
                        .stream().anyMatch(x -> x.toString().contains("Amitié : 160")),
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
                        .stream().anyMatch(x -> x.toString().contains("Échanger ce Pokémon")),
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
                for (int i = 0; i < 7; i++) {
                  click(197 + i * 55, 206);
                  require(
                      (int) field(screen, "tab") == i
                          && !((List<?>) field(screen, "lines")).isEmpty(),
                      "tab content " + i);
                }
                click(360, 206);
                scroll(400, 265, -1);
                require((int) field(screen, "detailOffset") == 3, "details scroll");
                screen.resize(client, screen.width, screen.height);
                require(
                    (int) field(screen, "detailOffset") == 3, "resize preserves reading position");
                stage = 6;
                ticks = 0;
              }
              case 6 -> {
                shot(client, "wiki-moves");
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                search.setText("");
                scroll(90, 170, -1);
                require((int) field(screen, "listOffset") == 3, "species list scroll");
                screen.resize(client, screen.width, screen.height);
                require((int) field(screen, "listOffset") == 3, "resize preserves list position");
                click(562, 30);
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

  void click(int x, int y) {
    var wiki = (WikiScreen) screen;
    screen.mouseClicked(wiki.left + (x + 0.5) * wiki.scale, wiki.top + (y + 0.5) * wiki.scale, 0);
  }

  void scroll(int x, int y, double amount) {
    var wiki = (WikiScreen) screen;
    screen.mouseScrolled(
        wiki.left + (x + 0.5) * wiki.scale, wiki.top + (y + 0.5) * wiki.scale, 0, amount);
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
