# Tropimon Wiki

By FastedCorsi — 0.1.8

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

Deux exemplaires identiques sont produits dans `build/release/0.1.8/local` et `build/release/0.1.8/shareable`, avec SHA-256. Ne jamais charger les deux exemplaires. L’entrée locale utilise sa copie autonome d’InstallManagedLocalMod.ps1 : elle vérifie le profil actif et son format, attend l’arrêt du jeu puis synchronise mods-user, mods et user-mods-tracked.json pour le seul Wiki. Elle préserve les autres mods et leurs désactivations, vérifie les empreintes, archive les anciennes copies hors des mods et refuse une cible modifiée, verrouillée, redirigée, ambiguë ou plus récente. Le launcher peut rester ouvert.

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


## Habitats, apparitions et EV

Le bouton « Habitats » présente les habitats et les apparitions naturelles de la forme choisie. Les niveaux, raretés, phases, biomes, horaires, météo, prérequis, exclusions, groupes et multiplicateurs proviennent des définitions des mods installés ; un poids relatif ne représente pas une probabilité en pourcentage. Les données personnalisées d’un serveur distant ne sont pas synchronisées par cette interface. Une absence locale signifie « aucune apparition renseignée », jamais « impossible sur le serveur ».

L’aperçu représente une variante de la véritable structure NBT locale, associée au bloc d’habitat portant son identifiant de pool. Aucun téléchargement ni image de structure redistribuée : les blocs sont dessinés avec les ressources du jeu. Les variantes, processeurs et raccords peuvent différer dans le monde. Le catalogue est lu une fois à la demande dans une tâche asynchrone ; seul le modèle de l’habitat sélectionné est conservé. Le Wiki reste indépendant de Tropimon Farm.

Le paquet d’espèce Cobblemon omet les EV gagnés. Le Wiki utilise les définitions du Cobblemon chargé si le client n’a pas cette information, avec héritage des formes et distinction entre zéro explicite et donnée inconnue. La provenance est expliquée au survol de la ligne EV. Le contrôle isolé parcourt toutes les espèces et formes locales et teste aussi un véritable aller-retour du paquet réseau.

Le bas du panneau rejoint l’ouverture du cadre ; le champ d’attaques utilise un fond sombre pour conserver le contraste avec le texte et son ombre native. Les essais isolés passent les échelles GUI demandées 1 à 4, consignent leur échelle effective et vérifient saisie, clics, défilement, redimensionnement et fermeture. Le mod ne change pas le réglage global du joueur.

## Barons / Alphas

Le bouton « Barons » ouvre les apparitions recensées, le butin après une victoire contre un Baron sauvage et les recettes de CT déblocables à sa capture. Le niveau est réglable de 1 à 100 (Maj pour avancer de dix niveaux). Les quantités, paliers et tables de butin sont lus dans le Cobblemon installé. Les poids sont normalisés par groupe, avec les entrées vides ; le Wiki distingue le pourcentage par tirage de la chance d’obtenir l’objet au moins une fois par Baron. Les tirages de quantité et de nombre de récompenses restent distincts. Le butin habituel de l’espèce reste dans « Butin ».

Pour Cobblemon 1.8.0 et 1.8.1, le script de récompenses utilise le premier type pour les deux essais de bonus à 50 %, y compris pour les doubles types. Cette particularité est reproduite et vérifiée en exécutant la branche réelle du script avec le moteur Molang. Une règle inconnue n’est pas présentée comme un zéro ou une certitude. Les tables et scripts des serveurs distants ne sont pas transmis : leur provenance locale est indiquée.

Les recettes de CT utilisent le registre synchronisé lorsqu’il est disponible, sinon les définitions locales. Le Wiki distingue les attaques accessibles au niveau choisi (y compris les pré-évolutions) des deux attaques CT bonus. Les bonus suivent deux tirages pondérés sans remplacement parmi les CT qui ne sont pas déjà des attaques apprises par niveau ; les probabilités sont calculées avec les poids de Cobblemon. Elles concernent le déblocage de la recette à la capture, pas la chance de capturer le Pokémon. Le Wiki montre les quantités, les ingrédients possibles et la CT vierge consommée dans la Machine à CT, avec les icônes d’objets et de types. Un filtre permet de chercher une CT par nom ou type. Aucun objet ni déblocage n’est ajouté au joueur.

Les tests couvrent les frontières des quatre paliers, les entrées vides, les tirages multiples, les petits ensembles de CT, les 40 tables locales, et comparent les chances calculées au sélecteur natif de Cobblemon. L’interface des Barons est incluse dans les essais des quatre échelles GUI.
