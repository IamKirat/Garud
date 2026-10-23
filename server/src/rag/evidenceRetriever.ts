import { EvidencePassage } from "../types";

/**
 * Local entity extraction; no unreviewed seed records are returned as evidence.
 * Reviewed evidence is supplied through Qdrant after provenance review.
 */
export class EvidenceRetriever {

  /**
   * Extracts entities, organisations, claims, and keywords from input.
   */
  public extractEntities(content: string): string[] {
    const lower = content.toLowerCase();
    const candidates = [
      "otp", "pin", "password", "cvv", "verification code",
      "anydesk", "teamviewer", "quicksupport", "rustdesk", "screen share", "remote",
      ".apk", "download", "install",
      "electricity", "power", "disconnected", "bill",
      "fedex", "dhl", "ups", "courier", "delivery", "parcel", "address",
      "sbi", "hdfc", "icici", "axis bank", "bank", "paytm", "kyc", "pan",
      "lottery", "winner", "prize", "reward", "fee", "transfer",
      "police", "cbi", "arrest", "court", "customs",
      "bit.ly", "tinyurl", "link"
    ];

    return candidates.filter(k => lower.includes(k));
  }

  /** Legacy local fallback is intentionally empty until source records are reviewed. */
  public retrieve(content: string, limit = 3): EvidencePassage[] {
    // Unreviewed summaries with generic publisher links are not returned as cited evidence.
    void content;
    void limit;
    return [];
  }
}
