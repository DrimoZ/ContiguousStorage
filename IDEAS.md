# Contiguous Storage — marché et pistes

**Ce que fait ce fichier :** l'état du marché au 16 octobre 2026, et les idées de ce que le mod
pourrait apporter en plus du plan de `CLAUDE.md` §8. Chaque idée est triée : faisable ou non en
restant **serveur seul et sans mixin**, ce qu'elle coûte, et si elle vaut la peine.

**Ce que ce fichier ne fait pas :** décider. Une idée retenue part dans `DESIGN.md` (décision) puis
dans `CLAUDE.md` (travail). Ce fichier n'est pas une roadmap publique.

Marqueurs : **[lu]** = lu dans le code de FS (branche `1.21`) ; **[mesuré]** = chiffre tiré d'une API ;
**[supposé]** = à vérifier.

---

## 1. Le marché

### Les chiffres

**[mesuré]** API Modrinth, le 16 octobre 2026. CurseForge pèse beaucoup plus (FS y dépasse les 50 M),
mais les proportions servent quand même de repère.

| Mod | Téléchargements Modrinth |
|---|---|
| Tom's Simple Storage | 20,8 M |
| Sophisticated Storage | 5,5 M |
| Refined Storage | 3,0 M |
| **Functional Storage** | **1,14 M** |
| More Functional Storage (addon) | 9,5 k |
| Functional Storage Create Aero (addon) | 2 k |
| Create Style Functional Storage (addon) | 1,5 k |
| Adaptive Functional Storage (addon) | 42 |

**Les téléchargements de FS par version de Minecraft** (répartis par version de fichier) :

| Version | Part |
|---|---|
| **1.20.1** | **~61 %** (693 k) |
| 1.21 / 1.21.1 | ~33 % (378 k) |
| 1.19.2 | ~6 % |
| 26.1.2 | < 0,1 %, mais **26.1-1.6.4 est sortie le 14 octobre 2026** |

### Ce que ça veut dire

1. **Un addon de FS se télécharge peu en direct.** Le meilleur addon fait moins de 1 % du mod. Les
   joueurs n'ajoutent pas d'addons à FS eux-mêmes : c'est **un public de packs**. La page CurseForge
   doit donc parler aux auteurs de packs (config, serveur seul, coût mesuré) plus qu'aux joueurs.
2. **1.20.1 est la plus grosse population**, et de loin : c'est la version des gros packs (ATM9 et
   apparentés). Le port 1.20.1 (`CLAUDE.md` §11) passe devant 26.1 dans l'ordre de valeur. Il reste
   conditionné à la lecture de FS 1.2.x.
3. **Aucun concurrent direct.** Aucun addon sur Modrinth ne relie les tiroirs par contact. Adaptive
   Functional Storage relie *ses propres* tiroirs à sa Matrix, pas ceux de FS. Le slug
   `contiguous-storage` est **libre sur Modrinth** (404). CurseForge reste à vérifier à la main.

### La demande existe, et l'amont l'a déjà écartée

**[lu]** `Buuz135/FunctionalStorage#353` (ouverte), « Simple Storage Controller » : un contrôleur qui
lit les tiroirs voisins sans Linking Tool, « comme le Drawer Controller de Storage Drawers ». C'est
exactement ce mod. La réponse du mainteneur : utiliser le mode zone du Linking Tool.

Conséquences :

- **L'issue en amont de `DESIGN.md` §2 a sa réponse.** L'idée a été proposée et renvoyée vers le
  Linking Tool. Ouvrir une seconde issue a peu de chances d'aboutir. Une **PR** avec le BFS écrit
  reste possible, mais le mod ne doit pas l'attendre.
- **#353 est le premier endroit où annoncer le mod** à la release : les gens qui la suivent sont le
  public exact.

Autres issues qui parlent au mod :

| Issue | Ce qu'elle dit | Ce que ça change pour nous |
|---|---|---|
| #335 | Relier 20 480 tiroirs à la main ; lag au-delà de 256 tiroirs | La pose suffit, c'est un argument de vente. Et des réseaux de cette taille existent : le BFS doit les tenir (voir §3, idée P1). |
| #314 | Gel **client** de plusieurs secondes à chaque lien sur 200-300 tiroirs | Voir §3, idée P2 : chaque `ADD` coûte un gel côté client. |
| #161 | Tiroirs verrouillés qui « décrochent » au rechargement avec plusieurs contrôleurs | À reproduire en game test : le filet ne doit pas aggraver ce cas. |
| #382, #491, #493 | Duplications liées aux mises à jour de blocs, au déplacement de blocs, au déchargement de chunks | Notre `REMOVE` ne déplace aucun item, mais il faut un test qui le prouve avec Create (§3, idée C1). |

### Ce que FS fait déjà, à ne pas réinventer

**[lu]** Deux fonctions de Storage Drawers qu'on aurait pu croire absentes de FS :

- **Verrouiller tout un réseau** depuis le contrôleur : `StorageControllerTile.toggleLocking` propage
  le verrou à tous les tiroirs liés (`StorageControllerTile.java:162-169`).
