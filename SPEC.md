# Argilus — spécification

## Le concept

Un golem d'argile qui entretient une ferme : il récolte les cultures mûres,
replante immédiatement derrière lui, répare la terre piétinée, et vide son
inventaire dans un conteneur.

Volontairement plus simple que ce qui existe ailleurs : **pas de faim, pas de
durée de vie, pas de sommeil, pas de tiers**. Un golem, quelques comportements
bien faits.

## Décisions de design (figées)

| Point                  | Valeur retenue                                    |
| ---------------------- | ------------------------------------------------- |
| Nom du mob             | Argilus / Golem d'argile                          |
| Taille du mob          | hitbox du golem de cuivre (0.49 × 0.98)           |
| Apparence              | chapeau de paille à bande rouge, 6 finitions d'argile tirées au sort |
| Invocation             | citrouille sculptée **ou lanterne** sur un bloc d'argile |
| Rayon d'action         | 12 blocs (config : 4 à 24)                        |
| Cadence de récolte     | 1 bloc max toutes les 20 ticks (1 s)              |
| Inventaire             | 2 rangées de 9 (config), ouvert au clic droit      |
| Ramassage au sol       | objets à portée, rayon 7 (config)                 |
| Points de vie          | 20, jamais de despawn                             |
| Déclencheur de dépôt   | inventaire plein **ou** plus rien à faire depuis 100 ticks (inventaire non vide) |
| Conteneur de dépôt     | le plus proche du centre du champ détecté, mémorisé |
| Cultures gérées        | toute `CropBlock` mûre (vanilla et moddée), citrouilles/melons, baies douces, nether wart |
| Labourage              | par adjacence à de la terre labourée, si une graine est disponible |
| Poudre d'os            | prélevée dans le conteneur pendant un dépôt, 1 slot réservé |
| Melons                 | récoltés en tranches, comme à mains nues |

### Invocation — détail

Comportement volontairement **vanilla-style** : pas de condition supplémentaire,
pas de vérification de terre labourée à proximité. On assume le risque de
déclenchement accidentel, comme pour le golem de neige.

Comme le golem de cuivre vanilla, le bloc du haut peut être une **citrouille
sculptée ou une lanterne**.

Les deux chemins doivent fonctionner :

1. Placer le bloc du haut sur un bloc d'argile déjà posé.
2. Tailler à la cisaille une citrouille posée sur un bloc d'argile.

Le second cas est le plus souvent oublié dans les implémentations maison.

La détection de pattern du golem de cuivre est la référence à lire — structure
identique, seul le bloc de base change.

### Récolte — familles distinctes

**1. Cultures classiques** (`CropBlock` avec un âge)

Résolveur **générique**, pas une liste codée en dur : toute `CropBlock` dont
l'âge est au maximum est une cible. La graine de replantation est déduite des
drops en cherchant l'item qui repose ce bloc, à défaut dans l'inventaire du
golem.

La table de surcharge prévue ici n'a jamais été nécessaire. Le seul cas qu'elle
devait couvrir — une culture qui ne fournit aucune graine — est traité par une
règle générale plutôt que par une liste : cette culture n'est pas récoltée du
tout, et la case reste intacte.

C'est ce qui donne la compatibilité mods gratuitement : la plupart des cultures
moddées étendent `CropBlock`.

**2. Citrouilles et melons** (blocs-fruits sur tige)

Ni des `CropBlock`, ni dans `#minecraft:crops`. Pas de notion de maturité, pas de
replantation — la tige repousse seule.

Règle stricte : ne casser un bloc citrouille ou melon **que s'il est adjacent à
une tige attachée qui pointe vers lui**. Sans ça, le golem démonte les
citrouilles décoratives et les têtes de golems de neige du joueur. Ne jamais
casser la tige elle-même.

Récolte **à mains nues** : le melon donne des tranches, exactement ce qu'obtient
un joueur. Le contexte de loot « toucher de soie » d'abord retenu ramassait le
bloc entier — de la valeur créée à partir de rien, revenue après test en partie
réelle. La citrouille tombe entière en vanilla, elle n'est pas affectée.

**3. Baies douces**

Le buisson n'est jamais cassé : à partir de l'âge 2, il est cueilli et retombe à
l'âge 1, comme au clic droit du joueur. Pas de replantation — le buisson est
toujours debout — et pas de plantation de nouveaux buissons, décision explicite
pour que le golem ne colonise pas le terrain.

