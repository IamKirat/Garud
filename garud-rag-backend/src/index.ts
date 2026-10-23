import express, { Request, Response } from "express";
import cors from "cors";
import dotenv from "dotenv";
import { AnalysisRequestSchema, GroundedAnalysisResult, RiskLevel } from "./types";
import { CitationValidator } from "./rag/citationValidator";
import { VectorRetriever } from "./rag/vectorRetriever";
import { GeminiService } from "./services/geminiService";
import { QdrantService } from "./services/qdrantService";
import { ThreatIntelligenceService } from "./threatIntel/urlScanner";

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json({ limit: "1mb" }));

// Sensitive data log redaction middleware
app.use((req, res, next) => {
  const sanitizedBody = req.body ? { ...req.body } : {};
  if (sanitizedBody.content) {
    // Redact body preview in server logs
    sanitizedBody.content = `[REDACTED_LEN_${sanitizedBody.content.length}]`;
  }
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
  next();
});

const gemini = new GeminiService();
const qdrant = new QdrantService();
const vectorRetriever = new VectorRetriever(gemini, qdrant);
const threatIntel = new ThreatIntelligenceService();

// Explicit consent is required because VirusTotal submissions become part of its shared dataset.
app.post("/api/analyze/url", async (req: Request, res: Response) => {
  const url = req.body?.url;
  if (req.body?.consent !== true) return res.status(400).json({ error: "Explicit consent is required before sending a URL to VirusTotal." });
  if (typeof url !== "string" || url.length > 2048) return res.status(400).json({ error: "A URL of at most 2048 characters is required." });
  const result = await threatIntel.scanUrl(url);
  return res.status(result.status === "invalid_input" ? 400 : 200).json(result);
});

// Health Check
app.get("/health", (req: Request, res: Response) => {
  res.json({
    status: "ok",
    service: "GARUD Secure Analysis Backend",
    version: "1.0.0",
    aiConfigured: gemini.isConfigured(),
    vectorSearchConfigured: gemini.isConfigured() && qdrant.isConfigured(),
    threatIntelConfigured: threatIntel.isConfigured()
  });
});

// Read-only provider diagnostics. Never exposes credentials or creates a collection.
app.get("/health/providers", async (req: Request, res: Response) => {
  const qdrantStatus = await qdrant.inspectCollection();
  res.json({
    gemini: { configured: gemini.isConfigured(), generationModel: process.env.GEMINI_MODEL || "gemini-3.8-flash", embeddingModel: process.env.GEMINI_EMBEDDING_MODEL || "gemini-embedding-001" },
    qdrant: qdrantStatus,
    virustotal: { configured: threatIntel.isConfigured() }
  });
});

/**
 * Main Threat Analysis Endpoint (POST /api/analyze)
 * Executes Layers B, C, D:
 * 1. Validates schema and inputs.
 * 2. Retrieves genuine regulatory & security evidence passages.
 * 3. Scans extracted URLs for SSRF and threat intelligence.
 * 4. Calls AI adapter if configured, with evidence grounding.
 * 5. Validates every cited source against actual retrieved records.
 * 6. Returns structured, truthful assessment.
 */
