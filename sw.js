// sw.js — Service Worker pour "Objets Trouvés du Campus"
// Versionnage strict du cache (incrémenter pour chaque nouvelle version)
const CACHE_NAME = "campus-lost-found-v4";

// App Shell pré-mis en cache à l'installation
const STATIC_ASSETS = [
  "/",
  "/index.html",
  "/style.css",
  "/app.js",
  "/offline.html",
  "/manifest.json",
  "/icons/favicon.svg",
  "/icons/icon-192.png",
  "/icons/icon-512.png",
  "/icons/maskable-512.png",
  "/objets.md",
  "https://cdn.jsdelivr.net/npm/idb@8/build/umd.js"
];

// --------------------------------------------------------------------------
// 1. Installation : mise en cache des assets statiques
// Stratégie (b) : pas de self.skipWaiting() automatique ici,
// la page propose la mise à jour à l'utilisateur via une bannière (§ 7 cheatsheet)
// --------------------------------------------------------------------------
self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(STATIC_ASSETS);
    })
  );
});

// --------------------------------------------------------------------------
// 2. Activation : purge des anciens caches et prise de contrôle des clients
// --------------------------------------------------------------------------
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((k) => k !== CACHE_NAME)
            .map((k) => caches.delete(k))
        )
      )
      .then(() => self.clients.claim())
  );
});

// --------------------------------------------------------------------------
// 3. Message : écoute du signal SKIP_WAITING envoyé par le bouton "Mettre à jour"
// --------------------------------------------------------------------------
self.addEventListener("message", (event) => {
  if (event.data && event.data.type === "SKIP_WAITING") {
    self.skipWaiting();
  }
});

// --------------------------------------------------------------------------
// 4. Fetch : Routage selon le type de ressource (§ 4 & § 13 cheatsheet)
// --------------------------------------------------------------------------
self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);

  // A. Requêtes de navigation (HTML de la page)
  // Network First avec fallback sur le cache (avec ignoreSearch: true pour supporter /?item=<id>)
  // et repli ultime sur /offline.html
  if (request.mode === "navigate") {
    event.respondWith(
      fetch(request)
        .then((response) => {
          if (response && response.status === 200) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, clone));
          }
          return response;
        })
        .catch(async () => {
          // Tenter d'ouvrir l'App Shell en cache en ignorant les query params (?item=xyz)
          const cachedShell = await caches.match(request, { ignoreSearch: true });
          if (cachedShell) return cachedShell;

          // Si rien dans le cache pour cette route, servir la page de secours offline
          return caches.match("/offline.html");
        })
    );
    return;
  }

  // B. Requêtes API (/api/)
  // Network First avec mise en cache ou réponse hors ligne
  if (url.pathname.startsWith("/api/")) {
    event.respondWith(
      fetch(request)
        .then((response) => {
          // On ne met en cache que les GET réussis
          if (request.method === "GET" && response.status === 200) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, clone));
          }
          return response;
        })
        .catch(() => {
          // En cas de panne réseau, essayer le cache de l'API
          return caches.match(request).then((cached) => {
            if (cached) return cached;
            // Si pas dans le cache, retourner un JSON d'état hors ligne
            return new Response(
              JSON.stringify({ offline: true, error: "Réseau indisponible. Utilisation des données locales." }),
              { headers: { "Content-Type": "application/json" } }
            );
          });
        })
    );
    return;
  }

  // C. Ressources statiques (CSS, JS, images, icônes, polices, CDN)
  // Stratégie Cache First (§ 4 cheatsheet)
  if (
    url.pathname.match(/\.(css|js|png|jpg|jpeg|svg|webp|woff2|ico)$/) ||
    url.origin === "https://cdn.jsdelivr.net"
  ) {
    event.respondWith(
      caches.match(request).then((cached) => {
        if (cached) return cached;
        return fetch(request).then((networkResponse) => {
          if (networkResponse && networkResponse.status === 200) {
            const clone = networkResponse.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, clone));
          }
          return networkResponse;
        });
      })
    );
    return;
  }

  // D. Autres requêtes : Stale While Revalidate
  event.respondWith(
    caches.open(CACHE_NAME).then(async (cache) => {
      const cached = await cache.match(request);
      const fetchPromise = fetch(request)
        .then((response) => {
          if (response && response.status === 200) {
            cache.put(request, response.clone());
          }
          return response;
        })
        .catch(() => cached);
      return cached || fetchPromise;
    })
  );
});

// --------------------------------------------------------------------------
// 5. Background Sync (§ 10 cheatsheet)
// --------------------------------------------------------------------------
self.addEventListener("sync", (event) => {
  if (event.tag === "sync-campus-items") {
    event.waitUntil(syncOutboxViaSW());
  }
});

// Synchronisation depuis le Service Worker avec notification des clients
async function syncOutboxViaSW() {
  try {
    // Si IndexedDB est accessible
    const allClients = await self.clients.matchAll({ includeUncontrolled: true });
    for (const client of allClients) {
      client.postMessage({ type: "TRIGGER_SYNC" });
    }
  } catch (err) {
    console.error("[SW] Erreur Background Sync:", err);
  }
}
