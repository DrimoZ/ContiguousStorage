# Essais manuels en jeu

**Ce que fait ce fichier :** la liste des vérifications à faire à la main, en jeu, avant la release.
Il couvre ce que les game tests ne peuvent pas voir : un vrai client, un vrai déchargement de chunk,
le message au joueur, le coût sur une vraie base. **Ce qu'il ne fait pas :** il ne remplace pas les
game tests (`./gradlew runGameTestServer`, 22 tests) et ne décrit pas l'architecture (`CLAUDE.md`).

Cocher au fur et à mesure. Noter tout écart en dessous de l'essai concerné, avec la date.

---

## 0. Mise en place

```bash
./gradlew runClient
```

- Créer un monde **créatif**, plat, avec les commandes activées.
- Objets :
  `/give @s functionalstorage:storage_controller`, `/give @s functionalstorage:oak_1 64`,
  `/give @s functionalstorage:linking_tool`.
- **Voir si un tiroir est relié :**
  - Prendre le Linking Tool, faire un clic droit sur le contrôleur pour le configurer, puis le garder
    en main. FS affiche alors la portée et les tiroirs reliés **[à vérifier]**, c'est le rendu de FS
    et pas le nôtre.
  - Contrôle fonctionnel, qui marche à coup sûr : mettre un objet dans un tiroir, puis vérifier qu'il
    apparaît en passant par le contrôleur, par exemple avec un entonnoir sous le contrôleur.
- **Config :** `run/config/contiguousstorage-common.toml`, avec les clés `mode` et
  `validationIntervalTicks`.
  - NeoForge recharge normalement une config commune quand le fichier change **[à vérifier]**. Si le
    changement de mode n'a aucun effet, quitter le monde et le rouvrir.
- **Piège du dev :** dans les runs de dev, le **cuivre est conducteur**, parce que le tag de test de
  `src/gametest` est fusionné au mod. Le jar livré, lui, n'a pas de tag (§4).

---

## 1. Mode ASSIST (défaut)

- [ ] **Pose contre le contrôleur** : un tiroir posé contre le contrôleur est relié.
- [ ] **Pose au bout d'une chaîne** : avec une chaîne de 5 tiroirs reliés, un tiroir posé au bout est
      relié. Un tiroir posé à un bloc d'écart ne l'est pas.
- [ ] **Contrôleur posé contre un mur** : construire un mur de tiroirs libres, puis poser le
      contrôleur contre lui. Tout le mur est relié, dans la portée.
- [ ] **Portée** : une ligne de 12 tiroirs depuis le contrôleur. Les tiroirs au-delà de la portée de
      FS (8 sans upgrade) restent libres.
- [ ] **Casse au milieu** : casser le 3e tiroir d'une chaîne. **Rien ne se coupe**, car ASSIST ne
      retire jamais rien. Le tiroir cassé, lui, est retiré par FS.
- [ ] **Linking Tool à distance** : relier un tiroir isolé à 4 blocs. Il est relié et le reste.
- [ ] **Deux contrôleurs** :
  - Placer A, puis un tiroir T relié à A, puis un contrôleur B contre T. T reste à A.
  - Un tiroir posé contre B seul va à B. Un tiroir posé contre T va à A.

## 2. Mode STRICT

Passer `mode = "STRICT"`.

- [ ] **Passage sur un monde existant** : avant de changer de mode, relier un tiroir à distance au
      Linking Tool. Après le passage en STRICT, ce tiroir est délié **en moins de 5 s** (100 ticks),
      sans aucune action.
- [ ] **Casse au milieu** : casser le 3e tiroir d'une chaîne de 6. Les tiroirs 4 à 6 sont déliés
      **tout de suite**, et les tiroirs 1 et 2 restent reliés.
- [ ] **Explosion** : `/summon tnt` au milieu d'une chaîne. Tout ce qui ne touche plus le contrôleur
      est délié.
