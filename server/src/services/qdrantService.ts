import { QdrantClient } from "@qdrant/js-client-rest";
import { getEnvConfig } from "../config/env";
import { EvidencePassage, KnowledgeChunkMetadata, KnowledgeChunkMetadataSchema, SyntheticGuidanceMetadata, SyntheticGuidanceMetadataSchema } from "../types";

export class QdrantService {
  private client: QdrantClient | null = null;
  private url: string;
  private apiKey: string;
  private collectionName: string;
  public static readonly EXPECTED_VECTOR_SIZE = 3072;

  constructor() {
    const config = getEnvConfig();
    this.url = config.QDRANT_URL || "";
    this.apiKey = config.QDRANT_API_KEY || "";
    this.collectionName = config.QDRANT_COLLECTION;

    if (this.url && this.apiKey) {
      this.client = new QdrantClient({
        url: this.url,
        apiKey: this.apiKey,
        checkCompatibility: false
      });
    }
  }

  public isConfigured(): boolean {
    return Boolean(this.client && this.url && this.apiKey);
  }

  /** Read-only provider check; does not create, reset, or modify a collection. */
  public async inspectCollection(): Promise<{ configured: boolean; connected: boolean; exists: boolean; vectorDimension?: number; error?: string }> {
    if (!this.client) return { configured: false, connected: false, exists: false, error: "Qdrant is not configured." };
    try {
      const collections = await this.client.getCollections();
      if (!collections.collections.some(collection => collection.name === this.collectionName)) {
        return { configured: true, connected: true, exists: false, error: `Collection ${this.collectionName} does not exist.` };
      }
      const info = await this.client.getCollection(this.collectionName);
      const vectors: any = info.config?.params?.vectors;
      const dimension = typeof vectors === "object" ? vectors?.size : vectors;
      if (typeof dimension !== "number") {
        return { configured: true, connected: true, exists: true, error: "Collection vector configuration is named or unsupported." };
      }
      if (dimension !== QdrantService.EXPECTED_VECTOR_SIZE) {
        return { configured: true, connected: true, exists: true, vectorDimension: dimension, error: `Vector dimension mismatch: collection has ${dimension}, expected ${QdrantService.EXPECTED_VECTOR_SIZE}.` };
      }
      return { configured: true, connected: true, exists: true, vectorDimension: dimension };
    } catch (error) {
      return { configured: true, connected: false, exists: false, error: error instanceof Error ? error.message : "Qdrant connectivity check failed." };
    }
  }

  /**
   * Verifies Qdrant connectivity and collection configuration without deleting or overwriting data.
   * Validates vector dimension equals 3072.
   */
  public async ensureCollection(): Promise<{
    connected: boolean;
    collectionExists: boolean;
    vectorDimension: number;
    error?: string;
  }> {
    if (!this.client) {
      return {
        connected: false,
        collectionExists: false,
        vectorDimension: 0,
        error: "Qdrant is not configured. Missing QDRANT_URL or QDRANT_API_KEY."
      };
    }

    try {
      const collections = await this.client.getCollections();
      const existing = collections.collections.find(c => c.name === this.collectionName);

      if (!existing) {
        // Create collection if missing
        await this.client.createCollection(this.collectionName, {
          vectors: {
            size: QdrantService.EXPECTED_VECTOR_SIZE,
            distance: "Cosine"
          }
        });
        return {
          connected: true,
          collectionExists: true,
          vectorDimension: QdrantService.EXPECTED_VECTOR_SIZE
        };
      }

      // Check existing collection configuration without modifying data
      const info = await this.client.getCollection(this.collectionName);
      const vectorParams: any = info.config?.params?.vectors;
      const size = typeof vectorParams === "object" ? vectorParams?.size : 0;

      if (typeof size !== "number") {
        return { connected: true, collectionExists: true, vectorDimension: 0, error: "Collection uses named or unsupported vectors; GARUD requires one 3072-dimensional vector." };
      }
      if (size && size !== QdrantService.EXPECTED_VECTOR_SIZE) {
        return {
          connected: true,
          collectionExists: true,
          vectorDimension: size,
          error: `Vector dimension mismatch: collection has ${size}, expected ${QdrantService.EXPECTED_VECTOR_SIZE}`
        };
      }

      return {
        connected: true,
        collectionExists: true,
        vectorDimension: size || QdrantService.EXPECTED_VECTOR_SIZE
      };
    } catch (err: any) {
      return {
        connected: false,
        collectionExists: false,
        vectorDimension: 0,
        error: err.message || "Failed to connect to Qdrant cluster."
      };
    }
  }

