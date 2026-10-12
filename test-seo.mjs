// test-seo.mjs — Test automatisé des 4 vérifications SEO / LLM (§ 13 du cheatsheet)
import http from "node:http";

const BASE_URL = process.env.BASE_URL || "http://localhost:3000";

function fetchReq(urlPath, options = {}) {
  return new Promise((resolve, reject) => {
    const url = new URL(urlPath, BASE_URL);
    const req = http.request(url, options, (res) => {
      let data = "";
      res.on("data", (chunk) => (data += chunk));
      res.on("end", () => resolve({ status: res.statusCode, headers: res.headers, body: data }));
    });
    req.on("error", reject);
    req.end();
  });
}

async function runTests() {
  console.log(`🔍 Exécution des tests de visibilité et SEO sur ${BASE_URL}...\n`);
  let passed = 0;
  let total = 0;

  // Test 1 : Contenu dans le HTML initial (3 li data-seed)
  total++;
  try {
    const res = await fetchReq("/", { headers: { "User-Agent": "GPTBot" } });
    const liMatches = (res.body.match(/<li[ >]/g) || []).length;
    if (liMatches >= 3) {
      console.log(`✅ Test 1 réussi : Contenu public présent dans le HTML initial (${liMatches} éléments <li> trouvés)`);
      passed++;
    } else {
      console.error(`❌ Test 1 échoué : Moins de 3 éléments <li> trouvés (${liMatches})`);
    }
  } catch (e) {
    console.error("❌ Test 1 échoué (serveur éteint ?):", e.message);
  }

  // Test 2 : La jumelle Markdown répond
  total++;
  try {
    const res = await fetchReq("/objets.md", { headers: { "User-Agent": "ClaudeBot" } });
    if (res.status === 200 && res.body.includes("# Objets Trouvés du Campus")) {
      console.log("✅ Test 2 réussi : La jumelle Markdown /objets.md répond (200 OK)");
      passed++;
    } else {
      console.error("❌ Test 2 échoué : statut ou contenu inattendu pour /objets.md");
    }
  } catch (e) {
    console.error("❌ Test 2 échoué:", e.message);
  }

  // Test 3 : Type text/markdown et en-tête noindex
  total++;
  try {
    const res = await fetchReq("/objets.md", { method: "HEAD" });
    const ct = res.headers["content-type"] || "";
    const xRobots = res.headers["x-robots-tag"] || "";
    if (ct.includes("text/markdown") && xRobots.includes("noindex")) {
      console.log(`✅ Test 3 réussi : Headers valides pour /objets.md (Content-Type: ${ct}, X-Robots-Tag: ${xRobots})`);
      passed++;
    } else {
      console.error(`❌ Test 3 échoué : Headers reçus : Content-Type: ${ct}, X-Robots-Tag: ${xRobots}`);
    }
  } catch (e) {
    console.error("❌ Test 3 échoué:", e.message);
  }

  // Test 4 : Pas de négociation de contenu pour / (toujours text/html)
  total++;
  try {
    const res = await fetchReq("/", { headers: { Accept: "text/markdown" } });
    const ct = res.headers["content-type"] || "";
    if (ct.includes("text/html")) {
      console.log(`✅ Test 4 réussi : Pas de négociation non désirée sur / (Content-Type: ${ct})`);
      passed++;
    } else {
      console.error(`❌ Test 4 échoué : Content-Type reçu : ${ct}`);
    }
  } catch (e) {
    console.error("❌ Test 4 échoué:", e.message);
  }

  // Test 5 : Fichiers robots.txt, sitemap.xml, llms.txt, manifest.json
  const auxFiles = ["/robots.txt", "/sitemap.xml", "/llms.txt", "/manifest.json"];
  for (const f of auxFiles) {
    total++;
    try {
      const res = await fetchReq(f);
      if (res.status === 200) {
        console.log(`✅ Test Fichier ${f} : Accessible (200 OK)`);
        passed++;
      } else {
        console.error(`❌ Test Fichier ${f} : Statut ${res.status}`);
      }
    } catch (e) {
      console.error(`❌ Test Fichier ${f} : Erreur ${e.message}`);
    }
  }

  console.log(`\n📊 Bilan : ${passed}/${total} tests validés !`);
  process.exit(passed === total ? 0 : 1);
}

runTests();
