import crypto from "node:crypto";
import { KnowledgeChunkMetadata, KnowledgeChunkMetadataSchema } from "../types";

export interface KnowledgeSourceDocument {
  source_id: string;
  title: string;
  publisher: string;
  source_url: string;
  license: string;
  timestamp: number;
  reviewed_at: number;
  review_status: "VERIFIED";
  language: string;
  category: string;
  text: string;
}

/** Splits reviewed source text on paragraph/sentence boundaries and builds traceable chunk metadata. */
export function chunkKnowledgeDocument(document: KnowledgeSourceDocument, maxChars = 1000): KnowledgeChunkMetadata[] {
  if (maxChars < 120 || maxChars > 4000) throw new Error("maxChars must be between 120 and 4000.");
  const cleanText = document.text.replace(/\s+/g, " ").trim();
  if (!cleanText) throw new Error("Source text is empty.");

  const sentences = cleanText.match(/[^.!?]+(?:[.!?]+|$)/g)?.map(part => part.trim()).filter(Boolean) ?? [cleanText];
  const pieces: string[] = [];
  let current = "";
  for (const sentence of sentences) {
    if (sentence.length > maxChars) {
      if (current) pieces.push(current);
      current = "";
      let segment = "";
      for (const word of sentence.split(" ")) {
        if (segment && `${segment} ${word}`.length > maxChars) {
          pieces.push(segment);
          segment = word;
        } else segment = segment ? `${segment} ${word}` : word;
      }
      current = segment;
      continue;
    }
    if (current && `${current} ${sentence}`.length > maxChars) {
      pieces.push(current);
      current = sentence;
    } else current = current ? `${current} ${sentence}` : sentence;
  }
  if (current) pieces.push(current);

  return pieces.map(text => {
    const content_hash = crypto.createHash("sha256").update(text).digest("hex");
    const metadata = KnowledgeChunkMetadataSchema.parse({
      ...document,
      chunk_id: `${document.source_id}:${content_hash.slice(0, 20)}`,
      content_hash,
      text
    });
    return metadata;
  });
}

/** Deterministic UUID point ID so re-ingestion updates a source chunk instead of duplicating it. */
export function qdrantPointId(chunkId: string): string {
  const chars = crypto.createHash("sha256").update(`garud:${chunkId}`).digest("hex").slice(0, 32).split("");
  chars[12] = "5";
  chars[16] = ((parseInt(chars[16], 16) & 0x3) | 0x8).toString(16);
  const value = chars.join("");
  return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`;
}
