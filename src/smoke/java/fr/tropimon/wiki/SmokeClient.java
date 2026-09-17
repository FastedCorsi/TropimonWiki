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
                client.options.getGuiScale().setValue(Integer.getInteger("tropimon.smoke.guiScale", 2));
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
                require(wiki.scale <= 1F && InstrumentScreen.H * wiki.scale <= screen.height * 0.781F,
                    "compact window keeps vertical margins");
                require(screen.getTitle().getString().equals("Tropimon Wiki"), "Wiki product title");
                require(
                    ((List<?>) field(screen, "all")).size() > 1000, "species registry populated");
                stage = 1;
                ticks = 0;
              }
              case 1 -> {
                shot(client, "wiki");
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                click(40, 70);
                require(search.isFocused(), "scaled search field accepts focus");
                search.setText("eevee");
                require(
                    ((List<?>) field(screen, "filtered")).size() == 1,
                    "English identifier search with French translation");
                search.setText("#133");
                require(((List<?>) field(screen, "filtered")).size() == 1, "national number search");
                search.setText("no-such-pokemon");
                require(field(screen, "selected") == null, "empty search clears selection");
                require(field(screen, "portrait") == null, "empty search clears portrait");
                search.setText("#1");
                click(512, 173);
                require((int) field(screen, "formIndex") == 0, "single form has no active arrow");
                click(211, 201);
                scroll(400, 265, -1);
                require((int) field(screen, "detailOffset") > 0, "scaled details scroll");
                stage = 2;
                ticks = 0;
              }
              case 2 -> {
                shot(client, "wiki-stats");
                for (int i = 0; i < 6; i++) {
                  click(210 + i * 56, 203);
                  require((int) field(screen, "tab") == i, "scaled tab " + i);
                  require(!((List<?>) field(screen, "lines")).isEmpty(), "tab content " + i);
                }
                click(322, 203);
                require(
                    ((List<?>) field(screen, "lines")).size() > 10, "learnset details populated");
                stage = 3;
                ticks = 0;
              }
              case 3 -> {
                shot(client, "wiki-moves");
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                search.setText("#26");
                int count = ((List<?>) field(screen, "forms")).size();
                require(count > 1, "regional forms populated");
                click(512, 173);
                require((int) field(screen, "formIndex") == 1, "next form");
                click(333, 173);
                require((int) field(screen, "formIndex") == 0, "previous form");
                click(333, 173);
                require((int) field(screen, "formIndex") == count - 1, "previous form wraps");
                stage = 4;
                ticks = 0;
              }
              case 4 -> {
                shot(client, "wiki-form");
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                int selectedForm = (int) field(screen, "formIndex");
                scroll(400, 265, -1);
                int detail = (int) field(screen, "detailOffset");
                screen.resize(client, screen.width, screen.height);
                require((int) field(screen, "formIndex") == selectedForm
                    && (int) field(screen, "detailOffset") == detail, "resize preserves form and details");
                search = (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                search.setText("#14");
                click(378, 203);
                var form = (com.cobblemon.mod.common.pokemon.FormData) field(screen, "form");
                if (form.getEvolutions().isEmpty())
                  require(((List<?>) field(screen, "lines")).stream()
                      .anyMatch(line -> line.toString().contains("indisponibles")),
                      "missing evolution data is described as unavailable");
                stage = 5;
                ticks = 0;
              }
              case 5 -> {
                shot(client, "wiki-evolution");
                var search =
                    (net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "search");
                search.setText("");
                scroll(90, 170, -1);
                require((int) field(screen, "listOffset") == 3, "species list scroll");
                screen.resize(client, screen.width, screen.height);
                require((int) field(screen, "listOffset") == 3, "resize preserves list position");
                click(520, 30);
                require(client.currentScreen == null, "scaled close button");
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
    screen.mouseClicked(wiki.left + (x + 0.5) * wiki.scale,
        wiki.top + (y + 0.5) * wiki.scale, 0);
  }

  void scroll(int x, int y, double amount) {
    var wiki = (WikiScreen) screen;
    screen.mouseScrolled(wiki.left + (x + 0.5) * wiki.scale,
        wiki.top + (y + 0.5) * wiki.scale, 0, amount);
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
