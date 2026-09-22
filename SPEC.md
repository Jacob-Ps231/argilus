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

Depuis 26.2, ce rendement vient d'une table de loot dédiée
(`harvest/sweet_berry_bush`), donc aucune quantité n'est écrite en dur. Depuis le
portage en 26.3, elle est lue par le helper vanilla
`Block.dropFromBlockInteractLootTable`, qui remplit lui-même les paramètres de
contexte — dont `ORIGIN`, que 26.3 rend obligatoire.

La clé de cette table n'est pas codée en dur : elle est dérivée du bloc récolté,
`harvest/<id du bloc>`, là où se trouve celle du buisson vanilla. Un buisson
moddé qui livre la sienne à cet endroit rend donc ses propres baies. Sans rien à
cet endroit, on retombe sur la table vanilla — c'est exactement ce que le
buisson moddé donne au joueur s'il n'a pas redéfini sa cueillette, vanilla y
nommant sa table en dur.

Ce n'est pas une convention garantie : sur les trois tables `harvest/` de
vanilla, celle du buisson est la seule à ne desservir qu'un bloc. `beehive` sert
la ruche et le nid d'abeilles, `cave_vine` sert les deux blocs de vigne
caverneuse et ne porte l'id ni de l'un ni de l'autre. C'est un point d'entrée
offert aux mods, pas une règle du jeu.

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

#### Résultats mesurés — Farmer's Delight Refabricated

Mesuré une première fois sur `26.2-3.6.17`, revérifié sur `26.3-3.6.26` après le
portage.

Testé en jeu. Le résolveur générique a tenu tel quel sur le chou et l'oignon. Les
deux autres, la tomate et le riz, ont demandé chacune une règle — génériques
toutes les deux, écrites sur la forme de la culture et non sur le mod, sans une
ligne qui le nomme.

| Culture | Bloc | Comportement |
| --- | --- | --- |
| Chou | `CabbageBlock extends CropBlock` | récolté et replanté ✅ |
| Oignon | `OnionBlock extends CropBlock` | récolté et replanté ✅ |
| Riz | `RicePaniclesBlock extends CropBlock` | panicules récoltées, le pied les repose ✅ |
| Tomate | `TomatoBlock extends CropBlock` | cueilli, la liane reste debout ✅ |

La poudre d'os fonctionne sur les quatre, qui implémentent `BonemealableBlock`.

Deux suppositions de cette spec étaient fausses :

- **Le riz n'est pas hors cas générique.** Le golem casse les panicules, la
  moitié haute qui est une `CropBlock`, et laisse le pied `RiceBlock` intact. Le
  plant repousse. Ça marche par construction, pas par chance : notre règle ne
  cible que les `CropBlock` mûres, et le pied n'en est pas une.

  **Mesuré à nouveau en 26.3 : le riz avait cessé de marcher**, et pas à cause du
  portage. La règle « laisser debout ce qu'on ne peut pas replanter », arrivée en
  1.1.0, l'avait éteint sans que personne rejoue le riz : à main nue les
  panicules donnent `rice_panicle`, qui n'est pas la graine — `rice` ne tombe
  qu'avec un couteau — donc plus rien à replanter, donc case notée et
  définitivement ignorée. Vu en jeu, ça ressemble à « le golem ignore le riz ».
  Corrigé par la règle 2 ci-dessous.
- **Les tomates ne sont pas hors de portée.** `TomatoBlock` étend `CropBlock`,
  donc le golem les récolte bel et bien.

