# Réseaux physiques pour Functional Storage — brief opérationnel

**Mod ID : `contiguousstorage`** (Contiguous Storage), choisi le 16 octobre 2026. Irréversible, voir §2.

**Ce que fait ce fichier :** l'opérationnel. Identité, cible technique, contraintes lues dans le code
de Functional Storage et de NeoForge, architecture, pièges connus, ordre de travail. C'est le fichier
à ouvrir avant d'écrire une ligne.

**Ce que ce fichier ne fait pas :** la réflexion produit — concept, positionnement, décisions
arrêtées et ouvertes, hors scope. Elle est dans `DESIGN.md`.

> Brief rédigé le 1er octobre 2026, depuis le projet Immaterial Drawers, par lecture des sources de
> Functional Storage (branche `1.21`, `mod_version` 1.5.8) et des sources patchées de NeoForge
> 21.1.248. **Rien n'a encore été compilé ni exécuté.** Les marqueurs :
> **[lu]** = lu dans le code, avec fichier:ligne ; **[à vérifier]** = supposé, pas encore lu ;
> **[vérifié]** = compilé et exécuté (le spike du 16 octobre 2026, voir `SPIKE.md`).
>
> Les références `Fichier.java:NN` renvoient à la branche `1.21` de FS, qui a **une demi-version
> d'avance** sur le jar publié 1.5.7 (voir §4). Leçon d'Immaterial Drawers : lire le bytecode du jar
> pour tout ce qui compte, pas seulement la branche.

---

## 1. Le mod en une ligne

Un tiroir de Functional Storage n'est relié à un Storage Controller que s'il le **touche**,
directement ou par une chaîne de tiroirs ou de blocs conducteurs, comme dans Storage Drawers. Il
n'ajoute **aucun bloc** et **aucun item** : il change la façon dont FS relie les tiroirs, rien d'autre.

→ `DESIGN.md` pour le pourquoi.

---

## 2. Identité et conventions — À RESPECTER STRICTEMENT

Le `modid` préfixe chaque nom de registre, chaque tag et chaque clé de config. **Le changer après une
release publique casse les configs et les datapacks des packs.** C'est moins grave qu'un modid de mod
à blocs (aucun monde ne l'enregistre), mais ça reste figé dès la première release.

| Élément | Valeur |
|---|---|
| Mod ID | **`contiguousstorage`** — préfixe de classes `CS` |
| groupId | `dev.drimoz.contiguousstorage` |
| Package racine | `dev.drimoz.contiguousstorage` (strictement identique au groupId) |
| Auteur | `DrimoZ` |
| Licence | **MIT** (code). `LICENSE-ASSETS` en All Rights Reserved pour le seul asset : le logo. |
| Dépôt | `https://github.com/DrimoZ/<NomDuMod>` |
| Slug | identique sur CurseForge et Modrinth. **Vérifier la faute de frappe avant l'approbation** (Immaterial Drawers a d'abord été créé en `immarterial-drawers`). |

**Convention reprise de PortableBeacons et d'Immaterial Drawers** : `dev.drimoz.<modid>`, avec le
groupId et le package racine identiques. Ne **pas** reproduire PunchThemAll (`com.drimoz.pta`).

`mod_license=MIT (code) / All Rights Reserved (assets)` : la chaîne est affichée telle quelle dans la
liste des mods.

### Découpage en packages

```
dev.drimoz.<modid>/
  <ModClass>.java          ← @Mod. Pas de ModuleController : il n'y a rien à enregistrer.
  <Prefix>Config.java      ← ModConfigSpec, voir §6
  network/                 ← NetworkWalker (BFS), ControllerRegistry, PendingChecks
  event/                   ← listeners : pose, voisinage, Linking Tool, ticks, chunks
  gametest/                ← <Prefix>GameTests
```

Exception, ajoutée le 17 octobre 2026 : `mixin/`, qui contient seulement le report du correctif de
FS contre le deadlock de chargement de la 1.5.7 (`DESIGN.md` §3). Il est appliqué à FS ≤ 1.5.7 par
`CSMixinPlugin`, et à supprimer quand FS publie la 1.5.8. Mixin exige un package dédié.

Exception, ajoutée le 21 octobre 2026 : `block/`, pour les trims (`DESIGN.md` §3). Il contient
`CSBlocks`, `TrimBlock`, `FramedTrimBlock`, `FramedTrimTile` et `TrimPack`, un pack généré en mémoire
pour les modèles, les noms, les recettes et les loot tables. Depuis, le mod n'est plus côté serveur
seul.

C'est petit à dessein. Si le mod a besoin d'un quatrième package, c'est qu'il est en train de devenir
autre chose.

---

## 3. Le mécanisme de FS qu'on contraint — ce qu'il faut savoir

### Un lien, c'est deux champs qui doivent s'accorder

- Côté contrôleur : une liste de positions, `ConnectedDrawers.connectedDrawers` (une `List<Long>`).
- Côté tiroir : `ControllableDrawerTile.controllerPos` **[lu]** `ControllableDrawerTile.java:51`.

**Un tiroir n'appartient qu'à un seul contrôleur.** `setControllerPos` retire d'abord le tiroir de
son ancien contrôleur **[lu]** `:200-208`.

### La seule porte d'entrée publique : `addConnectedDrawers`

`StorageControllerTile.addConnectedDrawers(ActionMode, BlockPos...)` **[lu]** `StorageControllerTile.java:203-228`.
C'est la méthode qu'appelle le Linking Tool. Elle fait tout ce qu'il faut et dans le bon ordre :

- `ADD` : vérifie la **portée** (AABB `inflate(getStorageMultiplier())`), vérifie que la cible est un
  `ControllableDrawerTile`, ignore les `StorageControllerBlock`, appelle `setControllerPos`, ajoute à
  la liste.
