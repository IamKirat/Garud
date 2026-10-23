import { z } from "zod";

export type RiskLevel = "HIGH" | "CAUTION" | "LOW" | "INSUFFICIENT_INFORMATION";

export interface EvidencePassage {
  id: string;
  title: string;
  publisher: string;
  url: string;
  snippet: string;
  timestamp?: number;
  score?: number;
  metadata?: KnowledgeChunkMetadata;
  /** Synthetic guidance helps frame safe responses but is not externally verified evidence. */
  sourceType?: "verified_evidence" | "synthetic_guidance";
  reviewStatus?: "VERIFIED" | "synthetic";
}

export const SyntheticGuidanceMetadataSchema = z.object({
  chunk_id: z.string().regex(/^garud-notif-\d{3}$/),
  topic: z.string().min(1),
  title: z.string().min(1),
  text: z.string().min(1),
  content_hash: z.string().regex(/^[a-f0-9]{64}$/),
  source_type: z.literal("synthetic_guidance"),
  source_url: z.null(),
  ingested_at: z.number().int().nonnegative().nullable(),
  embedding_model: z.string().nullable(),
  source_checked_at: z.null(),
  review_status: z.literal("synthetic"),
  language: z.literal("en"),
  source_license: z.null()
}).strict();
export type SyntheticGuidanceMetadata = z.infer<typeof SyntheticGuidanceMetadataSchema>;

export const KnowledgeChunkMetadataSchema = z.object({
  chunk_id: z.string(),
  source_id: z.string(),
  title: z.string(),
  publisher: z.string(),
  source_url: z.string().url().refine(value => new URL(value).protocol === "https:"),
  content_hash: z.string().regex(/^[a-f0-9]{64}$/i, "Must be valid SHA-256 hash"),
  license: z.string(),
  timestamp: z.number().int().nonnegative().optional(),
  review_status: z.enum(["VERIFIED", "PENDING", "UNVERIFIED"]),
  reviewed_at: z.number().int().nonnegative().optional(),
  language: z.string().regex(/^[a-z]{2,3}(?:-[A-Z]{2})?$/).optional(),
  category: z.string(),
  text: z.string().min(1)
}).strict().superRefine((metadata, context) => {
  if (metadata.review_status === "VERIFIED" && metadata.reviewed_at === undefined) {
    context.addIssue({ code: z.ZodIssueCode.custom, path: ["reviewed_at"], message: "VERIFIED records require a provenance and license review timestamp." });
  }
  if (metadata.review_status === "VERIFIED" && metadata.timestamp === undefined) {
    context.addIssue({ code: z.ZodIssueCode.custom, path: ["timestamp"], message: "VERIFIED records require the source publication timestamp." });
  }
});

export type KnowledgeChunkMetadata = z.infer<typeof KnowledgeChunkMetadataSchema>;


export interface ThreatIntelligenceResult {
  target: string;
  type: "URL" | "DOMAIN" | "IP";
  isFlagged: boolean;
  threatCategory?: string;
  source: string;
  details: string;
}

export const AnalysisRequestSchema = z.object({
  content: z.string().min(1, "Message content is required").max(10000, "Content too large"),
  sender: z.string().max(255).optional(),
  source: z.enum(["SMS", "WHATSAPP", "TELEGRAM", "EMAIL", "OTHER"]).default("SMS"),
  localIndicators: z.array(z.string()).optional(),
  cloudConsent: z.boolean().default(false)
});

export type AnalysisRequest = z.infer<typeof AnalysisRequestSchema>;

export const AiAnalysisResponseSchema = z.object({
  risk_level: z.enum(["HIGH", "CAUTION", "LOW", "INSUFFICIENT_INFORMATION"]),
  summary: z.string(),
  indicators: z.array(z.string()),
  supporting_evidence: z.array(z.string()),
  contradicting_evidence: z.array(z.string()),
  citations: z.array(
    z.object({
      id: z.string(),
      title: z.string(),
      url: z.string(),
      publisher: z.string(),
      snippet: z.string()
    })
  ),
  recommended_actions: z.array(z.string()),
  limitations: z.string()
});

export type AiAnalysisResponse = z.infer<typeof AiAnalysisResponseSchema>;

export interface GroundedAnalysisResult {
  risk_level: RiskLevel;
  summary: string;
  indicators: string[];
  supporting_evidence: string[];
  contradicting_evidence: string[];
  citations: EvidencePassage[];
  recommended_actions: string[];
  limitations: string;
  is_citation_verified: boolean;
  evidence_count: number;
  engine_mode: "RAG_AI_PIPELINE" | "LOCAL_EVIDENCE_GROUNDING" | "OFFLINE_FALLBACK";
  retrieval_source?: "QDRANT_VECTOR_SEARCH" | "LOCAL_EVIDENCE_COLLECTION" | "EMPTY";
  provider_status?: string;
}
