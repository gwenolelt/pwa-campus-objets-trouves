// server.mjs — Serveur local HTTP natif Node.js
// Sert les fichiers statiques PWA + fournit l'API REST avec gestion If-Match (§ 11)

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PORT = process.env.PORT || 3000;

// Base de données en mémoire pré-remplie avec les 3 graines du HTML initial
let itemsDatabase = [
  {
    id: "seed-1",
    title: "Clés avec porte-clé bleu et badge CROUS",
    category: "Clés & Badges",
    description: "Trousseau de clés avec badge d'accès magnétique CROUS et porte-clé bleu trouvé sur une table d'étude au 2ème étage de la BU Centrale.",
    dateFound: "12 octobre 2026",
    locationFound: "BU Centrale, salle de lecture 2ème étage",
    depositLocation: "Accueil de la Bibliothèque Universitaire (BU)",
    photo: "/icons/icon-512.png",
    status: "active",
    dateReturned: null,
    version: 1,
    createdAt: Date.now() - 3600000,
    updatedAt: Date.now() - 3600000,
  },
  {
    id: "seed-2",
    title: "Calculatrice graphique TI-84 Plus CE",
    category: "Électronique",
    description: "Calculatrice graphique TI-84 Plus CE en parfait état trouvée sur une tablette de l'amphithéâtre A après le cours de mathématiques.",
    dateFound: "11 octobre 2026",
    locationFound: "Amphi A — Bâtiment Sciences",
    depositLocation: "Loge du gardien, hall principal Sciences",
    photo: "/icons/icon-512.png",
    status: "active",
    dateReturned: null,
    version: 1,
    createdAt: Date.now() - 86400000,
    updatedAt: Date.now() - 86400000,
  },
  {
    id: "seed-3",
    title: "Gourde isotherme inox noire 750ml",
    category: "Vie quotidienne",
    description: "Gourde isotherme en inox noire de 750ml oubliée sur les tables extérieures de la terrasse de la cafétéria CROUS.",
    dateFound: "10 octobre 2026",
    locationFound: "Cafétéria CROUS, terrasse extérieure",
    depositLocation: "Comptoir cafétéria",
    photo: "/icons/icon-512.png",
    status: "returned",
    dateReturned: "11 octobre 2026",
    version: 2,
    createdAt: Date.now() - 172800000,
    updatedAt: Date.now() - 90000000,
  },
];

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".mjs": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".md": "text/markdown; charset=utf-8",
  ".txt": "text/plain; charset=utf-8",
  ".xml": "application/xml; charset=utf-8",
};

const server = http.createServer((req, res) => {
  const parsedUrl = new URL(req.url, `http://${req.headers.host}`);
  const pathname = parsedUrl.pathname;

  // CORS headers
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, If-Match");

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  // --------------------------------------------------------------------------
  // API REST : /api/items
  // --------------------------------------------------------------------------
  if (pathname.startsWith("/api/items")) {
    const parts = pathname.split("/").filter(Boolean); // ['api', 'items', (id)]
    const itemId = parts[2];

    // GET /api/items : liste tous les objets
    if (req.method === "GET" && !itemId) {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(itemsDatabase));
      return;
    }

    // GET /api/items/:id : un objet précis
    if (req.method === "GET" && itemId) {
      const item = itemsDatabase.find((i) => i.id === itemId);
      if (!item) {
        res.writeHead(404, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ error: "Objet introuvable" }));
        return;
      }
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(item));
      return;
    }

    // POST /api/items : création d'un nouvel objet
    if (req.method === "POST" && !itemId) {
      let body = "";
      req.on("data", (chunk) => (body += chunk));
      req.on("end", () => {
        try {
          const newItem = JSON.parse(body);
          newItem.version = 1;
          newItem.createdAt = Date.now();
          newItem.updatedAt = Date.now();
          itemsDatabase.push(newItem);
          res.writeHead(201, { "Content-Type": "application/json" });
          res.end(JSON.stringify(newItem));
        } catch (err) {
          res.writeHead(400, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: "Corps de requête invalide" }));
        }
      });
      return;
    }

    // PUT /api/items/:id : modification avec réconciliation de conflits If-Match (§ 11)
    if (req.method === "PUT" && itemId) {
      let body = "";
      req.on("data", (chunk) => (body += chunk));
      req.on("end", () => {
        try {
          const existingIndex = itemsDatabase.findIndex((i) => i.id === itemId);
          if (existingIndex === -1) {
            res.writeHead(404, { "Content-Type": "application/json" });
            res.end(JSON.stringify({ error: "Objet introuvable" }));
            return;
          }

          const existing = itemsDatabase[existingIndex];
          const ifMatch = req.headers["if-match"];

          // Vérification de la précondition If-Match (§ 11 cheatsheet)
          if (ifMatch) {
            const expectedMatch = `"${existing.version}"`;
            if (ifMatch !== expectedMatch && ifMatch !== "*") {
              // Conflit détecté ! HTTP 412 Precondition Failed
              res.writeHead(412, { "Content-Type": "application/json" });
              res.end(JSON.stringify(existing));
              return;
            }
          }

          const updateData = JSON.parse(body);
          const updatedItem = {
            ...existing,
            ...updateData,
            version: (existing.version || 1) + 1,
            updatedAt: Date.now(),
          };

          itemsDatabase[existingIndex] = updatedItem;
          res.writeHead(200, { "Content-Type": "application/json" });
          res.end(JSON.stringify(updatedItem));
        } catch (err) {
          res.writeHead(400, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: "Erreur traitement PUT" }));
        }
      });
      return;
    }

    // DELETE /api/items/:id
    if (req.method === "DELETE" && itemId) {
      itemsDatabase = itemsDatabase.filter((i) => i.id !== itemId);
      res.writeHead(204);
      res.end();
      return;
    }
  }

  // --------------------------------------------------------------------------
  // Fichiers statiques
  // --------------------------------------------------------------------------
  let filePath = path.join(__dirname, pathname === "/" ? "index.html" : pathname);

  // Sécurité traversée de dossier
  if (!filePath.startsWith(__dirname)) {
    res.writeHead(403);
    res.end("Accès interdit");
    return;
  }

  fs.stat(filePath, (err, stats) => {
    if (err || !stats.isFile()) {
      // Si la ressource demandée n'existe pas, renvoyer l'index pour le routage PWA
      filePath = path.join(__dirname, "index.html");
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || "application/octet-stream";

    const headers = { "Content-Type": contentType };

    // En-tête X-Robots-Tag: noindex pour les fichiers markdown (§ 13 cheatsheet)
    if (ext === ".md") {
      headers["X-Robots-Tag"] = "noindex";
    }

    // En-têtes de sécurité
    headers["X-Content-Type-Options"] = "nosniff";
    headers["X-Frame-Options"] = "SAMEORIGIN";

    res.writeHead(200, headers);
    fs.createReadStream(filePath).pipe(res);
  });
});

server.listen(PORT, () => {
  console.log(`🚀 Serveur PWA Objets Trouvés du Campus démarré sur http://localhost:${PORT}`);
  console.log(`📡 API REST active sur http://localhost:${PORT}/api/items`);
});
