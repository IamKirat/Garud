import { EvidenceRetriever } from "./evidenceRetriever";
import { GeminiService } from "../services/geminiService";
import { QdrantService } from "../services/qdrantService";
import { EvidencePassage } from "../types";

export interface RetrievalResult {
  passages: EvidencePassage[];
  source: "QDRANT_VECTOR_SEARCH" | "LOCAL_EVIDENCE_COLLECTION" | "EMPTY";
  providerStatus: string;
}

export class VectorRetriever {
  private gemini: GeminiService;
  private qdrant: QdrantService;
  private localFallback: EvidenceRetriever;

  constructor(gemini?: GeminiService, qdrant?: QdrantService) {
    this.gemini = gemini || new GeminiService();
    this.qdrant = qdrant || new QdrantService();
    this.localFallback = new EvidenceRetriever();
  }

  /**
   * Retrieves genuine, verified evidence passages for input query.
   * Uses Qdrant Cloud semantic search when available and compatible.
   * Falls back to local curated index if Qdrant is unconfigured, reporting actual state honestly.
   */
  public async retrieveEvidence(query: string, limit = 3, allowCloud = false): Promise<RetrievalResult> {
    let fallbackStatus = !allowCloud
      ? "CLOUD_CONSENT_NOT_GIVEN_LOCAL_FALLBACK_ACTIVE"
      : !this.gemini.isConfigured()
        ? "GEMINI_UNCONFIGURED_LOCAL_FALLBACK_ACTIVE"
        : !this.qdrant.isConfigured()
          ? "QDRANT_UNCONFIGURED_FALLBACK_ACTIVE"
          : "VECTOR_SEARCH_UNAVAILABLE_LOCAL_FALLBACK_ACTIVE";
    if (allowCloud && this.gemini.isConfigured() && this.qdrant.isConfigured()) {
      try {
        const collection = await this.qdrant.inspectCollection();
        if (!collection.connected || !collection.exists || collection.error) {
          throw new Error(collection.error || "Qdrant collection is unavailable.");
        }
        // Embedding and text generation are separate Gemini operations/models.
        const queryVector = await this.gemini.getEmbedding(query);
        if (queryVector.length !== QdrantService.EXPECTED_VECTOR_SIZE) {
          throw new Error(`Embedding dimension ${queryVector.length} does not match Qdrant collection dimension ${QdrantService.EXPECTED_VECTOR_SIZE}.`);
        }

        // 2. Perform similarity search in Qdrant collection
        const qdrantResults = await this.qdrant.searchSimilar(queryVector, limit, 0.4);

        if (qdrantResults.length > 0) {
          return {
            passages: qdrantResults,
            source: "QDRANT_VECTOR_SEARCH",
            providerStatus: "QDRANT_CLOUD_ONLINE"
          };
        }
        fallbackStatus = "QDRANT_ZERO_MATCH_LOCAL_FALLBACK_ACTIVE";
      } catch (err: any) {
        console.warn("[VECTOR_RETRIEVAL_WARN] Vector search unavailable, falling back:", err.message);
        fallbackStatus = "VECTOR_SEARCH_UNAVAILABLE_LOCAL_FALLBACK_ACTIVE";
      }
    }

    // Fallback: Local rule-indexed evidence collection
    const localResults = this.localFallback.retrieve(query, limit);
    if (localResults.length > 0) {
      return {
        passages: localResults,
        source: "LOCAL_EVIDENCE_COLLECTION",
        providerStatus: fallbackStatus
      };
    }

    return {
      passages: [],
      source: "EMPTY",
      providerStatus: `${fallbackStatus}_NO_LOCAL_VERIFIED_PASSAGES`
    };
  }
}
