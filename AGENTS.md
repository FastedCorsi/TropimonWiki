# Confidentialité permanente des mods Tropimon

Cette règle demandée par l'utilisateur s'applique à toute création, correction, optimisation, compilation et livraison des mods Tropimon de ce dépôt, y compris leurs futurs modules.

## Attribution et données du développeur

- La mention d'auteur publique du développeur est exactement « By FastedCorsi ». Dans fabric.mod.json, utiliser `"authors": ["By FastedCorsi"]` pour son attribution ; conserver les crédits et licences des autres auteurs.
- Ne jamais ajouter de prénom, nom civil, adresse personnelle ou professionnelle, employeur, nom de compte système ou chemin absolu personnel du développeur dans le code, les ressources, scripts, tests, exemples, documentation, messages de commit ou artefacts destinés à être partagés.
- Ne pas recopier ces données dans ce fichier de consignes, une liste de détection versionnée ou un rapport destiné à la publication. Les éventuels relevés confidentiels restent hors des dépôts et masquent les valeurs sensibles.
- Utiliser des chemins portables et des données fictives neutres. Examiner chaque occurrence avant de la modifier : préserver les identifiants techniques, données de joueurs nécessaires au fonctionnement, dépendances, licences et crédits tiers.
- Conserver les URLs et clés explicitement publiques nécessaires au fonctionnement. Ne jamais embarquer de secret, jeton personnel ou configuration réelle de session ; ne pas considérer l'obfuscation comme une protection.

## Contrôle avant livraison

- Pour chaque changement livré, contrôler les fichiers modifiés et le contenu des nouveaux artefacts : métadonnées auteur/contact, constantes compilées, ressources et archives imbriquées, chemins personnels et secrets.
- Vérifier les JAR finaux après compilation, pas seulement les sources. Exclure des paquets les configurations personnelles, logs, captures personnelles, sauvegardes, fichiers .env et dossiers .git.
- Lors du premier nettoyage d'un module, examiner tout son contenu destiné à être publié ; après cela, prévenir les réintroductions. Installer ou conserver un contrôle automatisé dans le build/CI quand la tâche porte sur ce nettoyage ou sa prévention ; ne pas y inscrire de données personnelles réelles.
- Si le contrôle détecte des données personnelles ou un secret, corriger avant de déclarer l'artefact prêt à diffuser. Ne pas supprimer les originaux personnels sur disque pour nettoyer une distribution.
- Signaler les éléments non vérifiés et les risques résiduels ; ne pas promettre une anonymisation absolue ou l'effacement de copies déjà récupérées.

## Git et périmètre des actions

- Avant tout nouveau commit autorisé, vérifier l'identité d'auteur et de committer effective : pseudonyme FastedCorsi et adresse GitHub noreply valide déjà vérifiée. Ne pas inventer d'adresse ; si aucune adresse valide n'est disponible, terminer le travail local sans créer le commit concerné et signaler le point.
- Ne pas modifier la configuration Git globale. Signaler séparément les données restant dans l'historique, les releases ou les copies déjà distribuées ; changer un fichier ou ajouter .gitignore ne les efface pas.
- Ne pas réécrire l'historique, pousser de force, publier, remplacer un JAR du launcher ou révoquer une clé sans autorisation correspondante. Ne jamais tester un secret contre un service pour vérifier sa validité dans le cadre de ce nettoyage.
- Préserver la logique, les fonctionnalités et l'indépendance de chaque mod. Une dépendance à un autre mod développé par nous doit être remplacée par un équivalent autonome lorsqu'elle est concernée par la tâche, sans retirer une protection nécessaire.
- Ajouter cette règle sans données personnelles dans tout nouveau dépôt autonome de mod Tropimon. Team Hunt et Bid Maker restent mis de côté tant que l'utilisateur ne demande pas leur reprise.

## Deux livraisons JAR à chaque version