26.2 range ce rendement dans une table de loot dédiée
(`harvest/sweet_berry_bush`), donc aucune quantité n'est écrite en dur.

Le buisson est de type `PathType.DAMAGING`, malus -1 : le navigateur refuse d'y
entrer. Une baie au milieu d'un carré de baies devient alors inatteignable, le
golem ne récolte que le pourtour — constaté en jeu. Les deux malus,
`DAMAGING` et `DAMAGING_IN_NEIGHBOR`, sont donc ramenés à 0.

**Les cactus partagent `DAMAGING`** et il n'existe pas de malus par bloc :
ouvrir les baies ouvre les cactus. Le golem est donc rendu insensible aux deux
types de dégâts, sans quoi un golem en désert finirait par mourir contre un
cactus. Il peut toujours s'y bloquer physiquement — le cactus a une collision —
mais le goal abandonne une cible qu'il n'atteint pas.

**4. Nether wart**

`NetherWartBlock` n'est pas une `CropBlock`, mais son drop est le `BlockItem`
qui repose le bloc : la règle générique de replantation s'applique sans
adaptation. Pas de poudre d'os, le bloc n'est pas `BonemealableBlock`.

Semis par adjacence, sur ce que `#minecraft:supports_nether_wart` autorise
plutôt que sur le sable des âmes nommé en dur. Pas besoin du garde-fou du
labourage : ce support ne s'assèche pas.

**5. Labourage par adjacence**

Pas de mémoire NBT. Règle unique : une position est labourable si c'est de la
**terre nue adjacente à de la terre labourée**, dans le rayon.

Cette règle couvre deux besoins d'un coup — réparer le piétinement (une case
piétinée devient de la terre collée à de la terre labourée) et étendre
progressivement le champ.

**Garde-fou obligatoire** : ne labourer que si le golem a une graine à planter
immédiatement dedans. Sans cette condition, il laboure jusqu'aux limites du
rayon, et la terre labourée nue sèche puis redevient de la terre, qu'il relaboure
— boucle infinie.

Exclusions : bloc d'herbe, terre stérile, podzol, mycélium, terre enracinée. La
terre stérile est le piège : la houe la transforme en terre normale, pas en terre
labourée.

**Exclusion supplémentaire, découverte en partie réelle : les emplacements de
fruits.** Une tige fait pousser son melon sur une case voisine, posée sur la
terre en dessous. Cette terre est nue et collée à de la terre labourée, donc la
règle d'adjacence la prend — le golem laboure, sème, et la tige n'a plus jamais
où pousser. Une case dont le **dessus** est voisin d'une tige est donc refusée.
Les deux types de tige comptent : une tige attachée redevient libre dès que son
fruit est cueilli, et peut alors viser n'importe quelle direction.

*Limite assumée :* si la totalité du champ est piétinée, plus aucune case
labourée n'amorce la règle et le golem s'arrête. Le joueur relaboure une case à
la main. Cas rare, non traité.

**6. Poudre d'os**

Le golem applique de la poudre d'os sur les cultures non mûres de son rayon, s'il
en a.

Il ne s'approvisionne **que pendant un dépôt**, en prélevant dans le conteneur ce
qui s'y trouve. Jamais de trajet dédié à la recherche de poudre d'os. Un slot
d'inventaire lui est réservé, il n'en reste donc que 17 pour la récolte.

Sans effet sur les tiges de melon et de citrouille — comportement vanilla, ne pas
chercher à le contourner.

### Compatibilité mods

