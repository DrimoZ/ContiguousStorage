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
- [ ] **Tag vide dans le jar livré** : dans une instance qui utilise le jar
      (`build/libs/contiguousstorage-0.1.0+1.21.1.jar`), le cuivre ne conduit pas.
  - Ajouter un datapack contenant `data/contiguousstorage/tags/block/conductors.json` avec
    `{"values": ["minecraft:copper_block"]}`, puis `/reload` : le cuivre conduit.

## 5. Serveur seul, client sans le mod

```bash
./gradlew runServer
```

- Premier lancement : passer `eula=true` dans `run/eula.txt`, et `online-mode=false` dans
  `run/server.properties`.
- [ ] **Connexion d'un client qui n'a pas Contiguous Storage** :
  - Le client n'a que NeoForge 21.1.248, Functional Storage 1.21-1.5.7 et Titanium 1.21-4.0.34, par
    exemple dans une instance Prism ou dans le launcher officiel. FS a des blocs, donc le client en a
    besoin ; c'est **notre** mod qui ne doit pas y être.
  - La connexion passe, et `runClient` ne convient pas, puisqu'il embarque le mod.
- [ ] **Message côté client sans le mod** : le message du Linking Tool s'affiche en anglais, grâce au
      texte de repli. La clé brute `message.contiguousstorage…` ne doit pas apparaître.
- [ ] Les essais du §2 refaits côté serveur donnent le même résultat.

## 6. Coût

- [ ] **STRICT, base redstone à côté des tiroirs** :
  - Monter une horloge redstone rapide, collée à un mur de 50 tiroirs reliés.
  - `/debug start`, attendre 30 s, puis `/debug stop`. Ouvrir le profil dans `run/debug/`.
  - `CSEvents.onNeighborNotify` et `Networks.validate` ne doivent pas ressortir.
- [ ] **ASSIST / OFF** : même montage. Le listener doit sortir au premier test, donc avoir un coût
      négligeable.

---

## Résultats

| Date | Section | Résultat | Notes |
|---|---|---|---|
| | | | |
