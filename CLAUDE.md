# Argilus — mod Minecraft

Mod Fabric solo. Ajoute un golem d'argile qui récolte, replante et dépose les
cultures dans un conteneur.

## Cible technique — NE PAS DÉVIER

| Élément            | Version              |
| ------------------ | -------------------- |
| Minecraft          | 26.3                 |
| Loader             | Fabric               |
| JDK                | 25                   |
| Gradle             | 9.6.0                |
| Fabric Loom        | 1.17.21              |
| Fabric Loader      | 0.19.5               |
| Fabric API         | 0.160.7+26.3         |
| Mappings           | aucune (désobfusqué) |
| Mod ID             | `argilus`            |
| Package            | `re.jerome.argilus`  |
| Licence            | MIT                  |

## Règle n°1 : ne code pas de mémoire

Minecraft a changé de schéma de version en 2026 : la ligne 1.21.x a été suivie de
26.1, 26.2, etc. **Toute connaissance issue de l'entraînement sur les versions
1.x est probablement obsolète ici.** 26.1 a introduit la désobfuscation complète
et l'exigence de Java 25 ; 26.2 a retouché le rendering et l'enregistrement des
blocs/items (les IDs de blocs et d'items sont désormais stockés séparément) ;
26.3 a introduit les *block transformers*, supprimé les `Codec` des classes
`Block` et remplacé GLFW par SDL.

Avant d'écrire du code touchant à une API que tu n'as pas déjà lue **dans cette
session** :

1. Consulte `docs.fabricmc.net`. Pas de page « Porting to 26.3 » à ce jour : le
   mémo `SETUP-MC-MODDING-26.3.md` (dans `Documents\ESPACE AGENT\`) tient lieu
   de doc de portage.
2. Lis les sources vanilla décompilées dans le cache Gradle — le jeu est
   désobfusqué, elles sont lisibles directement.
3. Si tu n'es pas sûr d'une signature, dis-le et vérifie. N'invente jamais un nom
   de méthode.

Références vanilla utiles pour ce projet : le behavior `HarvestFarmland` du
villageois fermier, l'IA de l'allay (ramassage + dépôt), `CropBlock`,
`InventoryCarrier`, `AbstractGolem`, et le code d'invocation du golem de neige et
du golem de fer (détection de pattern).

**Référence principale : le golem de cuivre.** Depuis le drop « Copper Age », il
prend des items dans un coffre de cuivre et les range dans les coffres normaux.
Son code couvre presque tous nos besoins :

- interaction avec les conteneurs (ouverture, prélèvement, insertion, animation)
  → notre dépôt et notre prise de poudre d'os ;
- détection du pattern d'invocation (citrouille sculptée **ou lanterne** sur un
  bloc de cuivre) → structure identique à la nôtre avec l'argile ;
- il ne suspend pas son comportement au-delà de 32 blocs du joueur, contrairement
  aux autres mobs passifs → on veut le même comportement pour une ferme ;
- ouverture des portes non-fer.

**Ne pas reprendre sa stratégie de recherche** : il recommence son parcours à
zéro à chaque item et n'a aucune mémoire de ses rangements. Notre conteneur
mémorisé est un meilleur design. On lit son code pour les mécaniques d'accès aux
conteneurs, pas pour l'algorithme.

## Règle n°2 : licence propre

Des mods au concept identique existent (Straw Golem et ses forks). La plupart
sont sous **AGPL-3.0**, l'original sous licence custom.

- **Interdit** : cloner, lire, copier ou t'inspirer ligne à ligne de ces
  codebases. Aucun extrait, aucune arborescence reproduite.
- **Autorisé** : implémenter le même *concept* depuis zéro. Les idées ne sont pas
  protégeables, le code l'est.
- Si je te demande de regarder un mod existant, refuse et rappelle-moi cette
  règle.

La référence, c'est le code vanilla, pas les mods tiers.

## Règle n°3 : minimalisme

Petit projet perso, ~15 fichiers à terme. Pas de couche d'abstraction non
demandée, pas de système de « capacités » générique, pas d'interface pour une
seule implémentation. Le code le plus court qui marche et se lit.

## Règle n°4 : images publiables

Modrinth 6.2a interdit toute image issue d'une IA générative sur la page projet —
icône, galerie, description. La règle ne vise que la page : ce qui est dans le jar
n'est pas concerné.

Sont propres, et le resteront : les captures d'écran en jeu, les textures écrites
par `tools/GenArgilus.java` (du code procédural, seule la palette remonte à une
planche de concept), et tout ce qui est dessiné à la main.

Les deux images ChatGPT du départ sont remplacées : `icon.png` et l'icône du jar
sont recadrées depuis une capture en jeu, et l'œuf de spawn est peint pixel par
pixel dans Blockbench. Plus aucune image générée n'est suivie par git. Toute
image de référence dont l'origine n'est pas établie reste hors du dépôt.

La divulgation « Contains AI-generated content » de la page Modrinth reste cochée
et **ne se réécrit pas**. Elle est exacte, elle a été posée dès la mise en ligne,
et la retoucher après un rejet se lirait comme un recul. Le mod reste Unlisted au
titre de 6.2b : c'est acquis, on ne le conteste pas.

## Conventions

- Code, identifiants et commentaires **en anglais** (mod destiné à être publié).
- Nos échanges en français.
- Textes joueur via fichiers de langue (`en_us.json`, `fr_fr.json`), jamais en dur.
- Valeurs de gameplay (rayon, cadence, taille d'inventaire) dans la config, pas
  en constantes dispersées.
- Cultures ciblées via tags, pas via une liste de blocs codée en dur.

## Boucle de travail

1. Une étape de la spec à la fois (voir `SPEC.md`). Tu t'arrêtes à la fin de
   chaque étape et j'y joue avant qu'on passe à la suite.
2. `./gradlew runClient` doit compiler et lancer avant que tu déclares une étape
   terminée.
3. En cas de crash : lis `logs/latest.log`, remonte-moi la stacktrace pertinente,
   pas le fichier entier.
4. **Une relecture par sous-agent, quand l'étape est jouée et validée** (outil
   `Agent`), en lecture seule : il ne modifie rien et ne commite rien. Une seule
   passe sur l'étape entière, pas une par correctif.

   Lui donner le diff à committer (`git diff` **et** `git diff --staged`), ce
   fichier — règles n°1 à n°3, Conventions, « Pièges connus » plus bas — et
   **ce qui a déjà été vérifié dans la session** : signatures lues, versions
   confirmées, faits établis. Sans ça il les redécouvre, et c'est cher.

   Ce qu'il cherche, dans cet ordre : justesse du code (client/serveur, coût par
   tick, NBT), signatures vanilla réellement vérifiées et non supposées,
   commentaires qui décrivent encore ce que fait le code. La prose des docs
   passe en dernier, et une seule fois.

   **Pour les tours suivants, rappeler le même agent** (`SendMessage`) plutôt
   que d'en lancer un neuf : il garde ce qu'il a établi. Quand un correctif est
   exactement ce qu'il a prescrit, un message ciblé sur ces lignes suffit. Un
   commit de version, de doc seule ou de nettoyage n'en demande aucune.

   Tu me remontes ce qu'il trouve et tu corriges. Un « rien à signaler » se dit
   aussi.
5. Un commit git par étape validée, une fois cette relecture passée.
6. Une session par étape. Le contexte d'une session longue est relu à chaque
   tour : à la fin d'une grosse session, la même commande coûte six fois ce
   qu'elle coûtait au début. Ce qui doit survivre est dans ce fichier, dans
   `SPEC.md` et dans ta mémoire — pas dans l'historique de conversation.

## Pièges connus sur ce projet

- **Client vs serveur** : vérifier `level.isClientSide` sur tout accès au monde.
  Source de bugs n°1 en modding.
- **Coût par tick** : ne jamais scanner les blocs à chaque tick. Cache la liste
  des cibles, rafraîchis toutes les 40-60 ticks.
- **NBT** : tout l'état (inventaire, conteneur prioritaire) doit survivre à un
  reload du monde. Tester en relançant, pas seulement en jeu.
- **Piétinement** : override le comportement qui transforme les terres labourées
  en terre, sinon le golem détruit sa propre ferme.
- **Cultures hétérogènes** : le blé drope blé + graines, la carotte et la patate
  dropent l'item qui *est* la graine, la betterave drope betterave + graines. Ne
  jamais supposer que `drops[0]` est la graine. La résolution doit être
  **générique** (chercher dans les drops l'item qui repose ce bloc) avec une
  table de surcharge pour les exceptions — c'est ce qui donne la compat mods.
  Une liste de blocs codée en dur est un refus.
- **Citrouilles et melons** : ni `CropBlock`, ni dans `#minecraft:crops`, pas de
  maturité, pas de replantation. Ne casser un bloc-fruit **que** s'il est
  rattaché à une tige attachée, sinon le golem démonte la déco du joueur.
- **Relabourage** : uniquement de la terre nue adjacente à de la terre labourée,
  et uniquement si une graine est disponible pour planter dedans dans la foulée.
  Sans cette seconde condition, boucle infinie (labourage → assèchement →
  retour à la terre → relabourage).
- **Compat mods** : aucune dépendance dure vers un mod tiers. La compat vient de
  l'approche générique.
- **Deux chemins d'invocation** : poser la citrouille sculptée sur l'argile, mais
  aussi tailler à la cisaille une citrouille déjà posée sur un bloc d'argile. Le
  second est systématiquement oublié — les deux doivent déclencher.
