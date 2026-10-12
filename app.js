// app.js — Logique principale PWA "Objets Trouvés du Campus"
// Utilise Vanilla ES Modules, Service Worker, IndexedDB (idb), Web Share, MediaDevices

// --------------------------------------------------------------------------
// 1. Initialisation IndexedDB via idb (§ 8 cheatsheet)
// --------------------------------------------------------------------------
const DB_NAME = "campus-lost-found-db";
const DB_VERSION = 1;

const dbPromise = idb.openDB(DB_NAME, DB_VERSION, {
  upgrade(db) {
    // Store des objets du catalogue
    if (!db.objectStoreNames.contains("items")) {
      const itemStore = db.createObjectStore("items", { keyPath: "id" });
      itemStore.createIndex("status", "status", { unique: false });
      itemStore.createIndex("dateFound", "dateFound", { unique: false });
    }
    // Store outbox pour la synchronisation hors ligne
    if (!db.objectStoreNames.contains("outbox")) {
      db.createObjectStore("outbox", { keyPath: "outboxId", autoIncrement: true });
    }
  },
});

// --------------------------------------------------------------------------
// 2. Gestion et cycle de vie du Service Worker (§ 2 & § 7 cheatsheet)
// --------------------------------------------------------------------------
let swRegistration = null;

async function registerServiceWorker() {
  if (!("serviceWorker" in navigator)) return;

  try {
    const reg = await navigator.serviceWorker.register("/sw.js");
    swRegistration = reg;
    console.log("✅ Service Worker enregistré avec succès. Scope:", reg.scope);

    // Écoute de la mise à jour (bannière utilisateur § 7)
    reg.addEventListener("updatefound", () => {
      const newWorker = reg.installing;
      if (!newWorker) return;

      newWorker.addEventListener("statechange", () => {
        if (newWorker.state === "installed" && navigator.serviceWorker.controller) {
          // Un nouveau SW est prêt en attente -> afficher la bannière
          const banner = document.getElementById("update-banner");
          if (banner) banner.style.display = "flex";

          const btnUpdate = document.getElementById("btn-update");
          if (btnUpdate) {
            btnUpdate.onclick = () => {
              newWorker.postMessage({ type: "SKIP_WAITING" });
            };
          }
        }
      });
    });

    // Quand le nouveau SW prend le contrôle -> recharger la page
    navigator.serviceWorker.addEventListener("controllerchange", () => {
      window.location.reload();
    });

    // Écoute des messages provenant du SW (ex. déclencheur Background Sync)
    navigator.serviceWorker.addEventListener("message", (event) => {
      if (event.data?.type === "TRIGGER_SYNC") {
        syncPendingData();
      }
    });

  } catch (err) {
    console.error("❌ Erreur lors de l'enregistrement du Service Worker:", err);
  }
}

// --------------------------------------------------------------------------
// 3. Gestion de l'installation PWA custom (beforeinstallprompt § 6)
// --------------------------------------------------------------------------
let deferredPrompt = null;
const btnInstall = document.getElementById("btn-install");

window.addEventListener("beforeinstallprompt", (e) => {
  e.preventDefault();
  deferredPrompt = e;
  if (btnInstall) btnInstall.style.display = "inline-flex";
});

if (btnInstall) {
  btnInstall.addEventListener("click", async () => {
    if (!deferredPrompt) return;
    deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    console.log("[PWA Install] Choix utilisateur:", outcome);
    deferredPrompt = null;
    btnInstall.style.display = "none";
  });
}

window.addEventListener("appinstalled", () => {
  if (btnInstall) btnInstall.style.display = "none";
  showToast("🎉 L'application a été installée avec succès sur votre appareil !", "success");
});

// --------------------------------------------------------------------------
// 4. Détection réseau & UX Offline (§ 9 & § 10)
// --------------------------------------------------------------------------
const networkStatusPill = document.getElementById("network-status");
const networkStatusText = document.getElementById("network-status-text");
const offlineBanner = document.getElementById("offline-banner");
const syncBadge = document.getElementById("sync-badge");
const syncCountEl = document.getElementById("sync-count");

function updateNetworkUI() {
  const isOnline = navigator.onLine;
  if (isOnline) {
    document.body.classList.remove("is-offline");
    if (networkStatusPill) {
      networkStatusPill.className = "status-pill online";
    }
    if (networkStatusText) networkStatusText.textContent = "En ligne";
    if (offlineBanner) offlineBanner.style.display = "none";
  } else {
    document.body.classList.add("is-offline");
    if (networkStatusPill) {
      networkStatusPill.className = "status-pill offline";
    }
    if (networkStatusText) networkStatusText.textContent = "Hors ligne";
    if (offlineBanner) offlineBanner.style.display = "flex";
  }
}

window.addEventListener("online", () => {
  updateNetworkUI();
  showToast("🌐 Connexion rétablie ! Synchronisation des données...", "success");
  syncPendingData();
});

