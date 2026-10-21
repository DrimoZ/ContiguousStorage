# Réseaux physiques pour Functional Storage — réflexion produit

**Ce que fait ce fichier :** le pourquoi. Concept, positionnement, décisions arrêtées, décisions encore
ouvertes, hors scope. Écrit avant le code, le 1er octobre 2026.

**Ce que ce fichier ne fait pas :** l'opérationnel (contraintes lues dans le code, architecture, ordre
de travail). C'est dans `CLAUDE.md`.

---

## 1. Concept

Dans Functional Storage, un tiroir rejoint un Storage Controller par le **Linking Tool**, à distance,
dans la portée du contrôleur. Rien ne touche rien : un mur de tiroirs peut être relié à un contrôleur
caché trois blocs plus loin, derrière de la pierre.

Dans Storage Drawers, le mod qui a installé le genre, c'est l'inverse : **un tiroir fait partie du
réseau parce qu'il touche le contrôleur**, directement ou par d'autres tiroirs (et des *trims*).
Poser suffit, et casser au milieu d'une chaîne coupe la suite.

Ce mod donne ce comportement à Functional Storage, réglable, sans rien lui ajouter d'autre.

**Pour qui :** les packs qui veulent que le stockage se construise plutôt qu'il ne se configure, et
les joueurs venus de Storage Drawers pour qui le Linking Tool est une étape de trop.

---

## 2. Positionnement

- **Aucune contribution chez FS : ni PR, ni issue.** Décidé le 21 octobre 2026. L'idée d'origine
  était de proposer d'abord le filtre de connectivité en amont. Depuis, on sait que la demande a
  déjà été faite et écartée (`#353`). Les problèmes de FS trouvés en chemin (le deadlock de 1.5.7,
  les lenteurs sur les grands réseaux) ne sont pas signalés non plus. Le deadlock est contourné
  chez nous (§3), et les lenteurs sont documentées sans être corrigées (§3).
- **Pas dans Immaterial Drawers.** Immaterial Drawers ajoute des types de tiroir. Ce mod change la
  façon dont FS relie *ses propres* tiroirs. Les publics sont différents, et un bug de déconnexion ne
  doit pas atterrir sur la page d'un mod de stockage d'énergie. Décidé le 1er octobre 2026.
- **Concurrence :** vérifié sur Modrinth le 16 octobre 2026, aucun addon ne fait ça, et le slug
  `contiguous-storage` est libre (CurseForge reste à voir). L'amont a déjà reçu la demande
  (`Buuz135/FunctionalStorage#353`) et l'a renvoyée vers le Linking Tool. Voir `IDEAS.md` §1.

---

## 3. Décisions arrêtées

