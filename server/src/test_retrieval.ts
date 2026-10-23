import { VectorRetriever } from "./rag/vectorRetriever";

async function testRetrieval() {
  const retriever = new VectorRetriever();

  console.log("=== Testing Vector Retrieval Query 1: OTP Theft ===");
  const res1 = await retriever.retrieveEvidence("Your SBI account debit of Rs 49,999 is pending. Please share your OTP with our executive to cancel.", 3, true);
  console.log("Source:", res1.source);
  console.log("Status:", res1.providerStatus);
  console.log("Matches found:", res1.passages.length);
  for (const p of res1.passages) {
    console.log(`- [${p.id}] (score: ${p.score?.toFixed(4)}) ${p.title} -> ${p.url}`);
    console.log(`  Hash: ${p.metadata?.content_hash?.slice(0, 16)}... Status: ${p.metadata?.review_status}`);
  }

  console.log("\n=== Testing Vector Retrieval Query 2: Remote Access Install ===");
  const res2 = await retriever.retrieveEvidence("Please install AnyDesk from Play Store to fix your banking transaction error immediately.", 3, true);
  console.log("Source:", res2.source);
  console.log("Matches found:", res2.passages.length);
  for (const p of res2.passages) {
    console.log(`- [${p.id}] (score: ${p.score?.toFixed(4)}) ${p.title}`);
  }
}

testRetrieval().catch(console.error);