Objectif : marcher avec les mods de cultures (type Farmer's Delight) **sans
dépendance dure**. La compat vient de l'approche générique, pas d'un code
spécifique par mod.

#### Résultats mesurés — Farmer's Delight Refabricated 26.2-3.6.17

Testé en jeu. Le résolveur générique a tenu sur trois cultures sur quatre sans
rien changer. La quatrième, la tomate, a demandé un ajustement — lui aussi
générique, et sans une ligne spécifique au mod.

| Culture | Bloc | Comportement |
| --- | --- | --- |
| Chou | `CabbageBlock extends CropBlock` | récolté et replanté ✅ |
| Oignon | `OnionBlock extends CropBlock` | récolté et replanté ✅ |
| Riz | `RicePaniclesBlock extends CropBlock` | récolté et replanté ✅ |
| Tomate | `TomatoBlock extends CropBlock` | **ignoré**, la liane reste debout ✅ |

La poudre d'os fonctionne sur les quatre, qui implémentent `BonemealableBlock`.

Deux suppositions de cette spec étaient fausses :

- **Le riz n'est pas hors cas générique.** Le golem casse les panicules, la
  moitié haute qui est une `CropBlock`, et laisse le pied `RiceBlock` intact. Le
  plant repousse. Ça marche par construction, pas par chance : notre règle ne
  cible que les `CropBlock` mûres, et le pied n'en est pas une.
- **Les tomates ne sont pas hors de portée.** `TomatoBlock` étend `CropBlock`,
  donc le golem les récolte bel et bien.

**Les tomates sont désormais ignorées, pas saccagées.** Règle générique, sans une
ligne de code spécifique au mod : une culture mûre dont ni les drops ni
l'inventaire ne fournissent de quoi la reposer n'est **pas** récoltée. La casser
stériliserait la case, et un joueur sans graine ne rase pas sa parcelle non plus.

Vérifié dans le jar : `TomatoBlock.useWithoutItem` cueille les tomates et fait
redescendre l'âge sans casser la liane — le même principe que les baies. Mais
Farmer's Delight code ses drops en dur (`popResource` avec `ModItems.TOMATO`),
sans table de loot à interroger, et `TOMATO_SEEDS` pose `BUDDING_TOMATO_CROP`,
pas la liane. Il n'y a donc rien à lire ni rien à replanter : l'ignorer est le
seul comportement correct, et c'est celui que le joueur veut, ses lianes
continuant de produire.

**Mémoire par case, jamais par type de bloc.** Le blé peut tomber zéro graine sur
un tirage malchanceux ; blacklister le type aurait bloqué le blé définitivement,
puisque c'est le blé récolté qui fournit les graines. La note est levée dès que
le bloc change ou que le golem porte une graine adaptée.

Aucune culture vanilla n'est concernée : blé, betterave, torchflower et pitcher
dropent tous l'item qui repose leur bloc — lu dans leurs tables de loot.

Ne sont pas des `CropBlock` et restent donc invisibles au golem :
`BuddingTomatoBlock`, `RiceBlock` et `WildRiceBlock`.

**À vérifier avant de promettre quoi que ce soit :** que les mods visés soient
effectivement portés en 26.2. Beaucoup sont encore bloqués en 1.21.x.

### Survie et inventaire visible

Ajoutés après le premier test en partie réelle.

**Jamais de despawn.** `Mob.checkDespawn` supprime toute entité au-delà de la
distance de despawn de sa catégorie qui répond oui à `removeWhenFarAway`. Un
golem posé dans un enclos disparaissait pendant que le joueur minait. La réponse
est donnée en code plutôt qu'en posant le drapeau de persistance à l'invocation :
ce drapeau revient de la sauvegarde, donc les golems déjà placés resteraient
condamnés.

**Inventaire au clic droit**, en dépôt et retrait libres — c'est le chemin le
plus court pour lui donner de la poudre d'os. L'écran est un coffre vanilla, qui
n'existe qu'en rangées de neuf : la config compte donc des rangées et non des
slots.

Le `SimpleContainer` est sous-classé pour une seule réponse. Le sien dit oui à
tout le monde, indéfiniment : l'écran resterait ouvert et utilisable à l'autre
bout de la carte, et survivrait à la mort du golem. Il répond désormais par la
portée et l'existence, comme le bateau à coffre vanilla.

**Butin à la mort** : tout l'inventaire, plus une ou deux boules d'argile. Rien
ne le fait par défaut — `InventoryCarrier` ne contient aucun code de drop, et un
mob qui ne surcharge pas `dropEquipment` emporte sa cargaison, comme le
villageois. Le cheval à coffre est le contre-exemple, il surcharge
explicitement.

### Comportements repris du golem de cuivre

- **Ne pas suspendre l'activité au-delà de 32 blocs du joueur.** Contrairement
  aux autres mobs passifs, le golem de cuivre continue de travailler. C'est le
  comportement voulu pour une ferme qui tourne pendant que le joueur est ailleurs
  (dans la limite des chunks chargés).
- **Ouverture des portes non-fer**, utile si la ferme est clôturée.
- **Comportement dans l'eau** : le golem de cuivre coule. Nos cultures ont de
  l'eau à proximité par définition — prévoir que le golem l'évite plutôt que d'y
  tomber.

### Équilibrage — à revoir après test

Un bloc d'argile coûte 4 boules d'argile, et l'argile est renouvelable via boue +
stalactite. Le coût est donc quasi nul pour une ferme automatique complète.
Assumé pour un usage solo. Si ça doit être corrigé un jour, les leviers sont la
cadence et le rayon — **pas** le pattern d'invocation, qui reste simple.

## Étapes

Chaque étape doit être jouable et committée avant de passer à la suivante.

### Étape 1 — Squelette

Projet Fabric qui compile et se lance. Entité enregistrée, œuf de spawn creative,
déplacement aléatoire, modèle du golem de fer avec une texture unie temporaire.

*Validé quand :* le golem apparaît en jeu et se balade sans crash.

### Étape 2 — Récolte des cultures

Scan périodique dans le rayon, détection générique des `CropBlock` mûres,
navigation, récolte. Les drops tombent au sol pour l'instant. Ne piétine pas les
cultures.

*Validé quand :* il fait le tour du champ et casse uniquement le mûr.

### Étape 3 — Replantation

Récupération des drops via l'API de loot (sans faire pop d'entités), résolution
générique de la graine, remise du bloc à l'âge 0, le reste tombe au sol. Table de
surcharge pour les exceptions.

