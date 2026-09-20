package fr.tropimon.wiki;

import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.TypeIcon;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.pokemon.*;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Compact, read-only reference with explicit data provenance. */
public final class WikiScreen extends InstrumentScreen {
  private static final int ACCENT = 0xFFE9DE8C;
  static final int LIST_ROWS = 12, DETAIL_ROWS = 10, DETAIL_X = 187, TAB_Y = 198;
  private static final Identifier POKE_BALL =
      Identifier.of("cobblemon", "textures/gui/pokedex/pokedex_screen_poke_ball.png");
  private static final Identifier PLATFORM =
      Identifier.of("cobblemon", "textures/gui/pokedex/platform_base.png");
  private static final String[] TABS = {"profile", "stats", "moves", "evolve", "breeding", "drops"};
  private final List<Species> all;
  private final WikiData data = new WikiData();
  private List<Species> filtered = List.of();
  private Species selected;
  private FormData form;
  private int listOffset, detailOffset, tab, formIndex, generation;
  private TextFieldWidget search;
  private ModelWidget portrait;
  private List<WikiDetails.Ability> abilities = List.of();
  private final List<TypeIcon> typeIcons = new ArrayList<>();
  private final List<FormData> forms = new ArrayList<>();
  private final List<String> lines = new ArrayList<>();
  private final Map<Integer, Integer> statBars = new HashMap<>();
  private final Set<Integer> headings = new HashSet<>();
  private final Map<Integer, MoveTemplate> moveRows = new HashMap<>();
  private final Map<Integer, TypeIcon> moveIcons = new HashMap<>();
  private final Map<Integer, Integer> indents = new HashMap<>();
  private final List<EvolutionPreview> evolutionPreviews = new ArrayList<>();
  private int paragraphX = 195;

  private record EvolutionPreview(int row, int end, ModelWidget model) {}

  public WikiScreen() {
    super("Tropimon Wiki");
    all =
        PokemonSpecies.getSpecies().stream()
            .sorted(
                Comparator.comparingInt(Species::getNationalPokedexNumber)
                    .thenComparing(Species::getName))
            .toList();
  }

  @Override
  protected void init() {
    super.init();
    String old = search == null ? "" : search.getText();
    int previousListOffset = listOffset, previousDetailOffset = detailOffset;
    boolean wasFocused = search != null && search.isFocused();
    search = new TextFieldWidget(textRenderer, 34, 68, 128, 12, Text.literal(data.ui("search")));
    search.setDrawsBackground(false);
    search.setEditableColor(WHITE);
    search.setMaxLength(80);
    search.setPlaceholder(Text.literal(data.ui("search.hint")));
    search.setChangedListener(this::filter);
    search.setText(old);
    filter(old);
    listOffset = Math.clamp(previousListOffset, 0, Math.max(0, filtered.size() - LIST_ROWS));
    search.setFocused(wasFocused);
    updatePortrait();
    rebuild();
    detailOffset = Math.clamp(previousDetailOffset, 0, Math.max(0, lines.size() - DETAIL_ROWS));
  }

  private void filter(String query) {
    filtered =
        all.stream()
            .filter(
                s ->
                    generation == 0
                        || WikiDetails.generation(s.getNationalPokedexNumber()) == generation)
            .filter(
                s ->
                    WikiSearch.matches(
                            query, data.species(s), s.getName(), s.getNationalPokedexNumber())
                        || WikiSearch.matches(
                            query,
                            s.getTranslatedName().getString(),
                            s.getName(),
                            s.getNationalPokedexNumber()))
            .toList();
    listOffset = 0;
    if (!filtered.contains(selected)) select(filtered.isEmpty() ? null : filtered.getFirst());
  }

  private void select(Species species) {
    selected = species;
    formIndex = 0;
    forms.clear();
    if (species != null) {
      forms.add(species.getStandardForm());
      for (var candidate : species.getForms()) if (!forms.contains(candidate)) forms.add(candidate);
    }
    form = species == null ? null : species.getStandardForm();
    updatePortrait();
    rebuild();
  }

