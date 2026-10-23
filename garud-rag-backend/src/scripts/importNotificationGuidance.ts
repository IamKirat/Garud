import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { TaskType } from "@google/generative-ai";
import { getEnvConfig } from "../config/env";
import { qdrantPointId } from "../rag/chunker";
import { GeminiService } from "../services/geminiService";
import { QdrantService } from "../services/qdrantService";
import { SyntheticGuidanceMetadata, SyntheticGuidanceMetadataSchema } from "../types";

const EXPECTED_IDS = Array.from({ length: 10 }, (_, index) => `garud-notif-${String(index + 1).padStart(3, "0")}`);
const SEMANTIC_CHECKS = [
  { query: "Someone asked me to tell them my one time password", expected: "garud-notif-002" },
  { query: "Is this unexpected shortened login link safe to open?", expected: "garud-notif-004" },
  { query: "Pay a fee now to get a refund or unlock my account", expected: "garud-notif-003" },
  { query: "A bank employee sent me a message asking for unusual account action", expected: "garud-notif-005" },
  { query: "An urgent tone alone proves that a notification is fraud", expected: "garud-notif-007" }
];

export function loadNotificationGuidanceRecords(): SyntheticGuidanceMetadata[] {
  const file = path.resolve(__dirname, "../data/garud_notification_chunks.jsonl");
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/).filter(line => line.trim());
  const parsed = lines.map((line, index) => {
    let value: unknown;
    try { value = JSON.parse(line); } catch { throw new Error(`Invalid JSON on JSONL line ${index + 1}.`); }
    const record = SyntheticGuidanceMetadataSchema.parse(value);
    const computedHash = crypto.createHash("sha256").update(record.text, "utf8").digest("hex");
    if (computedHash !== record.content_hash) throw new Error(`Content hash mismatch for ${record.chunk_id}.`);
    return record;
  });
  if (parsed.length !== 10 || parsed.some((record, index) => record.chunk_id !== EXPECTED_IDS[index])) {
    throw new Error("Expected exactly the 10 ordered garud-notif-001 through garud-notif-010 records.");
  }
  if (new Set(parsed.map(record => record.chunk_id)).size !== parsed.length) throw new Error("Duplicate chunk IDs in import file.");
  return parsed;
}

function assertPayloadMatches(actual: Record<string, unknown>, expected: SyntheticGuidanceMetadata): void {
  for (const [key, value] of Object.entries(expected)) {
    if (actual[key] !== value) throw new Error(`Qdrant payload mismatch for ${expected.chunk_id}.${key}.`);
  }
}

export async function importNotificationGuidance(): Promise<void> {
  // GARUD_BRAIN.md requires previously shared provider keys to be rotated before any live provider calls.
  if (process.env.GARUD_KEYS_ROTATED !== "true") {
    throw new Error("Live import blocked: rotate the previously shared Gemini and Qdrant keys, then set GARUD_KEYS_ROTATED=true in the process environment before retrying.");
  }

  const records = loadNotificationGuidanceRecords();
  console.log(`Validated ${records.length} synthetic guidance records and their SHA-256 hashes.`);
  const config = getEnvConfig();
  const gemini = new GeminiService();
  const qdrant = new QdrantService();
  if (!gemini.isConfigured() || !qdrant.isConfigured()) throw new Error("Gemini or Qdrant credentials are not configured.");

  // Read-only preflight; do not create, reset, or modify a collection.
  const collection = await qdrant.inspectCollection();
  if (!collection.connected || !collection.exists || collection.error || collection.vectorDimension !== QdrantService.EXPECTED_VECTOR_SIZE) {
    throw new Error(`Qdrant preflight failed: ${collection.error || "collection or vector dimensions are incompatible"}`);
  }
  console.log(`Qdrant preflight passed; vector dimension ${collection.vectorDimension}.`);

  const ingestedAt = Date.now();
  const prepared: Array<{ id: string; vector: number[]; metadata: SyntheticGuidanceMetadata }> = [];
  for (const record of records) {
    const metadata: SyntheticGuidanceMetadata = { ...record, ingested_at: ingestedAt, embedding_model: config.GEMINI_EMBEDDING_MODEL };
    const vector = await gemini.getEmbedding(`${metadata.title}\nTopic: ${metadata.topic}\n${metadata.text}`, TaskType.RETRIEVAL_DOCUMENT);
    if (vector.length !== QdrantService.EXPECTED_VECTOR_SIZE || vector.some(value => !Number.isFinite(value))) {
      throw new Error(`Embedding for ${metadata.chunk_id} is not ${QdrantService.EXPECTED_VECTOR_SIZE} finite dimensions.`);
    }
    prepared.push({ id: qdrantPointId(metadata.chunk_id), vector, metadata });
  }

  // Batches are deterministic and upsert-only, so retries update these IDs without touching unrelated points.
  let uploaded = 0;
  for (let offset = 0; offset < prepared.length; offset += 5) {
    const result = await qdrant.upsertSyntheticGuidance(prepared.slice(offset, offset + 5));
    if (!result.success) throw new Error(`Qdrant upsert failed after ${uploaded} points: ${result.error}`);
    uploaded += result.count;
  }

  const stored = await qdrant.retrieveSyntheticGuidance(prepared.map(item => item.id));
  const storedByChunkId = new Map(stored.map(point => [String(point.payload?.chunk_id || ""), point.payload]));
  for (const item of prepared) {
    const payload = storedByChunkId.get(item.metadata.chunk_id);
    if (!payload) throw new Error(`Post-upsert verification could not find ${item.metadata.chunk_id}.`);
    assertPayloadMatches(payload, item.metadata);
  }

  const semanticResults: Array<{ query: string; expected: string; found: boolean; topIds: string[] }> = [];
  for (const check of SEMANTIC_CHECKS) {
    const queryVector = await gemini.getEmbedding(check.query, TaskType.RETRIEVAL_QUERY);
    if (queryVector.length !== collection.vectorDimension) throw new Error("Semantic test query embedding dimension mismatch.");
    const matches = await qdrant.searchSimilar(queryVector, 10, 0);
    const topIds = matches.map(match => match.id);
    semanticResults.push({ query: check.query, expected: check.expected, found: topIds.includes(check.expected), topIds });
  }

  console.log(JSON.stringify({
    uploaded,
    skipped: 0,
    failures: [],
    collection: config.QDRANT_COLLECTION,
    embeddingModel: config.GEMINI_EMBEDDING_MODEL,
    vectorDimension: collection.vectorDimension,
    payloadsVerified: stored.length,
    semanticResults
  }, null, 2));
  if (semanticResults.some(result => !result.found)) throw new Error("One or more semantic retrieval checks did not retrieve the expected guidance chunk.");
}

if (require.main === module) {
  importNotificationGuidance().catch(error => {
    console.error("Synthetic notification guidance import failed:", error instanceof Error ? error.message : "unknown error");
    process.exitCode = 1;
  });
}
