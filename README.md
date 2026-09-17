# Tropimon Wiki

By FastedCorsi — 0.1.1

**F7** : Pokédex Wiki (touche reconfigurable).

Interface au style Cobblemon / Tropimon : cadre vert du Pokédex Cobblemon, panneaux turquoise, accents sable, icônes de types natives, boutons au survol et jauges de statistiques. Flèches pour parcourir les formes dans les deux sens, indicateurs de défilement et bouton de fermeture. Les textures sont chargées depuis Cobblemon, sans être redistribuées.

Recherche par nom traduit, nom technique ou numéro national ; modèle 3D, formes et types. Six fiches : description/statistiques/EV, talents, attaques et sources d'apprentissage, évolutions, élevage et butin. Molette dans la liste ou les détails ; flèches haut/bas pour parcourir les espèces.

Les informations proviennent du registre Cobblemon chargé. Une fiche n'implique pas qu'un système d'élevage, une CT ou un butin soit effectivement activé sur le serveur. Les conditions avancées d'évolution conservent leur représentation technique lorsque aucune présentation fiable n'est disponible. Pas de suggestions de combat ni de calculateur.

## Compilation et vérification

Java 21, Minecraft 1.21.1, Fabric et Cobblemon >= 1.8.0. Sans borne supérieure mineure artificielle.

```text
gradlew build remapSmokeJar
gradlew build -PcobblemonJar=<jar-de-la-version-minimale>
gradlew build -PofficialDependenciesOnly
gradlew prepareReleaseDelivery
```

Le build local exige un unique JAR Cobblemon actif. `TROPIMON_HOME` permet de choisir une instance ; la matrice utilise `-PcobblemonJar`. Un exemple de CI utilisant le minimum officiel est fourni sous tools ; aucun workflow distant n'est activé dans cette livraison. Tests unitaires, contrôle de confidentialité des sources et des JAR (archives imbriquées comprises), tests d'installation sous Windows, puis test hors ligne isolé avec `tools/VerifyClient.ps1`. Aucun journal, sauvegarde ou profil réel n'est publié.

## Distribution

Deux exemplaires identiques sont produits dans `build/release/0.1.1/local` et `build/release/0.1.1/shareable`, avec SHA-256. Ne jamais charger les deux exemplaires. Le script du dossier local attend l'arrêt de Minecraft, vérifie les empreintes, conserve l'ancien JAR hors des mods et refuse une cible modifiée depuis la préparation. Le launcher peut rester ouvert.

L'auto-update est autonome : uniquement la Release du dépôt de ce mod, SHA-256, identifiant et version exacts, préparation hors des mods, remplacement différé après arrêt du jeu sous Windows. Vérification asynchrone au démarrage, espacée d'au moins six heures entre les sessions. Désactivation locale possible dans le fichier `config/<mod_id>-updater.json`.

## Périmètre de la première version

Cette version n'est pas une copie complète de HunterBoard. Pas d'interface de combat, pas de dépendance à un autre mod développé par By FastedCorsi. Les crédits tiers figurent dans THIRD_PARTY.md. Les préférences persistantes sont conservées dans AGENTS.md.

Les tests réseau sont réalisés avec des paquets synthétiques dans un vrai client Minecraft isolé. Ils ne remplacent pas une validation connectée à un événement Tropimon en cours. Aucune partie réelle n'est pilotée automatiquement.
