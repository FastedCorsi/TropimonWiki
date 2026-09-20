package fr.tropimon.wiki;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Standalone Wiki shell using the installed Cobblemon Pokédex frame. */
abstract class InstrumentScreen extends Screen {
  static final int W = 600,
      H = 380,
      INK = 0xFF173C39,
      PANEL = 0xFF245650,
      MUTED = 0xFFADDDD0,
      WHITE = 0xFFF2FFF4;
  private static final Identifier FRAME =
      Identifier.of("cobblemon", "textures/gui/pokedex/pokedex_base_green.png");
  static final int CONTENT_OFFSET_X = 4;
  protected int left, top;
  protected float scale;

  InstrumentScreen(String title) {
    super(Text.literal(title));
  }

  @Override
  protected void init() {
    // Small fixed margins preserve readable text at large Minecraft GUI scales.
    scale = Math.min(1F, Math.min((width - 16F) / W, (height - 16F) / H));
    left = (int) ((width - W * scale) / 2);
    top = (int) ((height - H * scale) / 2);
  }

  protected int localX(double x) {
    return (int) Math.floor((x - left) / scale) - CONTENT_OFFSET_X;
  }

  protected int localY(double y) {
    return (int) Math.floor((y - top) / scale);
  }

  protected void begin(DrawContext c, int accent, String label, String subtitle) {
    c.fill(0, 0, width, height, 0xB5102425);
    c.getMatrices().push();
    c.getMatrices().translate(left, top, 0);
    c.getMatrices().scale(scale, scale, 1);
    // Backing stays inside the native aperture; the frame is painted above its contents.
    c.fill(31, 25, W - 31, 360, 0xFF386A5D);
    c.getMatrices().translate(CONTENT_OFFSET_X, 0, 0);
    c.fill(27, 25, W - 35, 50, INK);
    c.fill(29, 28, 31, 47, accent);
    label(c, label, 40, 28, WHITE);
    label(c, subtitle, 40, 40, MUTED);
    c.fill(27, 343, W - 35, 360, INK);
    label(c, "By FastedCorsi", 34, 345, MUTED);
    label(c, Text.translatable("tropimon_wiki.close").getString(), W - 116, 345, MUTED);
  }

  protected void end(DrawContext c) {
    c.getMatrices().translate(-CONTENT_OFFSET_X, 0, 0);
    c.drawTexture(FRAME, 0, 0, W, H, 0, 0, 345, 207, 345, 207);
    c.getMatrices().pop();
  }

  protected void label(DrawContext c, String s, int x, int y, int color) {
    c.drawText(textRenderer, s, x, y, color, false);
  }

  protected void text(DrawContext c, String s, int x, int y, int maxWidth, int color) {
    int line = 0;
    for (var part : textRenderer.wrapLines(Text.literal(s), maxWidth)) {
      c.drawText(textRenderer, part, x, y + line * 12, color, false);
      line++;
    }
  }

  protected void chip(
      DrawContext c, String s, int x, int y, int w, boolean on, boolean hovered, int accent) {
    // Stepped corners and a two-pixel lip match the Pokédex's pixel-art controls.
    int color = on ? accent : hovered ? 0xFF397C6C : PANEL;
    c.fill(x + 2, y, x + w - 2, y + 21, INK);
    c.fill(x, y + 2, x + w, y + 19, INK);
    c.fill(x + 2, y + 2, x + w - 2, y + 18, color);
    c.fill(x + 3, y + 2, x + w - 3, y + 3, on ? 0xFFF9F6BB : 0xFF70AB8A);
    String fitted = textRenderer.trimToWidth(s, w - 8);
    label(c, fitted, x + (w - textRenderer.getWidth(fitted)) / 2, y + 7, on ? INK : WHITE);
  }

  protected void scrollbar(DrawContext c, int x, int y, int h, int offset, int total, int visible) {
    if (total <= visible) return;
    c.fill(x, y, x + 3, y + h, 0xFF408C86);
    int thumb = Math.max(10, h * visible / total);
    int position = (h - thumb) * offset / (total - visible);
    c.fill(x, y + position, x + 3, y + position + thumb, 0xFFF1E7A0);
  }

  protected boolean hit(int mx, int my, int x, int y, int w, int h) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }

  @Override
  public boolean shouldPause() {
    return false;
  }
}
