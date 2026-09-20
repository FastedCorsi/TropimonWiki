# Tropimon Wiki

By FastedCorsi — 0.1.6

**F7** : Tropimon Wiki (touche reconfigurable).

Fenêtre centrée avec une marge minimale de huit pixels logiques sur chaque bord de l’écran, sans agrandissement au-delà de son échelle native. Titre Tropimon Wiki, pied de page dégagé et navigation des formes seulement lorsqu'il en existe plusieurs. Position de lecture conservée au redimensionnement.

Interface au style Cobblemon / Tropimon : cadre vert du Pokédex Cobblemon, panneaux turquoise, accents sable, icônes de types natives, boutons au survol et jauges de statistiques. Flèches pour parcourir les formes dans les deux sens, indicateurs de défilement et bouton de fermeture. Les textures sont chargées depuis Cobblemon, sans être redistribuées.

Filtre par génération 1 à 9 cumulable avec la recherche par nom traduit, nom technique ou numéro national ; modèle 3D, formes et types. Six fiches : profil, statistiques/EV, attaques et sources d'apprentissage, évolutions, élevage et butin. Les six statistiques sont visibles ensemble. Les talents apparaissent à droite du modèle : vert clair pour les talents ordinaires, jaune pour les talents cachés distincts, sans badge textuel. Leur description apparaît au survol. Un talent unique et ses éventuels doublons ne sont pas présentés comme un HA distinct. Les attaques classiques et les attaques d’œuf partagent les couleurs et icônes natives de leur type. Une Poké Ball rouge dans la liste signale les espèces déjà capturées selon les données du Pokédex synchronisées ; une icône grisée ne signifie pas capturé. Le statut se précise au survol, sans écrire dans le Pokédex. Molette dans la liste ou les détails ; flèches haut/bas pour parcourir les espèces.

Les informations proviennent du registre Cobblemon chargé. Les noms et descriptions suivent la langue et les packs de ressources du joueur. Les textes propres au Wiki sont disponibles en français et anglais, avec repli anglais pour les autres langues. Une fiche n'implique pas qu'un système d'élevage, une CT ou un butin soit effectivement activé sur le serveur. Si les évolutions ne sont pas transmises au client, le Wiki lit les définitions du mod Cobblemon réellement chargé, pour l'espèce et la forme sélectionnées. Ce repli peut différer des règles du serveur ou des extensions ; aucun bandeau technique n'est ajouté à la fiche. Les destinations, niveaux, pierres, échanges, amitié, moments et autres conditions connues sont traduits selon la langue chargée. Le modèle du Pokémon obtenu accompagne les conditions. Les conditions avancées inconnues restent visibles avec leurs paramètres, sans être omises. Pas de suggestions de combat ni de calculateur.

## Compilation et vérification

Java 21, Minecraft 1.21.1, Fabric et Cobblemon >= 1.8.0. Sans borne supérieure mineure artificielle.

```text
gradlew build remapSmokeJar
gradlew build -PcobblemonJar=<jar-de-la-version-minimale>
gradlew build -PofficialDependenciesOnly
gradlew prepareReleaseDelivery
```

Le build local identifie l'unique JAR Cobblemon par fabric.mod.json, même si son nom est une empreinte. Il sélectionne l'unique profil disponible ; en présence de plusieurs profils, choisir explicitement l'instance active avec TROPIMON_HOME. `TROPIMON_HOME` permet de choisir une instance ; la matrice utilise `-PcobblemonJar`. Un exemple de CI utilisant le minimum officiel est fourni sous tools ; aucun workflow distant n'est activé dans cette livraison. Tests unitaires, contrôle de confidentialité des sources et des JAR (archives imbriquées comprises), tests d'installation sous Windows, puis test hors ligne isolé avec `tools/VerifyClient.ps1`. Aucun journal, sauvegarde ou profil réel n'est publié.

## Distribution

Deux exemplaires identiques sont produits dans `build/release/0.1.6/local` et `build/release/0.1.6/shareable`, avec SHA-256. Ne jamais charger les deux exemplaires. L’entrée locale utilise sa copie autonome d’InstallManagedLocalMod.ps1 : elle vérifie le profil actif et son format, attend l’arrêt du jeu puis synchronise mods-user, mods et user-mods-tracked.json pour le seul Wiki. Elle préserve les autres mods et leurs désactivations, vérifie les empreintes, archive les anciennes copies hors des mods et refuse une cible modifiée, verrouillée, redirigée, ambiguë ou plus récente. Le launcher peut rester ouvert.

L'auto-update est autonome : uniquement la Release du dépôt de ce mod, SHA-256, identifiant et version exacts, préparation hors des mods, remplacement différé après arrêt du jeu sous Windows. Le nouvel updater prend aussi en charge les deux copies et le suivi du stockage géré, sans dépendance à un autre mod. Les anciennes versions déjà distribuées ne sont pas réparées rétroactivement. Vérification asynchrone au démarrage uniquement après consentement explicite, espacée d'au moins six heures entre les sessions. Désactivation locale possible dans le fichier `config/<mod_id>-updater.json`.

## Périmètre de la première version

Cette version n'est pas une copie complète de HunterBoard. Pas d'interface de combat, pas de dépendance à un autre mod développé par By FastedCorsi. Les crédits tiers figurent dans THIRD_PARTY.md. Les préférences persistantes sont conservées dans AGENTS.md.

Les tests réseau sont réalisés avec des paquets synthétiques dans un vrai client Minecraft isolé. Ils ne remplacent pas une validation connectée à un événement Tropimon en cours. Aucune partie réelle n'est pilotée automatiquement.


## Mises à jour avec consentement

Aucun téléchargement de mise à jour sans accord. Le premier écran propose uniquement d'autoriser la consultation des métadonnées GitHub (au démarrage, au plus toutes les six heures). Une seconde confirmation montre la version et demande explicitement le téléchargement du JAR et de son SHA-256. L'ancien réglage `enabled: true` ne donne aucune autorisation.

Après accord et vérification, un installateur local utilise le Java de Minecraft, attend la fermeture du jeu, sauvegarde l'ancien JAR hors des mods chargés et remplace uniquement ce mod. Aucun autre mod Tropimon ni changement de launcher n'est requis. Le dossier `mods` classique et le stockage géré Tropimon reconnu sont pris en charge ; une disposition inconnue, un fichier modifié/verrouillé ou une incompatibilité bloque l'installation sans forcer. Le nom du JAR installé est conservé pour rester enregistré par le launcher ; la version réelle se lit dans les métadonnées Fabric.

Pour modifier le choix en jeu : `/tropimonupdates tropimon_wiki`. Refuser laisse le mod utilisable. Les anciennes versions dont l'updater est défectueux nécessitent un premier remplacement manuel, jeu fermé. L'accord donné pour ce mod ne s'applique pas aux autres mods. Les tests automatisés sont exécutés sous Windows ; les autres systèmes doivent encore être validés en situation réelle.

Les versions à consentement utilisent un canal de releases distinct du lien GitHub « latest » historique : sélectionner la version par son tag. Cela évite de déclencher les anciens updaters sans accord.

Les marges intérieures sont centrées dans l’ouverture du cadre natif, sans fond rectangulaire extérieur. Les onglets Attaques et Élevage disposent d’un filtre local par nom traduit, identifiant technique ou type. Il conserve la recherche de Pokémon et se vide avec sa croix. Les barres de statistiques de base reprennent le dégradé rouge → jaune → vert : rouge à 40 ou moins, jaune à 90, vert à 140 ou plus ; les valeurs exactes restent affichées.