- À chaque livraison d'une version ou d'un changement de code, fournir deux JAR clairement séparés : un JAR local accompagné du système de mise à jour différée de l'instance du launcher, et un JAR prêt à partager. Utiliser deux dossiers ou noms explicites ; ne jamais installer les deux exemplaires simultanément.
- Les deux JAR proviennent de la même version validée et offrent les mêmes fonctionnalités. Ils peuvent être identiques octet pour octet : privilégier un petit script externe pour l'installation locale, sans dupliquer le code du mod ni embarquer ce mécanisme dans le JAR public.
- Le launcher peut rester ouvert : seule l'exécution du jeu Minecraft concerné bloque la mise à jour locale. Attendre l'arrêt du jeu avant de remplacer le JAR dans la bonne instance ; la fermeture du launcher n'est pas requise et ne prouve pas l'arrêt du jeu. Ne jamais forcer l'arrêt du launcher ou du jeu, toucher aux autres mods ni remplacer un fichier utilisé ou verrouillé.
- Cette demande constitue l'autorisation permanente de préparer et d'armer cette installation différée lors d'une livraison, sauf consigne explicite contraire pour la tâche. Une demande de conseil, d'audit ou de mise à jour des règles ne déclenche ni compilation ni installation.
- Réutiliser et adapter les outils locaux existants. Vérifier la cible exacte, l'intégrité du JAR et le résultat de la copie ; conserver une sauvegarde de l'ancien JAR hors du dossier des mods chargés. En cas de cible ambiguë, d'accès impossible ou de verrouillage, conserver le fichier préparé et signaler le blocage sans forcer.
- Le JAR partageable ne contient ni chemin personnel, configuration locale, secret, donnée privée ni outil d'installation spécifique à la machine. Appliquer les contrôles de confidentialité aux deux JAR et aux éventuels fichiers qui les accompagnent. Conserver l'attribution « By FastedCorsi » et les crédits tiers.
- Dans la livraison, indiquer les deux JAR et leur version, les contrôles effectués et l'état réel de l'installation locale : préparée, en attente de fermeture ou installée après vérification. Ne pas annoncer une installation réussie parce qu'un script a seulement été lancé.

## Compatibilité durable avec Cobblemon

- Les mods doivent rester compatibles avec les mises à jour mineures de Cobblemon sans exiger une recompilation à chaque fois. Déclarer une version minimale réellement prise en charge, sans borne maximale mineure artificielle ; une rupture majeure ou une incompatibilité réelle peut justifier une borne documentée.
- Compiler et tester chaque livraison contre le JAR Cobblemon actuellement installé et, lorsque le mod appelle directement son API, contre la version minimale annoncée. Utiliser l'API commune ou un petit adaptateur local pour les écarts réels ; ne pas dépendre des classes internes de nos autres mods.
- Le build doit refuser une ancienne borne de métadonnées et sélectionner automatiquement l'unique JAR Cobblemon actif, avec une option explicite pour la matrice de compatibilité. Vérifier la dépendance dans les deux JAR finaux.
- La compatibilité reste assurée par le code et les tests, indépendamment des notifications Modrinth. Aucun auto-updater intégré ne doit remplacer ces contrôles.

## Code simple, lisible et efficace

- Préserver strictement la logique, les fonctionnalités et les protections. Chercher les gains utiles de performance, mémoire et poids sans rendre le code difficile à comprendre.
- Choisir la solution la plus simple qui répond au besoin actuel. Éviter les classes, interfaces, factories, couches de services, méthodes relais et dépendances ajoutées sans utilité concrète ; ne pas bâtir un framework pour un cas isolé.
- Garder des classes cohérentes et des méthodes lisibles quand leur séparation aide réellement. Ne pas tout fusionner dans une classe géante ni compacter le code : moins de fichiers ou de lignes ne garantit pas de meilleures performances.
- Réutiliser ce qui existe dans le mod ; supprimer le code mort seulement après vérification des usages, y compris mixins, réflexion, événements, ressources et compatibilité. Pas de réécriture générale pour une optimisation locale.
- Cibler les coûts identifiés : travail répété par tick ou par frame, scans, allocations, entrées/sorties et caches sans limite. Justifier les gains et vérifier les comportements concernés ; ne pas ajouter de cache, de thread ou d'abstraction préventive sans besoin démontré.
- Chaque mod reste autonome : aucune dépendance aux classes, états ou services internes de nos autres mods. Recréer dans le mod concerné la petite implémentation nécessaire plutôt qu'imposer une bibliothèque commune ; préserver les dépendances officielles nécessaires.

## Damage Calculator : séparation permanente des modes

- Les combats normaux utilisent uniquement les suggestions de l'API Tropimon ; les Random Battles utilisent uniquement leurs données Random Battle. Ne jamais mélanger, fusionner ou conserver les données d'un mode dans l'autre.
- Une transition de mode ou de session doit invalider la provenance précédente, même si Cobblemon réutilise un objet ou un identifiant. Toute évolution de cette synchronisation doit inclure des tests de non-mélange dans les deux directions.

## UI Battle : séparation spectateur et participant

