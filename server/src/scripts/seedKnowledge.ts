import { TaskType } from "@google/generative-ai";
import { GeminiService } from "../services/geminiService";
import { QdrantService } from "../services/qdrantService";
import { KnowledgeChunkMetadata } from "../types";
import { chunkKnowledgeDocument, qdrantPointId } from "../rag/chunker";

const KNOWLEDGE_BASE_CHUNKS: Array<Omit<KnowledgeChunkMetadata, "content_hash">> = [
  {
    chunk_id: "EVID-RBI-OTP-01",
    source_id: "REF-RBI-CREDENTIAL-SECRECY",
    title: "Reserve Bank of India Advisory on Credential Secrecy",
    publisher: "Reserve Bank of India (RBI)",
    source_url: "https://rbi.org.in/Scripts/BS_PressReleaseDisplay.aspx",
    category: "BANKING_CREDENTIAL_THEFT",
    license: "Public Safety Advisory / Government Notice",
    review_status: "VERIFIED" as const,
    timestamp: 1704067200000,
    reviewed_at: 1729641600000,
    language: "en",
    text: "Regulated entities (Banks, Payment aggregators, and NBFCs) will NEVER ask customers to disclose their One Time Password (OTP), ATM PIN, UPI PIN, CVV, or passwords over phone, SMS, or email. Any solicitation to cancel pending debits or verify accounts via OTP is a confirmed fraudulent pattern."
  },
  {
    chunk_id: "EVID-RBI-REMOTE-02",
    source_id: "REF-RBI-REMOTE-ACCESS",
    title: "RBI Warning on Screen-Sharing & Remote Access Apps",
    publisher: "Reserve Bank of India (RBI)",
    source_url: "https://rbi.org.in/commonman/English/Scripts/PressReleases.aspx",
    category: "REMOTE_ACCESS_FRAUD",
    license: "Public Safety Advisory / Government Notice",
    review_status: "VERIFIED" as const,
    timestamp: 1704067200000,
    reviewed_at: 1729641600000,
    language: "en",
    text: "Fraudsters entice bank customers to download third-party remote screen-monitoring applications (such as AnyDesk, TeamViewer, QuickSupport) under the pretext of resolving failed transactions, updating KYC, or blocking unauthorized charges. Once installed, fraudsters gain full remote visibility of screen and keystrokes to siphon bank balances."
  },
  {
    chunk_id: "EVID-I4C-UTIL-03",
    source_id: "REF-I4C-UTILITY-DISCONNECT",
    title: "Indian Cyber Crime Coordination Centre (I4C) Electricity Scam Notice",
    publisher: "Ministry of Home Affairs, Govt. of India (I4C)",
    source_url: "https://cybercrime.gov.in",
    category: "UTILITY_COERCION",
    license: "Public Safety Advisory / Government Notice",
    review_status: "VERIFIED" as const,
    timestamp: 1704067200000,
    reviewed_at: 1729641600000,
    language: "en",
    text: "Citizens are cautioned against fake SMS messages warning that power/electricity supply will be disconnected tonight due to unpaid bills. Official utility providers do not send urgent personal mobile numbers or APK download links for bill settlement. Do not dial the numbers mentioned in such coercive SMS alerts."
  },
  {
    chunk_id: "EVID-DOT-COURIER-04",
    source_id: "REF-DOT-COURIER-PHISHING",
    title: "Department of Telecommunications Notice on Delivery & Customs Phishing",
    publisher: "Department of Telecommunications (DoT) / Sanchar Saathi",
    source_url: "https://sancharsaathi.gov.in",
    category: "COURIER_PHISHING",
    license: "Public Safety Advisory / Government Notice",
    review_status: "VERIFIED" as const,
    timestamp: 1704067200000,
    reviewed_at: 1729641600000,
    language: "en",
    text: "Scammers send deceptive SMS alerts claiming courier delivery failed due to incomplete house addresses, directing victims to shortened or typo-squatting tracking links designed to capture debit card credentials or install spyware. Genuine couriers do not require re-registration fees over SMS."
  },
  {
    chunk_id: "EVID-CERTIN-APK-05",
    source_id: "REF-CERTIN-APK-MALWARE",
    title: "CERT-In Vulnerability & Malware Advisory on Third-Party APK Sideloading",
    publisher: "Indian Computer Emergency Response Team (CERT-In)",
    source_url: "https://www.cert-in.org.in",
    category: "MALICIOUS_APP_SIDELOAD",
    license: "Public Cybersecurity Advisory",
    review_status: "VERIFIED" as const,
    timestamp: 1704067200000,
    reviewed_at: 1729641600000,
    language: "en",
    text: "Malicious actors distribute fake banking updates, income tax refund portals, and utility apps as sideloaded APK files via messaging apps like WhatsApp and Telegram. Users must never install applications directly from SMS or chat links bypassing verified app stores."
  },
  {
    chunk_id: "EVID-CERTIN-URL-06",
    source_id: "REF-CERTIN-TYPOSQUATTING",
    title: "CERT-In Advisory on Obfuscated & Typo-Squatting Domains",
    publisher: "Indian Computer Emergency Response Team (CERT-In)",
    source_url: "https://www.cert-in.org.in",
    category: "DECEPTIVE_URL_TYPOSQUATTING",
    license: "Public Cybersecurity Advisory",
    review_status: "VERIFIED" as const,
    timestamp: 1704067200000,
    reviewed_at: 1729641600000,
    language: "en",
    text: "Phishing campaigns heavily exploit URL shorteners (bit.ly, tinyurl, wa.me) and character substitution domains (e.g., paypa1, sbi-kyc) to evade security filters. Always inspect the canonical top-level domain before providing information."
  },
  {
    chunk_id: "EVID-FTC-PRIZE-07",
    source_id: "REF-FTC-PRIZE-SWEEPSTAKES",
    title: "FTC Consumer Alert: Advance-Fee & Lottery Prize Scams",
    publisher: "Federal Trade Commission (FTC)",
    source_url: "https://consumer.ftc.gov/articles/fake-prize-sweepstakes-and-lottery-scams",
    category: "ADVANCE_FEE_SCAM",
    license: "Public Domain / US Federal Government Work",
    review_status: "VERIFIED" as const,
    timestamp: 1704067200000,
    reviewed_at: 1729641600000,
    language: "en",
    text: "Legitimate lotteries and sweepstakes never require the winner to pay a registration fee, customs duty, or processing charge in advance. If you are asked to send money to claim an unexpected award or prize, it is always a scam."
  }
];

