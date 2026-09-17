package fr.tropimon.wiki;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WikiSearchTest {
  @Test
  void localizedAndIdentifiers() {
    assertTrue(WikiSearch.matches("evoli", "Évoli", "eevee", 133));
    assertTrue(WikiSearch.matches("eevee", "Évoli", "eevee", 133));
    assertTrue(WikiSearch.matches("#133", "Évoli", "eevee", 133));
    assertFalse(WikiSearch.matches("134", "Évoli", "eevee", 133));
  }

  @Test
  void punctuation() {
    assertEquals("mrmime", WikiSearch.normalize("Mr. Mime"));
    assertEquals("flabebe", WikiSearch.normalize("Flabébé"));
  }
}