- `REMOVE` : retire de la liste **et** appelle `clearControllerPos()` sur le tiroir.
- Dans les deux cas : `rebuild()` puis `markForUpdate()`.

**Règle : on ne passe jamais par autre chose.** On ne touche ni à la liste, ni à `controllerPos`
directement. Un `REMOVE` qui ne viderait que la liste laisserait `controllerPos` en place, et le tiroir
**se relierait tout seul** au prochain chargement de chunk (voir plus bas).

### Ce qui est déjà géré par FS — à ne pas refaire

- **Casse d'un tiroir** : `Drawer.onRemove` appelle `addConnectedDrawers(REMOVE, pos)` **[lu]**
  `Drawer.java:364-375`. Idem pour les extensions (`StorageControllerExtensionBlock.java:70`) et les
  ender drawers (`EnderDrawerBlock.java:93`). `onRemove` est appelé par `LevelChunk.setBlockState`
  **quels que soient les flags** du `setBlock` **[lu]** NeoForge `LevelChunk.java:274`.
- **Casse du contrôleur** : `StorageControllerBlock.onRemove` fait `clearControllerPos()` sur tous
  ses tiroirs **[lu]** `StorageControllerBlock.java:72-85`.
- **Portée** : appliquée par `addConnectedDrawers` et par chaque `rebuild()` **[lu]** `ConnectedDrawers.java:67-75`.

Conséquence : quand on casse un tiroir **qui est lui-même lié**, FS le retire. Ce que FS ne voit pas,
c'est **ce que cette casse déconnecte derrière** : le reste de la chaîne. C'est exactement le travail
de ce mod.

### L'auto-réparation au chargement — le piège central

`ControllableDrawerTile.onLoad` **[lu]** `:124-145` : si le tiroir a un `controllerPos` mais n'est
pas dans la liste du contrôleur, **il s'y rajoute** par `addConnectedDrawers(ADD, …)`.

Deux conséquences :

1. **Un `REMOVE` complet (liste + `controllerPos`) tient.** C'est pour ça qu'on passe toujours par
   `addConnectedDrawers(REMOVE, …)`.
2. **`rebuild()` retire aussi des tiroirs qui ne sont simplement pas chargés** : `!level.isLoaded(pos)`
   → `continue`, donc absent de la nouvelle liste **[lu]** `ConnectedDrawers.java:73`. Ils reviennent
   par `onLoad`. **Un tiroir absent de la liste n'est donc pas forcément déconnecté.** Pour savoir à
   quel contrôleur un tiroir appartient, la vérité est `controllerPos`, pas la liste.

**[vérifié] dans le jar 1.5.7** (`SPIKE.md`) : `onLoad` existe et relie à nouveau comme sur la
branche, mais par un `getBlockEntity` **bloquant**, gardé par `isLoaded`. La branche, elle, passe par
`getChunkNow`. `invalidateCapabilities` et `onChunkUnloaded` font la même lecture, puis seulement un
`rebuild()` : c'est le deadlock d'Immaterial Drawers (son CLAUDE.md §11). Un `REMOVE` complet survit
à un rechargement, et un tiroir qui a gardé son `controllerPos` revient dans la liste.

**[lu jar] Autres écarts entre 1.5.7 et la branche, à respecter :**
- `getPriority` / `setPriority` **n'existent pas** en 1.5.7. Les appeler lève `NoSuchMethodError`.
- `setControllerPos` envoie d'abord un `REMOVE` à l'ancien contrôleur, par `TileUtil.getTileEntity`,
  qui est un `getBlockEntity` bloquant. **On ne relie donc que des tiroirs à `controllerPos == null`**,
  sinon on chargerait le chunk de l'ancien contrôleur.
- Test de portée exact : `new AABB(ctrl).inflate(getStorageMultiplier()).contains(Vec3.atCenterOf(pos))`.
- Le stack du Linking Tool porte le contrôleur dans un data component : `stack.get(FSAttachments.CONTROLLER)`.

### Ce qui conduit, ce qui se relie

Tout ce qui se relie est un `ControllableDrawerTile` **[lu]** : tiroirs, compacting, simple
compacting, fluid, ender, framed, **extensions de contrôleur** (`StorageControllerExtensionTile
extends ItemControllableDrawerTile`), et les tiroirs de n'importe quel addon qui en hérite (ceux
d'Immaterial Drawers inclus, sans une ligne de notre côté).

**`StorageControllerTile` est lui aussi un `ControllableDrawerTile`** **[lu]** `StorageControllerTile.java:42`.
Un BFS qui ne filtrerait que sur `instanceof ControllableDrawerTile` traverserait un **autre**
contrôleur. Voir la décision « deux contrôleurs dans un même bloc » dans `DESIGN.md`.

L'**Armory Cabinet** est un `ActiveTile` simple **[lu]** `ArmoryCabinetTile.java:27` : FS ne le relie
pas, donc il ne conduit pas, sauf si on l'ajoute au tag des conducteurs.

---

## 4. Cible technique

Identique à Immaterial Drawers, qui est la ligne compilée et testée :

| Élément | Valeur |
|---|---|
| Loader | NeoForge |
| Minecraft | 1.21.1 |
| NeoForge | `21.1.248` |
| Java | 21 |
| Build | ModDevGradle `2.0.75`, Gradle `8.8` |
| Parchment | `1.21.1` / `2024.11.17` |
| Functional Storage | `maven.modrinth:functional-storage:1.21-1.5.7` — **`implementation`, jamais bundlé** |
| Titanium | `com.hrznstudio:titanium:1.21-4.0.34` (maven BlameJared). Nécessaire **pour compiler** : les tiles de FS héritent de `ActiveTile`. Pas de `ModuleController`, pas de `@Save`. |