- Dans Tropimon UI Battle, conserver des provenances et règles distinctes pour les observations reçues en spectateur et celles reçues en tant que participant. Ne jamais fusionner leurs états, déductions ou fenêtres de déduplication.
- Chaque changement de détection de combat doit être testé dans les deux modes. Une attaque, un talent et l'annonce officielle de Cobblemon décrivant le même changement de terrain ne doivent compter qu'une fois sur l'ensemble du tour, même si des paquets intermédiaires les séparent.



## Dépôts publics et distribution Modrinth

- Chaque mod livré possède son dépôt GitHub public propre. Chaque version validée est poussée, taguée et publiée dans une Release avec exactement un JAR partageable et son fichier SHA-256 ; les livrables LOCAL et les données propres à une machine ne sont jamais publiés.
- La distribution et les mises à jour destinées aux joueurs passent désormais par les projets officiels Modrinth autorisés. GitHub conserve les sources et livraisons autorisées ; ne pas effacer les anciens dépôts, tags ou releases pour retirer un updater du code.
- Aucun téléchargement ou remplacement de JAR, helper d'installation, job GitHub d'auto-update ou popup d'installation n'est embarqué dans les nouveaux mods. Ne pas conserver notre auto-installateur en changeant simplement son fournisseur pour Modrinth.
- La notification éventuelle est autonome, légère, asynchrone et espacée. Aucun travail réseau par tick ou frame, aucune bibliothèque commune ni nouveau launcher obligatoire. Les outils privés d'installation locale restent distincts des fonctionnalités livrées aux joueurs.



## Installation locale prise en charge par l’agent

- Lors des prochaines livraisons Tropimon, l’agent réalise lui-même l’installation locale autorisée ; ne pas demander à l’utilisateur de recopier ou réimporter le JAR si l’opération peut être menée sûrement avec les outils disponibles.
- Détecter le profil et la gestion des mods du launcher. Une copie dans `instance/mods` seule ne constitue pas une installation valide lorsque le launcher utilise `instance/mods-user` et `user-mods-tracked.json`.
- Sur ce schéma vérifié, synchroniser la copie importée, la copie chargée et le suivi du seul mod livré. Préserver les autres mods, leurs désactivations et le manifeste officiel ; ne jamais désactiver le contrôle des mods non gérés ni assouplir une protection du launcher.
- Réutiliser l’installateur local `InstallManagedLocalMod.ps1` lorsqu’il est disponible et en joindre une copie autonome à la livraison LOCAL. Remplacer ou adapter l’ancienne entrée d’installation avant de la lancer sur un profil géré ; un ancien script limité au dossier `mods` ne doit pas être utilisé tel quel. Aucun outil local n’est embarqué dans le JAR partageable, aucune dépendance entre mods n’est ajoutée.
- Attendre l’arrêt du jeu concerné sans forcer le launcher ni Minecraft. Vérifier les SHA-256, l’identifiant et la version, empêcher doublons et retours de version, sauvegarder hors des dossiers chargés et refuser les cibles modifiées, verrouillées, redirigées ou ambiguës. Une évolution inconnue du format impose une nouvelle vérification, pas une modification forcée.
- Vérifier les deux copies et l’enregistrement du launcher après installation ; indiquer séparément l’état sur disque et une éventuelle validation en jeu. Retirer l'auto-updater du nouveau code ne le retire pas rétroactivement des JAR déjà distribués. L'installateur local privé ne doit pas être réintroduit dans le JAR public.
- Ces consignes ne déclenchent pas à elles seules une compilation, une publication ni une modification des mods mis de côté. Tropimon Compagnon reste exclu tant que l’utilisateur demande de ne pas y toucher.


## Coordination générale des mods Tropimon