window.addEventListener("offline", () => {
  updateNetworkUI();
  showToast("📴 Mode hors ligne actif — vos actions seront synchronisées au retour du réseau.", "warning");
});

async function updateSyncBadge() {
  const db = await dbPromise;
  const count = await db.count("outbox");
  if (count > 0) {
    if (syncBadge) syncBadge.style.display = "inline-flex";
    if (syncCountEl) syncCountEl.textContent = count;
    if ("setAppBadge" in navigator) {
      navigator.setAppBadge(count).catch(() => {});
    }
  } else {
    if (syncBadge) syncBadge.style.display = "none";
    if ("clearAppBadge" in navigator) {
      navigator.clearAppBadge().catch(() => {});
    }
  }
}

// --------------------------------------------------------------------------
// 5. Synchronisation de l'Outbox (Background Sync + repli en ligne)
// --------------------------------------------------------------------------
async function syncPendingData() {
  if (!navigator.onLine) return;
  const db = await dbPromise;
  const outboxItems = await db.getAll("outbox");
  if (!outboxItems || outboxItems.length === 0) {
    await updateSyncBadge();
    return;
  }

  let successCount = 0;

  for (const entry of outboxItems) {
    try {
      if (entry.type === "create") {
        const response = await fetch("/api/items", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(entry.item),
        });

        if (response.ok) {
          await db.delete("outbox", entry.outboxId);
          // Marquer l'élément local comme synchronisé
          const local = await db.get("items", entry.item.id);
          if (local) {
            local.synced = true;
            await db.put("items", local);
          }
          successCount++;
        }
      } else if (entry.type === "update_status") {
        const response = await fetch(`/api/items/${entry.item.id}`, {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
            "If-Match": `"${entry.version}"`
          },
          body: JSON.stringify(entry.item),
        });

        if (response.status === 412) {
          // Conflit détecté ! (§ 11 cheatsheet)
          const serverVersion = await response.json();
          handleConflict(entry, serverVersion);
          return; // interrompre pour laisser l'utilisateur choisir
        }

        if (response.ok) {
          await db.delete("outbox", entry.outboxId);
          const local = await db.get("items", entry.item.id);
          if (local) {
            local.synced = true;
            await db.put("items", local);
          }
          successCount++;
        }
      }
    } catch (err) {
      console.warn("[Sync] Échec de transmission pour l'élément outbox:", entry.outboxId, err);
      // Réseau toujours instable : on conserve dans l'outbox
    }
  }

  await updateSyncBadge();
  if (successCount > 0) {
    showToast(`✅ ${successCount} modification(s) synchronisée(s) avec le serveur !`, "success");
    await renderItems();
  }
}

// --------------------------------------------------------------------------
// 6. Gestion des conflits de versions (§ 11 cheatsheet)
// --------------------------------------------------------------------------
const modalConflict = document.getElementById("modal-conflict");
const conflictServerInfo = document.getElementById("conflict-server-info");
const btnConflictServer = document.getElementById("btn-conflict-server");
const btnConflictMine = document.getElementById("btn-conflict-mine");

