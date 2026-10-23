export type UrlScanStatus = "provider_not_configured" | "submitted" | "completed" | "unavailable" | "invalid_input";

export interface UrlScanResult {
  target: string;
  type: "URL";
  status: UrlScanStatus;
  isFlagged: boolean | null;
  stats?: { malicious: number; suspicious: number; harmless: number; undetected: number };
  analysisId?: string;
  source: string;
  details: string;
}

/** Validates syntax only. GARUD sends the URL string to VirusTotal; it never fetches the URL itself. */
export function normalizeCandidateUrl(input: string): string | undefined {
  if (input.length > 2048) return undefined;
  try {
    const parsed = new URL(input.trim());
    if (!["http:", "https:"].includes(parsed.protocol) || !parsed.hostname || parsed.username || parsed.password) return undefined;
    return parsed.toString();
  } catch {
    return undefined;
  }
}

/** Legacy URL destination guard retained for any future component that performs direct fetches. */
export class SsrfValidator {
  static isSafeDestination(input: string): { isSafe: boolean; reason?: string } {
    const normalized = normalizeCandidateUrl(input);
    if (!normalized) return { isSafe: false, reason: "Invalid or unsupported URL." };
    const host = new URL(normalized).hostname.toLowerCase();
    if (host === "localhost" || host.endsWith(".local") || host.endsWith(".internal") ||
      /^(127\.|10\.|172\.(1[6-9]|2\d|3[01])\.|192\.168\.|169\.254\.|0\.)/.test(host) ||
      host === "::1" || host.startsWith("fc") || host.startsWith("fe80:")) {
      return { isSafe: false, reason: "Forbidden local, private, or link-local destination." };
    }
    return { isSafe: true };
  }
}

/** VirusTotal v3 URL submission adapter. No configured key means no provider result is claimed. */
export class ThreatIntelligenceService {
  constructor(
    private readonly apiKey: string | undefined = process.env.VIRUSTOTAL_API_KEY,
    private readonly fetchImpl: typeof fetch = fetch
  ) {}

  isConfigured(): boolean { return Boolean(this.apiKey?.trim()); }

  async scanUrl(input: string): Promise<UrlScanResult> {
    const target = normalizeCandidateUrl(input);
    if (!target) return { target: input, type: "URL", status: "invalid_input", isFlagged: null, source: "GARUD_URL_VALIDATOR", details: "Enter a valid HTTP or HTTPS URL without embedded credentials." };
    if (!this.isConfigured()) return { target, type: "URL", status: "provider_not_configured", isFlagged: null, source: "VirusTotal", details: "VirusTotal is not configured; no live URL verdict is available." };

    try {
      const body = new URLSearchParams({ url: target });
      const submit = await this.fetchImpl("https://www.virustotal.com/api/v3/urls", {
        method: "POST", headers: { "x-apikey": this.apiKey!, "content-type": "application/x-www-form-urlencoded" }, body,
        signal: AbortSignal.timeout(12000)
      });
      if (!submit.ok) throw new Error(`VirusTotal submission returned HTTP ${submit.status}`);
      const submitted = await submit.json() as { data?: { id?: string } };
      const analysisId = submitted.data?.id;
      if (!analysisId) throw new Error("VirusTotal response did not include an analysis ID");

      const analysisResponse = await this.fetchImpl(`https://www.virustotal.com/api/v3/analyses/${encodeURIComponent(analysisId)}`, {
        headers: { "x-apikey": this.apiKey! }, signal: AbortSignal.timeout(12000)
      });
      if (!analysisResponse.ok) throw new Error(`VirusTotal analysis returned HTTP ${analysisResponse.status}`);
      const analysis = await analysisResponse.json() as { data?: { attributes?: { status?: string; stats?: Record<string, unknown> } } };
      const attrs = analysis.data?.attributes;
      if (attrs?.status !== "completed" || !attrs.stats) {
        return { target, type: "URL", status: "submitted", isFlagged: null, analysisId, source: "VirusTotal", details: "VirusTotal accepted the scan; its analysis is still pending." };
      }
      const stats = {
        malicious: count(attrs.stats.malicious), suspicious: count(attrs.stats.suspicious),
        harmless: count(attrs.stats.harmless), undetected: count(attrs.stats.undetected)
      };
      const flagged = stats.malicious > 0 || stats.suspicious > 0;
      return { target, type: "URL", status: "completed", isFlagged: flagged, stats, analysisId, source: "VirusTotal", details: `${stats.malicious} malicious and ${stats.suspicious} suspicious detections from VirusTotal.` };
    } catch (error) {
      return { target, type: "URL", status: "unavailable", isFlagged: null, source: "VirusTotal", details: error instanceof Error ? error.message : "VirusTotal request failed." };
    }
  }
}

function count(value: unknown): number { return typeof value === "number" && Number.isFinite(value) && value >= 0 ? value : 0; }