- La tâche intitulée « MANAGEUR » est le point de coordination de tous les mods Tropimon. Les tâches spécialisées par mod conservent leur périmètre ; ce rôle ne les transforme pas toutes en gestionnaire global.
- Le code, les corrections et les tests propres à un mod sont réalisés dans sa tâche spécialisée. MANAGEUR transmet les demandes et leur contexte, suit les résultats, vérifie les preuves et coordonne les livraisons ; il ne code pas directement dans les mods sans demande explicite contraire de l’utilisateur. En cas de reprise, transmettre les changements existants et leurs limites sans les annuler ni travailler en concurrence.
- Le gestionnaire centralise l’état des travaux, les problèmes transversaux, les versions et les consignes communes. Avant d’intervenir dans un mod, vérifier le travail en cours dans sa tâche pour éviter doublons et modifications concurrentes.
- Coordonner la compatibilité, l’indépendance des mods, les performances, la simplicité du code, la confidentialité et l’attribution « By FastedCorsi », sans remplacer les contrôles propres à chaque projet.
- Prendre en charge les livraisons autorisées : JAR local et partageable, installation locale gérée par le launcher, sauvegardes, vérifications et état réel. Distinguer une version préparée, installée, testée en jeu et publiée ; ne jamais annoncer ces états sans preuve.
- Ce rôle n’autorise pas à lui seul une nouvelle fonctionnalité, une réécriture générale, une action destructive ou une surveillance en arrière-plan. Respecter les autorisations existantes et les exclusions : Team Hunt et Bid Maker restent en pause ; ne pas toucher à Tropimon Compagnon tant que cette restriction est maintenue.


## Consentement et notification Modrinth

- Toute récupération de fichier, y compris JAR, empreinte et catalogue externe, exige un accord éclairé préalable du joueur. Ne jamais télécharger en arrière-plan avant cet accord.
- La vérification des métadonnées de mise à jour est désactivée sans consentement explicite ; un ancien `enabled: true` généré automatiquement ne vaut pas accord. L'autorisation de vérifier ne vaut jamais autorisation de télécharger ou installer une version.
- Le consentement aux anciennes vérifications GitHub n'autorise pas implicitement une nouvelle source Modrinth. Présenter le fournisseur et la nature des requêtes ; refuser ou fermer ne provoque aucune requête. Préserver les consentements distincts des API utiles au jeu.
- Consulter seulement un projet Modrinth officiel vérifié. Filtrer les versions publiées et compatibles avec Minecraft, Fabric et les dépendances connues ; comparer réellement les versions, sans proposer de retour arrière. Un projet absent, privé, rejeté ou un accès indisponible ne signifie pas qu'une mise à jour existe.
- Le lien de chat ouvre la page HTTPS de la version officielle, jamais une commande shell, un téléchargement direct de JAR ou un protocole d'installation non vérifié. Aucun remplacement automatique par le mod.
- Tester absence de réseau sans consentement, version égale/ancienne/incompatible, projet non public, erreur réseau, liens officiels et absence d'installateur dans les artefacts. Ne pas confondre l'installation locale avec une mise à jour gérée par Modrinth.

- Transition : les anciennes versions distribuées conservent leur ancien comportement. Ne pas provoquer volontairement leur auto-update par un nouveau marqueur de canal ou par un déplacement de latest. Si une archive GitHub est publiée, préserver le canal historique sans ajouter le marqueur consommé par les anciens updaters ; Modrinth est le canal de mise à jour prévu. Une première mise à jour manuelle peut être nécessaire. Aucune suppression aveugle des configurations, sauvegardes ou fichiers préparés.

## Mise à jour accessible sans commande

- Aucun joueur ne doit taper une commande : après entrée dans un monde, une notification de chat cliquable indique uniquement une mise à jour Modrinth effectivement connue, avec le mod et les versions. Sans accord, un accès facultatif aux préférences peut proposer la vérification sans réseau préalable.
- Remplacer la popup générale d'installation précédemment demandée par cette notification simple. Éviter répétitions et doublons ; ne pas interrompre une interface ou un combat. Aucun menu ne propose de télécharger ou d'installer un JAR depuis le mod.
- Une automatisation via un gestionnaire Modrinth existant est envisageable seulement si sa compatibilité avec l'instance réelle est démontrée, sans installation contraignante ni modification forcée du launcher. À défaut, conserver le lien vers Modrinth et l'import géré normal du launcher, sans recréer un auto-installateur.

## Lisibilité aux quatre échelles GUI

- Gérer les échelles Minecraft 1, 2, 3 et 4 sans modifier le réglage global du joueur. Vérifier aussi les fenêtres réduites et le redimensionnement ; distinguer l'échelle demandée de celle réellement appliquée par Minecraft.
- Conserver des textes, valeurs, contrôles et infobulles lisibles. Adapter l'agencement et le défilement à l'espace disponible ; ne pas masquer une valeur essentielle ou remplacer sa lecture par une police minuscule.
- Rendu, clics, survol, glisser-déposer et découpe utilisent la même transformation. Contrôler les interactions et protections existantes, pas seulement une capture à l'échelle 2. Les mods restent indépendants, avec une implémentation locale simple.

## Parcours des tests en jeu