function handleConflict(outboxEntry, serverData) {
  if (!modalConflict) return;
  modalConflict.classList.add("open");
  if (conflictServerInfo) {
    conflictServerInfo.textContent = `Statut serveur : « ${serverData.status === "returned" ? "Restitué" : "Disponible"} », version ${serverData.version}. Modifié le ${new Date(serverData.updatedAt || Date.now()).toLocaleString("fr-FR")}.`;
  }

  btnConflictServer.onclick = async () => {
    // Prendre la version du serveur
    const db = await dbPromise;
    await db.put("items", { ...serverData, synced: true });
    await db.delete("outbox", outboxEntry.outboxId);
    modalConflict.classList.remove("open");
    await updateSyncBadge();
    await renderItems();
    showToast("Version du serveur adoptée.", "success");
  };

  btnConflictMine.onclick = async () => {
    // Écraser en forçant la version courante du serveur
    const db = await dbPromise;
    const forcedItem = { ...outboxEntry.item, version: (serverData.version || 1) + 1 };
    await fetch(`/api/items/${forcedItem.id}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        "If-Match": `"${serverData.version}"`
      },
      body: JSON.stringify(forcedItem),
    });
    forcedItem.synced = true;
    await db.put("items", forcedItem);
    await db.delete("outbox", outboxEntry.outboxId);
    modalConflict.classList.remove("open");
    await updateSyncBadge();
    await renderItems();
    showToast("Votre version locale a été conservée et envoyée.", "success");
  };
}

// --------------------------------------------------------------------------
// 7. Hydratation depuis le HTML initial (§ 13 cheatsheet)
// --------------------------------------------------------------------------
async function hydrateFromHTML() {
  const db = await dbPromise;
  const count = await db.count("items");

  // Premier lancement seulement : importer les exemples du HTML
  if (count === 0) {
    const seedElements = document.querySelectorAll("#items-list [data-seed]");
    for (const el of seedElements) {
      const id = el.getAttribute("data-id") || el.getAttribute("data-seed");
      const title = el.querySelector(".card-title")?.textContent.trim() || "";
      const category = el.querySelector(".card-category")?.textContent.trim() || "Autre";
      const description = el.querySelector(".card-desc")?.textContent.trim() || `Objet déclaré sur le campus. Retrouvez-le à l'adresse de dépôt indiquée.`;
      const statusBadge = el.querySelector(".badge-status");
      const isReturned = el.classList.contains("is-returned") || statusBadge?.textContent.includes("Restitué");
      
      const infoSpans = el.querySelectorAll(".card-info span");
      const dateFound = infoSpans[0]?.textContent.trim() || "12 octobre 2026";
      const locationFound = infoSpans[1]?.textContent.trim() || "Campus";
      const depositLocation = infoSpans[2]?.textContent.trim() || "Accueil central";
      const dateReturned = isReturned ? (infoSpans[3]?.textContent.trim() || "11 octobre 2026") : null;

      const item = {
        id,
        title,
        category,
        description,
        dateFound,
        locationFound,
        depositLocation,
        photo: "/icons/icon-512.png",
        status: isReturned ? "returned" : "active",
        dateReturned,
        version: 1,
        synced: true,
        createdAt: Date.now() - (id === "seed-1" ? 0 : id === "seed-2" ? 86400000 : 172800000),
      };

      await db.put("items", item);
    }
  }

  // Tenter de rafraîchir depuis l'API si le serveur est disponible
  try {
    const apiRes = await fetch("/api/items");
    if (apiRes.ok) {
      const apiItems = await apiRes.json();
      for (const item of apiItems) {
        await db.put("items", { ...item, synced: true });
      }

      // Aligner le catalogue local : supprimer les objets qui ne sont plus sur le serveur
      const outboxItems = await db.getAll("outbox");
      const outboxIds = new Set(outboxItems.map((o) => o.item?.id));
      const serverIds = new Set(apiItems.map((i) => i.id));
      const allLocal = await db.getAll("items");
      for (const localItem of allLocal) {
        if (!serverIds.has(localItem.id) && !outboxIds.has(localItem.id)) {
          await db.delete("items", localItem.id);
        }
      }
    }
  } catch {
    // Mode hors ligne ou API statique : continue en toute transparence
  }
}

// Réinitialisation complète du catalogue (vider les données et restaurer les 3 objets officiels)
async function resetCatalogData() {
  const db = await dbPromise;
  await db.clear("items");
  await db.clear("outbox");
  await updateSyncBadge();

  // Importer les 3 graines officielles du HTML
  const seedElements = document.querySelectorAll("#items-list [data-seed]");
  for (const el of seedElements) {
    const id = el.getAttribute("data-id") || el.getAttribute("data-seed");
    const title = el.querySelector(".card-title")?.textContent.trim() || "";
    const category = el.querySelector(".card-category")?.textContent.trim() || "Autre";
    const description = el.querySelector(".card-desc")?.textContent.trim() || `Objet déclaré sur le campus. Retrouvez-le à l'adresse de dépôt indiquée.`;
    const statusBadge = el.querySelector(".badge-status");
    const isReturned = el.classList.contains("is-returned") || statusBadge?.textContent.includes("Restitué");
    
    const infoSpans = el.querySelectorAll(".card-info span");
    const dateFound = infoSpans[0]?.textContent.trim() || "12 octobre 2026";
    const locationFound = infoSpans[1]?.textContent.trim() || "Campus";
    const depositLocation = infoSpans[2]?.textContent.trim() || "Accueil central";
    const dateReturned = isReturned ? (infoSpans[3]?.textContent.trim() || "11 octobre 2026") : null;

    const item = {
      id,
      title,
      category,
      description,
      dateFound,
      locationFound,
      depositLocation,
      photo: "/icons/icon-512.png",
      status: isReturned ? "returned" : "active",
      dateReturned,
      version: 1,
      synced: true,
      createdAt: Date.now() - (id === "seed-1" ? 0 : id === "seed-2" ? 86400000 : 172800000),
    };

    await db.put("items", item);
  }

  await renderItems();
  showToast("🧹 Données nettoyées : seuls les 3 objets officiels sont conservés !", "success");
}

const btnResetData = document.getElementById("btn-reset-data");
if (btnResetData) {
  btnResetData.addEventListener("click", async () => {
    if (window.confirm("Voulez-vous nettoyer les données de l'application et restaurer les 3 objets officiels du sujet ?")) {
      await resetCatalogData();
    }
  });
}

// --------------------------------------------------------------------------
// 8. Rendu de la liste des objets (Sécurisé XSS via textContent & createElement)
// --------------------------------------------------------------------------
let currentFilter = "all";
let currentSearch = "";
let currentCategory = "all";