| Décision | Pourquoi |
|---|---|
| **Pas de mixin**, sauf une exception, ajoutée le 17 octobre 2026. | Même règle qu'Immaterial Drawers : pas de mixin dans un mod tiers à 56M de téléchargements tant que le contrat public suffit. Il suffit : `addConnectedDrawers` et `getControllerPos` sont publics. Un mixin dans `rebuild()` ne dispenserait d'ailleurs pas des déclencheurs : FS ne reconstruit pas quand on casse un bloc qui n'est pas un tiroir. **L'exception** : reporter un correctif qui existe déjà chez FS, désactivé dès la version de FS qui le contient. Un seul cas aujourd'hui : le deadlock de chargement de FS 1.5.7. Un tiroir relié depuis un autre chunk que son contrôleur peut geler le serveur. Le cas a été reproduit sur serveur dédié, en mode OFF aussi, et l'auto-link le rend plus fréquent. FS n'a rien publié depuis juin 2026 (`mixin/ControllableDrawerTileMixin`, appliqué à FS ≤ 1.5.7). |
| ~~Aucun bloc, aucun item.~~ **Des trims, dans le mod principal.** Changé le 21 octobre 2026. | Un trim par bois de la liste de FS (`WOOD_TYPES`, y compris les bois ajoutés par un addon de FS), plus un trim encadré qui réutilise le système « framed » de FS. Ils sont désactivables par config : désactivés, ils n'ont plus de recette, sortent des onglets créatifs, et ceux déjà posés deviennent de la déco inerte. Les blocs restent toujours enregistrés, parce que client et serveur doivent avoir la même liste. **Conséquence assumée : le mod n'est plus côté serveur seul.** Tous les clients doivent l'installer. |
| **Les lenteurs de FS sur les grands réseaux ne sont pas corrigées.** Décidé le 21 octobre 2026. | Il y en a cinq, lues dans le jar 1.5.7 et mesurées en partie (`MANUAL_TESTS.md`, section Charge). On ne les corrige pas : pas de PR chez FS, et pas de mixin, puisque l'exception du mixin ne couvre que les correctifs que FS a déjà faits. Elles sont documentées pour les packs. Notre propre coût, lui, est réduit au minimum par l'index des membres. |
| **Événements + filet périodique.** | Les événements ne voient pas tout (`setBlock` sans flag 1). Le filet garantit qu'on finit toujours par corriger. Ni l'un ni l'autre ne suffit seul. Voir `CLAUDE.md` §5. |
| **Un morceau non chargé ne coupe rien.** | Une chaîne qu'on ne peut pas lire n'est pas une chaîne coupée. Sinon, s'éloigner d'une base déconnecterait ses murs. |
| **Toujours `addConnectedDrawers`, jamais la liste à la main.** | C'est la seule voie qui met à jour la liste **et** `controllerPos`. Sans les deux, l'auto-réparation de FS au chargement défait notre travail. |
| **Un mode en enum (`OFF` / `ASSIST` / `STRICT`), pas deux booléens.** | La combinaison « connexion obligatoire sans auto-link » est un piège, pas un mode. |
| **Les conducteurs décoratifs sont un tag, pas une liste en config.** | C'est de la donnée de pack, rechargeable par `/reload`. Ça couvre les trims sans créer de bloc. |
| **Faces seulement, pas de diagonales.** | Comme Storage Drawers. Une diagonale ne se voit pas comme un contact. |
| **La portée reste celle de FS.** | Les Range Upgrades du contrôleur gardent leur sens, et le BFS est borné gratuitement. |
| **Une coupure se voit : message en barre d'action et particules.** Décidé le 16 octobre 2026. | En `STRICT`, casser le milieu d'un mur coupe la moitié du réseau sans signe visible. Le joueur qui a coupé reçoit « N tiroirs déconnectés », et chaque tiroir retiré émet une fois une particule. Un tiroir relié à la pose en émet une autre. Ce sont des paquets vanilla : le mod reste serveur seul. Le nombre de particules par coupure est plafonné. `IDEAS.md` V1, V2. |
| **Une commande `dryrun` avant `STRICT`.** Décidé le 16 octobre 2026. | Passer en `STRICT` est la seule action du mod qui défait le travail d'un joueur. `/contiguousstorage dryrun` dit, dans n'importe quel mode, combien de tiroirs `STRICT` retirerait, sans rien retirer. `IDEAS.md` A1. |
| **L'auto-link exige l'accès au contrôleur.** Décidé le 16 octobre 2026. | Sans ça, poser un tiroir contre le mur d'un autre joueur le relie à son réseau, et ce que le réseau range peut finir dedans. Le Linking Tool, lui, exige un clic sur le contrôleur. On ne relie que si le poseur pourrait interagir avec le contrôleur. Pas d'option de config tant que personne ne la demande. `IDEAS.md` §4. |
| **Pas de release sans test de charge ni test avec Create.** Décidé le 16 octobre 2026. | Des réseaux de 20 000 tiroirs existent (`Buuz135/FunctionalStorage#335`), et Create déplace des tiroirs (duplications de FS : #491). Ce sont les deux endroits où les bugs arriveront. `IDEAS.md` P1, C1. |
| **Les trims de Storage Drawers sont dans le tag des conducteurs**, en entrée optionnelle. Décidé le 16 octobre 2026. | Un pack qui a les deux mods relie ses trims sans rien écrire. C'est une ligne de donnée (`"required": false`) : on ne parle pas à Storage Drawers. `IDEAS.md` R1. |

---

## 4. Décisions ouvertes — à trancher avant ou pendant le spike

> **Tranché le 16 octobre 2026 :**
> - **Nom : Contiguous Storage, mod ID `contiguousstorage`.** Le slug `contiguous-storage` doit encore
>   être vérifié sur Modrinth et CurseForge avant la création des pages.
> - **Mode par défaut : `ASSIST`.** Installer le mod sur un monde existant ne retire rien ; `STRICT` est
>   un choix explicite du pack.
> - **Deux contrôleurs : (a), le premier gagne.**
> - **Issue en amont : en parallèle du spike**, sans bloquer le projet. Depuis, on sait que la
>   demande a déjà été faite et écartée (`#353`, `IDEAS.md` §1).
> - **Retour visuel : oui**, message et particules (§3).
>
> Le détail ci-dessous reste comme trace du raisonnement.

### Nom et mod ID (avant la première ligne)

Contraintes : ne pas utiliser `functional` ni `storagedrawers` (ça laisse croire à un mod officiel de
l'un ou de l'autre), et décrire le **comportement**.

| Nom | Mod ID | Commentaire |
|---|---|---|
| Contiguous Storage | `contiguousstorage` | Le plus littéral. C'est ma recommandation. |
| Tethered Drawers | `tethereddrawers` | Plus imagé ; « tether » évoque aussi un lien à distance, ce qui est l'inverse. |
| Drawer Contact | `drawercontact` | Court, mais ambigu sans contexte. |

Vérifier la disponibilité du slug sur les deux plateformes avant de choisir (API Modrinth : 404 = libre).

### Le mode par défaut

`STRICT` par défaut, c'est cohérent avec un mod qu'on installe *pour* ça. Mais installer le mod sur un
monde existant coupe immédiatement tous les liens à distance. Une alternative est `ASSIST` par défaut,
qui ne retire jamais rien : `STRICT` devient un choix explicite du pack. **À trancher.** La réponse
change la première impression du mod sur un monde existant.

### Deux contrôleurs dans un même bloc de tiroirs

Le cas : un tiroir touche deux réseaux, ou un même mur contient deux contrôleurs.

- **(a) Le premier gagne.** Un tiroir déjà relié à un contrôleur atteignable garde ce lien ; un tiroir
  libre rejoint le contrôleur trouvé en premier par le BFS. Un autre contrôleur arrête le parcours.
- **(b) Interdit.** Storage Drawers refuse deux contrôleurs dans un même bloc. Ici, il faudrait le
  signaler au joueur, sans pouvoir empêcher la pose (pas de bloc à nous).

(a) est la seule option qui ne demande pas d'empêcher quelque chose. Elle est **recommandée**.

### Le Linking Tool en mode STRICT

- **(a) Laisser faire puis corriger** au tick suivant, avec un message. Couvre les modes simple et zone.
- **(b) Annuler** l'interaction sur une cible non connectée. Plus propre pour le joueur en mode simple,
  mais impossible à appliquer partiellement en mode zone.

(a) recommandée. On peut ajouter (b) pour le mode simple seulement si (a) déroute en jeu.

### Un retour visuel ?

Storage Drawers n'en a pas. Avec le mode `STRICT`, un joueur qui casse un tiroir au milieu d'un mur
ne voit pas que la moitié s'est déconnectée. Un message en barre d'action (« 12 tiroirs déconnectés »)
au joueur qui a cassé suffit sans doute. Il est faisable côté serveur : `BreakEvent` donne le joueur.
**Tranché le 16 octobre 2026** : message et particules, voir §3.

---

## 5. Hors scope (v1)

- **Un bloc *trim*.** Le tag couvre le besoin sans casser le « serveur seul ». À reconsidérer si les
  joueurs le réclament ; ce serait alors une v2, avec des assets et un client requis.
- **Un rendu permanent du réseau** (contours, overlay). Il faudrait un client. Les **particules**, en
  revanche, sont des paquets vanilla envoyés par le serveur, et elles sont dans la v1 (§3).
- **Modifier le Linking Tool** au-delà de ce qui est nécessaire en `STRICT`.
- **D'autres mods de stockage** (Storage Drawers lui-même, Sophisticated Storage…). Le mod parle à FS,
  et à FS seulement.
- **Un mixin**, même en dernier recours, tant que le §3 tient, en dehors de l'exception qui y est
  décrite.