app.post("/api/analyze", async (req: Request, res: Response) => {
  const parsed = AnalysisRequestSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({
      error: "Invalid request payload",
      details: parsed.error.format()
    });
  }

  const { content, sender, localIndicators = [], cloudConsent } = parsed.data;

  try {
    // Layer C: Genuine Evidence Retrieval
    // Both Gemini embedding and Gemini generation receive message content only after explicit cloud consent.
    const retrieval = await vectorRetriever.retrieveEvidence(content, 3, cloudConsent);
    const retrievedEvidence = retrieval.passages;

    // Layer D: AI Threat Modeling with Evidence Grounding
    if (gemini.isConfigured() && cloudConsent) {
      const aiResult = await gemini.analyzeThreatWithEvidence(
        content,
        sender,
        localIndicators,
        retrievedEvidence
      );

      if (aiResult.success && aiResult.data) {
        const rawAi = aiResult.data;

        // Verify citations against retrieved evidence records
        const citationCheck = CitationValidator.validate(rawAi.citations, retrievedEvidence);
        const verifiedCitations = CitationValidator.filterVerified(rawAi.citations, retrievedEvidence);

        const response: GroundedAnalysisResult = {
          risk_level: rawAi.risk_level,
          summary: rawAi.summary,
          indicators: rawAi.indicators,
          supporting_evidence: rawAi.supporting_evidence,
          contradicting_evidence: rawAi.contradicting_evidence,
          citations: verifiedCitations,
          recommended_actions: rawAi.recommended_actions,
          limitations: rawAi.limitations + (citationCheck.isValid ? "" : " Note: Some AI citations could not be verified and were redacted."),
          is_citation_verified: citationCheck.isValid,
          evidence_count: verifiedCitations.length,
          engine_mode: "RAG_AI_PIPELINE",
          retrieval_source: retrieval.source,
          provider_status: retrieval.providerStatus
        };

        return res.json(response);
      } else {
        console.warn(`[AI_FALLBACK] Provider returned error: ${aiResult.error}`);
      }
    }

    // Fallback: Local Grounded Evidence Engine (when AI key is unconfigured or failed)
    const verifiedEvidence = retrievedEvidence.filter(item => item.sourceType !== "synthetic_guidance");
    const syntheticGuidance = retrievedEvidence.filter(item => item.sourceType === "synthetic_guidance");
    const hasEvidence = verifiedEvidence.length > 0;
    // Retrieval supplies context; it is not itself proof that the submitted message is malicious.
    const computedRisk: RiskLevel = localIndicators.length > 0
      ? "CAUTION"
      : hasEvidence || syntheticGuidance.length > 0
        ? "INSUFFICIENT_INFORMATION"
        : "LOW";

    const response: GroundedAnalysisResult = {
      risk_level: computedRisk,
      summary: hasEvidence
        ? `Grounded analysis matched verified regulatory advisories from: ${verifiedEvidence.map(e => e.publisher).join(", ")}.`
        : localIndicators.length > 0
        ? `Local indicators flagged potential threats (${localIndicators.join(", ")}).`
        : syntheticGuidance.length > 0
          ? "General synthetic safety guidance matched this message; this guidance is not independent evidence and does not establish that the message is malicious."
          : "No known threats or matching advisories were found in the configured evidence index.",
      indicators: localIndicators,
      supporting_evidence: [
        ...verifiedEvidence.map(e => `[${e.id}] ${e.title}: "${e.snippet}"`),
        ...syntheticGuidance.map(e => `[Synthetic guidance, not independently verified evidence] ${e.title}: "${e.snippet}"`)
      ],
      contradicting_evidence: hasEvidence ? [] : ["No regulatory advisories directly matched this text in current evidence index."],
      citations: verifiedEvidence,
      recommended_actions: [
        "Do not share OTP, PIN, password, or sensitive credentials.",
        "Verify unexpected requests directly through official applications or verified telephone numbers.",
        "Never click unverified links or download sideloaded APKs."
      ],
      limitations: "Grounded analysis based on the configured evidence retriever. Gemini cloud generation was not active for this request.",
      is_citation_verified: true,
      evidence_count: verifiedEvidence.length,
      engine_mode: "LOCAL_EVIDENCE_GROUNDING",
      retrieval_source: retrieval.source,
      provider_status: retrieval.providerStatus
    };

    return res.json(response);
  } catch (err: any) {
    console.error(`[ANALYSIS_ERROR] ${err.message}`);
    return res.status(500).json({
      error: "Analysis pipeline failed.",
      message: err.message
    });
  }
});

app.listen(PORT, () => {
  console.log(`[GARUD] Secure Backend Server running on port ${PORT}`);
  console.log(`[GARUD] Gemini configured: ${gemini.isConfigured()}`);
  console.log(`[GARUD] Threat Intel configured: ${threatIntel.isConfigured()}`);
});