- **Le double clic** sur le contrôleur range tout l'inventaire (`INTERACTION_LOGGER`, 300 ms,
  `StorageControllerTile.java:100`).

---

## 2. Une hypothèse de `DESIGN.md` qui ne tient pas

`DESIGN.md` §5 : « Des particules ou un rendu du réseau. Il faudrait un client. »

**C'est faux pour les particules.** `ServerLevel.sendParticles` envoie un paquet **vanilla**, que
n'importe quel client affiche sans le mod. Idem pour les sons (`level.playSound`) et les messages de
barre d'action. Un retour visuel reste donc possible **sans casser le « serveur seul »**. C'est ce qui
ouvre les idées V1 à V3.

Ce qui demande toujours un client : un rendu permanent (contours, overlay), une info dans Jade.

---

## 3. Les pistes

Légende du coût : **S** = moins d'une demi-journée, **M** = un à deux jours, **L** = plus.

### Retour au joueur (le trou le plus visible du mod)

En `STRICT`, casser un tiroir au milieu d'un mur coupe la moitié du réseau **sans que rien ne se
voie**. C'est la question ouverte de `DESIGN.md` §4. Ce sont les pistes les plus rentables.

| # | Idée | Coût | Avis |
|---|---|---|---|
| V1 | **Message de barre d'action** au joueur qui a coupé : « 12 tiroirs déconnectés ». `BreakEvent` donne le joueur, le tick suivant donne le compte (`validate` le rend déjà). | S | **À faire en v1.** |
| V2 | **Particules sur les tiroirs qui viennent d'être déconnectés** (fumée, une fois), et sur ceux qui viennent d'être reliés à la pose (`HAPPY_VILLAGER`). Le joueur **voit** la coupure. | S | **À faire en v1.** Sur un mur de 200 tiroirs, plafonner le nombre de paquets. |
| V3 | **Linking Tool en main = aperçu du réseau** : toutes les 10 ticks, quelques particules sur la frontière du réseau du contrôleur visé, et une couleur différente sur les tiroirs **liés mais pas connectés** (ceux que `STRICT` retirerait). | M | Fort, c'est l'équivalent serveur du rendu de FS. Coût réseau à mesurer. |
| V4 | Un son discret quand une coupure retire des tiroirs. | S | Facultatif, à voir en jeu avec V2. |

### Migration et administration (le public des packs)

Le seul moment où le mod défait le travail d'un joueur, c'est le passage en `STRICT` (`CLAUDE.md` §6).
Les packs ont besoin de le voir venir.

