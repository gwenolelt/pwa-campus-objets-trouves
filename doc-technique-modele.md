# <Nom de la PWA> — Technical Design Doc

Auteur · Date · URL de production · Dépôt · Statut (draft / final)

> Modèle volontairement court. Une section = quelques phrases ou un tableau. Ce qui compte : chaque choix est **nommé, justifié, et vérifiable dans le code**. Supprimer ce qui ne s'applique pas ; ne rien inventer.

## 1. TL;DR

Trois phrases : ce que fait l'app, pour qui, et le choix technique le plus important.

## 2. Contexte et objectifs

- Le problème que l'app résout, et le sujet choisi sur le Hub.
- **Goals** : ce que la v1 fait.
- **Non-goals** : ce qu'elle ne fait volontairement pas.

## 3. Architecture

- Stack (vanilla / framework, librairies, Workbox ou SW manuel) et pourquoi.
- Schéma ou arborescence : page ↔ Service Worker ↔ caches ↔ IndexedDB ↔ serveur.
- Hébergement et pipeline de déploiement.

## 4. Cache et hors ligne

| Ressource | Stratégie | Stockage | Pourquoi |
|---|---|---|---|
| HTML | | | |
| CSS / JS | | | |
| Données | | | |

- Ce que l'utilisateur voit hors ligne ; ce qui est désactivé ; la page de secours.

## 5. Mise à jour

- Stratégie retenue : nouvelle version appliquée d'office **ou** proposée à l'utilisateur. **Pourquoi** pour cette app.
- Mécanisme (versionnage du cache, `skipWaiting`, bannière…).

## 6. Données et synchronisation

- Modèle de données (stores IndexedDB, clés, index).
- Outbox : ce qui est envoyé, quand, par quel chemin (Background Sync, repli `online`), ce qui se passe en cas d'échec.
- Conflits : ignorés (et pourquoi c'est acceptable) ou gérés (comment).

## 7. Permissions et capacités de l'appareil

| Capacité / API | Déclenchée quand | Pourquoi à ce moment | Si refus |
|---|---|---|---|
| | | | |

## 8. Notifications

- Locales ou push ; moment de la demande de permission ; contenu ; ce qui se passe au clic. (« Aucune » est une réponse valable, à justifier.)

## 9. Visibilité : SEO et agents IA

- Ce qui est dans le HTML initial, et ce qui ne l'est pas (et pourquoi).
- Fichiers de découverte : `robots.txt` (décision par crawler, avec la raison), `sitemap.xml`, `llms.txt`, Open Graph, JSON-LD.

## 10. Sécurité et vie privée

- Données stockées sur l'appareil ; ce qui part au serveur ; secrets (aucun côté client) ; en-têtes de sécurité posés.

## 11. Mesures

- Core Web Vitals (Lighthouse), résultat des vérifications `curl`, Lighthouse SEO.

## 12. Limites connues et suite

- Ce qui ne marche pas ou pas partout (navigateurs, iOS), et ce que ferait la v2.
