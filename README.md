<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHDuels

*Duels 1v1 sur invitation (/duel) dans une arène dédiée, avec commandes bloquées pendant le combat.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Duels 1v1 sur invitation (/duel) dans une arène dédiée, avec commandes bloquées pendant le combat.

Utilisé sur : **Opale**.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/duel` | — | Duel command for managing and participating in duels | — |

## Plusieurs serveurs (SHNetwork)

Avec SHNetwork installé et `reseau.actif: true` (valeur par défaut), les duels restent sur le serveur qui héberge l'arène : le monde de `arena.pos1` et `arena.pos2` est celui du spawn, que les autres serveurs ne font que copier (liste `mondes-distants` de SHNetwork).

- Sur un serveur qui ne fait que copier ce monde, `/duel <joueur>` répond que le duel se joue à l'arène du spawn.
- Un défi vers un joueur connecté sur un autre serveur répond qu'il n'est pas à portée, au lieu de « joueur non trouvé ».
- Un duelliste qui quitte le serveur en plein duel emporte ses états normaux (vitesse, saut, invulnérabilité), pas ceux du duel.
- `temp_data.yml` ne vaut que sur le serveur de l'arène : un joueur déconnecté en plein duel y revient (le monde du spawn renvoie vers ce serveur) et y est replacé à sa position d'avant-duel.

Sans SHNetwork, ou avec `reseau.actif: false`, rien ne change.

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHDuels/
├── <a href="src/main/resources/config.yml">config.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