| # | Idée | Coût | Avis |
|---|---|---|---|
| A1 | **`/contiguousstorage dryrun`** : en n'importe quel mode, compte ce que `STRICT` retirerait sur les contrôleurs chargés, sans rien retirer. Le BFS existe déjà, il suffit de ne pas envoyer le `REMOVE`. | S | **À faire en v1.** C'est ce qui rend `STRICT` sûr à adopter sur un serveur existant. |
| A2 | **`/contiguousstorage inspect`** sur le contrôleur visé : taille du réseau, tiroirs liés à distance, tiroirs libres au contact, chunks qui manquent. | S | Utile au support (« pourquoi ce tiroir n'est pas relié ? »). |
| A3 | **`/contiguousstorage relink`** : lance `linkFree` sur tous les contrôleurs chargés. Rattrape un monde où le mod arrive après la construction, en `ASSIST`. | S | Utile, et presque gratuit. |
| A4 | Les commandes passent par Brigadier côté serveur : l'autocomplétion marche sur un client vanilla. Permission 2 pour `relink`, 0 pour `inspect` et `dryrun` limités au contrôleur visé. | — | Contrainte, pas une idée. |

### Règles de réseau (ce qui distingue le mod de « Storage Drawers sur FS »)

| # | Idée | Coût | Avis |
|---|---|---|---|
| R1 | **Conducteurs par défaut, en entrées optionnelles du tag** : `storagedrawers:trim` (`"required": false`). Un pack qui a les deux mods relie ses trims sans rien écrire. On ne parle pas à Storage Drawers : c'est une ligne de donnée. | S | Bon rapport. Vérifier les IDs des trims en 1.21.1. |
| R2 | Un mode « contact pour relier, distance pour garder ». | — | Écarté : c'est déjà `ASSIST`. |
| R3 | **Plafond de taille par réseau** (`MAX_NETWORK_SIZE`), en plus de la portée de FS. Protège un serveur des murs de 20 000 tiroirs de #335. | S | À faire **seulement** si le test de perf P1 le demande. |
| R4 | **Ignorer la portée de FS en `STRICT`** (réseau borné par la taille, pas par la boîte). | — | **Impossible sans mixin** : `rebuild()` retire tout tiroir hors de la boîte. Écarté. |
| R5 | **Les ender drawers ne conduisent pas** (option) : un ender drawer est lié à distance par nature, le faire conduire crée des réseaux qui passent par un bloc qui n'a rien de physique. | S | À débattre. Par défaut, garder la règle `instanceof`. |

### Performance (le risque, plus qu'une idée)

| # | Idée | Coût | Avis |
|---|---|---|---|
| P1 | **Test de charge** : un mur de 2 000 puis 20 000 tiroirs (#335), mesurer un passage du filet et un `linkFree`. | M | **Obligatoire avant la release.** Le BFS est borné par la boîte de FS, mais une boîte de rayon 64 contient déjà 2 M positions. |
| P2 | **[lu] Chaque `ADD` ou `REMOVE` provoque un gel côté client** sur un gros réseau : `markForUpdate` → `deserializeNBT` → `rebuildShapes` (`ConnectedDrawers.java:155`), le coût de #314. Notre file regroupe déjà par contrôleur et par tick (`Networks.java:176`). Une pose par seconde reste un gel par seconde, **comme avec le Linking Tool**. Option : retarder l'`ADD` de quelques ticks pour regrouper les poses rapides. | S | À mesurer en jeu sur 300 tiroirs. On ne fait pas pire que FS, mais on le fait **sans que le joueur clique**. |

### Compatibilité (où les bugs arriveront)

| # | Idée | Coût | Avis |
|---|---|---|---|
| C1 | **Create** : un tiroir déplacé par une contraption quitte le réseau, puis revient. Vérifier que ça ne duplique rien (#491) et que le filet ne coupe pas pendant le déplacement. | M | **À tester avant la release.** Create est dans la plupart des packs. |
| C2 | **Carry On** : prendre un tiroir et le reposer. La repose émet-elle `EntityPlaceEvent` ? **[supposé]** | S | À tester. Carry On a déjà ses bugs avec FS (#305, #488). |
| C3 | **Claims (FTB Chunks…)** : un joueur qui pose un tiroir contre le réseau d'un autre l'y relie. | M | **Question de design**, voir §4. |
| C4 | **Le fork 1.20.1 « Unofficial Extended Life »** de FS : si le port 1.20.1 se fait, vérifier aussi contre ce fork. | — | Au moment du port. |

### Hors du mod (à ne pas faire ici)

- **Jade / WTHIT** : « relié à : contrôleur en x y z ». Demande un client. Un petit plugin séparé et
  optionnel, si les joueurs le réclament.
- **Un bloc trim** : toujours hors scope (`DESIGN.md` §5). R1 couvre les packs qui ont Storage Drawers.

---

## 4. Une question nouvelle : à qui appartient un réseau ?

Avec l'auto-link, **poser un tiroir contre le mur d'un autre joueur le relie à son contrôleur**. Ce
que le réseau range peut alors finir dans ce tiroir. Sur un serveur, c'est un vol en un geste : je
pose un tiroir vide verrouillé sur un item, et je récupère ce que le contrôleur y range.

FS a le même risque à distance, mais **[lu]** le Linking Tool exige d'abord un clic sur le contrôleur
(`LinkingToolItem.java:107`), donc d'y avoir accès. L'auto-link enlève cette condition.

Pistes :

1. **N'auto-lier que si le poseur peut interagir avec le contrôleur** : `level.mayInteract(player,
   controllerPos)`, que la plupart des mods de claims respectent **[supposé]**. Coût S.
2. Une option de config `AUTO_LINK_REQUIRES_ACCESS` (défaut `true`).

**À trancher avant la release** : les serveurs le remarqueront. Je recommande 1, sans option tant que
personne ne la demande.

---

## 5. Ce que je recommande d'ajouter à la v1

**Retenu le 16 octobre 2026**, les cinq points : décisions dans `DESIGN.md` §3, travail dans
`CLAUDE.md` §8, étapes 9 à 13.

Par ordre de valeur pour un coût faible :

1. **V1 + V2**, le retour de coupure (barre d'action + particules). Ferme la question ouverte de
   `DESIGN.md` §4.
2. **A1**, `dryrun`. Rend `STRICT` adoptable sur un serveur existant.
3. **§4 point 1**, le contrôle d'accès à l'auto-link.
4. **P1 et C1**, test de charge et test avec Create, avant la release.
5. **R1**, les trims de Storage Drawers dans le tag.

Puis en v1.1 : V3 (aperçu au Linking Tool), A2, A3. Puis le port 1.20.1.

---

## Sources

- API Modrinth (`/v2/project`, `/v2/search`, `/v2/project/functional-storage/version`), 16 octobre 2026.
- Issues GitHub de `Buuz135/FunctionalStorage` : #161, #305, #314, #335, #353, #382, #480, #488, #491, #493.
- [Adaptive Functional Storage](https://modrinth.com/project/y0D2T7r6), [Functional Storage](https://modrinth.com/project/functional-storage),
  [Storage Drawers](https://www.curseforge.com/minecraft/mc-mods/storage-drawers),
  [guide Storage Drawers](https://craftdownunder.co/guides/mods/storage-drawers).
- Sources de FS, branche `1.21` : `StorageControllerTile`, `ConfigurationToolItem`, `ConnectedDrawers`,
  `ControllerRenderer`, `LinkingToolItem`.
