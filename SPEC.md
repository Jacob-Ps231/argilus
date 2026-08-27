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
| Invocation             | citrouille sculptée **ou lanterne** sur un bloc d'argile |
| Rayon d'action         | 12 blocs (config : 4 à 24)                        |
| Cadence de récolte     | 1 bloc max toutes les 20 ticks (1 s)              |
| Inventaire             | 9 slots                                           |
| Points de vie          | 20                                                |
| Déclencheur de dépôt   | inventaire plein **ou** plus rien à faire depuis 100 ticks (inventaire non vide) |
| Conteneur de dépôt     | le plus proche du centre du champ détecté, mémorisé |
| Cultures gérées        | toute `CropBlock` mûre (vanilla et moddée) + citrouilles/melons |
| Labourage              | par adjacence à de la terre labourée, si une graine est disponible |
| Poudre d'os            | prélevée dans le conteneur pendant un dépôt, 1 slot réservé |
| Melons                 | récoltés entiers (contexte de loot « toucher de soie ») |

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

### Récolte — trois familles distinctes

**1. Cultures classiques** (`CropBlock` avec un âge)

Résolveur **générique**, pas une liste codée en dur : toute `CropBlock` dont
l'âge est au maximum est une cible. La graine de replantation est déduite des
drops en cherchant l'item qui repose ce bloc. Une table de surcharge explicite
gère les exceptions connues.

C'est ce qui donne la compatibilité mods gratuitement : la plupart des cultures
moddées étendent `CropBlock`.

**2. Citrouilles et melons** (blocs-fruits sur tige)

Ni des `CropBlock`, ni dans `#minecraft:crops`. Pas de notion de maturité, pas de
replantation — la tige repousse seule.

Règle stricte : ne casser un bloc citrouille ou melon **que s'il est adjacent à
une tige attachée qui pointe vers lui**. Sans ça, le golem démonte les
citrouilles décoratives et les têtes de golems de neige du joueur. Ne jamais
casser la tige elle-même.

Récolte avec un **contexte de loot « toucher de soie »** : le melon est récupéré
en bloc entier, pas en tranches. Cette approche généralise aux fruits moddés,
contrairement à un ajout d'item en dur. La citrouille tombe déjà entière en
vanilla, elle n'est pas affectée.

**3. Labourage par adjacence**

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

*Limite assumée :* si la totalité du champ est piétinée, plus aucune case
labourée n'amorce la règle et le golem s'arrête. Le joueur relaboure une case à
la main. Cas rare, non traité.

**4. Poudre d'os**

Le golem applique de la poudre d'os sur les cultures non mûres de son rayon, s'il
en a.

Il ne s'approvisionne **que pendant un dépôt**, en prélevant dans le conteneur ce
qui s'y trouve. Jamais de trajet dédié à la recherche de poudre d'os. Un slot
d'inventaire lui est réservé, il n'en reste donc que 8 pour la récolte.

Sans effet sur les tiges de melon et de citrouille — comportement vanilla, ne pas
chercher à le contourner.

### Compatibilité mods

Objectif : marcher avec les mods de cultures (type Farmer's Delight) **sans
dépendance dure**. La compat vient de l'approche générique, pas d'un code
spécifique par mod.

#### Résultats mesurés — Farmer's Delight Refabricated 26.2-3.6.17

Testé en jeu. Le résolveur générique n'a demandé **aucun ajustement**.

| Culture | Bloc | Comportement |
| --- | --- | --- |
| Chou | `CabbageBlock extends CropBlock` | récolté et replanté ✅ |
| Oignon | `OnionBlock extends CropBlock` | récolté et replanté ✅ |
| Riz | `RicePaniclesBlock extends CropBlock` | récolté et replanté ✅ |
| Tomate | `TomatoBlock extends CropBlock` | récolté, **jamais replanté** ⚠️ |

La poudre d'os fonctionne sur les quatre, qui implémentent `BonemealableBlock`.

Deux suppositions de cette spec étaient fausses :

- **Le riz n'est pas hors cas générique.** Le golem casse les panicules, la
  moitié haute qui est une `CropBlock`, et laisse le pied `RiceBlock` intact. Le
  plant repousse. Ça marche par construction, pas par chance : notre règle ne
  cible que les `CropBlock` mûres, et le pied n'en est pas une.
- **Les tomates ne sont pas hors de portée.** `TomatoBlock` étend `CropBlock`,
  donc le golem les récolte bel et bien.

**Limite assumée — les tomates s'épuisent.** Elles ne dropent pas de graine, il
faut la fabriquer, donc la règle « la graine est le drop qui repose ce bloc » ne
trouve rien et la case reste nue. Le joueur récupère ses tomates dans le coffre
mais doit replanter à la main. Garder une tomateraie hors du rayon du golem, ou
accepter de la replanter.

Ne sont pas des `CropBlock` et restent donc invisibles au golem :
`BuddingTomatoBlock`, `RiceBlock` et `WildRiceBlock`.

**À vérifier avant de promettre quoi que ce soit :** que les mods visés soient
effectivement portés en 26.2. Beaucoup sont encore bloqués en 1.21.x.

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

### Étape 8 — Passe de compatibilité

Test avec au moins un mod de cultures tiers, si disponible en 26.2. Ajustement du
résolveur générique, documentation des limites.

## Hors périmètre pour la v1

Cultures aquatiques ou multi-blocs, récolte par clic droit avec repousse
partielle, verrues du Nether, plusieurs golems coordonnés, interface de
configuration en jeu, animations avancées.