*Validé quand :* blé, carottes, patates et betteraves se replantent correctement.

### Étape 4 — Inventaire et dépôt

Inventaire persistant en NBT. Les drops vont dans l'inventaire au lieu du sol.
Départ vers le conteneur si plein, ou après 100 ticks sans rien à faire avec un
inventaire non vide. Gérer le conteneur plein et le conteneur détruit sans boucle
infinie.

Si les drops d'une récolte ne contiennent aucune graine replantable, en prélever
une dans l'inventaire plutôt que laisser la case vide. Ne se déclenche avec aucune
culture vanilla, qui dropent toutes leur graine — c'est un filet pour les cultures
moddées.

*Validé quand :* le cycle complet tourne 10 minutes sans intervention, et l'état
survit à un reload du monde.

### Étape 5 — Citrouilles, melons et labourage

Détection des blocs-fruits rattachés à une tige, récolte en toucher de soie.
Labourage par adjacence avec la condition de graine disponible.

*Validé quand :* il récolte une ferme à melons en blocs entiers sans toucher aux
citrouilles décoratives posées à côté, et répare un carré piétiné volontairement
sans déborder sur les chemins alentour.

### Étape 6 — Poudre d'os

Prélèvement pendant le dépôt, slot réservé, application sur les cultures non
mûres.

*Validé quand :* il vide la poudre d'os du coffre et accélère le champ, sans
faire d'aller-retour parasite quand le coffre est vide.

### Étape 7 — Invocation et finitions

Détection du pattern (les deux chemins), modèle et texture propres (export
Blockbench), sons, config, fichiers de langue FR/EN. L'œuf de spawn passe en
creative uniquement.

Scindée en deux, le modèle demandant un export Blockbench qui n'existe pas
encore :

- **7a** — détection du pattern et sons. La config et les fichiers de langue
  étaient déjà faits en chemin, et l'œuf de spawn est creative par nature.
  Les sons sont empruntés au bloc d'argile en attendant les nôtres.
- **7b** — modèle, texture, et le hitbox du golem de cuivre figé plus haut. À
  cette taille le volume tombe sous le seuil de `FarmlandBlock.fallOn` : vérifier
  que le mixin de piétinement devient inutile avant de le supprimer.

Le golem garde l'apparence du golem de fer entre les deux.

Faite finalement sans Blockbench : la géométrie est déclarée en Java comme le
fait le vanilla, et la texture est produite par `tools/GenArgilus.java`. Ce
générateur est versionné parce qu'il est la source du PNG — le repeindre à la
main reste possible, mais le fichier cesserait alors de décrire ce qui est sur
le disque.

### Étape 8 — Passe de compatibilité

Test avec au moins un mod de cultures tiers, si disponible en 26.2. Ajustement du
résolveur générique, documentation des limites.

## Hors périmètre pour la v1

Cultures aquatiques ou multi-blocs, récolte par clic droit avec repousse
partielle, verrues du Nether, plusieurs golems coordonnés, interface de
configuration en jeu, animations avancées.
