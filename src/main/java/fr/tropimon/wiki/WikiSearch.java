package fr.tropimon.wiki;

import java.text.Normalizer;
import java.util.Locale;

final class WikiSearch {
  static String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]", "");
  }

  static boolean matchesMove(String query, String name, String id, String type) {
    String q = normalize(query);
    return normalize(name).contains(q) || normalize(id).contains(q) || normalize(type).contains(q);
  }

  static boolean matches(String query, String name, String id, int number) {
    String q = normalize(query);
    return normalize(name).contains(q)
        || normalize(id).contains(q)
        || Integer.toString(number).equals(q);
  }
}