- Pour les essais manuels et visuels, passer par le launcher Tropimon : cliquer sur Jouer, puis sur Solo et entrer dans un monde solo de test. Utiliser les mods réellement installés dans cette instance pour reproduire le parcours utilisateur.
- Les tests automatisés et clients isolés restent complémentaires ; ne pas les présenter comme une validation de ce parcours dans le launcher.
- Un essai en solo ne valide pas les fonctions dépendantes du serveur Tropimon. Indiquer séparément les comportements contrôlés en solo et ceux restant à vérifier sur le serveur.
- Pour tester la notification Modrinth, vérifier le consentement, le message et son lien avec le launcher réel. Le parcours ne lève jamais une interdiction explicite d'installation et ne justifie pas de modifier le launcher. Les essais historiques d'auto-update ne sont pas une preuve du nouveau fonctionnement.

## Distribution fusionnée : nouveau Tropimon Compagnion

- Le nouveau Tropimon Compagnion est distinct de l'ancien Compagnon. Son périmètre initial autorisé réunit Damage Calculator, Wiki, Team Builder, Better PC, Catch Preview, Chat Filter, Events et UI Battle dans un seul mod, un seul identifiant Fabric et un seul JAR distribué.
- Les évolutions et corrections des modules continuent dans leurs tâches spécialisées habituelles. La tâche Compagnion intègre des révisions identifiées et validées dans son propre projet, sans modifier en concurrence les sources originales ni copier un état de travail incertain. MANAGEUR coordonne les versions à intégrer et les preuves.
- Pour ces huit modules, la distribution des nouvelles évolutions passe par Compagnion : un seul projet de distribution Modrinth et une seule version de l'ensemble, sans updater intégré. Cette consigne remplace la publication et l'installation automatiques de JAR individuels pour ces évolutions ; leurs sources et artefacts de validation peuvent rester séparés. Ne pas supprimer les dépôts, anciennes releases ou fichiers personnels pour appliquer ce changement.
- Le message de chat signale une version de Compagnion comme ensemble. L'activation ou la configuration d'une fonctionnalité intégrée ne constitue pas une mise à jour partielle de son ancien JAR. Ne pas embarquer les huit anciens updaters ni leurs notifications individuelles dans le JAR fusionné.
- Les mods hors de ce périmètre ne sont pas ajoutés implicitement à Compagnion et conservent leur autonomie. Team Hunt et Bid Maker restent en pause. La coopération avec d'autres mods pour une éventuelle fenêtre commune ne doit pas imposer Compagnion comme dépendance.
- La livraison de Compagnion conserve les deux exemplaires LOCAL et partageable, les contrôles de confidentialité, de compatibilité, les sauvegardes et le suivi exact des états. Sa migration doit éviter le double chargement des huit anciens mods en préservant leurs réglages et sauvegardes ; aucune suppression aveugle ni contournement d'une interdiction ponctuelle d'installation pour un test en cours.

## Instance du launcher et données du Wiki

- Le programme du launcher et ses données peuvent être séparés. Détecter le profil actif avant une installation ; le dossier mods historique peut être un miroir et ne prouve pas quels JAR sont chargés.
- À l'exécution, utiliser l'origine du ModContainer Fabric pour identifier le JAR chargé et dériver son dossier mods et son instance. Vérifier le JAR et son SHA-256 dans cette instance avant d'annoncer une installation réussie.
- Afficher les talents par couleur et expliquer leur effet au survol dans la langue du joueur, sans badge de statut ni onglet Talents. Classer les talents à partir de leur statut explicite, jamais de leur ordre ou de leur nombre. Un talent unique, y compris un doublon normal/caché de même nom, ne doit pas être présenté comme un HA distinct.
- Présenter les évolutions avec leur méthode et toutes leurs conditions. Conserver les conditions inconnues plutôt que de les ignorer. Documenter le repli local sans bandeau technique dans la fiche, conformément à la demande utilisateur.

## Transition du Wiki vers Compagnion

- Ce dépôt maintient les sources du module Wiki. Transmettre une révision identifiée et validée à MANAGEUR et à la tâche Compagnion ; ne pas modifier leur projet en parallèle.
- Aucun updater ni vérificateur Modrinth individuel actif dans le Wiki. Compagnion porte seul la notification de version du package.
- Les JAR produits ici sont des artefacts internes de validation : pas de Release autonome, installation ou armement automatique pour cette transition. Les outils privés LOCAL restent séparés du code livré aux joueurs.