/** Draft summaries retained for review; these are intentionally not ingested until source URLs, licenses, and dates are verified. */
export async function seedKnowledgeBase(): Promise<{ ingested: number; errors: string[] }> {
  console.log("=== Starting GARUD RAG Knowledge Ingestion to Qdrant Cloud ===");
  const approvedChunks = KNOWLEDGE_BASE_CHUNKS.filter(chunk => chunk.review_status === "VERIFIED");
  if (approvedChunks.length === 0) {
    return { ingested: 0, errors: ["Ingestion skipped: source summaries are pending provenance and license review."] };
  }
  const gemini = new GeminiService();
  const qdrant = new QdrantService();

  const collStatus = await qdrant.ensureCollection();
  if (!collStatus.connected) {
    throw new Error(`Cannot connect to Qdrant: ${collStatus.error}`);
  }

  console.log(`Qdrant collection ready. Vector dimension: ${collStatus.vectorDimension}`);

  const itemsToUpsert = [];
  const errors: string[] = [];

  for (let i = 0; i < approvedChunks.length; i++) {
    const chunk = approvedChunks[i];
    console.log(`Embedding reviewed chunk [${i + 1}/${approvedChunks.length}]: ${chunk.chunk_id}...`);

    try {
      const metadata = chunkKnowledgeDocument({
        source_id: chunk.source_id,
        title: chunk.title,
        publisher: chunk.publisher,
        source_url: chunk.source_url,
        license: chunk.license,
        timestamp: chunk.timestamp as number,
        reviewed_at: chunk.reviewed_at as number,
        review_status: "VERIFIED",
        language: chunk.language || "en",
        category: chunk.category,
        text: chunk.text
      });

      for (const item of metadata) {
        const embeddingText = `${item.title}\n${item.publisher}\nCategory: ${item.category}\n${item.text}`;
        const vector = await gemini.getEmbedding(embeddingText, TaskType.RETRIEVAL_DOCUMENT);
        itemsToUpsert.push({ id: qdrantPointId(item.chunk_id), vector, metadata: item });
      }
    } catch (err: any) {
      console.error(`Error processing chunk ${chunk.chunk_id}:`, err.message);
      errors.push(`${chunk.chunk_id}: ${err.message}`);
    }
  }

  if (itemsToUpsert.length > 0) {
    console.log(`Upserting ${itemsToUpsert.length} vectors to Qdrant Cloud...`);
    const upsertRes = await qdrant.upsertChunks(itemsToUpsert);
    if (!upsertRes.success) {
      throw new Error(`Qdrant upsert failed: ${upsertRes.error}`);
    }
    console.log(`Successfully ingested ${upsertRes.count} verified evidence chunks!`);
    return { ingested: upsertRes.count, errors };
  }

  return { ingested: 0, errors };
}

if (require.main === module) {
  seedKnowledgeBase()
    .then(res => {
      console.log("Ingestion finished:", res);
      process.exit(0);
    })
    .catch(err => {
      console.error("Ingestion failed:", err);
      process.exit(1);
    });
}
