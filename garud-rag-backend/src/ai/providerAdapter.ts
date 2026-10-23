import { AiAnalysisResponse, AiAnalysisResponseSchema, EvidencePassage, RiskLevel } from "../types";

export interface AiProviderConfig {
  apiKey?: string;
  baseUrl?: string;
  model?: string;
}

/**
 * AI Provider Adapter (Layer D)
 * Supports Kimi API (Moonshot) and OpenAI-compatible models.
 * Strictly adheres to truthfulness: never invents citations, validates output schema,
 * and reports provider unavailability honestly.
 */
export class AiProviderAdapter {
  private apiKey?: string;
  private baseUrl: string;
  private model: string;

  constructor(config?: AiProviderConfig) {
    this.apiKey = config?.apiKey || process.env.KIMI_API_KEY || process.env.AI_API_KEY;
    this.baseUrl = config?.baseUrl || process.env.AI_BASE_URL || "https://api.moonshot.ai/v1";
    this.model = config?.model || process.env.AI_MODEL || "kimi-k3";
  }

  public isConfigured(): boolean {
    return Boolean(this.apiKey && this.apiKey.trim().length > 0);
  }

  /**
   * Prompts the AI model with message context, rule indicators, and real retrieved evidence.
   */
  public async analyzeThreat(
    message: string,
    sender: string | undefined,
    localIndicators: string[],
    evidence: EvidencePassage[]
  ): Promise<{ success: boolean; data?: AiAnalysisResponse; error?: string }> {
    if (!this.isConfigured()) {
      return {
        success: false,
        error: "AI provider is not configured. Missing KIMI_API_KEY or AI_API_KEY."
      };
    }

    const evidencePrompt = evidence.length > 0
      ? evidence.map(e => `[${e.id}] ${e.title} (${e.publisher}):\nURL: ${e.url}\nExcerpt: "${e.snippet}"`).join("\n\n")
      : "No external regulatory advisories matched this specific message.";

    const systemPrompt = `You are GARUD, a cybersecurity threat analysis assistant.
Analyze the provided user message for scams, social engineering, and fraud.

IMPORTANT RULES:
1. Ground your assessment ONLY in verifiable facts, provided local indicators, and the retrieved evidence passages below.
2. DO NOT hallucinate or invent citation IDs, URLs, or regulatory sources. Only cite sources from the RETRIEVED EVIDENCE list using their exact ID and URL.
3. If no evidence applies, state that explicitly under limitations and leave citations empty.
4. Distinguish between confirmed scam patterns, suspicion/caution, and normal messages. Absence of threat rules does not guarantee safety.
5. You MUST return ONLY a valid, raw JSON object matching the requested schema. No markdown backticks, no markdown fence.

OUTPUT SCHEMA:
{
  "risk_level": "HIGH" | "CAUTION" | "LOW" | "INSUFFICIENT_INFORMATION",
  "summary": "Brief 1-2 sentence threat assessment",
  "indicators": ["List of specific threat flags identified"],
  "supporting_evidence": ["Evidence from message and advisories supporting the verdict"],
  "contradicting_evidence": ["Evidence or context arguing against fraud, or lack thereof"],
  "citations": [
    {
      "id": "Exact source ID from retrieved list",
      "title": "Exact source title",
      "url": "Exact source URL",
      "publisher": "Exact publisher",
      "snippet": "Relevant citation excerpt"
    }
  ],
  "recommended_actions": ["Specific immediate steps for the user"],
  "limitations": "Honest explanation of uncertainty and analytical boundaries"
}`;

    const userPrompt = `MESSAGE TO ANALYZE:
Sender: ${sender || "Unknown / Not specified"}
Content: "${message}"

LOCAL RULE INDICATORS TRIGGERED:
${localIndicators.length > 0 ? localIndicators.map(i => `- ${i}`).join("\n") : "None triggered"}

RETRIEVED EVIDENCE PASSAGES:
${evidencePrompt}`;

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 15000); // 15s timeout

      let response: Response;
      try {
        response = await fetch(`${this.baseUrl}/chat/completions`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${this.apiKey}`
        },
        body: JSON.stringify({
          model: this.model,
          messages: [
            { role: "system", content: systemPrompt },
            { role: "user", content: userPrompt }
          ],
          temperature: 0.1,
          response_format: { type: "json_object" }
        }),
          signal: controller.signal
        });
      } finally {
        clearTimeout(timeoutId);
      }

      if (!response.ok) {
        const errText = await response.text();
        return {
          success: false,
          error: `AI Provider HTTP ${response.status}: ${errText.slice(0, 100)}`
        };
      }

      const json = await response.json();
      const content = json.choices?.[0]?.message?.content;
      if (!content) {
        return { success: false, error: "Empty response received from AI model." };
      }

      // Parse and validate with Zod schema
      const parsed = JSON.parse(content);
      const validated = AiAnalysisResponseSchema.safeParse(parsed);

      if (!validated.success) {
        return {
          success: false,
          error: `AI output schema validation failed: ${validated.error.message}`
        };
      }

      return { success: true, data: validated.data };
    } catch (err: any) {
      if (err.name === "AbortError") {
        return { success: false, error: "AI provider request timed out (15s)." };
      }
      return { success: false, error: err.message || "Failed to contact AI provider." };
    }
  }
}
