package fr.tropimon.wiki;

import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.client.gui.TypeIcon;
import com.cobblemon.mod.common.pokemon.*;
import com.google.gson.Gson;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Pokédex-style reference: live Cobblemon registry, never battle recommendations. */
public final class WikiScreen extends InstrumentScreen {
  private static final int ACCENT = 0xFFE9DE8C;
  private static final Identifier POKE_BALL =
      Identifier.of("cobblemon", "textures/gui/pokedex/pokedex_screen_poke_ball.png");
  private static final Identifier PLATFORM =
      Identifier.of("cobblemon", "textures/gui/pokedex/platform_base.png");
  private static final String[] TABS = {
    "Fiche", "Talents", "Attaques", "Évolution", "Élevage", "Butin"
  };
  private final List<Species> all;
  private List<Species> filtered = List.of();
  private Species selected;
  private FormData form;
  private int listOffset, detailOffset, tab, formIndex;
  private TextFieldWidget search;
  private ModelWidget portrait;
  private final List<TypeIcon> typeIcons = new ArrayList<>();
  private final List<FormData> forms = new ArrayList<>();
  private final List<String> lines = new ArrayList<>();
  private final Map<Integer, Integer> statBars = new HashMap<>();

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
    search =
        new TextFieldWidget(textRenderer, 34, 68, 139, 12, Text.literal("Rechercher un Pokémon"));
    search.setDrawsBackground(false);
    search.setEditableColor(WHITE);
    search.setMaxLength(80);
    search.setPlaceholder(Text.literal("Nom ou numéro…"));
    search.setChangedListener(this::filter);
    search.setText(old);
    filter(old);
    updatePortrait();
  }

  private void filter(String query) {
    filtered =
        all.stream()
            .filter(
                s ->
                    WikiSearch.matches(
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
    detailOffset = 0;
    updatePortrait();
    rebuild();
  }

  private void updatePortrait() {
    typeIcons.clear();
    if (form != null) {
      int y = 94;
      for (var type : form.getTypes()) {
        typeIcons.add(new TypeIcon(328, y, type, null, false, true, 0F, 0F, 1F));
        y += 25;
      }
    }
    portrait =
        selected == null
            ? null
            : new ModelWidget(
                left + (int) (205 * scale),
                top + (int) (83 * scale),
                (int) (107 * scale),
                (int) (106 * scale),
                new RenderablePokemon(selected, new HashSet<>(form.getAspects()), ItemStack.EMPTY),
                2.0F * scale,
                25F,
                4 * scale,
                false,
                false,
                15);
  }

  private static String translated(String key) {
    return Language.getInstance().get(key);
  }

  private void paragraph(String value) {
    // Split with TextRenderer once when selection changes, never every frame.
    for (var ordered : textRenderer.wrapLines(Text.literal(value), 311)) {
      StringBuilder s = new StringBuilder();
      ordered.accept(
          (i, style, cp) -> {
            s.appendCodePoint(cp);
            return true;
          });
      lines.add(s.toString());
    }
  }

  private void rebuild() {
    lines.clear();
    statBars.clear();
    detailOffset = 0;
    if (selected == null || textRenderer == null) return;
    switch (tab) {
      case 0 -> {
        for (String key : form.getPokedex()) paragraph(translated(key));
        paragraph(
            "Taille : "
                + String.format(Locale.ROOT, "%.1f", form.getHeight() / 10F)
                + " m    Poids : "
                + String.format(Locale.ROOT, "%.1f", form.getWeight() / 10F)
                + " kg");
        paragraph(
            "Capture : "
                + form.getCatchRate()
                + "    Amitié initiale : "
                + form.getBaseFriendship());
        form.getBaseStats().entrySet().stream()
            .sorted(Comparator.comparing(e -> e.getKey().getShowdownId()))
            .forEach(e -> {
              statBars.put(lines.size(), e.getValue());
              paragraph(e.getKey().getDisplayName().getString());
            });
        paragraph(
            "Rendement EV : "
                + form.getEvYield().entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(e -> e.getKey().getDisplayName().getString() + " +" + e.getValue())
                    .toList());
      }
      case 1 -> {
        for (var ability : form.getAbilities()) {
          var a = ability.getTemplate();
          paragraph(translated(a.getDisplayName()) + " · " + ability.getPriority());
          paragraph(translated(a.getDescription()));
          paragraph("");
        }
      }
      case 2 -> {
        var moves = form.getMoves();
        moves.getLevelUpMoves().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> e.getValue().forEach(m -> move("Niv. " + e.getKey(), m)));
        moves.getTmMoves().forEach(m -> move("CT", m));
        moves.getEggMoves().forEach(m -> move("Œuf", m));
        moves.getTutorMoves().forEach(m -> move("Tuteur", m));
        moves.getEvolutionMoves().forEach(m -> move("Évolution", m));
        moves.getSpecialMoves().forEach(m -> move("Spécial", m));
        paragraph(
            "Les sources d'apprentissage sont celles du registre chargé. Aucun conseil de combat"
                + " n'est ajouté.");
      }
      case 3 -> {
        if (form.getEvolutions().isEmpty())
          paragraph("Aucune évolution renseignée pour cette forme.");
        for (var evolution : form.getEvolutions()) {
          paragraph("→ " + evolution.getResult().asString(" "));
          for (var requirement : evolution.getRequirements()) {
            String raw;
            try {
              raw = new Gson().toJson(requirement);
            } catch (RuntimeException ex) {
              raw = "Détail indisponible";
            }
            paragraph(
                requirement.getClass().getSimpleName().replace("Requirement", "") + " : " + raw);
          }
          paragraph("Objet tenu consommé : " + (evolution.getConsumeHeldItem() ? "oui" : "non"));
          paragraph("");
        }
        paragraph("Les conditions avancées sont conservées telles que définies par Cobblemon.");
      }
      case 4 -> {
        paragraph(
            "Groupes d'œufs : "
                + form.getEggGroups().stream().map(e -> e.getShowdownID()).sorted().toList());
        paragraph("Cycles d'œuf : " + selected.getEggCycles());
        float ratio = form.getMaleRatio();
        paragraph(
            ratio < 0
                ? "Sans sexe"
                : String.format(
                    Locale.ROOT,
                    "Mâles : %.1f %%   Femelles : %.1f %%",
                    ratio * 100,
                    (1 - ratio) * 100));
        paragraph("Attaques transmissibles :");
        for (var m : form.getMoves().getEggMoves()) move("Œuf", m);
        paragraph(
            "Ces données décrivent l'espèce ; elles ne confirment pas l'activation d'un système"
                + " d'élevage serveur.");
      }
      case 5 -> {
        if (form.getDrops() == null || form.getDrops().getEntries().isEmpty())
          paragraph("Aucun butin renseigné.");
        else
          for (var drop : form.getDrops().getEntries()) {
            if (drop instanceof ItemDropEntry item) {
              paragraph(Registries.ITEM.get(item.getItem()).getName().getString());
              paragraph(
                  "Quantité : "
                      + (item.getQuantityRange() == null
                          ? item.getQuantity()
                          : item.getQuantityRange())
                      + " · valeur de pourcentage : "
                      + item.getPercentage());
            } else paragraph("Entrée spéciale : " + drop.getClass().getSimpleName());
          }
        paragraph(
            "Données du registre Cobblemon chargé. Les règles de butin du serveur peuvent"
                + " différer.");
      }
    }
    if (lines.isEmpty()) paragraph("Aucune donnée disponible.");
  }

  private void move(String origin, MoveTemplate m) {
    paragraph(
        origin
            + " · "
            + m.getDisplayName().getString()
            + " / "
            + m.getElementalType().getDisplayName().getString());
    paragraph(
        "Puissance " + m.getPower() + " · Précision " + m.getAccuracy() + " · PP " + m.getPp());
    paragraph(m.getDescription().getString());
    paragraph("");
  }

  @Override
  public void render(DrawContext c, int mouseX, int mouseY, float delta) {
    int mx = localX(mouseX), my = localY(mouseY);
    begin(c, ACCENT, "TROPIMON  /  POKÉDEX", "Wiki · données Cobblemon");
    chip(c, "×", 512, 20, 20, false, hit(mx, my, 512, 20, 20, 21), ACCENT);
    c.fill(27, 54, 186, 309, INK);
    c.fill(30, 63, 181, 84, search.isFocused() ? ACCENT : 0xFF6FA88C);
    c.fill(31, 64, 180, 83, PANEL);
    search.render(c, mx, my, delta);
    label(c, filtered.size() + " Pokémon", 34, 91, MUTED);
    for (int i = 0; i < 11 && listOffset + i < filtered.size(); i++) {
      var s = filtered.get(listOffset + i);
      int y = 108 + i * 17;
      boolean active = s == selected;
      boolean hovered = hit(mx, my, 30, y - 3, 149, 16);
      c.fill(30, y - 3, 179, y + 13, active ? 0xFFB9E4C3 : hovered ? 0xFF397C6C : PANEL);
      if (active) c.fill(30, y - 3, 33, y + 13, ACCENT);
      label(c, String.format(Locale.ROOT, "%04d", s.getNationalPokedexNumber()), 36, y,
          active ? 0xFF366952 : MUTED);
      label(c, textRenderer.trimToWidth(s.getTranslatedName().getString(), 108), 65, y,
          active ? INK : WHITE);
    }
    scrollbar(c, 181, 105, 187, listOffset, filtered.size(), 11);
    label(c, "↑ ↓  ·  Molette", 34, 297, MUTED);
    if (selected == null) {
      text(c, all.isEmpty() ? "Entre dans un monde pour charger les espèces." : "Aucun résultat.",
          209, 113, 310, WHITE);
    } else {
      c.fill(199, 54, 533, 78, INK);
      label(c, textRenderer.trimToWidth(selected.getTranslatedName().getString(), 260), 209, 62, WHITE);
      label(c, "#" + selected.getNationalPokedexNumber(), 490, 62, MUTED);
      c.fill(199, 81, 315, 190, 0xFF2C7D78);
      c.fill(202, 84, 312, 187, 0xFF62B9B1);
      c.drawTexture(POKE_BALL, 216, 87, 84, 84, 0, 0, 109, 109, 109, 1744);
      c.drawTexture(PLATFORM, 211, 161, 94, 25, 0, 0, 113, 30, 113, 30);
      int typeY = 91;
      for (var type : form.getTypes()) {
        c.fill(323, typeY, 441, typeY + 21, INK);
        c.fill(323, typeY + 19, 441, typeY + 21, 0xFF000000 | type.getPrimaryColor());
        label(c, type.getDisplayName().getString(), 348, typeY + 7, WHITE);
        typeY += 25;
      }
      typeIcons.forEach(icon -> icon.render(c));
      label(c, "FORME  " + (formIndex + 1) + " / " + forms.size(), 323, 148, WHITE);
      chip(c, "‹", 323, 163, 21, false, hit(mx, my, 323, 163, 21, 21), ACCENT);
      chip(c, form == selected.getStandardForm() ? "Standard" : form.getName(),
          347, 163, 151, false, hit(mx, my, 347, 163, 151, 21), ACCENT);
      chip(c, "›", 501, 163, 22, false, hit(mx, my, 501, 163, 22, 21), ACCENT);
      for (int i = 0; i < TABS.length; i++)
        chip(c, TABS[i], 199 + i * 56, 195, 54, i == tab,
            hit(mx, my, 199 + i * 56, 195, 54, 21), ACCENT);
      c.fill(199, 219, 533, 309, 0xFFD9EEE0);
      c.fill(199, 219, 533, 221, 0xFF8CC8AF);
      for (int i = 0; i < 7 && detailOffset + i < lines.size(); i++) {
        int index = detailOffset + i;
        int y = 225 + i * 12;
        if (statBars.containsKey(index)) {
          int value = statBars.get(index);
          c.fill(357, y + 1, 485, y + 8, 0xFFB6D5C2);
          c.fill(357, y + 1, 357 + Math.clamp(value * 128 / 255, 0, 128), y + 8, 0xFF398971);
          label(c, Integer.toString(value), 495, y, INK);
        }
        label(c, lines.get(index), 207, y, INK);
      }
      scrollbar(c, 528, 224, 79, detailOffset, lines.size(), 7);
      label(c, (detailOffset + 1) + " / " + Math.max(1, lines.size()) + " · molette dans la fiche",
          209, 311, MUTED);
    }
    end(c);
    // ModelWidget scissors use screen coordinates rather than the outer UI transform.
    if (portrait != null) portrait.render(c, mouseX, mouseY, delta);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    int mx = localX(x), my = localY(y);
    if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(x, y, button);
    if (hit(mx, my, 512, 20, 20, 21)) {
      close();
      return true;
    }
    search.setFocused(hit(mx, my, 30, 63, 151, 21));
    if (search.isFocused()) {
      search.mouseClicked(mx, my, button);
      return true;
    }
    if (hit(mx, my, 30, 105, 149, 187)) {
      int i = listOffset + (my - 105) / 17;
      if (i < filtered.size()) select(filtered.get(i));
      return true;
    }
    if (selected != null && hit(mx, my, 323, 163, 200, 21)) {
      formIndex = Math.floorMod(formIndex + (mx < 344 ? -1 : 1), forms.size());
      form = forms.get(formIndex);
      updatePortrait();
      rebuild();
      return true;
    }
    if (selected != null && hit(mx, my, 199, 195, 334, 21)) {
      tab = Math.min(5, (mx - 199) / 56);
      rebuild();
      return true;
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (vertical == 0) return false;
    int mx = localX(x), my = localY(y);
    int change = vertical > 0 ? -3 : 3;
    if (hit(mx, my, 27, 54, 159, 255))
      listOffset = Math.clamp(listOffset + change, 0, Math.max(0, filtered.size() - 11));
    else if (hit(mx, my, 199, 219, 334, 90))
      detailOffset = Math.clamp(detailOffset + change, 0, Math.max(0, lines.size() - 7));
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
        listOffset = Math.clamp(i - 5, 0, Math.max(0, filtered.size() - 11));
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
