import dotenv from "dotenv";
dotenv.config();

import { QdrantClient } from "@qdrant/js-client-rest";

function cleanApiKey(key: string | undefined): string {
  if (!key) return "";
  const parts = key.trim().split("eyJhbGciOiJIUzI1Ni");
  if (parts.length > 1) {
    return "eyJhbGciOiJIUzI1Ni" + parts[1];
  }
  return key.trim();
}

async function initQdrant() {
  const url = process.env.QDRANT_URL?.trim();
  const rawKey = process.env.QDRANT_API_KEY;
  const apiKey = cleanApiKey(rawKey);
  const collectionName = process.env.QDRANT_COLLECTION?.trim() || "garud_knowledge";

  console.log("Connecting to Qdrant:", url);
  console.log("Collection name:", collectionName);

  const client = new QdrantClient({ url, apiKey });

  const collections = await client.getCollections();
  const exists = collections.collections.some(c => c.name === collectionName);
  console.log(`Collection '${collectionName}' exists:`, exists);

  if (!exists) {
    console.log(`Creating collection '${collectionName}' with size: 3072, distance: Cosine...`);
    await client.createCollection(collectionName, {
      vectors: {
        size: 3072,
        distance: "Cosine"
      }
    });
    console.log("Collection created successfully!");
  } else {
    const info = await client.getCollection(collectionName);
    console.log("Collection info:", JSON.stringify(info, null, 2));
  }
}

initQdrant().catch(console.error);