- **`1.5.8` n'est pas une release.** C'est le `mod_version` de la branche. On compile contre 1.5.7.
- `maven { url = 'https://api.modrinth.com/maven'; content { includeGroup 'maven.modrinth' } }` sert
  FS par numéro de version. CurseMaven n'est pas nécessaire.
- **Reprendre le `gradle.properties` d'Immaterial Drawers** (même format, substitution de template
  dans `src/main/templates/META-INF/neoforge.mods.toml`).
- **Bornes de version** : `minecraft_version_range=[1.21.1]`, `neo_version_range=[21.1,21.2)`. Ne jamais
  ouvrir à `[1.21.1,1.22)` ni `[21.1,)`.
- Dépendance FS dans le toml : `type = "required"`, `ordering = "AFTER"`. La version minimale reste à
  fixer (`[1.21-1.5.7,)` au minimum, voir §3 sur `onLoad`).

### Mod côté serveur uniquement

Le mod n'enregistre ni bloc, ni item, ni paquet réseau. **Les clients n'ont pas besoin de l'installer.**
- **[lu] Pas de `displayTest`** : cette clé n'existe pas en FML 21.1. Un mod qui n'enregistre aucun
  paquet laisse déjà entrer les clients vanilla. Reste **[à vérifier]** en jeu : `runServer`, puis
  connexion d'un client sans le mod.