async function renderItems() {
  const db = await dbPromise;
  let items = await db.getAll("items");

  // Trier par date décroissante
  items.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));

  // Filtrage
  if (currentFilter === "active") {
    items = items.filter((i) => i.status === "active");
  } else if (currentFilter === "returned") {
    items = items.filter((i) => i.status === "returned");
  }

  if (currentCategory !== "all") {
    items = items.filter((i) => i.category === currentCategory);
  }

  if (currentSearch.trim() !== "") {
    const q = currentSearch.toLowerCase();
    items = items.filter(
      (i) =>
        i.title.toLowerCase().includes(q) ||
        i.locationFound.toLowerCase().includes(q) ||
        i.depositLocation.toLowerCase().includes(q) ||
        (i.description && i.description.toLowerCase().includes(q))
    );
  }

  const listEl = document.getElementById("items-list");
  const countEl = document.getElementById("items-count");
  if (!listEl) return;

  if (countEl) {
    countEl.textContent = `${items.length} objet${items.length > 1 ? "s" : ""} répertorié${items.length > 1 ? "s" : ""}`;
  }

  // Reconstruction propre sans innerHTML arbitraire
  const fragment = document.createDocumentFragment();

  if (items.length === 0) {
    const emptyLi = document.createElement("li");
    emptyLi.style.gridColumn = "1 / -1";
    emptyLi.style.textAlign = "center";
    emptyLi.style.padding = "2rem";
    emptyLi.style.color = "var(--text-muted)";
    emptyLi.textContent = "Aucun objet ne correspond à votre recherche.";
    fragment.appendChild(emptyLi);
  } else {
    for (const item of items) {
      const isReturned = item.status === "returned";

      const li = document.createElement("li");
      li.className = `item-card ${isReturned ? "is-returned" : ""}`;
      li.setAttribute("data-id", item.id);

      // 1. Wrapper Image
      const imgWrapper = document.createElement("div");
      imgWrapper.className = "card-img-wrapper";

      const img = document.createElement("img");
      img.src = item.photo || "/icons/icon-512.png";
      img.alt = `Photo de ${item.title}`;
      img.className = "card-img";
      img.loading = "lazy";
      imgWrapper.appendChild(img);

      const statusBadge = document.createElement("span");
      statusBadge.className = `badge-status ${isReturned ? "returned" : "active"}`;
      statusBadge.textContent = isReturned ? "Restitué au propriétaire" : "Disponible au dépôt";
      imgWrapper.appendChild(statusBadge);

      if (item.synced === false) {
        const syncBadge = document.createElement("span");
        syncBadge.className = "badge-sync";
        syncBadge.textContent = "En attente d'envoi ⏳";
        imgWrapper.appendChild(syncBadge);
      }

      li.appendChild(imgWrapper);

      // 2. Corps
      const cardBody = document.createElement("div");
      cardBody.className = "card-body";

      const categorySpan = document.createElement("span");
      categorySpan.className = "card-category";
      categorySpan.textContent = item.category || "Autre";
      cardBody.appendChild(categorySpan);

      const titleH3 = document.createElement("h3");
      titleH3.className = "card-title";
      titleH3.textContent = item.title;
      cardBody.appendChild(titleH3);

      if (item.description) {
        const descP = document.createElement("p");
        descP.className = "card-desc";
        descP.textContent = item.description;
        cardBody.appendChild(descP);
      }

      const cardInfo = document.createElement("div");
      cardInfo.className = "card-info";

      const lineDate = createInfoLine("Trouvé le :", item.dateFound);
      const lineLoc = createInfoLine("Lieu :", item.locationFound);
      const lineDep = createInfoLine("Déposé à :", item.depositLocation);

      cardInfo.appendChild(lineDate);
      cardInfo.appendChild(lineLoc);
      cardInfo.appendChild(lineDep);

      if (isReturned && item.dateReturned) {
        const lineRet = createInfoLine("Restitué le :", item.dateReturned);
        lineRet.style.color = "var(--success)";
        cardInfo.appendChild(lineRet);
      }

      cardBody.appendChild(cardInfo);
      li.appendChild(cardBody);

      // 3. Actions
      const cardActions = document.createElement("div");
      cardActions.className = "card-actions";

      const btnView = document.createElement("button");
      btnView.className = "btn btn-secondary btn-sm btn-view-item";
      btnView.textContent = "Voir la fiche";
      btnView.onclick = () => openItemDetail(item.id);

      const btnShare = document.createElement("button");
      btnShare.className = "btn btn-outline btn-sm btn-share-item";
      btnShare.textContent = "Partager 🔗";
      btnShare.title = "Partager cette fiche publique";
      btnShare.onclick = (e) => {
        e.stopPropagation();
        shareItem(item);
      };

      cardActions.appendChild(btnView);
      cardActions.appendChild(btnShare);
      li.appendChild(cardActions);

      fragment.appendChild(li);
    }
  }

  listEl.replaceChildren(fragment);
}

function createInfoLine(label, value) {
  const div = document.createElement("div");
  div.className = "info-line";
  const strong = document.createElement("strong");
  strong.textContent = label;
  const span = document.createElement("span");
  span.textContent = ` ${value || "Non précisé"}`;
  div.appendChild(strong);
  div.appendChild(span);
  return div;
}

