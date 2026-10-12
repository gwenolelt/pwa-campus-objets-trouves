import http from "node:http";

function req(options, data) {
  return new Promise((resolve, reject) => {
    const r = http.request("http://localhost:3000" + options.path, options, (res) => {
      let body = "";
      res.on("data", (c) => (body += c));
      res.on("end", () => resolve({ status: res.statusCode, body }));
    });
    r.on("error", reject);
    if (data) r.write(JSON.stringify(data));
    r.end();
  });
}

async function testApi() {
  console.log("Testing API...");
  // 1. GET /api/items
  const getRes = await req({ path: "/api/items", method: "GET" });
  console.log("1. GET items status:", getRes.status, "count:", JSON.parse(getRes.body).length);

  // 2. PUT with matching If-Match: "1"
  const putRes1 = await req({
    path: "/api/items/seed-1",
    method: "PUT",
    headers: { "Content-Type": "application/json", "If-Match": '"1"' }
  }, { status: "returned" });
  const parsed1 = JSON.parse(putRes1.body);
  console.log("2. PUT matching If-Match status:", putRes1.status, "new version:", parsed1.version);

  // 3. PUT with outdated If-Match: "1" (should return 412 Precondition Failed!)
  const putRes2 = await req({
    path: "/api/items/seed-1",
    method: "PUT",
    headers: { "Content-Type": "application/json", "If-Match": '"1"' }
  }, { status: "returned" });
  console.log("3. PUT mismatch If-Match status (expected 412):", putRes2.status);

  if (putRes2.status === 412) {
    console.log("✅ Conflit If-Match 412 géré avec succès !");
  } else {
    console.error("❌ Échec du test 412");
  }
}

testApi();