- Les messages au joueur (barre d'action) en `Component.translatableWithFallback(clé, "texte anglais")` :
  un client sans le mod n'a pas le fichier lang.
- Le jour où le mod ajoute un bloc (un « trim » à la Storage Drawers), il cesse d'être serveur seul.
  C'est la raison pour laquelle les conducteurs décoratifs passent par un **tag** (§6).

### Mods de test dans le run de dev

`runtimeOnly`, jamais expédiés : **Jade** (voir `controllerPos` au réticule pendant les essais
manuels). C'est tout. Pas besoin de Powah, Mekanism ou Ars.

**Ajouter Immaterial Drawers en `runtimeOnly`** : un game test qui prouve qu'un tiroir d'addon conduit
et se relie vaut la peine. C'est la preuve que la règle `instanceof ControllableDrawerTile` généralise.

---

## 5. Architecture

### Trois mécanismes, et pourquoi il faut les trois

| Mécanisme | Hook | Rôle |
|---|---|---|
| **Auto-link** | `BlockEvent.EntityPlaceEvent` | Un tiroir posé contre un membre d'un réseau est relié. |
| **Détection** | `BlockEvent.NeighborNotifyEvent` | Un bloc a changé à côté d'un réseau : son contrôleur est marqué « à revérifier ». |
| **Filet** | `LevelTickEvent.Post`, tous les N ticks | Chaque contrôleur connu est revérifié, quoi que les événements aient raté. |

Les événements donnent la réactivité, le filet donne la garantie. Sans filet, un trou dans la
détection laisse un tiroir mal relié **définitivement**. Avec le filet, le pire cas est un tiroir mal
relié pendant N ticks.

### [lu] Ce que `NeighborNotifyEvent` voit, et ce qu'il ne voit pas

Sources patchées de NeoForge 21.1.248 : l'événement est émis **seulement** depuis
`Level.updateNeighborsAt` et `ServerLevel.updateNeighborsAt` / `updateNeighborsAtExceptFromFacing`
(`ServerLevel.java:~1112-1124`). C'est-à-dire seulement quand un `setBlock` porte le flag
`UPDATE_NEIGHBORS` (1), ou quand quelqu'un appelle `updateNeighborsAt` explicitement.

- **Couvert** : un joueur qui casse (`destroyBlock` → flag 3), `removeBlock` (flag 3), les explosions
  (passent par `destroyBlock` **[à vérifier]**), la plupart des mods.
- **Non couvert** : un `setBlock` sans flag 1 (WorldEdit et apparentés, certains placeurs de structures,
  des contraptions qui déplacent des blocs). C'est pour ça que le filet existe.
- `/setblock` et `/fill` posent en flag 2 puis appellent `blockUpdated` **[à vérifier]**, ce qui
  repasse par `updateNeighborsAt`.

**[lu]** L'événement est aussi émis **côté client** (`Level.java:360`) et par les répéteurs et
comparateurs (`DiodeBlock.java:205`). Le listener sort donc d'abord sur `level.isClientSide()`.
Explosions (`setBlock(...,3)`), `/setblock` et `/fill` (flag 2 puis `blockUpdated`) l'émettent tous.

**[lu] `EntityPlaceEvent`** est émis **après** le `setBlock`, alors que `captureBlockSnapshots` est
actif. Le block entity existe déjà, mais un autre mod peut encore annuler la pose. **L'auto-link ne
relie pas dans l'événement** : il met la position en file et la traite au `LevelTickEvent.Post`, en
vérifiant que le tiroir est toujours là.

L'événement est émis **pour la position modifiée**, avec les faces notifiées. Le listener regarde
les 6 voisins de `event.getPos()`. **C'est le chemin chaud du mod** : il tourne à chaque mise à jour de
bloc du serveur. Il doit sortir immédiatement quand le mode est `OFF`, et ne faire que des lectures
O(1) (`getChunkNow`, puis `getBlockEntity` sur un chunk déjà en main).

### Le BFS — `network/NetworkWalker`

À partir du contrôleur, parcours des 6 faces :

- **Traverse** : tout `ControllableDrawerTile` **libre ou déjà lié à ce contrôleur**, sauf un autre
  `StorageControllerTile`, et tout bloc du tag `#contiguousstorage:conductors`.
  **[vérifié]** Un tiroir lié à un **autre** contrôleur arrête aussi le parcours. Sans ça, un second
  contrôleur traverse les tiroirs du premier et capte les tiroirs libres posés contre eux : un pont.
  Le test 9 l'a trouvé à l'étape 5, une fois sur deux, selon l'ordre d'un `HashSet`. Il le vérifie
  maintenant aussi de façon déterministe.
- **Borne** : l'AABB de portée du contrôleur (`inflate(getStorageMultiplier())`), la même que FS. Le BFS
  ne peut pas exploser : il est borné par une boîte que FS borne déjà.
- **Résultat** : l'ensemble des positions atteintes, ou `null` si un chunk manquait.

**[vérifié]** Implémenté dans `network/NetworkWalker`. L'accès aux chunks est une
`LongFunction<LevelChunk>` indexée par `ChunkPos.asLong`. En jeu, c'est `loadedChunks(level)`, donc
`getChunkNow`. Dans le test 6, la même fonction masque un chunk. Le block entity se lit dans
`chunk.getBlockEntities()`, une simple lecture de map, alors que `getBlockEntity` peut en créer un.

Puis : chaque tiroir dont `controllerPos == ce contrôleur` et qui **n'est pas** atteint →
`addConnectedDrawers(REMOVE, …)`, en un seul appel pour tous (un seul `rebuild()`).

### ⚠️ Le BFS ne doit jamais charger un chunk

**Leçon payée par Immaterial Drawers** (CLAUDE.md §11, le deadlock de 1.5.7) : `Level.getBlockEntity`
sur un chunk qui n'est pas prêt est un fetch **bloquant**, et depuis le mauvais contexte il bloque le
thread serveur pour toujours. Le BFS lit les chunks par `level.getChunkSource().getChunkNow(x, z)`,
qui rend `null` au lieu d'attendre.

**Et si le BFS rencontre un chunk non chargé, il abandonne la vérification de ce contrôleur, sans
rien retirer.** Une chaîne dont un morceau n'est pas chargé n'est pas une chaîne coupée. Retirer dans
ce cas déconnecterait la moitié d'un mur chaque fois qu'un joueur s'éloigne. Le filet revérifiera
quand tout sera chargé.

### Le registre des contrôleurs — `network/ControllerRegistry`

Le filet a besoin de connaître les contrôleurs chargés. FS n'en tient pas de liste, et on ne peut pas
ticker leur tile.

- `ChunkEvent.Load` : parcourir `chunk.getBlockEntities()`, garder les `StorageControllerTile`.
  **[à vérifier]** sur quel thread `ChunkEvent.Load` est émis en 21.1. Si ce n'est pas le thread
  serveur, mettre en file et traiter au tick.
- `ChunkEvent.Unload` : retirer.
- `EntityPlaceEvent` d'un contrôleur : ajouter.
- Stockage : `Set<BlockPos>` par dimension. Pas de référence vers le tile (il peut être remplacé).

### Le Linking Tool

FS l'appelle directement (`LinkingToolItem.useOn` → `addConnectedDrawers`) **[lu]**
`LinkingToolItem.java:112-141`. Aucun événement n'en sort.

**Approche retenue par défaut : laisser faire, puis corriger.** `PlayerInteractEvent.RightClickBlock`
avec le Linking Tool en main : lire le contrôleur dans la stack (`FSAttachments.CONTROLLER`) et le
marquer à revérifier au tick suivant. Le tick retire ce qui n'est pas connecté et envoie au joueur un
message « N tiroirs non connectés ignorés ». C'est moins de code que d'intercepter, et ça couvre les
deux modes de l'outil (simple et zone) d'un coup.

Alternative : annuler l'interaction sur une cible non connectée. C'est plus propre pour le joueur en
mode simple, mais impossible à faire partiellement en mode zone. Décision dans `DESIGN.md`.

### Ce qui n'est pas couvert, et c'est assumé

Un autre mod qui appelle `addConnectedDrawers(ADD, …)` à distance. Le filet le corrige au passage
suivant. C'est cohérent avec la règle, mais c'est invisible pour ce mod.

---

## 6. Config — `<modid>-common.toml`

Config serveur. Elle s'applique au monde, pas au joueur.

| Clé | Défaut | Rôle |
|---|---|---|
| `MODE` | `ASSIST` (décidé, `DESIGN.md` §4) | `OFF` : FS d'origine. `ASSIST` : auto-link par contact, le Linking Tool marche toujours à distance. `STRICT` : connexion physique obligatoire. |
| `VALIDATION_INTERVAL_TICKS` | `100` | Période du filet. `0` désactive le filet (déconseillé, à documenter comme tel). |

Et un **tag de blocs**, pas une clé de config : `#<modid>:conductors`. Il ne contient par défaut que
les trims de Storage Drawers, en entrées optionnelles (étape 12). Un pack
y ajoute ce qui doit conduire sans être relié (blocs décoratifs, Armory Cabinet…). C'est de la donnée,
modifiable par datapack et rechargée par `/reload`.

**Un enum plutôt que deux booléens** : `REQUIRE=true, AUTO_LINK=false` est une combinaison où rien ne
relie plus jamais un tiroir sans le Linking Tool, puis le filet le retire. Ce n'est pas un mode, c'est
un piège.

**Passer en `STRICT` sur un monde existant coupe les liens à distance** au premier passage du filet.
C'est le comportement voulu, mais **la description de la clé dans le toml doit le dire en toutes
lettres**. C'est la seule action du mod qui défait le travail d'un joueur.

---

## 7. Game tests — `gametest/`

Modèle : `IDGameTests` d'Immaterial Drawers. Le linking est scriptable : `addConnectedDrawers` est
public.

**Piège hérité d'Immaterial Drawers (CLAUDE.md §7) : lier ne construit pas le réseau.**
`ConnectedDrawers` est construit avec un level `null`, et c'est le `serverTick` du contrôleur qui fait
`setLevel()` puis le premier vrai `rebuild()`. Tout test qui mesure juste après un linking mesure le
mauvais moment. **Attendre 2 ticks.** Même chose pour `onLoad`, qui arrive au tick suivant la pose.

Tests minimum, chacun avec le contrôle inverse :

1. `aDrawerPlacedAgainstTheControllerIsLinked` (ASSIST et STRICT) ; en OFF, il ne l'est pas.
2. `aDrawerPlacedAgainstTheChainIsLinked` — à trois tiroirs de distance.
3. `breakingTheMiddleOfAChainUnlinksTheFarSide` — et le côté proche reste lié.
4. `anExplosionThatCutsTheChainUnlinks`.
5. `aSilentSetBlockIsCaughtByTheSafetyNet` — `setBlock(pos, AIR, 2)` : rien pendant N−1 ticks, retiré à N.
6. `anUnloadedPartOfTheChainUnlinksNothing` — **le test le plus important**. **[vérifié]**
   `GameTestHelper` n'a pas d'API de chunk : le test passe à `Networks.validate` un accès aux chunks
   qui masque celui du **milieu d'une chaîne intacte**, et attend `ABORTED`. Le cas d'une chaîne
   réellement coupée dont le bout est illisible n'est pas ce test. Le BFS s'arrête à la coupure, avant
   le chunk illisible, et les tiroirs de ce chunk ne sont pas candidats : rien n'est retiré, mais le
   résultat est `0`, pas `ABORTED`. La première version du test confondait les deux cas.
7. `theLinkingToolCannotLinkAcrossAGap` (STRICT) ; en ASSIST, il le peut.
8. `aConductorTagBlockCarriesTheNetwork` — et un bloc hors tag ne le fait pas.
9. `aSecondControllerDoesNotStealOrBridge` — selon la décision de `DESIGN.md`.
10. `anAddonDrawerConductsAndLinks` — avec un tiroir d'Immaterial Drawers.
11. `aWallOfDrawersDoesNotRevalidateEveryTick` — 50 tiroirs, aucun `REMOVE` et aucun BFS en dehors
    des passages du filet. C'est le pendant du test anti-rebuild d'Immaterial Drawers.
12. `aDrawerPlacedByAnOutsiderIsNotLinked` — et le même joueur, avec l'accès, relie.
13. `aCutReportsTheDisconnectedDrawers` — le bon compte, et le plafond de particules tient.
14. `dryrunCountsWithoutRemoving` — en `ASSIST`, le compte est juste et rien n'est retiré.

---

## 8. Ordre de travail

**Ne pas dévier de l'ordre. La tâche 1 conditionne l'architecture.**

1. **[BLOQUANT] Spike, sous forme de game test.** **Fait, réussi** (`SPIKE.md`). Build qui compile contre FS 1.5.7, une classe `@Mod`
   vide, et deux tests :
   - un `addConnectedDrawers(REMOVE, …)` survit à un déchargement puis rechargement de chunk (le tiroir
     ne se relie **pas** tout seul) ;
   - le chemin `onLoad` / `invalidateCapabilities` du **jar 1.5.7** se comporte comme lu au §3.
   *Si le premier échoue, s'arrêter : tout le mod repose sur un REMOVE qui tient.*
2. **`NetworkWalker`** (BFS + règles de traversée + abandon sur chunk non chargé), testé seul.
   **Fait** le 16 octobre 2026 : chaîne, gap et diagonale, second contrôleur, portée, et chunk
   absent (test 6, au niveau du walker). Le tag des conducteurs est déjà lu, mais testé à l'étape 8.
3. **Validation à la demande** : contrôleur marqué → BFS → `REMOVE` groupé. Tests 3, 4, 6.
   **Fait** le 16 octobre 2026 : `Networks.validate`. Les candidats sont les tiroirs dont
   `controllerPos` vaut ce contrôleur, lus dans les chunks **chargés** de l'AABB de portée. Un seul
   `REMOVE` est envoyé. La méthode rend le nombre de tiroirs retirés, ou `ABORTED`. Le marquage arrive
   avec le filet (étape 6) : pour l'instant, la méthode est appelée directement.
4. **Auto-link à la pose.** Tests 1, 2, 9, 10.
   **Fait** le 16 octobre 2026.
   - `EntityPlaceEvent` met la position en file, et `LevelTickEvent.Post` appelle
     `Networks.linkPlaced`. Pour chaque bloc posé, on cherche le contrôleur auquel il appartient : le
     bloc lui-même ou un de ses 6 voisins, s'il est un contrôleur ou un tiroir lié à un contrôleur.
     `linkFree` lance alors le BFS depuis ce contrôleur et relie **tous les tiroirs libres atteints**
     en un seul `ADD`.
   - Le même code couvre un tiroir posé, un contrôleur posé contre des tiroirs libres, et un tiroir
     qui fait le pont vers une chaîne libre.
   - La config `MODE` est arrivée avec cette étape, parce que le test 1 a besoin de `OFF`. Les tests
     qui dépendent du mode tournent dans des batchs à eux (`@BeforeBatch` / `@AfterBatch`), puisque
     la config est globale et que les tests partagent le level.
   - **Pièges des game tests :** `helper.setBlock` n'émet pas `EntityPlaceEvent`, il faut poser par
     un joueur simulé (`placeAt`). `placeAt` lit l'objet **dans la main** du joueur, pas la stack
     passée en paramètre. La couche y=1 du template `wall` est pleine.
   - Immaterial Drawers est chargé en `runtimeOnly` depuis `../ImmaterialDrawers/build/libs`, s'il y
     est. Le test 10 est `required = false`.
   - **Reste pour l'étape 8 :** un tiroir posé contre une chaîne de **conducteurs** ne trouve pas
     encore son contrôleur, parce qu'un conducteur ne porte pas de `controllerPos`.
5. **Détection par `NeighborNotifyEvent`**, avec un coût mesuré : sortie immédiate en `OFF`.
   **Fait** le 16 octobre 2026.
   - Le listener sort au premier test sauf en `STRICT` côté serveur, puisque seul `STRICT` retire
     quoi que ce soit. Cela couvre aussi `ASSIST` et le client.
   - En `STRICT`, `Networks.neighborChanged` lit les 6 voisins dans la map des block entities des
     chunks via `getChunkNow`, et marque leur contrôleur. `LevelTickEvent.Post` valide les
     contrôleurs marqués.
   - Tests : en `STRICT`, une casse au milieu d'une chaîne coupe le bout sans appel explicite ; en
     `ASSIST`, elle ne coupe rien.
   - **Reste à faire :** mesurer le coût réel au profiler sur une base redstone (§12).
6. **Registre des contrôleurs et filet.** Tests 5, 11.
   **Fait** le 16 octobre 2026.
   - **Le registre** contient des positions par dimension. Il se remplit par trois voies :
     `ChunkEvent.Load`, qui ne lit que les positions des contrôleurs du chunk ; la pose d'un
     contrôleur ; et tout contrôleur marqué par un voisin, ce qui couvre aussi un contrôleur posé par
     commande ou par un autre mod. `ChunkEvent.Unload` le vide.
   - **Le filet** tourne en `STRICT` à chaque `gameTime % validationIntervalTicks == 0`. Un contrôleur
     dont le chunk n'est pas prêt est sauté sans être oublié ; un contrôleur disparu est oublié.
   - **Tests :** le 5 vérifie qu'un `setBlock(…, AIR, 2)` reste invisible jusqu'au passage du filet,
     puis se fait retirer. Le 11 vérifie qu'un mur de 50 tiroirs est parcouru une fois par passage,
     jamais entre deux, et rien n'est retiré. Le test 11 tourne seul dans son batch, parce que
     `Networks.validations` est un compteur global.
   - Le serveur de game tests enchaîne les ticks sans attendre : 250 ticks prennent environ 2 s.
7. **Linking Tool.** Test 7.
   **Fait** le 16 octobre 2026, avec l'option « laisser faire puis corriger ».
   - En `STRICT`, `RightClickBlock` (côté serveur, Linking Tool en main) lit
     `stack.get(FSAttachments.CONTROLLER)` et marque ce contrôleur avec le joueur. Le tick suivant
     valide le contrôleur et envoie au joueur, en barre d'action, « N drawer(s) not touching… »
     (`translatableWithFallback`). Le même code couvre les modes simple et zone.
   - Le test simule ce que fait `ServerPlayerGameMode.useItemOn` : l'événement, puis
     `ItemStack.useOn`. Il vérifie que FS a bien relié le tiroir (sinon il ne prouverait rien), puis
     que le lien est défait en `STRICT` et maintenu en `ASSIST`.
   - **Non testé :** le contenu du message, car le joueur simulé ne le reçoit pas. À voir en jeu.
8. **Config et tag.** Test 8.
   **Fait** le 16 octobre 2026.
   - **Config :** `mode` (arrivé à l'étape 4) et `validationIntervalTicks` (étape 6). Les deux
     descriptions dans le toml disent ce qu'elles font, et `mode` avertit que `STRICT` coupe les liens
     à distance.
   - **Tag :** `#contiguousstorage:conductors` n'est pas livré, un tag absent vaut un tag vide. Les
     game tests ont le leur dans `src/gametest/resources` (cuivre), un source set de ressources ajouté
     au mod **dans les runs de dev seulement**. Le jar a été vérifié : le fichier n'y est pas.
   - **Pose contre des conducteurs :** un conducteur ne porte pas de `controllerPos`, donc
     `addOwners` remonte la file de conducteurs depuis le bloc posé, jusqu'à 512 blocs, pour trouver
     le contrôleur au bout.
   - **Limite :** casser un conducteur qui ne touche **aucun** tiroir ne marque rien, puisque
     `neighborChanged` ne lit que des block entities. Le filet le rattrape au passage suivant.

Les étapes 9 à 13 viennent de l'étude du 16 octobre 2026 (`IDEAS.md` §5, décisions dans
`DESIGN.md` §3). Elles passent toutes avant la release.

9. **Contrôle d'accès à l'auto-link** (`IDEAS.md` §4). Test 12.
   - Dans `linkPlaced`, ne relier que si le joueur qui a posé pourrait interagir avec le contrôleur
     trouvé : `level.mayInteract(player, controllerPos)` **[à vérifier]** dans 1.21.1, et
     **[à vérifier]** que FTB Chunks le respecte (il peut passer par ses propres événements).
   - La file de poses doit donc garder le joueur avec la position. Une pose sans joueur (un autre mod
     qui émet `EntityPlaceEvent` avec une entité non joueur) ne relie rien.
   - Test 12 `aDrawerPlacedByAnOutsiderIsNotLinked` : joueur simulé en mode aventure, ou claim
     simulé si l'API le permet ; contrôle inverse, le même joueur en survie relie.
   - **Fait** le 17 octobre 2026 (`883d8ae`).
     - **[lu]** `Level.mayInteract` vaut `true` dans `Level`. Dans `ServerLevel`, il teste seulement
       la protection du spawn et la bordure du monde (`ServerLevel.java:806`).
     - Le test 12 fait donc passer la bordure du monde entre deux contrôleurs, dans un batch à part.
       La protection du spawn est inactive sur un serveur de game tests.
     - **Limite assumée pour la v1, décidée le 17 octobre 2026 : les claims ne sont pas couverts.**
       Les mods de claims (FTB Chunks, Open Parties and Claims…) protègent en annulant des
       événements, pas par `mayInteract` **[supposé, non lu]**. Sur un serveur à claims, un joueur
       peut donc encore relier son tiroir au mur d'un autre en le posant hors du claim, contre ce
       mur. À écrire dans le README et la page du mod. Une intégration optionnelle avec les claims
       est reportée après la v1.
10. **Retour de coupure** (`IDEAS.md` V1, V2). Test 13.
   - Le joueur qui a cassé : `BreakEvent` le met avec le contrôleur marqué, comme le fait déjà le
     Linking Tool à l'étape 7. Au tick, `validate` rend le compte : message en barre d'action
     « N drawer(s) disconnected » (`translatableWithFallback`), seulement si N > 0.
   - Particules : `ServerLevel.sendParticles`, une fois par tiroir retiré (fumée), et une fois par
     tiroir relié à la pose (`HAPPY_VILLAGER`). **Plafond** de tiroirs à particules par coupure
     (32), pour ne pas inonder un client sur un mur de 2 000 tiroirs.
   - `validate` et `linkFree` doivent donc rendre les positions, pas seulement le nombre.
   - Test 13 : le compte rendu est le bon, et le plafond tient. Le message lui-même ne se teste pas
     (le joueur simulé ne le reçoit pas, voir l'étape 7).
   - **Fait** le 17 octobre 2026.
     - `Networks.broken`, appelé par `BreakEvent`, marque les contrôleurs du bloc cassé et de ses
       voisins, avec un `Report` pour le joueur.
     - `validate` et `linkFree` passent par `unlink` et `link`, qui rendent les positions.
       `strays` calcule ce que STRICT délierait sans rien retirer : il resservira pour `dryrun`.
     - Les particules sont `SMOKE` à la coupure et `HAPPY_VILLAGER` à l'auto-link, plafonnées à
       `PARTICLE_CAP = 32` tiroirs.
     - **Le message se teste quand même.** Un `Player` de test (`ListeningPlayer`) garde ce que la
       barre d'action aurait montré. Le test vérifie « 3 drawer(s) disconnected » puis « 120 … »,
       c'est-à-dire le texte de repli qu'un client sans le mod reçoit. Il vérifie aussi 3 puis 32
       tiroirs à particules. Il tourne seul dans son batch, puisque `particlesSent` est global.
11. **Commande `/contiguousstorage dryrun`** (`IDEAS.md` A1). Test 14.
   - `RegisterCommandsEvent`, dans `event/` : pas de nouveau package (§2).
   - Fait le travail de `validate` sur les contrôleurs chargés de la dimension, **sans** le
     `REMOVE`, dans n'importe quel mode. Rend : contrôleurs lus, contrôleurs sautés (chunk manquant),
     tiroirs que `STRICT` retirerait. Permission 2.
   - Test 14 `dryrunCountsWithoutRemoving` : en `ASSIST`, une chaîne coupée donne le bon compte et
     les tiroirs restent liés.
   - **Fait** le 18 octobre 2026 (`21785df`).
     - `Networks.dryRun` s'appuie sur `strays` et parcourt les contrôleurs du registre de la
       dimension. Il rend `DryRun(checked, skipped, strays)`. Un contrôleur dont le chunk ou une partie
       du réseau n'est pas chargé compte comme sauté.
     - La commande est enregistrée dans `CSEvents.onRegisterCommands`, avec la permission
       `LEVEL_GAMEMASTERS`. Sa réponse a un texte de repli anglais. Elle rend le nombre de tiroirs,
       ce qui permet `execute store`.
     - Le test 14 compte sur le contrôleur du test seul, parce que les autres tests laissent des
       contrôleurs dans le monde. Il lance aussi la vraie commande par le dispatcher, avec une source
       qui capture la réponse.
     - **Limite :** la commande ne voit que le registre. Un contrôleur posé par commande en OFF n'y
       entre qu'au rechargement de son chunk.
12. **Trims de Storage Drawers dans le tag** (`IDEAS.md` R1). Test 8 étendu.
   - Cela **change l'étape 8** : le tag est désormais livré, avec des entrées `"required": false`.
     Un pack sans Storage Drawers garde un tag vide, et un pack peut toujours le remplacer
     (`"replace": true`).
   - **[à vérifier]** les IDs des trims de Storage Drawers en 1.21.1 (un par essence de bois, a priori).
   - Vérifier que le jar contient ce fichier, et **toujours pas** le tag des game tests.
   - **Fait** le 21 octobre 2026 (`e20cfe6`).
     - **[lu]** IDs sur la branche `1.21` de Storage Drawers (13.2.0, Minecraft 1.21, jar 1.21.1 non
       lu) : `oak_trim`, `spruce_trim`, `birch_trim`, `jungle_trim`, `acacia_trim`, `dark_oak_trim`,
       `mangrove_trim`, `cherry_trim`, `bamboo_trim`, `crimson_trim`, `warped_trim`, `framed_trim`
       (`core/ModBlocks.java`). Ils sont tous en `"required": false` : un ID faux ne coûte rien.
     - **Piège trouvé :** dans un même mod, deux fichiers au même chemin se masquent au lieu de
       fusionner. Le tag de test (cuivre) est donc passé dans un mod de dev à part,
       `contiguousstorage_testdata`. Il n'a aucun code (`lowcodefml`) : c'est seulement
       `src/gametest/resources` et son `neoforge.mods.toml`.
     - Le test 8 vérifie aussi que le tag contient exactement le cuivre en dev. Si une entrée avait
       fait échouer le chargement, tout le tag serait tombé.
     - Jar vérifié : le tag livré contient 12 trims et pas de cuivre.
13. **Test de charge et test avec Create** (`IDEAS.md` P1, P2, C1).
   - Charge : un mur de 2 000 puis 20 000 tiroirs (`Buuz135/FunctionalStorage#335`). Mesurer un
     passage du filet et un `linkFree`. Si un passage dépasse quelques ms, ajouter un plafond de
     taille de réseau (`IDEAS.md` R3), pas avant.
   - Gel client : sur 300 tiroirs en jeu, chaque `ADD` déclenche `rebuildShapes` chez le client
     (`ConnectedDrawers.java:155`, issue #314). Vérifier que poser vite ne gèle pas plus qu'avec le
     Linking Tool.
   - Create : un tiroir déplacé par une contraption puis reposé. Rien ne doit être dupliqué (#491),
     et le tiroir doit revenir dans le réseau à la repose.
   - Résultats dans `MANUAL_TESTS.md` pour ce qui ne se fait pas en game test.
   - **Fait** le 21 octobre 2026 (`6517c0f` pour le scénario étendu). Le détail est dans `MANUAL_TESTS.md`.
     - **Charge :** linéaire, et l'index des membres évite les parcours.
     - **Gel #314 :** non reproduit à 306 tiroirs.
     - **Create :** il ne déplace pas les tiroirs de FS, que FS met dans `c:relocation_not_supported`.
       Un tiroir d'Immaterial Drawers, lui, bouge sans duplication.
       - En STRICT, il est délié à distance et n'est **pas** relié à nouveau au retour, puisqu'aucun
         joueur ne l'a posé.
       - En ASSIST, il reste relié.
     - **Client sans le mod :** refusé proprement (`runClientWithoutMod`).
14. Docs publiques, page CurseForge, release. Annoncer le mod sur `Buuz135/FunctionalStorage#353`.

Avant la tâche 1, **proposer l'option en amont** (issue ou PR sur `Buuz135/FunctionalStorage`) : si
FS l'intègre, ce mod n'a pas besoin d'exister. Voir `DESIGN.md` §2.

---

## 9. Commandes

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
./gradlew runGameTestServer
```

Sources de référence en local, dans `.reference/` (gitignoré) :

```bash
git clone --depth 1 -b 1.21 https://github.com/Buuz135/FunctionalStorage.git .reference/FunctionalStorage
```

Les sources patchées de Minecraft + NeoForge sont dans `build/moddev/artifacts/neoforge-21.1.248-sources.jar`
après le premier build. C'est là qu'ont été lus les points de NeoForge de ce brief.

---

## 10. Documentation — mêmes règles qu'Immaterial Drawers

Chaque fichier a un seul métier et l'annonce en ouverture, y compris ce qu'il n'est pas.

| Fichier | Langue | Quand |
|---|---|---|
| `CLAUDE.md` | fr | maintenant (celui-ci) |
| `DESIGN.md` | fr | maintenant |
| `SPIKE.md` | en | après la tâche 1 : analyse, puis ce qui s'est réellement passé, puis où l'analyse avait tort |
| `README.md`, `ROADMAP.md`, `CHANGELOG.md` | en | **après le spike**. Un README qui promet un comportement non validé est le premier fichier à réécrire. |
| `STORE.md` | en | avant la release : copie CurseForge prête à coller |
| `LICENSE` / `LICENSE-ASSETS` | — | au premier commit |
| `NOTICE` | en | **seulement** si du code de FS est copié ou adapté (MIT : préserver `Copyright (c) 2021 Buuz135, Rid`). Le plan actuel n'en copie pas. |

- **La doc arrive avec le code**, dans le même commit, jamais lors d'une passe ultérieure.
- **Une branche par version de Minecraft, `main` = la plus récente.** Chaque version garde sa branche.
- `CHANGELOG.md` : le plus récent en haut, `Added` / `Changed` / `Fixed` / `Notes`, versions `{mod}+{minecraft}`.

---

## 11. Ports — à estimer, pas à promettre

| Cible | Ce qui change | État |
|---|---|---|
| 1.20.1 (Forge 47, FS 1.20.1-1.2.14) | Forge, pas NeoForge. Événements équivalents à retrouver (`BlockEvent.NeighborNotifyEvent` existe sur Forge). | **[à vérifier]** que `addConnectedDrawers`, `controllerPos` et l'auto-réparation au chargement existent sous cette forme dans FS 1.2.14. |
| 26.1 (NeoForge 26.1, FS 26.1-1.6.1) | Java 25. | **[à vérifier]** idem. |

Un mod sans bloc ni asset se porte vite, **à condition** que l'API de linking de FS soit la même. C'est
la première chose à lire sur chaque branche, avant d'estimer quoi que ce soit.

---

## 12. Limites de ce brief

- Tout est **lu**, rien n'est **exécuté**. Les points marqués [à vérifier] sont des suppositions.
- Lu sur la branche `1.21` de FS, pas sur le jar 1.5.7. La dérive entre les deux a déjà coûté une
  erreur de compilation et un deadlock à Immaterial Drawers.
- Le coût réel du listener `NeighborNotifyEvent` n'est pas mesuré. L'estimation (6 lectures O(1) par
  mise à jour de bloc, nulles en `OFF`) est à confirmer au profiler sur une base redstone chargée.