// --------------------------------------------------------------------------
// 9. Capacité Caméra (Prise de photo via MediaDevices / getUserMedia)
// --------------------------------------------------------------------------
let mediaStream = null;
let capturedPhotoData = null;

const btnCamera = document.getElementById("btn-camera");
const btnUploadFile = document.getElementById("btn-upload-file");
const fileInput = document.getElementById("file-input");
const cameraCaptureInput = document.getElementById("camera-capture-input");
const cameraStreamBox = document.getElementById("camera-stream-box");
const cameraVideo = document.getElementById("camera-video");
const cameraCanvas = document.getElementById("camera-canvas");
const btnCapturePhoto = document.getElementById("btn-capture-photo");
const btnCancelCamera = document.getElementById("btn-cancel-camera");
const cameraErrorMsg = document.getElementById("camera-error-msg");
const photoPreviewBox = document.getElementById("photo-preview-box");
const photoPreviewImg = document.getElementById("photo-preview-img");
const btnRetakePhoto = document.getElementById("btn-retake-photo");
const photoButtonsBox = document.getElementById("photo-buttons-box");

// Déclenchement de la caméra APRÈS clic explicite utilisateur
if (btnCamera) {
  btnCamera.addEventListener("click", async () => {
    if (cameraErrorMsg) cameraErrorMsg.style.display = "none";

    // Si getUserMedia n'est pas disponible (ex: contexte HTTP non sécurisé sur mobile au lieu de HTTPS/localhost)
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      if (cameraCaptureInput) {
        // Déclenche l'appareil photo natif du smartphone via l'API HTML5 capture="environment"
        cameraCaptureInput.click();
        return;
      }
      if (cameraErrorMsg) {
        cameraErrorMsg.textContent = "⚠️ Les navigateurs exigent HTTPS pour activer le flux vidéo en direct (getUserMedia). Utilisez le sélecteur de fichier ou l'appareil photo natif.";
        cameraErrorMsg.style.display = "block";
      }
      return;
    }

    try {
      // Demande de permission et démarrage du flux vidéo
      mediaStream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: "environment", // caméra arrière sur smartphone
          width: { ideal: 1280 },
          height: { ideal: 720 }
        },
        audio: false,
      });

      if (cameraVideo) {
        cameraVideo.srcObject = mediaStream;
        await cameraVideo.play();
      }

      if (cameraStreamBox) cameraStreamBox.style.display = "flex";
      if (photoButtonsBox) photoButtonsBox.style.display = "none";

    } catch (err) {
      console.warn("[Caméra] Permission refusée ou erreur:", err);
      if (cameraErrorMsg) {
        cameraErrorMsg.textContent = "⚠️ Accès à la caméra refusé ou indisponible. Vous pouvez importer une photo depuis vos fichiers.";
        cameraErrorMsg.style.display = "block";
      }
      stopCameraStream();
    }
  });
}

function stopCameraStream() {
  if (mediaStream) {
    mediaStream.getTracks().forEach((track) => track.stop());
    mediaStream = null;
  }
  if (cameraVideo) cameraVideo.srcObject = null;
  if (cameraStreamBox) cameraStreamBox.style.display = "none";
  if (!capturedPhotoData && photoButtonsBox) {
    photoButtonsBox.style.display = "flex";
  }
}

if (btnCancelCamera) {
  btnCancelCamera.addEventListener("click", () => {
    stopCameraStream();
  });
}

// Capture instantanée de la caméra sur Canvas
if (btnCapturePhoto) {
  btnCapturePhoto.addEventListener("click", () => {
    if (!cameraVideo || !cameraCanvas) return;
    const width = cameraVideo.videoWidth || 640;
    const height = cameraVideo.videoHeight || 480;

    // Redimensionnement max 800px pour préserver la mémoire IndexedDB
    const maxDim = 800;
    let targetW = width;
    let targetH = height;
    if (width > maxDim || height > maxDim) {
      if (width > height) {
        targetW = maxDim;
        targetH = Math.round((height / width) * maxDim);
      } else {
        targetH = maxDim;
        targetW = Math.round((width / height) * maxDim);
      }
    }

    cameraCanvas.width = targetW;
    cameraCanvas.height = targetH;
    const ctx = cameraCanvas.getContext("2d");
    ctx.drawImage(cameraVideo, 0, 0, targetW, targetH);

    // Encodage image JPEG compressée
    capturedPhotoData = cameraCanvas.toDataURL("image/jpeg", 0.82);

    stopCameraStream();
    displayPhotoPreview(capturedPhotoData);
    showToast("📸 Photo capturée avec succès !", "success");
  });
}

// Fallback : importation via sélecteur de fichier
if (btnUploadFile) {
  btnUploadFile.addEventListener("click", () => {
    if (fileInput) fileInput.click();
  });
}