- [ ] **`/setblock`** : `/setblock <milieu de chaîne> air`. Le bout est délié tout de suite, parce que
      la commande appelle `blockUpdated`.
- [ ] **Linking Tool à travers un vide, mode simple** : le tiroir est relié, puis délié au tick
      suivant.
  - Un message apparaît en barre d'action : « 1 drawer(s) not touching the controller's network were
    not linked ». **C'est le seul endroit où ce message est vérifié.**
- [ ] **Linking Tool, mode zone** : sélectionner une boîte qui contient des tiroirs collés et des
      tiroirs isolés. Seuls les isolés sont déliés, et le message donne leur nombre.
- [ ] **Unlink puis rechargement du monde** : casser le milieu d'une chaîne, quitter le monde et le
      rouvrir. Le bout est **toujours délié**. C'est le spike, en vrai.

## 3. Déchargement réel — le point que le spike n'a pas couvert

- [ ] **Chaîne sur deux chunks** :
  - Avec F3 + G pour voir les bordures, construire une chaîne qui traverse une bordure de chunk, le
    contrôleur d'un côté.
  - `/tp @s ~1000 ~ ~`, attendre 10 s, revenir.
  - En STRICT : **tout est encore relié**.
- [ ] **Moitié chargée** :
  - Placer le contrôleur à la limite de la view distance, avec une chaîne qui part vers l'extérieur.
  - Se placer de façon qu'une partie de la chaîne soit hors des chunks chargés, puis attendre au
    moins deux passages du filet (10 s).
  - En revenant, rien n'est délié. Une chaîne qu'on ne peut pas lire n'est pas une chaîne coupée.
- [ ] **Pas de blocage au chargement** : rouvrir un monde qui contient un grand mur relié en STRICT.
      Le monde charge normalement, sans blocage à 100 %. C'est le piège de FS 1.5.7, voir
      `SPIKE.md`.

## 4. Conducteurs

- [ ] **Cuivre en dev** : contrôleur, 2 blocs de cuivre, puis un tiroir posé au bout. Le tiroir est
      relié. Le même montage en planches ne relie rien.
- [ ] **Casser un conducteur (STRICT)** : casser le cuivre du milieu. Le tiroir au bout est délié,
      **au plus tard au passage du filet** (5 s). Si le cuivre cassé ne touchait aucun tiroir, aucun
      événement ne voit la coupure.
- [ ] **Tag du jar livré** : dans une instance qui utilise le jar
      (`build/libs/contiguousstorage-0.1.0+1.21.1.jar`), le cuivre ne conduit pas. Avec Storage
      Drawers installé, ses trims conduisent (entrées optionnelles du tag, étape 12). Sans lui, le
      monde charge sans erreur de tag.
  - Ajouter un datapack contenant `data/contiguousstorage/tags/block/conductors.json` avec
    `{"values": ["minecraft:copper_block"]}`, puis `/reload` : le cuivre conduit.

## 4 bis. Trims (ajoutés le 21 octobre 2026)

Rien de cette section n'a été vu en jeu : il faut un client.

- [ ] **Rendu des trims en bois** : chaque trim montre la bûche de son bois en cadre et ses planches au
      centre, légèrement en retrait. Vérifier les 10 bois, y compris crimson et warped (tige).
- [ ] **Item en main et dans l'inventaire** : même rendu que le bloc.
- [ ] **Trim encadré** : avec la recette encadrée de FS (bloc A, bloc B, trim encadré), le cadre prend
      A et le centre prend B. Le look reste après la pose, après un rechargement du monde, et après
      la casse puis la repose.
- [ ] **Trim encadré en item** : un trim habillé s'affiche probablement **sans** son habillage dans
      l'inventaire. FS dessine ses items encadrés avec un rendu propre à ses items, que les nôtres
      n'utilisent pas. Limite probable, à confirmer.
