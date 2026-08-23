# Argilus — mod Minecraft

Mod Fabric solo. Ajoute un golem d'argile qui récolte, replante et dépose les
cultures dans un conteneur.

## Cible technique — NE PAS DÉVIER

| Élément            | Version              |
| ------------------ | -------------------- |
| Minecraft          | 26.2                 |
| Loader             | Fabric               |
| JDK                | 25                   |
| Gradle             | 9.5.1                |
| Fabric Loom        | 1.17                 |
| Fabric Loader      | 0.19.3               |
| Fabric API         | 0.158.0+26.2         |
| Mappings           | aucune (désobfusqué) |
| Mod ID             | `argilus`            |
| Package            | `re.jerome.argilus`  |
| Licence            | MIT                  |

## Règle n°1 : ne code pas de mémoire

Minecraft a changé de schéma de version en 2026 : la ligne 1.21.x a été suivie de
26.1, 26.2, etc. **Toute connaissance issue de l'entraînement sur les versions
1.x est probablement obsolète ici.** 26.1 a introduit la désobfuscation complète
et l'exigence de Java 25 ; 26.2 a retouché le rendering et l'enregistrement des
blocs/items (les IDs de blocs et d'items sont désormais stockés séparément).

Avant d'écrire du code touchant à une API que tu n'as pas déjà lue **dans cette
session** :

1. Consulte `docs.fabricmc.net` (dont la page « Porting to 26.2 »).
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
4. Commit git à chaque étape validée.

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