  private void updatePortrait() {
    typeIcons.clear();
    abilities = form == null ? List.of() : data.abilities(form);
    if (form != null) {
      int x = 309;
      for (var type : form.getTypes()) {
        typeIcons.add(new TypeIcon(x, 88, type, null, false, true, 0F, 0F, 1F));
        x += 133;
      }
    }
    portrait =
        selected == null
            ? null
            : new ModelWidget(
                left + (int) (190 * scale),
                top + (int) (82 * scale),
                (int) (100 * scale),
                (int) (86 * scale),
                new RenderablePokemon(selected, new HashSet<>(form.getAspects()), ItemStack.EMPTY),
                1.8F * scale,
                25F,
                4 * scale,
                false,
                false,
                15);
  }

  private void paragraph(String value) {
    if (value.isEmpty()) {
      lines.add("");
      return;
    }
    for (var ordered : textRenderer.wrapLines(Text.literal(value), 555 - paragraphX)) {
      StringBuilder s = new StringBuilder();
      ordered.accept(
          (i, style, cp) -> {
            s.appendCodePoint(cp);
            return true;
          });
      indents.put(lines.size(), paragraphX);
      lines.add(s.toString());
    }
  }

  private void heading(String value) {
    headings.add(lines.size());
    paragraph(value);
  }