- [ ] **Noms** : « Oak Trim », « Dark Oak Trim », « Framed Trim ». Le mod est maintenant requis côté
      client, donc un client sans le fichier de langue n'existe plus.
- [ ] **Onglets créatifs** : les trims sont dans l'onglet de FS et dans « Building Blocks ». Avec
      `trimsEnabled = false`, ils n'y sont plus, leurs recettes disparaissent après `/reload`, et un
      trim posé ne porte plus le réseau.
- [ ] **Hache** : un trim se casse vite à la hache.

## 5. Client sans le mod

Depuis les trims, le mod n'est plus côté serveur seul : un client sans le mod doit être **refusé
proprement**, pas planter.

```bash
./gradlew runServer
./gradlew runClientWithoutMod
```

- Premier lancement : passer `eula=true` dans `run/eula.txt`, et `online-mode=false` dans
  `run/server.properties`.
- `runClientWithoutMod` est un client avec FS, Titanium et les mods de dev, mais sans Contiguous
  Storage (source set vide `nomod`, dossier `run-nomod`). Il se connecte tout seul à `localhost:25565`.
- [x] **Refusé proprement**, le 21 octobre 2026. Le client reste sur l'écran de déconnexion, sans
  plantage, avec la raison « The server send registries with unknown keys:
  ResourceKey[minecraft:block / contiguousstorage:crimson_trim], … ». Côté serveur, on lit seulement
  « lost connection: Disconnected ». Le message est celui de NeoForge : il nomme nos trims, donc un
  joueur comprend quel mod lui manque.
- Les messages en `translatableWithFallback` restent utiles : un client qui a le mod mais un pack de
  langue sans nos clés lit l'anglais.

## 6. Coût

- [ ] **STRICT, base redstone à côté des tiroirs** :
  - Monter une horloge redstone rapide, collée à un mur de 50 tiroirs reliés.
  - `/debug start`, attendre 30 s, puis `/debug stop`. Ouvrir le profil dans `run/debug/`.
  - `CSEvents.onNeighborNotify` et `Networks.validate` ne doivent pas ressortir.
- [ ] **ASSIST / OFF** : même montage. Le listener doit sortir au premier test, donc avoir un coût
      négligeable.

---

## Scénario joué dans un vrai client — `./gradlew runClient -Pscenario`

Le mod de développement `contiguousstorage_testdata` (`src/gametest`, jamais dans le jar) joue ce
scénario tout seul dans un vrai client, sur le monde `run/saves/scenario` (monde plat).

- **Comment il joue.** Les poses et les casses passent par le `gameMode` du client, comme pour un
  joueur, donc par les vrais paquets. Le Linking Tool est utilisé de la même façon. Les messages de la
  barre d'action et du chat sont capturés, et des captures d'écran sont prises en chemin.
- **Comment il vérifie.** Chaque résultat attendu est lu sur le serveur intégré, des deux côtés du lien
  (`controllerPos` sur le tiroir, liste du contrôleur), puis écrit dans
  `run/scenario/report-phaseN.txt`.
- **Phase 1** : construire et vérifier, puis sauvegarder et quitter.
- **Phase 2** : relancer la même commande, qui recharge le monde et vérifie que tout a tenu.
- Pour rejouer depuis zéro : supprimer `run/scenario` et recopier un monde plat dans
  `run/saves/scenario`.