  /**
   * Ingests verified knowledge chunks into Qdrant vector database.
   */
  public async upsertChunks(
    items: Array<{
      id: string | number;
      vector: number[];
      metadata: KnowledgeChunkMetadata;
    }>
  ): Promise<{ success: boolean; count: number; error?: string }> {
    if (!this.client) {
      return { success: false, count: 0, error: "Qdrant client not configured." };
    }

    // Validate metadata schema for each chunk before storage
    for (const item of items) {
      const validation = KnowledgeChunkMetadataSchema.safeParse(item.metadata);
      if (!validation.success) {
        return {
          success: false,
          count: 0,
          error: `Invalid chunk metadata for ${item.id}: ${validation.error.message}`
        };
      }
      if (validation.data.review_status !== "VERIFIED" || validation.data.reviewed_at === undefined || validation.data.timestamp === undefined) {
        return { success: false, count: 0, error: `Chunk ${item.id} is not fully provenance-reviewed.` };
      }
      if (item.vector.length !== QdrantService.EXPECTED_VECTOR_SIZE) {
        return { success: false, count: 0, error: `Chunk ${item.id} vector dimension ${item.vector.length} does not match ${QdrantService.EXPECTED_VECTOR_SIZE}.` };
      }
    }

    try {
      const points = items.map(item => ({
        id: item.id,
        vector: item.vector,
        payload: item.metadata
      }));

      await this.client.upsert(this.collectionName, {
        wait: true,
        points
      });

      return { success: true, count: items.length };
    } catch (err: any) {
      return { success: false, count: 0, error: err.message };
    }
  }

  /** Upserts synthetic response guidance separately from externally verified evidence. */
  public async upsertSyntheticGuidance(items: Array<{ id: string; vector: number[]; metadata: SyntheticGuidanceMetadata }>): Promise<{ success: boolean; count: number; error?: string }> {
    if (!this.client) return { success: false, count: 0, error: "Qdrant client not configured." };
    for (const item of items) {
      const validation = SyntheticGuidanceMetadataSchema.safeParse(item.metadata);
      if (!validation.success) return { success: false, count: 0, error: `Invalid synthetic guidance metadata for ${item.id}: ${validation.error.message}` };
      if (item.vector.length !== QdrantService.EXPECTED_VECTOR_SIZE || item.vector.some(value => !Number.isFinite(value))) {
        return { success: false, count: 0, error: `Chunk ${item.metadata.chunk_id} vector must contain ${QdrantService.EXPECTED_VECTOR_SIZE} finite dimensions.` };
      }
    }
    try {
      await this.client.upsert(this.collectionName, {
        wait: true,
        points: items.map(item => ({ id: item.id, vector: item.vector, payload: item.metadata as unknown as Record<string, unknown> }))
      });
      return { success: true, count: items.length };
    } catch (err: any) {
      return { success: false, count: 0, error: err.message || "Synthetic guidance upsert failed." };
    }
  }

  /** Read-only verification for imported synthetic guidance IDs. */
  public async retrieveSyntheticGuidance(pointIds: string[]): Promise<Array<{ id: string | number; payload: Record<string, unknown> }>> {
    if (!this.client) throw new Error("Qdrant client not configured.");
    return await this.client.retrieve(this.collectionName, { ids: pointIds, with_payload: true }) as Array<{ id: string | number; payload: Record<string, unknown> }>;
  }

  /**
   * Searches Qdrant collection for similar chunks using query vector.
   * Validates retrieved payloads against GARUD RAG Knowledge Metadata Schema.
   */
  public async searchSimilar(
    queryVector: number[],
    limit = 3,
    minScore = 0.4
  ): Promise<EvidencePassage[]> {
    if (!this.client) {
      return [];
    }
    if (queryVector.length !== QdrantService.EXPECTED_VECTOR_SIZE || queryVector.some(value => !Number.isFinite(value))) {
      throw new Error(`Query vector must contain ${QdrantService.EXPECTED_VECTOR_SIZE} finite dimensions.`);
    }

    try {
      const response = await this.client.query(this.collectionName, {
        query: queryVector,
        limit,
        with_payload: true,
        score_threshold: minScore
      });

      const passages: EvidencePassage[] = [];
      const points = response.points || [];

      for (const hit of points) {
        const payload = hit.payload;
        if (!payload) continue;

        const verified = KnowledgeChunkMetadataSchema.safeParse(payload);
        if (verified.success) {
          const meta = verified.data;
          if (meta.review_status !== "VERIFIED" || meta.reviewed_at === undefined) continue;
          passages.push({ id: meta.chunk_id, title: meta.title, publisher: meta.publisher, url: meta.source_url, snippet: meta.text, timestamp: meta.timestamp, score: hit.score, metadata: meta, sourceType: "verified_evidence", reviewStatus: "VERIFIED" });
          continue;
        }
        const synthetic = SyntheticGuidanceMetadataSchema.safeParse(payload);
        if (synthetic.success) {
          const meta = synthetic.data;
          passages.push({ id: meta.chunk_id, title: meta.title, publisher: "GARUD synthetic guidance", url: "", snippet: meta.text, score: hit.score, sourceType: "synthetic_guidance", reviewStatus: "synthetic" });
        } else {
          console.warn(`[METADATA_WARN] Point ${hit.id} skipped - payload does not match a supported knowledge schema.`);
        }
      }

      return passages;
    } catch (err) {
      throw err instanceof Error ? err : new Error("Qdrant search failed.");
    }
  }
}
