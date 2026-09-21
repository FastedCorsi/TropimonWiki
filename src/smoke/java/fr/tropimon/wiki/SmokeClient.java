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
                click(480, 64);
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
                var catalog = (WikiSpawns.Catalog) field(screen, "spawnCatalog");
                auditHabitatEvs(catalog);
                require(
                    (int) field(screen, "habitatPhase") == 1,
                    "selected Charmeleon habitat opens matching phase");
                require(
                    ((List<String>) field(screen, "lines"))
                        .stream()
                            .anyMatch(
                                line ->
                                    line.contains("+1")
                                        && line.contains(new WikiData().name("stat", "spa"))
                                        && line.contains(new WikiData().name("stat", "spe"))),
                    "habitat roster displays both Charmeleon EVs");
                shot(client, "wiki-habitat");
                click(250, 327);
                require(
                    (int) field(screen, "habitatPhase") == 0,
                    "habitat conditions remain accessible");
                click(295, 327);
                require((int) field(screen, "habitatPhase") > 0, "habitat phase navigation");
                click(320, 236);
                require(!(boolean) field(screen, "habitatMode"), "switch to wild spawns");
                entries = (List<WikiSpawns.Entry>) field(screen, "spawnEntries");
                require(
                    entries.stream().anyMatch(e -> e.spawn().has("herdMember")),
                    "herd spawns included");
                require(
                    entries.stream().anyMatch(e -> e.spawn().has("condition")),
                    "wild conditions retained");
                click(410, 64);
                stage = 14;
                ticks = 0;
              }
              case 14 -> {
                if ((boolean) field(screen, "baronLoading")) return;
                var catalog = (WikiBarons.Catalog) field(screen, "baronCatalog");
                require(
                    catalog != null && catalog.knownDrops() && catalog.knownMoves(),
                    "Alpha local rules recognized");
                auditBarons(catalog);
                shot(client, "wiki-barons-info");
                click(300, 236);
                require((int) field(screen, "baronPage") == 1, "Alpha loot selected");
                require(
                    ((List<String>) field(screen, "lines"))
                        .stream().anyMatch(l -> l.contains("44,44") || l.contains("44.44")),
                    "tier 2 weighted candy chances");
                require(
                    ((List<String>) field(screen, "lines"))
                        .stream().anyMatch(l -> l.contains("60,94") || l.contains("60.94")),
                    "type reward odds include both 50 percent gates");
                stage = 15;
                ticks = 0;
              }
              case 15 -> {
                shot(client, "wiki-barons-loot");
                click(547, 236);
                require(
                    (int) field(screen, "baronLevel") == 51, "Alpha tier boundary changes at 51");
                require(
                    ((List<String>) field(screen, "lines"))
                        .stream().anyMatch(l -> l.contains("11,76") || l.contains("11.76")),
                    "tier 3 candy probability includes empty entry");
                click(360, 236);
                require(
                    (int) field(screen, "baronPage") == 2
                        && !((Map<?, ?>) field(screen, "moveRows")).isEmpty(),
                    "Alpha TM recipes displayed");
                click(250, 257);
                ((net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "moveSearch"))
                    .setText("flamethrower");
                require(((Map<?, ?>) field(screen, "moveRows")).size() == 1, "Alpha TM search");
                require(
                    ((Map<?, ?>) field(screen, "itemRows")).size() == 3,
                    "blank TM and both crafting ingredients");
                stage = 16;
                ticks = 0;
              }
              case 16 -> {
                shot(client, "wiki-barons-tm");
                click(550, 257);
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
                click(410, 64);
                click(360, 236);
                click(250, 257);
                ((net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "moveSearch"))
                    .setText("flamethrower");
                require(
                    ((net.minecraft.client.gui.widget.TextFieldWidget) field(screen, "moveSearch"))
                        .isFocused(),
                    "Alpha search hitbox GUI " + testedScale);
                screen.resize(client, screen.width, screen.height);
                stage = 17;
                ticks = 0;
              }
              case 17 -> {
                shot(client, "wiki-gui-barons-" + testedScale);
                click(550, 257);
                scroll(400, 300, -1);
                require((int) field(screen, "detailOffset") > 0, "Alpha scroll GUI " + testedScale);
                click(480, 64);
                click(220, 236);
                click(250, 327);
                if ((int) field(screen, "habitatPhase") == 0) click(295, 327);
                require(
                    (int) field(screen, "habitatPhase") > 0,
                    "habitat phase hitboxes GUI " + testedScale);
                int phase = (int) field(screen, "habitatPhase");
                screen.resize(client, screen.width, screen.height);
                require(
                    (int) field(screen, "habitatPhase") == phase,
                    "habitat phase survives resize GUI " + testedScale);
                stage = 18;
                ticks = 0;
              }
              case 18 -> {
                if ((boolean) field(screen, "previewLoading")) return;
                shot(client, "wiki-gui-habitat-ev-" + testedScale);
                scroll(450, 290, -1);
                require(
                    (int) field(screen, "detailOffset") > 0,
                    "habitat EV list scrolls GUI " + testedScale);
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

  void auditHabitatEvs(WikiSpawns.Catalog catalog) {
    var data = new WikiData();
    var berry =
        catalog.entries().stream()
            .filter(e -> e.pool().endsWith(":berry_patch"))
            .findFirst()
            .orElseThrow();
    var one = WikiSpawns.residents(catalog, berry.pool(), 1, data);
    var two = WikiSpawns.residents(catalog, berry.pool(), 2, data);
    String zigzagoon = data.name("species", "zigzagoon");
    require(
        one.stream().noneMatch(r -> r.name().equals(zigzagoon))
            && two.stream().anyMatch(r -> r.name().equals(zigzagoon)),
        "phase roster does not mix adjacent phases");
    require(
        one.stream()
            .allMatch(
                r ->
                    r.yield().available()
                        && r.yield().values().values().stream().mapToInt(Integer::intValue).sum()
                            > 0),
        "habitat EVs resolved from loaded species or local fallback");
    var lickitung =
        one.stream()
            .filter(r -> r.name().equals(data.name("species", "lickitung")))
            .findFirst()
            .orElseThrow();
    require(lickitung.yield().values().getOrDefault("hp", 0) == 2, "Lickitung yields 2 HP EVs");
    var synthetic = new ArrayList<WikiSpawns.Entry>();
    var phase = new com.google.gson.JsonObject();
    phase.addProperty("phases", "1");
    for (String pokemon : List.of("diglett", "diglett", "diglett alolan", "missing_species"))
      synthetic.add(new WikiSpawns.Entry("example:pool", "test", pokemon, phase, List.of()));
    var sample =
        WikiSpawns.residents(
            new WikiSpawns.Catalog(synthetic, catalog.structures(), 0), "example:pool", 1, data);
    require(sample.size() == 3, "habitat duplicates merge but regional forms remain distinct");
    require(
        sample.stream().filter(r -> r.yield().available()).count() == 2,
        "unknown habitat species keeps unknown EVs");
    require(
        !data.evSummary(new WikiData.Yield(Map.of(), false, true))
            .equals(data.evSummary(new WikiData.Yield(Map.of(), true, true))),
        "unknown EV display differs from explicit zero");
    var allHabitatEntries =
        catalog.entries().stream()
            .filter(WikiSpawns.Entry::habitat)
            .map(
                e ->
                    new WikiSpawns.Entry(
                        "example:all",
                        "test",
                        e.pokemon(),
                        phase,
                        List.<com.google.gson.JsonObject>of()))
            .toList();
    var allResidents =
        WikiSpawns.residents(
            new WikiSpawns.Catalog(allHabitatEntries, catalog.structures(), 0),
            "example:all",
            1,
            data);
    for (var resident : allResidents)
      require(
          resident.yield().available()
              && resident.yield().values().values().stream().mapToInt(Integer::intValue).sum() > 0,
          "actual habitat resident EVs: " + resident.name());
    System.out.println("HABITAT_RESIDENTS_WITH_EVS=" + allResidents.size());
    int checked = 0;
    for (var entry : catalog.entries())
      if (entry.habitat()) {
        if (entry.spawn().has("phases"))
          require(!entry.phases().isEmpty(), "installed habitat phase expression understood");
        else require(entry.phases().isEmpty(), "habitat without phase keeps conditions view");
        checked++;
      }
    System.out.println("HABITAT_PHASE_ENTRIES=" + checked);
  }

  void auditBarons(WikiBarons.Catalog catalog) throws Exception {
    require(catalog.loot().size() == 40, "all 40 Alpha reward tables present");
    for (var table : catalog.loot().values())
      for (var pool : WikiBaronRules.pools(table))
        require(
            Math.abs(pool.drops().stream().mapToDouble(WikiBaronRules.Drop::probability).sum() - 1)
                < 1e-9,
            "Alpha loot pool normalized");
    var runtime = com.bedrockk.molang.MoLang.createRuntime();
    com.cobblemon.mod.common.api.molang.MoLangFunctions.INSTANCE.addStandardFunctions(
        runtime.getEnvironment().query);
    var root =
        net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer("cobblemon")
            .orElseThrow()
            .findPath("data/cobblemon/callbacks/battle_fainted/pokemon_alpha_drops.molang")
            .orElseThrow();
    String callback = Files.readString(root);
    String branch =
        callback.substring(
            callback.indexOf("t.types[0]"), callback.indexOf("(math.random_integer"));
    String actual =
        runtime
            .execute(
                com.bedrockk.molang.MoLang.parse(
                    "t.pokemon_types = q.array('fire', 'flying'); t.pokemon.level = 50; "
                        + branch
                        + " return t.types[1];"))
            .asString();
    require(actual.equals("fire"), "actual Alpha callback repeats primary type for dual types");
    require(
        !WikiBarons.alpha("charmander held_item=cobblemon:fire_gem")
            && WikiBarons.alpha("charizard alpha=true"),
        "boss followers are not Alphas");
    for (String name : List.of("charizard", "magikarp", "eevee")) {
      var species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(name);
      var form = species.getStandardForm();
      var entries = WikiBarons.machines(catalog, form, 50);
      require(
          entries.stream()
              .allMatch(e -> Double.isFinite(e.chance()) && e.chance() >= 0 && e.chance() <= 1),
          "TM probabilities valid: " + name);
      require(
          entries.stream().noneMatch(WikiBarons.TM::local),
          "TM recipes use loaded registry: " + name);
      var manager =
          new com.cobblemon.mod.common.api.tms.TMMoveManager(
              new java.util.UUID(0, 2), new HashSet<>());
      for (int sample = 0; sample < 20; sample++) {
        var pokemon =
            com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(
                    name + " level=50 alpha=true")
                .create();
        var nativeRecipes = manager.getLearnableTMsFromPokemon(pokemon);
        require(
            entries.stream()
                .filter(e -> e.chance() == 1)
                .allMatch(e -> nativeRecipes.contains(e.machine().getId())),
            "guaranteed recipes agree with native capture resolver: " + name);
        require(
            nativeRecipes.stream()
                .allMatch(id -> entries.stream().anyMatch(e -> e.machine().getId().equals(id))),
            "native capture recipes covered: " + name);
      }
      var level = form.getMoves().getLevelUpMovesUpTo(50);
      var pool =
          form.getMoves().getTmMoves().stream().filter(m -> !level.contains(m)).distinct().toList();
      if (pool.size() < 3) continue;
      var predicted =
          WikiBaronRules.twoChoices(
              pool.stream().mapToDouble(m -> m.getSelectionWeight(form)).toArray());
      var observed = new HashMap<com.cobblemon.mod.common.api.moves.MoveTemplate, Integer>();
      int samples = 12000;
      for (int i = 0; i < samples; i++) {
        var chosen = new HashSet<com.cobblemon.mod.common.api.moves.MoveTemplate>();
        for (int slot = 0; slot < 2; slot++) {
          var move =
              com.cobblemon.mod.common.api.moves.MoveSelector.Companion.getTM()
                  .invoke(form, form.getMoves(), 50, chosen);
          if (move != null) {
            chosen.add(move);
            observed.merge(move, 1, Integer::sum);
          }
        }
      }
      for (int i = 0; i < pool.size(); i++)
        require(
            Math.abs(observed.getOrDefault(pool.get(i), 0) / (double) samples - predicted[i])
                < .025,
            "native weighted TM selector agrees: " + pool.get(i).getName());
    }
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