  private void rebuild() {
    lines.clear();
    statBars.clear();
    headings.clear();
    moveRows.clear();
    moveIcons.clear();
    indents.clear();
    evolutionPreviews.clear();
    paragraphX = 195;
    detailOffset = 0;
    if (selected == null || textRenderer == null) return;
    switch (tab) {
      case 0 -> {
        heading(data.ui("about") + data.species(selected));
        for (String key : form.getPokedex()) paragraph(data.tr(key));
        paragraph("");
        paragraph(
            data.ui("height")
                + WikiDetails.number(form.getHeight() / 10F)
                + data.ui("weight")
                + WikiDetails.number(form.getWeight() / 10F)
                + " kg");
        paragraph(data.ui("catch_rate") + form.getCatchRate());
        paragraph(data.ui("friendship") + form.getBaseFriendship());
        paragraph(data.ui("stats.hint"));
      }
      case 1 -> {
        int total = 0;
        for (String id : List.of("hp", "atk", "def", "spa", "spd", "spe")) {
          for (var entry : form.getBaseStats().entrySet())
            if (entry.getKey().getShowdownId().equals(id)) {
              statBars.put(lines.size(), entry.getValue());
              paragraph(data.name("stat", id));
              total += entry.getValue();
            }
        }
        paragraph("");
        heading(data.ui("total") + total);
        String evs =
            String.join(
                " · ",
                form.getEvYield().entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(
                        e ->
                            "+"
                                + e.getValue()
                                + " "
                                + data.name("stat", e.getKey().getShowdownId()))
                    .toList());
        paragraph(data.ui("evs") + (evs.isEmpty() ? data.ui("none") : evs));
      }
      case 2 -> {
        var moves = form.getMoves();
        moves.getLevelUpMoves().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> e.getValue().forEach(m -> move(data.ui("level") + e.getKey(), m)));
        moves.getTmMoves().forEach(m -> move(data.ui("tm"), m));
        moves.getEggMoves().forEach(m -> move(data.ui("egg_move.origin"), m));
        moves.getTutorMoves().forEach(m -> move(data.ui("tutor"), m));
        moves.getEvolutionMoves().forEach(m -> move(data.ui("on_evolution"), m));
        moves.getSpecialMoves().forEach(m -> move(data.ui("special_move"), m));
      }
      case 3 -> {
        var evolutions = data.evolutions(selected, form);
        if (!evolutions.available()) paragraph(data.ui("evolution.unavailable"));
        else if (evolutions.entries().isEmpty()) paragraph(data.ui("evolution.none"));
        for (var entry : evolutions.entries()) {
          var evolution = entry.getAsJsonObject();
          if (!lines.isEmpty()) paragraph("");
          int row = lines.size();
          paragraphX = 245;
          String result = WikiDetails.value(evolution, "result", "?");
          heading(data.name("properties", result));
          for (String condition : WikiDetails.evolution(evolution, data::name))
            paragraph("• " + condition);
          while (lines.size() - row < 4) paragraph("");
          try {
            var pokemon = PokemonProperties.Companion.parse(result).asRenderablePokemon();
            if (pokemon != null)
              evolutionPreviews.add(
                  new EvolutionPreview(
                      row,
                      lines.size(),
                      new ModelWidget(
                          0,
                          0,
                          (int) (46 * scale),
                          (int) (44 * scale),
                          pokemon,
                          1.0F * scale,
                          25F,
                          0,
                          false,
                          false,
                          15)));
          } catch (RuntimeException unavailableModel) {
            // Keep the destination and its conditions even if an extension has no model.
          }
          paragraphX = 195;
        }
      }
      case 4 -> {
        heading(data.ui("breeding.title"));
        paragraph(
            data.ui("egg_groups")
                + String.join(
                    ", ",
                    form.getEggGroups().stream()
                        .map(e -> eggGroup(e.getShowdownID()))
                        .sorted()
                        .toList()));
        paragraph(data.ui("egg_cycles") + selected.getEggCycles());
        float ratio = form.getMaleRatio();
        paragraph(
            ratio < 0
                ? data.ui("genderless")
                : data.ui("male_ratio")
                    + WikiDetails.number(ratio * 100)
                    + data.ui("female_ratio")
                    + WikiDetails.number((1 - ratio) * 100)
                    + " %");
        paragraph(data.ui("breeding.server"));
        paragraph("");
        if (form.getMoves().getEggMoves().isEmpty()) paragraph(data.ui("egg_move.none"));
        for (var move : form.getMoves().getEggMoves()) move(data.ui("egg_move"), move);
      }
      case 5 -> {
        heading(data.ui("drops.title"));
        paragraph(data.ui("drops.server"));
        paragraph("");
        if (form.getDrops() == null || form.getDrops().getEntries().isEmpty())
          paragraph(data.ui("drops.none"));
        else
          for (var drop : form.getDrops().getEntries()) {
            if (drop instanceof ItemDropEntry item) {
              heading(data.name("item", item.getItem().toString()));
              paragraph(
                  data.ui("quantity")
                      + (item.getQuantityRange() == null
                          ? item.getQuantity()
                          : item.getQuantityRange())
                      + data.ui("chance")
                      + WikiDetails.number(item.getPercentage())
                      + " %");
              paragraph("");
            } else paragraph(data.ui("drops.special") + drop.getClass().getSimpleName());
          }
      }
    }
    if (lines.isEmpty()) paragraph(data.ui("data.none"));
  }

  private String eggGroup(String id) {
    return switch (id.toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "")) {
      case "monster" -> data.ui("egg.monster");
      case "water1" -> data.ui("egg.water1");
      case "water2" -> data.ui("egg.water2");
      case "water3" -> data.ui("egg.water3");
      case "bug" -> data.ui("egg.bug");
      case "flying" -> data.ui("egg.flying");
      case "field" -> data.ui("egg.field");
      case "fairy" -> data.ui("egg.fairy");
      case "grass" -> data.ui("egg.grass");
      case "humanlike" -> data.ui("egg.humanlike");
      case "mineral" -> data.ui("egg.mineral");
      case "amorphous" -> data.ui("egg.amorphous");
      case "ditto" -> data.ui("egg.ditto");
      case "dragon" -> data.ui("egg.dragon");
      case "undiscovered" -> data.ui("egg.undiscovered");
      default -> id;
    };
  }

  private void move(String origin, MoveTemplate move) {
    int row = lines.size();
    moveRows.put(row, move);
    moveIcons.put(
        row, new TypeIcon(196, 0, move.getElementalType(), null, false, true, 0F, 0F, 1F));
    paragraphX = 213;
    heading(data.text(move.getDisplayName()) + " · " + origin);
    paragraphX = 195;
    paragraph(
        data.ui(
            "move.stats",
            data.text(move.getElementalType().getDisplayName()),
            move.getPower() <= 0 ? "—" : WikiDetails.number(move.getPower()),
            move.getAccuracy() <= 0 ? "—" : WikiDetails.number(move.getAccuracy()) + " %",
            move.getPp()));
    paragraph(data.text(move.getDescription()));
    paragraph("");
  }

  private PokedexEntryProgress knowledge(Species species) {
    var dex = CobblemonClient.INSTANCE.getClientPokedexData();
    return dex == null ? null : dex.getHighestKnowledgeForSpecies(species.getResourceIdentifier());
  }

  private String captureLabel(PokedexEntryProgress progress) {
    return data.ui(
        progress == null
            ? "capture.unknown"
            : progress == PokedexEntryProgress.OWNED
                ? "capture.owned"
                : progress == PokedexEntryProgress.SEEN ? "capture.seen" : "capture.unseen");
  }

  private void captureIcon(DrawContext c, int x, int y, PokedexEntryProgress progress) {
    boolean caught = progress == PokedexEntryProgress.OWNED;
    int color = caught ? 0xFFE66668 : progress == PokedexEntryProgress.SEEN ? MUTED : 0xFF65887B;
    c.fill(x + 2, y, x + 8, y + 10, INK);
    c.fill(x, y + 2, x + 10, y + 8, INK);
    c.fill(x + 2, y + 1, x + 8, y + 4, color);
    c.fill(x + 1, y + 2, x + 9, y + 4, color);
    c.fill(x + 1, y + 6, x + 9, y + 8, caught ? WHITE : PANEL);
    c.fill(x + 2, y + 8, x + 8, y + 9, caught ? WHITE : PANEL);
    c.fill(x + 4, y + 4, x + 6, y + 6, WHITE);
  }

  private void clip(DrawContext c, int x, int y, int w, int h) {
    c.enableScissor(
        left + (int) (x * scale),
        top + (int) (y * scale),
        left + (int) ((x + w) * scale),
        top + (int) ((y + h) * scale));
  }

  @Override
  public void render(DrawContext c, int mouseX, int mouseY, float delta) {
    int mx = localX(mouseX), my = localY(mouseY);
    begin(c, ACCENT, "Tropimon Wiki", data.ui("subtitle"));
    boolean closeHovered = hit(mx, my, 544, 20, 20, 21);
    c.fill(544, 20, 564, 41, closeHovered ? 0xFF693B40 : PANEL);
    label(c, "×", 551, 27, closeHovered ? 0xFFFF777A : WHITE);
    c.fill(27, 54, 174, 349, INK);
    c.fill(30, 63, 170, 84, search.isFocused() ? ACCENT : 0xFF6FA88C);
    c.fill(31, 64, 169, 83, PANEL);
    search.render(c, mx, my, delta);
    chip(c, "‹", 30, 88, 20, false, hit(mx, my, 30, 88, 20, 21), ACCENT);
    String generationLabel =
        generation == 0 ? data.ui("generation.all") : data.ui("generation.number", generation);
    label(c, generationLabel, 54, 95, MUTED);
    chip(c, "›", 148, 88, 20, false, hit(mx, my, 148, 88, 20, 21), ACCENT);
    label(c, filtered.size() + " Pokémon", 34, 113, MUTED);
    for (int i = 0; i < LIST_ROWS && listOffset + i < filtered.size(); i++) {
      var species = filtered.get(listOffset + i);
      int y = 130 + i * 16;
      boolean active = species == selected;
      boolean hovered = hit(mx, my, 30, y - 3, 137, 16);
      c.fill(30, y - 3, 167, y + 13, active ? 0xFFB9E4C3 : hovered ? 0xFF397C6C : PANEL);
      if (active) c.fill(30, y - 3, 33, y + 13, ACCENT);
      label(
          c,
          String.format(Locale.ROOT, "%04d", species.getNationalPokedexNumber()),
          36,
          y,
          active ? 0xFF366952 : MUTED);
      label(c, textRenderer.trimToWidth(data.species(species), 83), 65, y, active ? INK : WHITE);
      captureIcon(c, 154, y - 1, knowledge(species));
    }
    scrollbar(c, 169, 127, LIST_ROWS * 16, listOffset, filtered.size(), LIST_ROWS);
    label(c, data.ui("scroll"), 34, 335, MUTED);
    if (selected == null) {
      text(
          c,
          all.isEmpty() ? data.ui("world.required") : data.ui("search.empty"),
          198,
          110,
          350,
          WHITE);
    } else {
      c.fill(187, 54, 565, 78, INK);
      label(c, textRenderer.trimToWidth(data.species(selected), 295), 197, 62, WHITE);
      label(c, "#" + selected.getNationalPokedexNumber(), 531, 62, MUTED);
      c.fill(187, 81, 292, 169, 0xFF2C7D78);
      c.fill(190, 84, 289, 166, 0xFF62B9B1);
      c.drawTexture(POKE_BALL, 205, 85, 69, 69, 0, 0, 109, 109, 109, 1744);
      c.drawTexture(PLATFORM, 197, 145, 85, 22, 0, 0, 113, 30, 113, 30);
      int typeX = 304;
      for (var type : form.getTypes()) {
        c.fill(typeX, 84, typeX + 128, 105, INK);
        c.fill(typeX, 103, typeX + 128, 105, 0xFF000000 | type.getPrimaryColor());
        label(
            c,
            textRenderer.trimToWidth(data.text(type.getDisplayName()), 99),
            typeX + 26,
            91,
            WHITE);
        typeX += 133;
      }
      typeIcons.forEach(icon -> icon.render(c));
      label(c, data.ui("abilities"), 304, 112, MUTED);

      for (int i = 0; i < Math.min(3, abilities.size()); i++) {
        var ability = abilities.get(i);
        int y = 125 + i * 22;
        c.fill(304, y, 565, y + 20, hit(mx, my, 304, y, 261, 20) ? 0xFF397C6C : PANEL);
        label(
            c,
            textRenderer.trimToWidth(ability.name(), 246),
            311,
            y + 6,
            ability.hidden() ? ACCENT : 0xFFB9E4C3);
      }
      if (abilities.isEmpty()) label(c, data.ui("missing"), 311, 133, MUTED);
      boolean multiple = forms.size() > 1;
      int formX = multiple ? 211 : 187, formWidth = multiple ? 57 : 105;
      c.fill(formX, 173, formX + formWidth, 194, PANEL);
      String formName =
          textRenderer.trimToWidth(
              form == selected.getStandardForm() ? data.ui("standard") : form.getName(),
              formWidth - 8);
      label(c, formName, formX + (formWidth - textRenderer.getWidth(formName)) / 2, 180, WHITE);
      if (multiple) {
        chip(c, "‹", 187, 173, 21, false, hit(mx, my, 187, 173, 21, 21), ACCENT);
        chip(c, "›", 271, 173, 21, false, hit(mx, my, 271, 173, 21, 21), ACCENT);
      }
      for (int i = 0; i < TABS.length; i++)
        chip(
            c,
            data.ui("tab." + TABS[i]),
            DETAIL_X + i * 63,
            TAB_Y,
            61,
            i == tab,
            hit(mx, my, DETAIL_X + i * 63, TAB_Y, 61, 21),
            ACCENT);
      c.fill(187, 224, 565, 349, 0xFFE2F1E5);
      for (int i = 0; i < DETAIL_ROWS && detailOffset + i < lines.size(); i++) {
        int index = detailOffset + i, y = 230 + i * 12;
        if (headings.contains(index)) c.fill(192, y - 2, 558, y + 10, 0xFFBBDDC8);
        if (statBars.containsKey(index)) {
          int value = statBars.get(index);
          c.fill(340, y + 1, 524, y + 8, 0xFFB6D5C2);
          c.fill(340, y + 1, 340 + Math.clamp(value * 184 / 255, 0, 184), y + 8, 0xFF398971);
          label(c, Integer.toString(value), 537, y, INK);
        }
        int textColor = INK;
        if (moveRows.containsKey(index)) {
          var move = moveRows.get(index);
          int color = 0xFF000000 | move.getElementalType().getPrimaryColor();
          c.fill(192, y - 2, 558, y + 10, INK);
          c.fill(192, y - 2, 194, y + 10, color);
          c.getMatrices().push();
          c.getMatrices().translate(0, y - 1, 0);
          moveIcons.get(index).render(c);
          c.getMatrices().pop();
          // Preserve type hue while keeping even dark types legible on the teal header.
          int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
          textColor =
              0xFF000000 | ((r * 3 / 5 + 102) << 16) | ((g * 3 / 5 + 102) << 8) | (b * 3 / 5 + 102);
        }
        label(c, lines.get(index), indents.getOrDefault(index, 195), y, textColor);
      }
      scrollbar(c, 560, 229, 114, detailOffset, lines.size(), DETAIL_ROWS);
      if (lines.size() > DETAIL_ROWS)
        label(
            c,
            data.ui("scroll.detail")
                + (detailOffset + 1)
                + "–"
                + Math.min(detailOffset + DETAIL_ROWS, lines.size())
                + " / "
                + lines.size(),
            220,
            355,
            MUTED);
    }
    end(c);
    if (portrait != null) {
      clip(c, 190, 84, 99, 82);
      portrait.render(c, mouseX, mouseY, delta);
      c.disableScissor();
    }
    for (var preview : evolutionPreviews) {
      if (preview.row() + 4 <= detailOffset || preview.row() >= detailOffset + DETAIL_ROWS)
        continue;
      var model = preview.model();
      model.setX(left + (int) (196 * scale));
      model.setY(top + (int) ((228 + (preview.row() - detailOffset) * 12) * scale));
      clip(c, 192, 224, 49, 125);
      model.render(c, mouseX, mouseY, delta);
      c.disableScissor();
    }
    List<Text> tooltip = new ArrayList<>();
    for (int i = 0; i < Math.min(3, abilities.size()); i++) {
      if (hit(mx, my, 304, 125 + i * 22, 261, 20)) {
        var ability = abilities.get(i);
        tooltip.add(Text.literal(ability.name()).withColor(ability.hidden() ? ACCENT : 0xFFB9E4C3));
        tooltip.add(Text.literal(ability.description()));
      }
    }
    for (int i = 0; i < LIST_ROWS && listOffset + i < filtered.size(); i++)
      if (hit(mx, my, 151, 127 + i * 16, 16, 16))
        tooltip.add(Text.literal(captureLabel(knowledge(filtered.get(listOffset + i)))));
    if (!tooltip.isEmpty()) {
      List<net.minecraft.text.OrderedText> wrapped = new ArrayList<>();
      for (Text text : tooltip)
        wrapped.addAll(textRenderer.wrapLines(text, Math.min(260, width - 24)));
      c.drawOrderedTooltip(textRenderer, wrapped, mouseX, mouseY);
    }
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    int mx = localX(x), my = localY(y);
    if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(x, y, button);
    if (hit(mx, my, 544, 20, 20, 21)) {
      close();
      return true;
    }
    search.setFocused(hit(mx, my, 30, 63, 140, 21));
    if (search.isFocused()) {
      search.mouseClicked(mx, my, button);
      return true;
    }
    if (hit(mx, my, 30, 88, 20, 21) || hit(mx, my, 148, 88, 20, 21)) {
      generation = Math.floorMod(generation + (mx < 50 ? -1 : 1), 10);
      filter(search.getText());
      return true;
    }
    if (hit(mx, my, 30, 127, 137, LIST_ROWS * 16)) {
      int index = listOffset + (my - 127) / 16;
      if (index < filtered.size()) select(filtered.get(index));
      return true;
    }
    if (selected != null
        && forms.size() > 1
        && (hit(mx, my, 187, 173, 21, 21) || hit(mx, my, 271, 173, 21, 21))) {
      formIndex = Math.floorMod(formIndex + (mx < 211 ? -1 : 1), forms.size());
      form = forms.get(formIndex);
      updatePortrait();
      rebuild();
      return true;
    }
    if (selected != null) {
      if (hit(mx, my, DETAIL_X, TAB_Y, 378, 21) && (mx - DETAIL_X) % 63 < 61) {
        tab = Math.min(5, (mx - DETAIL_X) / 63);
        rebuild();
        return true;
      }
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (vertical == 0) return false;
    int mx = localX(x), my = localY(y), change = vertical > 0 ? -3 : 3;
    if (hit(mx, my, 27, 54, 147, 295))
      listOffset = Math.clamp(listOffset + change, 0, Math.max(0, filtered.size() - LIST_ROWS));
    else if (hit(mx, my, 187, 224, 378, 125))
      detailOffset = Math.clamp(detailOffset + change, 0, Math.max(0, lines.size() - DETAIL_ROWS));
    else return false;
    return true;
  }

  @Override
  public boolean keyPressed(int key, int scan, int mods) {
    if (key == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scan, mods);
    if (search.isFocused() && search.keyPressed(key, scan, mods)) return true;
    if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
      int i =
          Math.clamp(
              filtered.indexOf(selected) + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1),
              0,
              Math.max(0, filtered.size() - 1));
      if (!filtered.isEmpty()) {
        select(filtered.get(i));
        listOffset = Math.clamp(i - 5, 0, Math.max(0, filtered.size() - LIST_ROWS));
      }
      return true;
    }
    return super.keyPressed(key, scan, mods);
  }

  @Override
  public boolean charTyped(char ch, int mods) {
    return search.charTyped(ch, mods) || super.charTyped(ch, mods);
  }
}
