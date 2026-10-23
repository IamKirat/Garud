import { EvidencePassage } from "../types";

/**
 * Validates citations to prevent hallucinated, fake, or ungrounded sources.
 */
export class CitationValidator {
  /**
   * Validates that cited source IDs and URLs match the authentic records
   * retrieved during this pipeline run.
   */
  public static validate(
    cited: Array<{ id: string; url: string }>,
    retrieved: EvidencePassage[]
  ): { isValid: boolean; invalidIds: string[] } {
    if (cited.length === 0) {
      return { isValid: true, invalidIds: [] };
    }

    const retrievedMap = new Map<string, EvidencePassage>();
    retrieved.filter(item => item.sourceType !== "synthetic_guidance").forEach(item => retrievedMap.set(item.id, item));

    const invalidIds: string[] = [];

    for (const citation of cited) {
      const actual = retrievedMap.get(citation.id);
      if (!actual) {
        invalidIds.push(citation.id);
      } else if (citation.url) {
        try {
          if (new URL(citation.url).origin !== new URL(actual.url).origin) invalidIds.push(`${citation.id} (URL mismatch)`);
        } catch {
          invalidIds.push(`${citation.id} (invalid URL)`);
        }
      }
    }

    return {
      isValid: invalidIds.length === 0,
      invalidIds
    };
  }

  /**
   * Cleans citations, keeping only authentic, verified retrieved passages.
   */
  public static filterVerified(
    cited: Array<{ id: string }>,
    retrieved: EvidencePassage[]
  ): EvidencePassage[] {
    const validIds = new Set(retrieved.filter(r => r.sourceType !== "synthetic_guidance").map(r => r.id));
    return retrieved.filter(r => cited.some(c => c.id === r.id && validIds.has(c.id)));
  }
}