function processImageFile(file) {
  if (!file) return;
  const reader = new FileReader();
  reader.onload = (event) => {
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement("canvas");
      const maxDim = 800;
      let w = img.width;
      let h = img.height;
      if (w > maxDim || h > maxDim) {
        if (w > h) {
          h = Math.round((h / w) * maxDim);
          w = maxDim;
        } else {
          w = Math.round((w / h) * maxDim);
          h = maxDim;
        }
      }
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext("2d");
      ctx.drawImage(img, 0, 0, w, h);
      capturedPhotoData = canvas.toDataURL("image/jpeg", 0.82);
      displayPhotoPreview(capturedPhotoData);
      showToast("📸 Photo enregistrée !", "success");
    };
    img.src = event.target.result;
  };
  reader.readAsDataURL(file);
}

if (fileInput) {
  fileInput.addEventListener("change", (e) => {
    processImageFile(e.target.files?.[0]);
  });
}

if (cameraCaptureInput) {
  cameraCaptureInput.addEventListener("change", (e) => {
    processImageFile(e.target.files?.[0]);
  });
}

function displayPhotoPreview(dataUrl) {
  if (photoPreviewImg) photoPreviewImg.src = dataUrl;
  if (photoPreviewBox) photoPreviewBox.style.display = "flex";
  if (photoButtonsBox) photoButtonsBox.style.display = "none";
}

if (btnRetakePhoto) {
  btnRetakePhoto.addEventListener("click", () => {
    capturedPhotoData = null;
    if (photoPreviewBox) photoPreviewBox.style.display = "none";
    if (photoButtonsBox) photoButtonsBox.style.display = "flex";
    if (fileInput) fileInput.value = "";
  });
}

// --------------------------------------------------------------------------
// 10. Capacité Web Share API (Partage natif + repli presse-papiers)
// --------------------------------------------------------------------------
async function shareItem(item) {
  const shareUrl = `${window.location.origin}/?item=${encodeURIComponent(item.id)}`;
  const shareTitle = `[Campus] Objet trouvé : ${item.title}`;
  const shareText = `Objet trouvé à ${item.locationFound}, déposé à : ${item.depositLocation}. Consultez la fiche publique pour le récupérer.`;

  if (navigator.share) {
    try {
      await navigator.share({
        title: shareTitle,
        text: shareText,
        url: shareUrl,
      });
      showToast("Lien partagé avec succès !", "success");
    } catch (err) {
      if (err.name !== "AbortError") {
        console.warn("[Web Share] Échec:", err);
        fallbackCopyToClipboard(shareUrl);
      }
    }
  } else {
    // Fallback presse-papiers
    fallbackCopyToClipboard(shareUrl);
  }
}

function fallbackCopyToClipboard(url) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(url)
      .then(() => showToast("📋 Lien public copié dans le presse-papiers !", "success"))
      .catch(() => showToast(`Lien public : ${url}`));
  } else {
    showToast(`Lien public : ${url}`);
  }
}

// --------------------------------------------------------------------------
// 11. Modal : Fiche Publique d'un Objet (Deep link ?item=<id>)
// --------------------------------------------------------------------------
let currentSelectedItem = null;
const modalDetail = document.getElementById("modal-item-detail");
const detailHeading = document.getElementById("detail-heading");
const detailCategory = document.getElementById("detail-category");
const detailStatusBadge = document.getElementById("detail-status-badge");
const detailImg = document.getElementById("detail-img");
const detailDescription = document.getElementById("detail-description");
const detailDate = document.getElementById("detail-date");
const detailLocation = document.getElementById("detail-location");
const detailDeposit = document.getElementById("detail-deposit");
const detailReturnedRow = document.getElementById("detail-returned-row");
const detailReturnedDate = document.getElementById("detail-returned-date");
const detailPublicUrl = document.getElementById("detail-public-url");
const btnCopyLink = document.getElementById("btn-copy-link");
const btnShareDetail = document.getElementById("btn-share-detail");
const btnMarkReturned = document.getElementById("btn-mark-returned");
const btnCloseDetail = document.getElementById("btn-close-detail");
const btnCloseDetailFooter = document.getElementById("btn-close-detail-footer");

async function openItemDetail(itemId) {
  const db = await dbPromise;
  const item = await db.get("items", itemId);
  if (!item) {
    showToast("Objet introuvable ou non synchronisé.", "warning");
    return;
  }

  currentSelectedItem = item;
  const isReturned = item.status === "returned";

  if (detailHeading) detailHeading.textContent = item.title;
  if (detailCategory) detailCategory.textContent = item.category || "Autre";
  if (detailStatusBadge) {
    detailStatusBadge.className = `badge-status ${isReturned ? "returned" : "active"}`;
    detailStatusBadge.textContent = isReturned ? "Restitué au propriétaire" : "Disponible au dépôt";
  }
  if (detailImg) {
    detailImg.src = item.photo || "/icons/icon-512.png";
    detailImg.alt = `Photo de ${item.title}`;
  }
  if (detailDescription) {
    detailDescription.textContent = item.description || "Aucune description supplémentaire fournie.";
  }
  if (detailDate) detailDate.textContent = item.dateFound;
  if (detailLocation) detailLocation.textContent = item.locationFound;
  if (detailDeposit) detailDeposit.textContent = item.depositLocation;

  if (isReturned && item.dateReturned) {
    if (detailReturnedRow) detailReturnedRow.style.display = "flex";
    if (detailReturnedDate) detailReturnedDate.textContent = item.dateReturned;
    if (btnMarkReturned) btnMarkReturned.style.display = "none";
  } else {
    if (detailReturnedRow) detailReturnedRow.style.display = "none";
    if (btnMarkReturned) btnMarkReturned.style.display = "inline-flex";
  }

  const fullUrl = `${window.location.origin}/?item=${encodeURIComponent(item.id)}`;
  if (detailPublicUrl) detailPublicUrl.textContent = fullUrl;

  // Mettre à jour l'URL sans rechargement
  history.pushState({ itemId: item.id }, "", `/?item=${encodeURIComponent(item.id)}`);

  if (modalDetail) modalDetail.classList.add("open");
}