| Étape | Ce qui est fait | Attendu |
|---|---|---|
| A1–A2 (ASSIST) | Pose d'un contrôleur, de 5 tiroirs en ligne, puis d'un tiroir à un bloc d'écart | La ligne est reliée, l'isolé est libre |
| A3 | Linking Tool sur le contrôleur, puis sur le tiroir isolé | Relié (ASSIST permet la distance) |
| A4 | Casse du 3e tiroir | Rien n'est délié |
| S1 (STRICT) | Passage en STRICT | En moins de 6 s, le tiroir relié à distance et les tiroirs coupés sont déliés |
| S2 | Repose du 3e tiroir | La ligne entière se relie toute seule |
| S3 | Casse du 2e tiroir | Le bout est délié, « 3 drawer(s) disconnected » en barre d'action, fumée |
| S4 | Linking Tool à travers le vide | « 1 drawer(s) not touching… », le tiroir reste libre |
| S5 | Horloge d'observateurs collée à un tiroir pendant 5 s | 0 parcours de réseau |
| Z (STRICT) | Linking Tool en **mode zone** sur 5 tiroirs : 3 contre le contrôleur, 2 après un vide | Les 3 restent reliés, les 2 sont déliés, « 2 drawer(s) not touching… » |
| I | Contrôleur, **tiroir d'énergie d'Immaterial Drawers** posé par le joueur, puis un tiroir FS derrière | Les deux sont reliés : un tiroir d'addon conduit et se relie |
| K0 | Contrôle Create : piston mécanique, moteur créatif, planches | Les planches montent de 2 blocs (le montage marche) |
| K1–K5 | Même montage avec un tiroir FS relié, 32 diamants | Rien ne bouge, rien n'est dupliqué, le tiroir reste relié |
| M1–M7 | Piston collant Create, **tiroir d'énergie d'Immaterial Drawers** relié, 10 000 FE, poussé de 2 blocs puis ramené, en STRICT puis en ASSIST | Ni duplication ni perte. STRICT : délié une fois éloigné. ASSIST : reste relié |
| W | Mur de 306 planches, puis de 306 tiroirs, posés un par tick | Écart maximal entre deux ticks du client, comparé entre les deux murs (gel de FS #314) |
| T1 | Barre d'inventaire avec les trims, dont un trim encadré habillé (diamant et or) | Capture du rendu des items |
| T2 | Contrôleur, trim chêne, trim sapin, trim encadré nu, trim encadré habillé, tiroir | Tiroir relié, habillage gardé à la pose, capture rapprochée |
| T3 | La même rangée en planches | Tiroir libre |
| T4 | `/contiguousstorage dryrun` tapé dans le chat | Une réponse |
| T5 | Onglets créatifs | Les trims y sont |
| T6 | Trims désactivés + `/reload`, puis réactivés | Désactivés : tiroir libre, aucune recette, absents des onglets. Réactivés : tout revient |
| C1 | Chaîne à cheval sur une bordure de chunk, joueur à 3 000 blocs pendant 20 s, puis retour | Tout est encore relié, pas de gel |
| P1–P4 (phase 2) | Rechargement du monde | Charge sans geler, les coupures de S3 tiennent, les trims (habillage compris) et la chaîne de C1 tiennent |

### Résultats du 21 octobre 2026

- **Premier passage : 33 réussis, 14 échoués, tous dus au pilote.** Une téléportation et une action
  dans le même tick perdent l'action, parce que le serveur n'a pas encore reçu la confirmation de la
  téléportation. Le pilote a été corrigé : chaque déplacement a maintenant son étape.
- **Second passage, monde neuf : phase 1 = 47 réussis, 0 échoué ; phase 2 = 13 réussis, 0 échoué.**
- **Captures** (`run/screenshots/scenario_*.png`) :
  - les trims chêne et sapin ont leur bûche en cadre et leurs planches au centre ;
  - le trim encadré nu affiche le damier gris, comme les blocs encadrés nus de FS ;
  - le trim encadré habillé a son cadre en diamant et son centre en or, et il reste identique après
    le rechargement ;
  - Jade affiche « Spruce Trim, Contiguous Storage » ;
  - le message « 3 drawer(s) disconnected » et la fumée apparaissent à la coupure ;
  - le toast « New Recipes Unlocked » montre un trim, donc les recettes générées sont chargées ;
  - **le trim encadré habillé s'affiche avec son habillage en main et dans la barre d'inventaire.**
    La limite prévue par la session des trims ne se produit pas.

### Résultats du 21 octobre 2026, scénario étendu (Z, I, K, M, W), Create 6.0.10 et Immaterial Drawers 0.2.1

- **Phase 1 : 67 réussis, 0 échoué. Phase 2 : 13 réussis, 0 échoué.**
- **Mode zone du Linking Tool (Z)** : correct, en STRICT. Les 3 tiroirs qui touchent restent reliés,
  les 2 qui ne touchent pas sont déliés, et le message annonce bien 2 tiroirs.
- **Immaterial Drawers (I)** : un tiroir d'énergie posé contre le contrôleur est relié, et un tiroir
  FS atteint seulement à travers lui l'est aussi.
- **Create, tiroirs FS (K)** : **Create ne déplace pas les tiroirs de FS.** FS les met tous, avec le
  contrôleur et les extensions, dans le tag `c:relocation_not_supported`, que Create respecte. Le
  contrôle K0 prouve que le montage fonctionne : les planches montent. Avec le tiroir, le piston reste
  rétracté. Il n'y a donc ni duplication (#491) ni lien à défaire. **Ce qui n'est pas dans ce tag peut
  bouger** : nos trims, les tiroirs d'addon et les conducteurs du tag.
- **Create, tiroir d'Immaterial Drawers (M)** : il bouge, et rien n'est dupliqué ni perdu. On compte
  1 tiroir et 10 000 FE à chaque étape, et aucun objet au sol.
  - **STRICT** : poussé à 2 blocs, il est délié. Ramené contre le contrôleur, il **reste libre** :
    l'auto-link ne relie que ce qu'un joueur pose, et une contraption ne pose rien au sens des
    événements. C'est la règle d'accès décidée (`DESIGN.md` §3). Pour le relier à nouveau : le
    Linking Tool, ou le casser et le reposer. **À écrire dans le README.**
  - **ASSIST** : relié, il reste relié à 2 blocs, puis une fois ramené. Create garde `controllerPos`
    dans les données du bloc, et l'auto-réparation de FS au chargement le relie à nouveau.
  - Premier passage avec un piston non collant : le tiroir ne revient pas, ce qui ne prouvait rien.
    Le scénario utilise maintenant un piston collant.
- **Gel client de FS #314 (W)** : **non reproduit à 306 tiroirs.** Pour le mur de planches, l'écart
  maximal entre deux ticks du client est de 72,3 ms ; pour le mur de tiroirs, de 73,7 ms. Aucun écart
  ne dépasse 100 ms dans les deux cas. La pose moyenne côté serveur prend 0,51 ms pour les planches et
  0,79 ms pour les tiroirs. Le dernier tiroir du mur est relié. Sur deux passages précédents, les
  écarts allaient de 63,7 à 71,3 ms, avec le même résultat. #314 parle de réseaux bien plus grands :
  ce n'est pas prouvé au-delà.
- **Client sans le mod** : refusé proprement, voir §5.

**Toujours pas couvert :**
- **Le gel client de FS (#314) au-delà de 306 tiroirs.**
- **Un trim déplacé par Create au milieu d'une chaîne** : en STRICT, il coupe la chaîne derrière lui.
  Revenu, il ne relie rien, pour la même raison que M5. Ce cas est déduit, pas joué.
- **Le déchargement réel en C1** est très probable (3 000 blocs, distance de vue 8), mais il n'est pas
  prouvé par un relevé.
- **Le chargement de phase 2 sans gel** ne compte qu'un seul essai : sur serveur dédié, le gel de
  FS 1.5.7 n'arrivait que 2 fois sur 3 sans le correctif.

## Résultats

### Campagne du 16 octobre 2026 — serveur dédié, piloté par Claude en RCON

Ce qui a tourné :
- un vrai serveur (`./gradlew runServer`), sur un monde plat ;
- des chunks forcés et déforcés, et des redémarrages réels ;
- un contrôle de l'état par `/data get block`, qui lit `controllerPos` sur le tiroir et
  `connectedDrawers` sur le contrôleur.

**Aucun joueur n'était connecté**, donc les liens ont été créés en posant chaque tiroir avec
`controllerPos` en NBT. FS le relie alors tout seul dans son `onLoad`. Tout ce qui demande un client
reste à faire à la main.

| Section | Essai | Résultat | Notes |
|---|---|---|---|
| §1 | Casse au milieu en ASSIST | ✅ | Rien n'est délié, ni la chaîne ni le tiroir distant. |
| §2 | Passage ASSIST → STRICT à chaud | ⚠️ | Le rechargement à chaud du toml marche. Mais un contrôleur posé par `/setblock` en ASSIST n'est **pas au registre** : rien n'est délié tant que son chunk n'est pas rechargé. Voir *Trouvé* ci-dessous. |
| §2 | Monde existant redémarré en STRICT | ✅ | Démarrage en 0,7 s. Le bout de chaîne et le tiroir distant sont déliés au premier passage du filet, les deux premiers tiroirs restent reliés. |
| §2 | `/setblock air` au milieu, STRICT | ✅ | Délié en moins de 0,4 s. |
| §2 | TNT, STRICT | ✅ | À 5 blocs de la chaîne : 1003 et 1006 détruits ; 1001-1002 restent reliés, 1004-1005 et 1007-1008 sont déliés. À 6 blocs (contrôle) : rien de cassé, tout reste relié. À 3 blocs, la chaîne en chêne est entièrement détruite. |
| §2 | Linking Tool, message | ⏳ | Demande un joueur. |
| §3 | Déchargement réel puis rechargement, chaîne sur deux chunks | ✅ puis ❌ FS | Le premier cycle donne 8/8 reliés. Les deux suivants **bloquent le serveur** (watchdog, 60 s) dans `ControllableDrawerTile.invalidateCapabilities` de FS 1.5.7 (ligne 369), sans aucune frame de ce mod, et aussi en mode OFF. Voir *Trouvé*. |
| §3 | Milieu déchargé, contrôleur chargé | ⏳ | Impossible sans joueur : un chunk voisin d'un chunk forcé reste chargé (niveau de ticket 32). Couvert par le game test 6. |
| §4 | Cuivre ×3 (STRICT, filet actif) | ✅ | Reste relié. Planches ×2 : délié au passage du filet. |
| §4 | Casse d'un cuivre qui ne touche aucun tiroir | ✅ | Filet à 72000 : toujours relié 3 s après. Filet remis à 100 : délié en moins de 7 s. |
| §4 | Tag vide dans le jar livré | ⏳ | Demande une instance avec le jar. |
| §5 | Client sans le mod, message de repli | ⏳ | Demande un client. |
| §6 | Coût : 50 tiroirs et 100 observateurs en horloge | ✅ | Tick moyen de 0,7 ms en STRICT, 0,6 ms en OFF, 0,5 ms sans horloge (P99 : 2,8 / 4,0 / 2,4 ms). Les 50 tiroirs restent reliés après 25 s d'impulsions. |

**Trouvé :**
1. **Trou du registre (ce mod).** Un contrôleur créé autrement que par un joueur (commande, autre
   mod) pendant qu'on n'est pas en STRICT n'entre au registre qu'au rechargement de son chunk. De
   même, un contrôleur posé par un joueur en OFF n'est pas enregistré, puisque la pose n'est pas mise
   en file en OFF. Après un passage à chaud en STRICT, le filet l'ignore donc jusqu'au prochain
   chargement. Un redémarrage règle le problème.
   **Corrigé le 17 octobre 2026**, avec deux game tests qui échouaient avant le correctif :
   - une pose enregistre un contrôleur dans tous les modes ;
   - en ASSIST, une mise à jour de bloc enregistre un contrôleur posé à cette position ;
   - en STRICT, `neighborChanged` regarde aussi la position elle-même.

   Reste un seul cas, assumé : un contrôleur posé par commande **en OFF**, où le listener sort au
   premier test. Il entre au registre au rechargement de son chunk.
2. **Deadlock de FS 1.5.7 (amont).** Un tiroir relié dans un autre chunk que son contrôleur peut
   geler le serveur pendant que ses chunks se chargent. C'est reproduit 2 fois sur 3 cycles, et
   également en OFF. La branche 1.21 de FS (1.5.8, pas encore publiée) corrige le problème avec
   `getChunkNow`. Ce mod ne l'aggrave pas directement, mais l'auto-link relie facilement de grands
   murs qui traversent des bordures de chunk. Le monde `run/cs-test` est conservé comme cas de
   reproduction.
   **Contourné le 17 octobre 2026** par un mixin qui reporte le correctif de la branche 1.21 de FS
   (`mixin/ControllableDrawerTileMixin`, appliqué à FS ≤ 1.5.7) :
   - même monde `cs-test` : **0 gel** sur 3 démarrages et 5 vrais cycles de déchargement et
     rechargement, avec 8/8 tiroirs reliés à chaque cycle ;
   - sans le correctif, le même monde avait gelé 2 fois sur 3 ;
   - 4 autres cycles n'ont pas déchargé le chunk à temps et ne comptent pas ;
   - le contrôle sans mixin date d'avant, pas de la même session.

**Outillage (pas le mod) :**
- PowerShell 5.1 écrit l'UTF-8 avec un BOM, ce que nightconfig refuse : il recrée alors le toml avec
  les valeurs par défaut.
- `/setblock` refuse de remplacer un bloc identique, donc le NBT n'est pas appliqué.
- `forceload remove all` a laissé un chunk forcé.

### Charge — 21 octobre 2026, game tests `loadOf2000Drawers` / `loadOf20000Drawers`

Ce sont des mesures, pas des tests réussis ou échoués (`required = false`). Elles sont journalisées
sous `LOAD …` par `./gradlew runGameTestServer`. Chaque mesure construit un bloc plein de tiroirs
au-dessus du template, avec le contrôleur au milieu. Le réseau de 20 383 tiroirs demande une portée
×2 (16), obtenue par un objet portant `CONTROLLER_RANGE_MODIFIER`. Mesuré sur la machine de dev, en
millisecondes, sur deux runs :

| Réseau | Tout relier (`linkFree`, un seul ADD, travail de FS compris) | Un passage du filet (`validate`, réseau intact) | `dryrun` | Poser **un** tiroir contre le réseau |
|---|---|---|---|---|
| 2 027 tiroirs | 41 – 50 | 6 – 9 | 4 – 6 | 9 |
| 20 383 tiroirs | 1 318 – 1 399 | 21 – 38 | 24 – 30 | 48 |

**Lecture :**
- Le coût est linéaire dans le nombre de tiroirs, à environ 1,5 µs par tiroir et par parcours.
- Relier 20 000 tiroirs d'un coup coûte surtout du côté de FS : son `addConnectedDrawers` fait un
  `List.contains` par tiroir, donc un coût quadratique. C'est un cas rare (un contrôleur posé contre
  un mur libre géant).
- Un passage du filet ne coûte que par contrôleur et par intervalle : 8 ms toutes les 5 s pour
  2 000 tiroirs, 30 ms pour 20 000.
- **Le vrai risque est en STRICT** : une horloge redstone collée à un réseau de 20 000 tiroirs le
  ferait revalider à chaque mise à jour, soit 30 ms par tick. L'essai du 16 octobre (0,7 ms par tick)
  ne portait que sur 50 tiroirs.
- Poser un tiroir contre un réseau de 20 000 coûte 48 ms : on le sent quand on construit vite.

**Après l'index des membres (21 octobre 2026).** Chaque opération est répétée après échauffement ;
les valeurs sont min – médiane, en ms :

| Opération | 2 027 tiroirs | 20 383 tiroirs |
|---|---|---|
| Parcours complet (avant : chaque passage du filet, chaque impulsion redstone en STRICT) | 8,7 – 11,4 | 26 – 37 |
| **Vérification du filet par l'index** (sans parcours) | **0,75 – 1,0** | **1,9 – 2,6** |
| Poser 1 tiroir, ancienne façon (parcours complet + ADD de FS) | 8,6 – 9,7 | 44 – 51 |
| **Poser 1 tiroir, par l'index** (incrémental + ADD de FS) | **2,0 – 2,4** | **15,7 – 22,8** |
| dont l'ADD de FS seul, sans rien de nous | 1,9 – 2,2 | 14,4 – 15,6 |
| Horloge redstone collée au réseau (game test `theIndexWalksOnlyForRealCuts`) | 0 parcours | 0 parcours |

- La pose ne coûte plus que ce que coûte FS : sur 20 000 tiroirs, l'ADD d'un seul tiroir lui prend
  environ 15 ms, parce qu'il reconstruit et retrie toute sa liste. Ce n'est plus de notre ressort.
- Le filet ne re-parcourt plus un réseau intact : il vérifie l'index en environ 1 à 3 ms.
- Une vraie coupure coûte un parcours complet, une fois. Une casse en bordure, ou une casse dans une
  boucle, n'en coûte aucun.

**Lenteurs de Functional Storage, non corrigées (décision du 21 octobre 2026 : ni PR, ni mixin).**
Elles sont lues dans le jar 1.5.7 et seront à reprendre dans la doc publique pour les packs à grands
réseaux :

1. **Inventaire du contrôleur** : chaque accès à un emplacement vérifie le tiroir par un
   `List.contains` sur toute la liste des tiroirs (`ControllerInventoryHandler`). Un balayage complet
   par une machine (entonnoir, tuyau, storage bus) coûte donc emplacements × tiroirs. C'est
   **estimé** à quelques ms par balayage et par machine sur 2 000 tiroirs, **non mesuré**.
2. **Reconstruction** (`ConnectedDrawers.rebuild`) : le tri recalcule ses clés à chaque comparaison,
   et chaque block entity est relue deux fois. C'est le coût de tout `ADD` ou `REMOVE`, **mesuré** :
   environ 2 ms sur 2 000 tiroirs, environ 15 ms sur 20 000.
3. **Ajout en lot** (`addConnectedDrawers`) : un `List.contains` par tiroir ajouté, donc un coût
   quadratique. **Mesuré** : relier 20 000 tiroirs d'un coup prend 2 à 5 s.
4. **Rechargement d'un mur** : les tiroirs déchargés sortent de la liste, puis chacun se rajoute à
   son `onLoad` avec une reconstruction complète. Revenir vers une base de 2 000 tiroirs coûte donc
   2 000 reconstructions. **Non mesuré.**
5. **Client** : chaque lien renvoie toute la liste au client, qui recalcule la forme de tout le
   réseau (`rebuildShapes`, issue FS #314). **Non mesuré** : il faudrait un client.

**Create (C1) — pas fait.** Il faudrait Create dans le run de dev et un joueur pour assembler une
contraption. Ce qu'il faudra vérifier en jeu :
- un tiroir relié, déplacé puis reposé, ne doit pas se dupliquer (#491) ;
- à la repose, il revient dans le réseau s'il touche, et il est délié en STRICT s'il ne touche plus.

Si Create repose le tiroir avec son ancien `controllerPos`, le `onLoad` de FS le relie à distance.
En STRICT, le filet le défait au passage suivant ; en ASSIST, il reste relié **[supposé]**.

| Date | Section | Résultat | Notes |
|---|---|---|---|
| | | | |
