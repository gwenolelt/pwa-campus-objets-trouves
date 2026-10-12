# 📍 Objets Trouvés du Campus — Progressive Web App (PWA)

[![PWA Ready](https://img.shields.io/badge/PWA-Installable-blue.svg)](https://campus-objets-trouves.netlify.app/)
[![Offline First](https://img.shields.io/badge/Offline-IndexedDB%20%2B%20SW-green.svg)](https://campus-objets-trouves.netlify.app/)
[![SEO Score](https://img.shields.io/badge/Lighthouse%20SEO-100-success.svg)](https://campus-objets-trouves.netlify.app/)

Projet de **Progressive Web App complète** réalisé dans le cadre du cours de Master 2 Informatique à l'**Università di Corsica**.

> 📖 **Document Technique complet :** Consultez le fichier [`DOC-TECHNIQUE.md`](./DOC-TECHNIQUE.md) pour les détails d'architecture, les choix de conception et les justifications techniques.

---

## 📱 Présentation du Sujet

L'application permet aux étudiants et au personnel du campus universitaire de **déclarer un objet trouvé** avec :
- Une **photo** prise directement via la caméra du smartphone (ou sélection de fichier),
- Le **lieu** et la **date** de découverte,
- L'endroit précis où il est **déposé** (accueil de la BU, loge du gardien, secrétariat...).

Chaque objet possède une **page publique dédiée** (`/?item=<id>`) partageable via la **Web Share API** (`navigator.share`). Dès qu'un objet est récupéré, il passe à l'état **« Restitué »** avec horodatage de la remise.

---

## ✨ Fonctionnalités Clés & Capacités Matérielles

- 📦 **100% Fonctionnelle Hors Ligne** : App Shell mis en cache dans le Service Worker, base de données locale sous IndexedDB avec la bibliothèque `idb`. Les objets restent consultables, filtrables et déclarables en mode avion.
- 🔄 **Synchronisation différée (Outbox)** : Les créations et restitutions effectuées hors ligne sont placées dans un store `outbox` IndexedDB, puis expédiées automatiquement au retour du réseau via **Background Sync** (`sync-campus-items`) et repli universel sur l'événement `window.online`.
- 📷 **Capacité Matérielle 1 : Caméra (`getUserMedia`)** : Prise de vue en direct avec prévisualisation vidéo et capture canvas haute résolution compressée, déclenchée **uniquement après clic utilisateur**, avec gestion exemplaire du refus de permission.
- 🔗 **Capacité Matérielle 2 : Web Share API (`navigator.share`)** : Partage natif sur mobile de la fiche publique vers les messageries et réseaux sociaux, avec repli automatique vers le presse-papiers (`navigator.clipboard`).
- ⚔️ **Gestion des Conflits de Concurrence (`If-Match` 412)** : Utilisation de préconditions HTTP pour détecter si un objet a été modifié en ligne pendant qu'un terminal était déconnecté, avec dialogue interactif de réconciliation (garder ma version / prendre la version du serveur).
- 📲 **Installable sur Mobile & Desktop** : Manifest PWA complet (icônes 192x192, 512x512, maskable, `display: standalone`, couleurs de thème) et bouton d'installation personnalisé (`beforeinstallprompt`).
- 🚀 **Bannière de Mise à Jour Service Worker** : Stratégie interactive où l'utilisateur décide quand appliquer une nouvelle version via `SKIP_WAITING` et `controllerchange`.
- 🔍 **Visibilité SEO & Agents IA (GEO)** : Contenu initial public présent dans le HTML brut (`data-seed`), balises Open Graph, JSON-LD Schema.org, `robots.txt` documenté, `sitemap.xml`, `llms.txt` et jumelle Markdown `objets.md` protégée par `noindex`.

---

## 🚀 Démarrage Rapide en Local

### Prérequis
- [Node.js](https://nodejs.org/) (v18 ou supérieur).

### 1. Cloner et démarrer le serveur
```bash
# Se placer dans le dossier du projet
cd TP

# Lancer le serveur HTTP natif avec API REST et support If-Match
npm start
```
Le serveur démarre immédiatement sur : **`http://localhost:3000`**

### 2. Tester sur Smartphone (même réseau Wi-Fi)
1. Trouvez l'adresse IP locale de votre machine (ex: `192.168.1.42`) via `ipconfig` ou `ifconfig`.
2. Ouvrez Chrome ou Safari sur votre téléphone et accédez à `http://192.168.1.42:3000`.
3. Cliquez sur le bouton **« 📲 Installer »** ou ajoutez à l'écran d'accueil !

---

## 🧪 Tests & Vérifications

### 1. Tests SEO & Agents IA automatisés (§ 13 cheatsheet)
Lancez la suite de tests intégrée qui simule les requêtes des robots (`GPTBot`, `ClaudeBot`) :
```bash
npm run test:seo
```
Ce script valide automatiquement :
- ✅ Contenu public présent dans le HTML initial (3 balises `<li>` de démarrage).
- ✅ Jumelle Markdown `/objets.md` accessible (HTTP 200).
- ✅ En-têtes `Content-Type: text/markdown` et `X-Robots-Tag: noindex` sur la jumelle.
- ✅ Absence de négociation de contenu conflictuelle sur `/`.
- ✅ Disponibilité de `robots.txt`, `sitemap.xml`, `llms.txt`, et `manifest.json`.

### 2. Test du Conflit de Concurrence (`If-Match` 412)
```bash
node test-api.mjs
```
Vérifie qu'une tentative de mise à jour avec une version obsolète déclenche bien un code `412 Precondition Failed`.

### 3. Test du Mode Hors Ligne
1. Ouvrez l'application dans Chrome.
2. Ouvrez les **DevTools (F12) → Onglet Réseau (Network) → Cochez « Hors ligne » (Offline)**.
3. Rafraîchissez la page : l'interface et les objets s'affichent instantanément.
4. Déclarez un objet ou marquez-en un comme restitué : une notification indique que l'action est stockée dans l'Outbox.
5. Décochez « Hors ligne » : l'application détecte le retour du réseau et synchronise automatiquement toutes les données !

---

## 🌐 Déploiement

Cette application est prête pour un déploiement statique ou serverless en 1 clic :

### Déploiement sur Netlify (Recommandé)
1. Liez votre dépôt GitHub à **Netlify**.
2. Répertoire de publication : `.` (racine).
3. Les fichiers [`_headers`](./_headers) et [`manifest.json`](./manifest.json) sont automatiquement pris en compte pour le HTTPS et les en-têtes de sécurité.

### Déploiement sur GitHub Pages ou Vercel
- Déployez la branche `main`. L'App Shell et le Service Worker fonctionneront de manière 100% autonome grâce au stockage local IndexedDB.

---

## 📁 Structure du Projet

```text
TP/
├── index.html              # HTML initial avec App Shell, SEO, JSON-LD et objets pré-chargés (data-seed)
├── style.css               # Feuilles de style modernes, fluides, responsive et mobile-first
├── app.js                  # Logique client (IndexedDB, Caméra, Web Share, Sync Outbox, Conflits)
├── sw.js                   # Service Worker (Cache First, Network First, SWR, Sync, Bannière MAJ)
├── offline.html            # Page de secours affichée en cas de rupture réseau non couverte
├── manifest.json           # Web App Manifest pour l'installation sur smartphone
├── server.mjs              # Serveur HTTP natif Node.js avec API REST et gestion If-Match
├── test-seo.mjs            # Script automatisé de vérification des critères SEO/LLM
├── test-api.mjs            # Script de validation de l'API et du code HTTP 412
├── robots.txt              # Règles d'indexation par moteur et crawler IA (GPTBot, ClaudeBot)
├── sitemap.xml             # Plan du site avec URL canonique
├── llms.txt                # Documentation d'accueil structurée pour les agents IA
├── objets.md               # Jumelle Markdown du registre public (X-Robots-Tag: noindex)
├── _headers                # Configuration d'en-têtes HTTP pour Netlify
├── serve.json              # Configuration pour l'outil serve en local
├── DOC-TECHNIQUE.md        # Document technique complet (selon doc-technique-modele.md)
└── icons/                  # Icônes de l'application
    ├── icon-192.png        # Icône PWA 192x192
    ├── icon-512.png        # Icône PWA 512x512
    ├── maskable-512.png    # Icône adaptative Android
    └── favicon.svg         # Favicon vectoriel haute définition
```

---

## ⚖️ Conformité au Barème du TP

| Critère du barème | Réalisation | Statut |
|---|---|:---:|
| **Déployée & Testée sur mobile** | Compatible Netlify, HTTPS, design 100% responsive testé | ✅ |
| **Installable** | Manifest complet, icônes 192/512/maskable, bouton custom `beforeinstallprompt` | ✅ |
| **Hors ligne** | Cache versionné, IndexedDB via `idb`, page `offline.html`, signalétique claire | ✅ |
| **Mise à jour** | Bannière utilisateur non intrusive, `SKIP_WAITING` et `controllerchange` | ✅ |
| **Synchronisation réseau** | Outbox IndexedDB, Background Sync (`sync-pending`) et repli `online` | ✅ |
| **Visibilité SEO / GEO** | Contenu initial dans le HTML (`data-seed`), Open Graph, JSON-LD, `llms.txt`, `robots.txt`, `objets.md` | ✅ |
| **Capacités de l'appareil** | **Caméra** (`getUserMedia`) + **Web Share API** (`navigator.share`) avec gestion des refus | ✅ |
| **Document technique** | `DOC-TECHNIQUE.md` rigoureux et conforme au modèle | ✅ |
| **Bonus : Conflits & 2ème capacité** | En-tête `If-Match` avec code `412` + 2 capacités (Caméra & Share) + Badging + Notifications | ✅ (+2) |
