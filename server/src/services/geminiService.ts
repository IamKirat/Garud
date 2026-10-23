import { GoogleGenerativeAI, TaskType } from "@google/generative-ai";
import { getEnvConfig } from "../config/env";
import { AiAnalysisResponse, AiAnalysisResponseSchema, EvidencePassage } from "../types";

export class GeminiService {
  private genAI: GoogleGenerativeAI | null = null;
  private apiKey: string;
  private modelName: string;
  private embeddingModelName: string;

  constructor() {
    const config = getEnvConfig();
    this.apiKey = config.GEMINI_API_KEY || "";
    this.modelName = config.GEMINI_MODEL;
    this.embeddingModelName = config.GEMINI_EMBEDDING_MODEL;

    if (this.apiKey) {
      this.genAI = new GoogleGenerativeAI(this.apiKey);
    }
  }

  public isConfigured(): boolean {
    return Boolean(this.genAI && this.apiKey.length > 0);
  }

  /**
   * Generates embedding vector for a given text using gemini-embedding-001.
   */
  public async getEmbedding(text: string, taskType: TaskType = TaskType.RETRIEVAL_QUERY): Promise<number[]> {
    if (!this.genAI) {
      throw new Error("Gemini API is not configured (missing GEMINI_API_KEY).");
    }

    try {
      const model = this.genAI.getGenerativeModel({ model: this.embeddingModelName });
      const result = await model.embedContent({
        content: { role: "user", parts: [{ text }] },
        taskType
      });
      const values = result.embedding?.values;
      if (!values || values.length === 0) {
        throw new Error("Empty embedding vector returned from Gemini.");
      }
      return values;
    } catch (err: any) {
      throw new Error(`Gemini embedding failed (${this.embeddingModelName}): ${err.message}`);
    }
  }

  /**
   * Analyzes a threat message with grounded evidence using Gemini model.
   */
  public async analyzeThreatWithEvidence(
    message: string,
    sender: string | undefined,
    localIndicators: string[],
    evidence: EvidencePassage[]
  ): Promise<{ success: boolean; data?: AiAnalysisResponse; error?: string }> {
    if (!this.genAI) {
      return {
        success: false,
        error: "Gemini provider is not configured. Missing GEMINI_API_KEY."
      };
    }

    const verifiedEvidence = evidence.filter(item => item.sourceType !== "synthetic_guidance");
    const syntheticGuidance = evidence.filter(item => item.sourceType === "synthetic_guidance");
    const evidencePrompt = verifiedEvidence.length > 0
      ? verifiedEvidence.map(e => `[${e.id}] ${e.title} (${e.publisher}):\nURL: ${e.url}\nSnippet: "${e.snippet}"`).join("\n\n")
      : "No external regulatory advisories matched this specific message.";
    const guidancePrompt = syntheticGuidance.length > 0
      ? syntheticGuidance.map(e => `[SYNTHETIC GUIDANCE: ${e.id}] ${e.title}: ${e.snippet}`).join("\n\n")
      : "No synthetic response guidance matched this message.";

    const systemPrompt = `You are GARUD, an advanced cybersecurity threat-analysis system.
Analyze the following user-submitted message for security threats, scams, coercion, or credential theft.

CRITICAL CITATION RULES:
1. Assess the provided message and local indicators. Treat RETRIEVED EVIDENCE as external evidence, and SYNTHETIC RESPONSE GUIDANCE only as general response advice.
2. DO NOT hallucinate, invent, or extrapolate citation IDs or URLs.
3. Every cited source in the "citations" array MUST match an exact source from the RETRIEVED EVIDENCE list below.
4. If no evidence applies, leave the "citations" array empty.
5. Synthetic response guidance is not independently verified evidence: never present it as proof, quote it as an external source, or cite it.
6. Suspicious indicators are not conclusive by themselves. If the available evidence is insufficient, express uncertainty and use INSUFFICIENT_INFORMATION.
7. Return ONLY a valid JSON object matching the requested schema. No code fences, no extra text.

SCHEMA:
{
  "risk_level": "HIGH" | "CAUTION" | "LOW" | "INSUFFICIENT_INFORMATION",
  "summary": "Concise 1-2 sentence risk explanation",
  "indicators": ["List of detected indicators"],
  "supporting_evidence": ["Direct evidence and advisory excerpts supporting verdict"],
  "contradicting_evidence": ["Context suggesting safety, or lack thereof"],
  "citations": [
    {
      "id": "Exact ID from retrieved evidence",
      "title": "Exact title",
      "url": "Exact URL",
      "publisher": "Exact publisher",
      "snippet": "Relevant citation text"
    }
  ],
  "recommended_actions": ["Clear, immediate action steps for the user"],
  "limitations": "Analytical boundaries and uncertainty disclaimer"
}`;

    const userPrompt = `MESSAGE TO ANALYZE:
Sender: ${sender || "Unknown / Not specified"}
Content: "${message}"

LOCAL INDICATORS TRIGGERED:
${localIndicators.length > 0 ? localIndicators.map(i => `- ${i}`).join("\n") : "None triggered"}

RETRIEVED EVIDENCE PASSAGES:
${evidencePrompt}

SYNTHETIC RESPONSE GUIDANCE (general advice only; not proof and never citable):
${guidancePrompt}`;

    const candidateModels = [this.modelName, "gemini-3.8-flash", "gemini-2.5-flash-lite"];

    for (const targetModel of candidateModels) {
      try {
        const model = this.genAI.getGenerativeModel({
          model: targetModel,
          generationConfig: {
            temperature: 0.1,
            responseMimeType: "application/json"
          }
        });

        const result = await model.generateContent([
          { text: systemPrompt },
          { text: userPrompt }
        ]);

        const textResponse = result.response.text();
        if (!textResponse) {
          continue;
        }

        const parsed = JSON.parse(textResponse);
        const validated = AiAnalysisResponseSchema.safeParse(parsed);
        if (validated.success) {
          return { success: true, data: validated.data };
        } else {
          console.warn(`[GEMINI_SCHEMA_WARN] Schema validation failed for ${targetModel}:`, validated.error.message);
        }
      } catch (err: any) {
        console.warn(`[GEMINI_TRY_ERROR] Model ${targetModel} failed:`, err.message);
      }
    }

    return {
      success: false,
      error: "All Gemini generation models failed or returned invalid response."
    };
  }
}
