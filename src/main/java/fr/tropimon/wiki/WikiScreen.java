package fr.tropimon.wiki;

import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
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
  static final int LIST_ROWS = 13, DETAIL_ROWS = 10, DETAIL_X = 187, TAB_Y = 198;
  private static final Identifier POKE_BALL =
      Identifier.of("cobblemon", "textures/gui/pokedex/pokedex_screen_poke_ball.png");
  private static final Identifier PLATFORM =
      Identifier.of("cobblemon", "textures/gui/pokedex/platform_base.png");
  private static final String[] TABS = {
    "Profil", "Stats", "Talents", "Attaques", "Évoluer", "Élevage", "Butin"
  };
  private final List<Species> all;
  private final WikiData data = new WikiData();
  private List<Species> filtered = List.of();
  private Species selected;
  private FormData form;
  private int listOffset, detailOffset, tab, formIndex;
  private TextFieldWidget search;
  private ModelWidget portrait;
  private List<WikiDetails.Ability> abilities = List.of();
  private final List<TypeIcon> typeIcons = new ArrayList<>();
  private final List<FormData> forms = new ArrayList<>();
  private final List<String> lines = new ArrayList<>();
  private final Map<Integer, Integer> statBars = new HashMap<>();
  private final Set<Integer> headings = new HashSet<>();
  private final List<Integer> abilityOffsets = new ArrayList<>();

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
    int previousListOffset = listOffset;
    boolean wasFocused = search != null && search.isFocused();
    search =
        new TextFieldWidget(textRenderer, 34, 68, 128, 12, Text.literal("Rechercher un Pokémon"));
    search.setDrawsBackground(false);
    search.setEditableColor(WHITE);
    search.setMaxLength(80);
    search.setPlaceholder(Text.literal("Nom ou numéro…"));
    search.setChangedListener(this::filter);
    search.setText(old);
    filter(old);
    listOffset = Math.clamp(previousListOffset, 0, Math.max(0, filtered.size() - LIST_ROWS));
    search.setFocused(wasFocused);
    updatePortrait();
  }

  private void filter(String query) {
    filtered =
        all.stream()
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
        x += 137;
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
    for (var ordered : textRenderer.wrapLines(Text.literal(value), 364)) {
      StringBuilder s = new StringBuilder();
      ordered.accept(
          (i, style, cp) -> {
            s.appendCodePoint(cp);
            return true;
          });
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
    abilityOffsets.clear();
    detailOffset = 0;
    if (selected == null || textRenderer == null) return;
    switch (tab) {
      case 0 -> {
        heading("À PROPOS DE " + data.species(selected));
        for (String key : form.getPokedex()) paragraph(data.tr(key));
        paragraph("");
        paragraph(
            "Taille : "
                + WikiDetails.number(form.getHeight() / 10F)
                + " m     Poids : "
                + WikiDetails.number(form.getWeight() / 10F)
                + " kg");
        paragraph("Taux de capture : " + form.getCatchRate());
        paragraph("Amitié de base : " + form.getBaseFriendship());
        paragraph("Statistiques et EV : onglet Stats.");
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
        heading("TOTAL : " + total);
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
        paragraph("EV gagnés : " + (evs.isEmpty() ? "aucun" : evs));
      }
      case 2 -> {
        for (var ability : abilities) {
          abilityOffsets.add(lines.size());
          heading(
              ability.name() + (ability.hidden() ? " — HA / Talent caché" : " — Talent normal"));
          paragraph(ability.description());
          paragraph("");
        }
        if (abilities.stream().noneMatch(WikiDetails.Ability::hidden))
          paragraph("Aucun talent caché distinct pour cette forme.");
      }
      case 3 -> {
        var moves = form.getMoves();
        moves.getLevelUpMoves().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> e.getValue().forEach(m -> move("Niveau " + e.getKey(), m)));
        moves.getTmMoves().forEach(m -> move("CT", m));
        moves.getEggMoves().forEach(m -> move("Reproduction", m));
        moves.getTutorMoves().forEach(m -> move("Tuteur", m));
        moves.getEvolutionMoves().forEach(m -> move("À l'évolution", m));
        moves.getSpecialMoves().forEach(m -> move("Apprentissage spécial", m));
      }
      case 4 -> {
        var evolutions = data.evolutions(selected, form);
        heading(
            evolutions.local()
                ? "RÉFÉRENCE : COBBLEMON INSTALLÉ"
                : "ÉVOLUTIONS DU REGISTRE CHARGÉ");
        if (evolutions.local()) paragraph("Le serveur peut modifier ces conditions.");
        if (!evolutions.available())
          paragraph("Cette forme n'a pas de référence locale disponible.");
        else if (evolutions.entries().isEmpty())
          paragraph("Aucune évolution définie pour cette forme dans cette référence.");
        for (var entry : evolutions.entries()) {
          var evolution = entry.getAsJsonObject();
          paragraph("");
          heading(
              "→ "
                  + data.name(
                      "properties",
                      WikiDetails.value(evolution, "result", "Destination inconnue")));
          for (String condition : WikiDetails.evolution(evolution, data::name))
            paragraph("• " + condition);
        }
      }
      case 5 -> {
        heading("REPRODUCTION");
        paragraph(
            "Groupes d'œufs : "
                + String.join(
                    ", ",
                    form.getEggGroups().stream()
                        .map(e -> eggGroup(e.getShowdownID()))
                        .sorted()
                        .toList()));
        paragraph("Cycles d'œuf : " + selected.getEggCycles());
        float ratio = form.getMaleRatio();
        paragraph(
            ratio < 0
                ? "Sans sexe"
                : "Mâles : "
                    + WikiDetails.number(ratio * 100)
                    + " %     Femelles : "
                    + WikiDetails.number((1 - ratio) * 100)
                    + " %");
        paragraph("L'élevage dépend des fonctionnalités du serveur.");
        paragraph("");
        if (form.getMoves().getEggMoves().isEmpty()) paragraph("Aucune attaque d'œuf renseignée.");
        for (var move : form.getMoves().getEggMoves()) move("Attaque d'œuf", move);
      }
      case 6 -> {
        heading("BUTIN POSSIBLE");
        paragraph("Les règles du serveur peuvent différer.");
        paragraph("");
        if (form.getDrops() == null || form.getDrops().getEntries().isEmpty())
          paragraph("Aucun butin renseigné.");
        else
          for (var drop : form.getDrops().getEntries()) {
            if (drop instanceof ItemDropEntry item) {
              heading(data.name("item", item.getItem().toString()));
              paragraph(
                  "Quantité : "
                      + (item.getQuantityRange() == null
                          ? item.getQuantity()
                          : item.getQuantityRange())
                      + "     Chance : "
                      + WikiDetails.number(item.getPercentage())
                      + " %");
              paragraph("");
            } else paragraph("Butin spécial : " + drop.getClass().getSimpleName());
          }
      }
    }
    if (lines.isEmpty()) paragraph("Aucune donnée disponible.");
  }

  private static String eggGroup(String id) {
    return switch (id) {
      case "monster" -> "Monstrueux";
      case "water1" -> "Aquatique 1";
      case "water2" -> "Aquatique 2";
      case "water3" -> "Aquatique 3";
      case "bug" -> "Insectoïde";
      case "flying" -> "Aérien";
      case "field" -> "Terrestre";
      case "fairy" -> "Féerique";
      case "grass" -> "Végétal";
      case "humanlike" -> "Humanoïde";
      case "mineral" -> "Minéral";
      case "amorphous" -> "Amorphe";
      case "ditto" -> "Métamorph";
      case "dragon" -> "Draconique";
      case "undiscovered" -> "Inconnu";
      default -> id;
    };
  }

  private void move(String origin, MoveTemplate move) {
    heading(data.text(move.getDisplayName()) + " · " + origin);
    paragraph(
        "Type "
            + data.text(move.getElementalType().getDisplayName())
            + "   Puiss. "
            + (move.getPower() <= 0 ? "—" : WikiDetails.number(move.getPower()))
            + "   Préc. "
            + WikiDetails.number(move.getAccuracy())
            + " %   PP "
            + move.getPp());
    paragraph(data.text(move.getDescription()));
    paragraph("");
  }

  @Override
  public void render(DrawContext c, int mouseX, int mouseY, float delta) {
    int mx = localX(mouseX), my = localY(mouseY);
    begin(c, ACCENT, "Tropimon Wiki", "Pokémon · Talents · Évolutions");
    chip(c, "×", 552, 20, 20, false, hit(mx, my, 552, 20, 20, 21), ACCENT);
    c.fill(27, 54, 174, 349, INK);
    c.fill(30, 63, 170, 84, search.isFocused() ? ACCENT : 0xFF6FA88C);
    c.fill(31, 64, 169, 83, PANEL);
    search.render(c, mx, my, delta);
    label(c, filtered.size() + " Pokémon", 34, 91, MUTED);
    for (int i = 0; i < LIST_ROWS && listOffset + i < filtered.size(); i++) {
      var species = filtered.get(listOffset + i);
      int y = 108 + i * 17;
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
      label(c, textRenderer.trimToWidth(data.species(species), 96), 65, y, active ? INK : WHITE);
    }
    scrollbar(c, 169, 105, LIST_ROWS * 17, listOffset, filtered.size(), LIST_ROWS);
    label(c, "↑ ↓  ·  Molette", 34, 335, MUTED);
    if (selected == null) {
      text(
          c,
          all.isEmpty() ? "Entre dans un monde pour charger les espèces." : "Aucun Pokémon trouvé.",
          198,
          110,
          350,
          WHITE);
    } else {
      c.fill(187, 54, 573, 78, INK);
      label(c, textRenderer.trimToWidth(data.species(selected), 295), 197, 62, WHITE);
      label(c, "#" + selected.getNationalPokedexNumber(), 531, 62, MUTED);
      c.fill(187, 81, 292, 169, 0xFF2C7D78);
      c.fill(190, 84, 289, 166, 0xFF62B9B1);
      c.drawTexture(POKE_BALL, 205, 85, 69, 69, 0, 0, 109, 109, 109, 1744);
      c.drawTexture(PLATFORM, 197, 145, 85, 22, 0, 0, 113, 30, 113, 30);
      int typeX = 304;
      for (var type : form.getTypes()) {
        c.fill(typeX, 84, typeX + 132, 105, INK);
        c.fill(typeX, 103, typeX + 132, 105, 0xFF000000 | type.getPrimaryColor());
        label(
            c,
            textRenderer.trimToWidth(data.text(type.getDisplayName()), 99),
            typeX + 26,
            91,
            WHITE);
        typeX += 137;
      }
      typeIcons.forEach(icon -> icon.render(c));
      label(c, "TALENTS", 304, 112, MUTED);
      label(c, "Cliquer pour lire", 470, 112, MUTED);
      for (int i = 0; i < Math.min(3, abilities.size()); i++) {
        var ability = abilities.get(i);
        int y = 125 + i * 22;
        c.fill(304, y, 573, y + 20, hit(mx, my, 304, y, 269, 20) ? 0xFF397C6C : PANEL);
        label(c, textRenderer.trimToWidth(ability.name(), 175), 311, y + 6, WHITE);
        c.fill(495, y + 3, 569, y + 17, ability.hidden() ? ACCENT : INK);
        label(
            c,
            ability.hidden() ? "HA · Caché" : "Normal",
            501,
            y + 6,
            ability.hidden() ? INK : MUTED);
      }
      if (abilities.isEmpty()) label(c, "Non renseigné", 311, 133, MUTED);
      boolean multiple = forms.size() > 1;
      int formX = multiple ? 211 : 187, formWidth = multiple ? 57 : 105;
      c.fill(formX, 173, formX + formWidth, 194, PANEL);
      String formName =
          textRenderer.trimToWidth(
              form == selected.getStandardForm() ? "Standard" : form.getName(), formWidth - 8);
      label(c, formName, formX + (formWidth - textRenderer.getWidth(formName)) / 2, 180, WHITE);
      if (multiple) {
        chip(c, "‹", 187, 173, 21, false, hit(mx, my, 187, 173, 21, 21), ACCENT);
        chip(c, "›", 271, 173, 21, false, hit(mx, my, 271, 173, 21, 21), ACCENT);
      }
      for (int i = 0; i < TABS.length; i++)
        chip(
            c,
            TABS[i],
            DETAIL_X + i * 55,
            TAB_Y,
            53,
            i == tab,
            hit(mx, my, DETAIL_X + i * 55, TAB_Y, 53, 21),
            ACCENT);
      c.fill(187, 224, 573, 349, 0xFFE2F1E5);
      for (int i = 0; i < DETAIL_ROWS && detailOffset + i < lines.size(); i++) {
        int index = detailOffset + i, y = 230 + i * 12;
        if (headings.contains(index)) c.fill(192, y - 2, 566, y + 10, 0xFFBBDDC8);
        if (statBars.containsKey(index)) {
          int value = statBars.get(index);
          c.fill(340, y + 1, 524, y + 8, 0xFFB6D5C2);
          c.fill(340, y + 1, 340 + Math.clamp(value * 184 / 255, 0, 184), y + 8, 0xFF398971);
          label(c, Integer.toString(value), 537, y, INK);
        }
        label(c, lines.get(index), 195, y, INK);
      }
      scrollbar(c, 568, 229, 114, detailOffset, lines.size(), DETAIL_ROWS);
      if (lines.size() > DETAIL_ROWS)
        label(
            c,
            "Molette · "
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
    if (portrait != null) portrait.render(c, mouseX, mouseY, delta);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    int mx = localX(x), my = localY(y);
    if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(x, y, button);
    if (hit(mx, my, 552, 20, 20, 21)) {
      close();
      return true;
    }
    search.setFocused(hit(mx, my, 30, 63, 140, 21));
    if (search.isFocused()) {
      search.mouseClicked(mx, my, button);
      return true;
    }
    if (hit(mx, my, 30, 105, 137, LIST_ROWS * 17)) {
      int index = listOffset + (my - 105) / 17;
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
      for (int i = 0; i < Math.min(3, abilities.size()); i++)
        if (hit(mx, my, 304, 125 + i * 22, 269, 20)) {
          tab = 2;
          rebuild();
          detailOffset = Math.min(abilityOffsets.get(i), Math.max(0, lines.size() - DETAIL_ROWS));
          return true;
        }
      if (hit(mx, my, DETAIL_X, TAB_Y, 383, 21) && (mx - DETAIL_X) % 55 < 53) {
        tab = Math.min(6, (mx - DETAIL_X) / 55);
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
    else if (hit(mx, my, 187, 224, 386, 125))
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