function closeItemDetail() {
  if (modalDetail) modalDetail.classList.remove("open");
  currentSelectedItem = null;
  history.pushState(null, "", window.location.pathname);
}

if (btnCloseDetail) btnCloseDetail.onclick = closeItemDetail;
if (btnCloseDetailFooter) btnCloseDetailFooter.onclick = closeItemDetail;

if (btnCopyLink) {
  btnCopyLink.onclick = () => {
    if (currentSelectedItem) {
      const fullUrl = `${window.location.origin}/?item=${encodeURIComponent(currentSelectedItem.id)}`;
      fallbackCopyToClipboard(fullUrl);
    }
  };
}

if (btnShareDetail) {
  btnShareDetail.onclick = () => {
    if (currentSelectedItem) shareItem(currentSelectedItem);
  };
}

// Marquer comme restitué (avec versioning et gestion de conflit § 11)
if (btnMarkReturned) {
  btnMarkReturned.onclick = async () => {
    if (!currentSelectedItem) return;

    const confirmMsg = "Confirmez-vous que cet objet a bien été récupéré et restitué à son propriétaire ?";
    if (!window.confirm(confirmMsg)) return;

    const db = await dbPromise;
    const nowIso = new Date().toLocaleDateString("fr-FR", {
      day: "numeric",
      month: "long",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit"
    });

    const oldVersion = currentSelectedItem.version || 1;
    currentSelectedItem.status = "returned";
    currentSelectedItem.dateReturned = nowIso;
    currentSelectedItem.version = oldVersion + 1;
    currentSelectedItem.synced = false;

    // Enregistrement optimiste local dans IndexedDB
    await db.put("items", currentSelectedItem);

    // Enregistrement dans l'outbox
    await db.add("outbox", {
      type: "update_status",
      item: currentSelectedItem,
      version: oldVersion,
      timestamp: Date.now(),
    });

    await updateSyncBadge();
    closeItemDetail();
    await renderItems();

    showToast("✅ Objet marqué comme restitué !", "success");

    // Tenter l'envoi immédiat ou Background Sync
    if (navigator.onLine) {
      await syncPendingData();
    } else {
      if (swRegistration && "sync" in swRegistration) {
        await swRegistration.sync.register("sync-campus-items").catch(() => {});
      }
      showToast("📴 Enregistré hors ligne — envoi dès le retour du réseau.", "warning");
    }
  };
}

// --------------------------------------------------------------------------
// 12. Modal : Déclaration d'un objet trouvé (Formulaire)
// --------------------------------------------------------------------------
const modalDeclare = document.getElementById("modal-declare");
const btnOpenDeclare = document.getElementById("btn-open-declare");
const btnCloseDeclare = document.getElementById("btn-close-declare");
const btnCancelDeclare = document.getElementById("btn-cancel-declare");
const formDeclare = document.getElementById("form-declare");

function openDeclareModal() {
  if (modalDeclare) modalDeclare.classList.add("open");
  const dateInput = document.getElementById("input-date");
  if (dateInput) {
    const now = new Date();
    // Format YYYY-MM-DDTHH:mm
    const pad = (n) => String(n).padStart(2, "0");
    dateInput.value = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
  }
}

function closeDeclareModal() {
  stopCameraStream();
  capturedPhotoData = null;
  if (photoPreviewBox) photoPreviewBox.style.display = "none";
  if (photoButtonsBox) photoButtonsBox.style.display = "flex";
  if (cameraErrorMsg) cameraErrorMsg.style.display = "none";
  if (formDeclare) formDeclare.reset();
  if (modalDeclare) modalDeclare.classList.remove("open");
}

if (btnOpenDeclare) btnOpenDeclare.onclick = openDeclareModal;
if (btnCloseDeclare) btnCloseDeclare.onclick = closeDeclareModal;
if (btnCancelDeclare) btnCancelDeclare.onclick = closeDeclareModal;

