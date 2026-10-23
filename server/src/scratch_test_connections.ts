import dotenv from "dotenv";
dotenv.config();

import { QdrantClient } from "@qdrant/js-client-rest";
import { GoogleGenerativeAI } from "@google/generative-ai";

async function testQdrant() {
  console.log("--- Testing Qdrant Connection ---");
  const url = process.env.QDRANT_URL;
  const apiKey = process.env.QDRANT_API_KEY;
  console.log("Qdrant URL:", url);
  console.log("Qdrant API Key present:", Boolean(apiKey));

  try {
    const client = new QdrantClient({ url, apiKey });
    const collections = await client.getCollections();
    console.log("Qdrant connection SUCCESS! Collections:", JSON.stringify(collections, null, 2));
    return { success: true, collections: collections.collections };
  } catch (err: any) {
    console.error("Qdrant connection FAILED:", err.message);
    if (err.status) console.error("Status:", err.status);
    return { success: false, error: err.message };
  }
}

async function testGemini() {
  console.log("\n--- Testing Google Gemini ---");
  const apiKey = process.env.GEMINI_API_KEY?.trim();
  const modelName = process.env.GEMINI_MODEL?.trim() || "gemini-2.5-flash";
  const embeddingModelName = process.env.GEMINI_EMBEDDING_MODEL?.trim() || "gemini-embedding-001";
  console.log("Gemini Key present:", Boolean(apiKey));
  console.log("Target Model:", modelName);
  console.log("Target Embedding Model:", embeddingModelName);

  if (!apiKey) {
    console.log("Gemini API key is empty");
    return { success: false, error: "Missing key" };
  }

  const genAI = new GoogleGenerativeAI(apiKey);

  // Test Text Generation
  try {
    const model = genAI.getGenerativeModel({ model: modelName });
    const result = await model.generateContent("Reply with 'GARUD_OK' if you receive this.");
    const response = await result.response;
    console.log("Gemini Generation SUCCESS:", response.text().trim());
  } catch (err: any) {
    console.error("Gemini Generation FAILED:", err.message);
  }

  // Test Embedding
  try {
    const embModel = genAI.getGenerativeModel({ model: embeddingModelName });
    const embResult = await embModel.embedContent("Test cybersecurity embedding for GARUD.");
    const vector = embResult.embedding?.values;
    console.log("Gemini Embedding SUCCESS! Vector dimension:", vector?.length);
  } catch (err: any) {
    console.error(`Gemini Embedding with ${embeddingModelName} FAILED:`, err.message);
    // Also test text-embedding-004 to see if that works
    try {
      console.log("Trying fallback embedding model 'text-embedding-004'...");
      const embModel2 = genAI.getGenerativeModel({ model: "text-embedding-004" });
      const embResult2 = await embModel2.embedContent("Test cybersecurity embedding for GARUD.");
      console.log("text-embedding-004 SUCCESS! Vector dimension:", embResult2.embedding?.values?.length);
    } catch (err2: any) {
      console.error("text-embedding-004 also FAILED:", err2.message);
    }
  }
}

async function main() {
  await testQdrant();
  await testGemini();
}

main();
