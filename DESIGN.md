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

- **D'abord, demander en amont.** Le filtre de connectivité a sa place naturelle dans
  `ConnectedDrawers.rebuild()`, chez FS. Une issue sur `Buuz135/FunctionalStorage` qui propose une
  option désactivée par défaut, avec le BFS écrit, coûte une heure. Si elle est acceptée, ce mod n'a
  pas besoin d'exister, et c'est le meilleur résultat possible. Si elle est refusée ou reste sans
  réponse, on garde le code et on fait le mod.
- **Pas dans Immaterial Drawers.** Immaterial Drawers ajoute des types de tiroir. Ce mod change la
  façon dont FS relie *ses propres* tiroirs. Les publics sont différents, et un bug de déconnexion ne
  doit pas atterrir sur la page d'un mod de stockage d'énergie. Décidé le 1er octobre 2026.
- **Concurrence :** [à vérifier] qu'aucun addon FS ne fait déjà ça, sur CurseForge et Modrinth, avant de
  réserver le nom.

---

## 3. Décisions arrêtées

| Décision | Pourquoi |
|---|---|
| **Pas de mixin.** | Même règle qu'Immaterial Drawers : pas de mixin dans un mod tiers à 56M de téléchargements tant que le contrat public suffit. Il suffit : `addConnectedDrawers` et `getControllerPos` sont publics. Un mixin dans `rebuild()` ne dispenserait d'ailleurs pas des déclencheurs : FS ne reconstruit pas quand on casse un bloc qui n'est pas un tiroir. |
| **Aucun bloc, aucun item.** | Le mod reste serveur seul, sans assets ni registre à synchroniser, et facile à porter. |
| **Événements + filet périodique.** | Les événements ne voient pas tout (`setBlock` sans flag 1). Le filet garantit qu'on finit toujours par corriger. Ni l'un ni l'autre ne suffit seul. Voir `CLAUDE.md` §5. |
| **Un morceau non chargé ne coupe rien.** | Une chaîne qu'on ne peut pas lire n'est pas une chaîne coupée. Sinon, s'éloigner d'une base déconnecterait ses murs. |
| **Toujours `addConnectedDrawers`, jamais la liste à la main.** | C'est la seule voie qui met à jour la liste **et** `controllerPos`. Sans les deux, l'auto-réparation de FS au chargement défait notre travail. |
| **Un mode en enum (`OFF` / `ASSIST` / `STRICT`), pas deux booléens.** | La combinaison « connexion obligatoire sans auto-link » est un piège, pas un mode. |
| **Les conducteurs décoratifs sont un tag, pas une liste en config.** | C'est de la donnée de pack, rechargeable par `/reload`. Ça couvre les trims sans créer de bloc. |
| **Faces seulement, pas de diagonales.** | Comme Storage Drawers. Une diagonale ne se voit pas comme un contact. |
| **La portée reste celle de FS.** | Les Range Upgrades du contrôleur gardent leur sens, et le BFS est borné gratuitement. |

---

## 4. Décisions ouvertes — à trancher avant ou pendant le spike

> **Tranché le 16 octobre 2026 :**
> - **Nom : Contiguous Storage, mod ID `contiguousstorage`.** Le slug `contiguous-storage` doit encore
>   être vérifié sur Modrinth et CurseForge avant la création des pages.
> - **Mode par défaut : `ASSIST`.** Installer le mod sur un monde existant ne retire rien ; `STRICT` est
>   un choix explicite du pack.
> - **Deux contrôleurs : (a), le premier gagne.**
> - **Issue en amont : en parallèle du spike**, sans bloquer le projet.
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
**À décider après l'avoir vu en jeu.**

---

## 5. Hors scope (v1)

- **Un bloc *trim*.** Le tag couvre le besoin sans casser le « serveur seul ». À reconsidérer si les
  joueurs le réclament ; ce serait alors une v2, avec des assets et un client requis.
- **Des particules ou un rendu du réseau.** Il faudrait un client.
- **Modifier le Linking Tool** au-delà de ce qui est nécessaire en `STRICT`.
- **D'autres mods de stockage** (Storage Drawers lui-même, Sophisticated Storage…). Le mod parle à FS,
  et à FS seulement.
- **Un mixin**, même en dernier recours, tant que le §3 tient.