if (formDeclare) {
  formDeclare.addEventListener("submit", async (e) => {
    e.preventDefault();

    const title = document.getElementById("input-title")?.value.trim();
    const category = document.getElementById("select-category")?.value;
    const rawDate = document.getElementById("input-date")?.value;
    const locationFound = document.getElementById("input-location")?.value.trim();
    const depositLocation = document.getElementById("input-deposit")?.value.trim();
    const description = document.getElementById("input-desc")?.value.trim();

    if (!title || !locationFound || !depositLocation) {
      showToast("Veuillez renseigner les champs obligatoires.", "warning");
      return;
    }

    let formattedDate = rawDate;
    if (rawDate) {
      try {
        const d = new Date(rawDate);
        formattedDate = d.toLocaleDateString("fr-FR", {
          day: "numeric",
          month: "long",
          year: "numeric",
          hour: "2-digit",
          minute: "2-digit"
        });
      } catch {
        formattedDate = rawDate;
      }
    }

    const newItem = {
      id: `obj-${Date.now()}-${Math.random().toString(36).substr(2, 6)}`,
      title,
      category,
      dateFound: formattedDate,
      locationFound,
      depositLocation,
      description: description || "Déposé à l'endroit indiqué.",
      photo: capturedPhotoData || "/icons/icon-512.png",
      status: "active",
      version: 1,
      synced: false,
      createdAt: Date.now(),
    };

    const db = await dbPromise;

    // 1. Sauvegarde locale optimiste
    await db.put("items", newItem);

    // 2. Ajout dans l'outbox
    await db.add("outbox", {
      type: "create",
      item: newItem,
      timestamp: Date.now(),
    });

    await updateSyncBadge();
    closeDeclareModal();
    await renderItems();

    showToast("🎉 Objet déclaré avec succès !", "success");

    // 3. Synchronisation
    if (navigator.onLine) {
      await syncPendingData();
    } else {
      if (swRegistration && "sync" in swRegistration) {
        await swRegistration.sync.register("sync-campus-items").catch(() => {});
      }
      showToast("📴 Enregistré hors ligne — envoi automatique dès le retour du réseau.", "warning");
    }
  });
}

// --------------------------------------------------------------------------
// 13. Notifications locales & permission (§ 12 cheatsheet)
// --------------------------------------------------------------------------
const btnNotification = document.getElementById("btn-notification");

if (btnNotification) {
  btnNotification.addEventListener("click", async () => {
    if (!("Notification" in window)) {
      showToast("Les notifications ne sont pas supportées sur ce navigateur.");
      return;
    }

    const permission = await Notification.requestPermission();
    if (permission === "granted") {
      showToast("🔔 Notifications autorisées !", "success");
      new Notification("Objets Trouvés Campus", {
        body: "Vous recevrez des alertes lors de la restitution de vos objets.",
        icon: "/icons/icon-192.png",
        tag: "campus-welcome-notification",
      });
    } else {
      showToast("Notifications refusées.", "warning");
    }
  });
}

// --------------------------------------------------------------------------
// 14. Recherche et Filtres UI
// --------------------------------------------------------------------------
const searchInput = document.getElementById("search-input");
const categorySelect = document.getElementById("category-select");
const filterTabs = document.querySelectorAll(".filter-tab");

if (searchInput) {
  searchInput.addEventListener("input", (e) => {
    currentSearch = e.target.value;
    renderItems();
  });
}

if (categorySelect) {
  categorySelect.addEventListener("change", (e) => {
    currentCategory = e.target.value;
    renderItems();
  });
}

filterTabs.forEach((tab) => {
  tab.addEventListener("click", () => {
    filterTabs.forEach((t) => t.classList.remove("active"));
    tab.classList.add("active");
    currentFilter = tab.getAttribute("data-filter") || "all";
    renderItems();
  });
});

// --------------------------------------------------------------------------
// 15. Système de Toasts d'alerte UI
// --------------------------------------------------------------------------
function showToast(message, type = "info") {
  const container = document.getElementById("toast-container");
  if (!container) return;

  const toast = document.createElement("div");
  toast.className = `toast ${type === "success" ? "toast-success" : type === "warning" ? "toast-warning" : type === "danger" ? "toast-danger" : ""}`;
  
  const span = document.createElement("span");
  span.textContent = message;
  toast.appendChild(span);

  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = "0";
    toast.style.transform = "translateY(10px)";
    toast.style.transition = "all 0.3s ease";
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// --------------------------------------------------------------------------
// 16. Démarrage de l'Application
// --------------------------------------------------------------------------
window.addEventListener("DOMContentLoaded", async () => {
  updateNetworkUI();
  await registerServiceWorker();
  await hydrateFromHTML();
  await updateSyncBadge();
  await renderItems();

  // Vérifier si un item est demandé via l'URL (ex: /?item=seed-1)
  const urlParams = new URLSearchParams(window.location.search);
  const requestedItem = urlParams.get("item") || urlParams.get("id");
  if (requestedItem) {
    await openItemDetail(requestedItem);
  }

  // Tenter une synchronisation si des items sont restés dans l'outbox
  if (navigator.onLine) {
    syncPendingData();
  }
});
