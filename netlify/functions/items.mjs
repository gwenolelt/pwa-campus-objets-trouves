// netlify/functions/items.mjs — Netlify Serverless Function pour l'API /api/items
// Fournit la persistance en mémoire (et Netlify Blobs si activé) + gestion If-Match 412

let memoryItems = [
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

// Helper pour tenter d'utiliser Netlify Blobs si disponible
async function getStoredItems() {
  try {
    const { getStore } = await import("@netlify/blobs");
    const store = getStore("campus-items");
    const items = await store.get("items", { type: "json" });
    if (items && Array.isArray(items) && items.length > 0) {
      memoryItems = items;
    }
  } catch {
    // Si Blobs n'est pas actif, on utilise la mémoire du worker
  }
  return memoryItems;
}

async function saveStoredItems(items) {
  memoryItems = items;
  try {
    const { getStore } = await import("@netlify/blobs");
    const store = getStore("campus-items");
    await store.setJSON("items", items);
  } catch {
    // Mémoire du conteneur
  }
}

export default async (req, context) => {
  const method = req.method;
  const url = new URL(req.url);
  const pathname = url.pathname;
  
  // Extraire l'ID de l'objet éventuel (ex: /api/items/seed-1 ou /.netlify/functions/items/seed-1)
  const segments = pathname.split("/").filter(Boolean);
  let itemId = null;
  const lastSegment = segments[segments.length - 1];
  if (lastSegment && lastSegment !== "items") {
    itemId = lastSegment;
  }

  const corsHeaders = {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, If-Match",
  };

  if (method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders });
  }

  const items = await getStoredItems();

  // 1. GET /api/items ou /api/items/:id
  if (method === "GET") {
    if (itemId) {
      const item = items.find((i) => i.id === itemId);
      if (!item) {
        return new Response(JSON.stringify({ error: "Objet introuvable" }), {
          status: 404,
          headers: corsHeaders,
        });
      }
      return new Response(JSON.stringify(item), { status: 200, headers: corsHeaders });
    }
    return new Response(JSON.stringify(items), { status: 200, headers: corsHeaders });
  }

  // 2. POST /api/items (création)
  if (method === "POST") {
    try {
      const body = await req.json();
      body.version = 1;
      body.createdAt = body.createdAt || Date.now();
      body.updatedAt = Date.now();

      // Éviter les doublons si déjà présent
      const existingIdx = items.findIndex((i) => i.id === body.id);
      if (existingIdx !== -1) {
        items[existingIdx] = body;
      } else {
        items.unshift(body); // ajouter en début de liste
      }

      await saveStoredItems(items);
      return new Response(JSON.stringify(body), { status: 201, headers: corsHeaders });
    } catch (err) {
      return new Response(JSON.stringify({ error: "Corps JSON invalide" }), {
        status: 400,
        headers: corsHeaders,
      });
    }
  }

  // 3. PUT /api/items/:id (modification avec réconciliation If-Match § 11)
  if (method === "PUT") {
    try {
      const body = await req.json();
      const targetId = itemId || body.id;
      const existingIdx = items.findIndex((i) => i.id === targetId);

      if (existingIdx === -1) {
        return new Response(JSON.stringify({ error: "Objet introuvable" }), {
          status: 404,
          headers: corsHeaders,
        });
      }

      const existing = items[existingIdx];
      const ifMatch = req.headers.get("if-match");

      // Précondition If-Match
      if (ifMatch) {
        const expected = `"${existing.version}"`;
        if (ifMatch !== expected && ifMatch !== "*") {
          return new Response(JSON.stringify(existing), { status: 412, headers: corsHeaders });
        }
      }

      const updated = {
        ...existing,
        ...body,
        version: (existing.version || 1) + 1,
        updatedAt: Date.now(),
      };

      items[existingIdx] = updated;
      await saveStoredItems(items);
      return new Response(JSON.stringify(updated), { status: 200, headers: corsHeaders });
    } catch (err) {
      return new Response(JSON.stringify({ error: "Erreur traitement PUT" }), {
        status: 400,
        headers: corsHeaders,
      });
    }
  }

  // 4. DELETE /api/items/:id
  if (method === "DELETE" && itemId) {
    const filtered = items.filter((i) => i.id !== itemId);
    await saveStoredItems(filtered);
    return new Response(null, { status: 204, headers: corsHeaders });
  }

  return new Response(JSON.stringify({ error: "Méthode non autorisée" }), {
    status: 405,
    headers: corsHeaders,
  });
};
