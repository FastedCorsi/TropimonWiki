package fr.tropimon.wiki;

import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.pokemon.*;
import com.google.gson.Gson;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.lwjgl.glfw.GLFW;

/** Pokédex-style reference: live Cobblemon registry, never battle recommendations. */
public final class WikiScreen extends InstrumentScreen {
  private static final int ACCENT = 0xFFF47777;
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
  private final List<String> lines = new ArrayList<>();

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
        new TextFieldWidget(textRenderer, 23, 63, 151, 18, Text.literal("Rechercher un Pokémon"));
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
    form = species == null ? null : species.getStandardForm();
    detailOffset = 0;
    updatePortrait();
    rebuild();
  }

  private void updatePortrait() {
    portrait =
        selected == null
            ? null
            : new ModelWidget(
                left + (int) (209 * scale),
                top + (int) (87 * scale),
                (int) (100 * scale),
                (int) (97 * scale),
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
    for (var ordered : textRenderer.wrapLines(Text.literal(value), 324)) {
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
            .forEach(e -> paragraph(e.getKey().getDisplayName().getString() + "  " + e.getValue()));
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
    begin(c, ACCENT, "TROPIMON / WIKI", "Atlas Pokédex · registre Cobblemon chargé");
    c.fill(16, 54, 183, 308, PANEL);
    search.render(c, mx, my, delta);
    label(c, filtered.size() + " entrées", 24, 91, MUTED);
    for (int i = 0; i < 11 && listOffset + i < filtered.size(); i++) {
      var s = filtered.get(listOffset + i);
      int y = 108 + i * 17;
      if (s == selected) c.fill(20, y - 3, 179, y + 12, 0xFF375364);
      label(c, String.format(Locale.ROOT, "%04d", s.getNationalPokedexNumber()), 25, y, 0xFFEE9C94);
      label(c, textRenderer.trimToWidth(s.getTranslatedName().getString(), 115), 54, y, WHITE);
    }
    label(c, "↑ ↓ molette · recherche", 23, 295, MUTED);
    if (selected == null) {
      text(
          c,
          all.isEmpty() ? "Entre dans un monde pour charger les espèces." : "Aucun résultat.",
          209,
          113,
          310,
          WHITE);
    } else {
      label(
          c,
          textRenderer.trimToWidth(selected.getTranslatedName().getString(), 220),
          209,
          62,
          WHITE);
      label(c, "#" + selected.getNationalPokedexNumber(), 493, 62, MUTED);
      c.fill(206, 82, 307, 186, 0xFF1F4148);
      int typeY = 91;
      for (var type : form.getTypes()) {
        chip(c, type.getDisplayName().getString(), 323, typeY, 113, true, 0xFF9DDCD5);
        typeY += 25;
      }
      label(c, "FORME", 323, 148, MUTED);
      chip(c, textRenderer.trimToWidth(form.getName(), 137) + "  >", 323, 163, 200, false, ACCENT);
      for (int i = 0; i < TABS.length; i++)
        chip(c, TABS[i], 206 + i * 55, 195, 53, i == tab, ACCENT);
      for (int i = 0; i < 7 && detailOffset + i < lines.size(); i++)
        label(c, lines.get(detailOffset + i), 209, 225 + i * 12, WHITE);
      label(
          c,
          (detailOffset + 1) + " / " + Math.max(1, lines.size()) + " · molette dans la fiche",
          209,
          310,
          MUTED);
    }
    end(c);
    // ModelWidget scissors use screen coordinates rather than the outer UI transform.
    if (portrait != null) portrait.render(c, mouseX, mouseY, delta);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    int mx = localX(x), my = localY(y);
    search.setFocused(hit(mx, my, 23, 63, 151, 18));
    if (search.mouseClicked(mx, my, button)) return true;
    if (hit(mx, my, 20, 105, 159, 187)) {
      int i = listOffset + (my - 105) / 17;
      if (i < filtered.size()) select(filtered.get(i));
      return true;
    }
    if (selected != null && hit(mx, my, 323, 163, 200, 21)) {
      List<FormData> forms = new ArrayList<>();
      forms.add(selected.getStandardForm());
      for (var f : selected.getForms()) if (!forms.contains(f)) forms.add(f);
      formIndex = (formIndex + 1) % forms.size();
      form = forms.get(formIndex);
      updatePortrait();
      rebuild();
      return true;
    }
    if (hit(mx, my, 206, 195, 330, 21)) {
      tab = Math.min(5, (mx - 206) / 55);
      rebuild();
      return true;
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    int change = vertical > 0 ? -3 : 3;
    if (localX(x) < 190)
      listOffset = Math.clamp(listOffset + change, 0, Math.max(0, filtered.size() - 11));
    else detailOffset = Math.clamp(detailOffset + change, 0, Math.max(0, lines.size() - 7));
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