**Le point de départ : rien ne replante une liane à tomates.** Ni les drops ni
l'inventaire ne fournissent de quoi la reposer. La table de loot du bloc existe
pourtant et donne bien des `tomato_seeds` à la casse — mais cette graine pose
`budding_tomatoes`, pas la liane, donc la règle générique (« chercher dans les
drops l'item qui repose ce bloc ») ne trouve rien.

Trois règles s'enchaînent alors, **dans cet ordre, qui est celui du code**.

**Règle 1 : avant d'abandonner la case, demander au bloc.** Une culture qui
fructifie sur un plant qu'elle garde ne se casse pas, elle se cueille — et la
cueillette est une interaction, que ni les drops ni une table de loot ne
décrivent. Le golem appelle donc `useWithoutItem` sur le bloc, exactement le clic
droit du joueur, et passe à la règle suivante si le bloc refuse.

Vérifié dans le jar : `TomatoBlock.useWithoutItem` fait tomber les tomates au sol
et redescend l'âge sans casser la liane. Ce que le bloc fait tomber, le golem le
ramasse avec son goal de collecte, comme n'importe quel item au sol. Aucune ligne
spécifique au mod : aucune `CropBlock` vanilla ne surcharge `useWithoutItem`, un
champ vanilla répond donc `PASS` et n'est pas touché.

Précision : c'est le clic droit **à main vide**. Le clic d'un joueur passe
d'abord par `useItemOn`, toujours, et n'atteint `useWithoutItem` que si le
premier renvoie « essaie à main vide » ; un mod qui logerait sa cueillette dans
`useItemOn` resterait hors de portée du golem. La réciproque existe aussi : le
golem attaque `useWithoutItem` directement, donc un bloc que `useItemOn`
refuserait serait quand même cueilli par lui.

**Le joueur passé est `null`.** C'est une liberté prise, pas un usage supporté :
un golem n'est pas un joueur et rien ne fournit de doublure. Un bloc qui lit ce
paramètre lève, et un mod compilé contre une autre version du jeu lève aussi
(`LinkageError`) — les deux sont attrapés, le type de bloc est noté, on ne le lui
redemande plus de la partie. Un bloc qui répond « oui » sans cesser d'être
récoltable est traité comme un refus, sans quoi il serait resollicité à chaque
scan ; le verdict se lit sur la case seule, jamais sur ses voisines.

**Conditionné à `mobGriefing`.** Le bloc rend sa récolte au monde, pas à
l'appelant, et un mob ne ramasse au sol que si cette règle est active. Règle
inactive, le golem ne tente pas la cueillette du tout : vider le plant du joueur
dans un tas que personne ne ramasse serait pire que de le laisser tranquille.

**Règle 2 : une culture qui pousse sur une plante est un fruit.** Si le
bloc du dessous est lui-même une plante qui grandit — un `VegetationBlock` qui
implémente `BonemealableBlock` —, casser le haut ne stérilise rien : la plante
reste et repose son fruit toute seule. Le golem récolte donc sans replanter,
puisque replanter est le travail de la plante. C'est le cas du riz, et d'un
étage de culture quel qu'il soit construit de la même façon.

Aucun champ vanilla ne peut être lu comme un fruit, et pas par chance : une
`CropBlock` ne se pose que sur `#minecraft:supports_crops` et le nether wart que
sur `#minecraft:supports_nether_wart`, qui valent respectivement terre labourée
et sable des âmes — aucun des deux n'est un végétal.

**Une culture sous une culture n'est pas une plante porteuse**, c'est la même
culture un étage plus bas : une liane à tomates grimpe sa corde jusqu'à trois de
haut. Sans cette exclusion, le golem prend le haut de la pile pour un fruit et
casse la liane du joueur — vu en relecture, jamais en jeu.

**Règle 3 : sinon, laisser debout.** Rien ne peut la replanter, le bloc a refusé
d'être cueilli, et rien ne la repose : la casser stériliserait la case pour de
bon. Un joueur sans graine ne rase pas sa parcelle non plus, et le golem ne doit
pas être le moins bon fermier des deux. La case est notée pour que le trajet ne
soit pas refait à chaque scan.

**Mémoire par case, jamais par type de bloc** — celle des replantations, à ne pas
confondre avec celle des blocs qui lèvent ci-dessus, qui porte sur du code et
vaut donc pour tout le type. Le blé peut tomber zéro graine sur
un tirage malchanceux ; blacklister le type aurait bloqué le blé définitivement,
puisque c'est le blé récolté qui fournit les graines. La note est levée dès que
le bloc change ou que le golem porte une graine adaptée.

Aucune culture vanilla n'est concernée : blé, betterave, torchflower et pitcher
dropent tous l'item qui repose leur bloc — lu dans leurs tables de loot.

Ne sont pas des `CropBlock` et ne sont donc jamais récoltés :
`BuddingTomatoBlock`, `RiceBlock` et `WildRiceBlock`. `RiceBlock` n'est pas
invisible pour autant : la règle 2 le lit comme la plante qui porte les
panicules, sans jamais y toucher.

**À vérifier avant de promettre quoi que ce soit :** que les mods visés soient
effectivement portés en 26.3. Beaucoup sont encore bloqués en 1.21.x.

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

### Étape 9 — Modèle Blockbench, icône et œuf

Le modèle refait sous Blockbench, et non plus déclaré à la main comme en 7b.

Si la géométrie bouge, `tools/GenArgilus.java` doit suivre dans le même
mouvement : ses rectangles sont dictés par les `texOffs` de `ArgilusModel`, et un
décalage d'un côté peint la mauvaise face de l'autre.

L'étape porte aussi les deux images ChatGPT qu'il reste à remplacer, voir la
règle n°4 : `icon.png`, refait depuis une capture en jeu ou un rendu du nouveau
modèle, et l'œuf de spawn, redessiné à la main — `GenArgilus.java` refuse
délibérément de produire ces deux-là pour ne pas écraser de l'artwork.

*Validé quand :* le golem rendu en jeu correspond à l'export, les six finitions
sont toujours distinctes, et la page Modrinth ne porte plus aucune image générée.

Scindée en deux, les images demandant un travail à la main que le modèle
n'attend pas :

- **9a** — le modèle refait sous Blockbench et les textures qui le suivent. La
  tête passe à 8×4×8 et son patron descend en `texOffs(0, 42)`, faute de place à
  côté de la couronne ; la hitbox reste celle du golem de cuivre, seule la
  hauteur des yeux suit la nouvelle tête. Le générateur y gagne la mousse en
  taches, des facettes de pierre, des fleurs et des fissures, celles du visage
  écrites en dur. Validée en jeu le 22 septembre 2026.
- **9b** — `icon.png` et l'œuf de spawn, puis la page Modrinth. C'est elle qui
  porte la dernière clause du *Validé quand*. Les images sont faites le 22
  septembre 2026 : les deux icônes sont recadrées depuis une capture en jeu,
  l'œuf est un œuf d'argile moussu en 16×16, peint dans Blockbench. Reste la
  page Modrinth.

## Hors périmètre pour la v1

Cultures aquatiques ou multi-blocs, récolte par clic droit avec repousse
partielle, verrues du Nether, plusieurs golems coordonnés, interface de
configuration en jeu, animations avancées.
