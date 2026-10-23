import { describe, it } from "node:test";
import assert from "node:assert";
import fs from "node:fs";
import { EvidenceRetriever } from "../rag/evidenceRetriever";
import { CitationValidator } from "../rag/citationValidator";
import { AnalysisRequestSchema } from "../types";
import { KnowledgeChunkMetadataSchema } from "../types";
import { chunkKnowledgeDocument, qdrantPointId } from "../rag/chunker";
import { SsrfValidator, ThreatIntelligenceService, normalizeCandidateUrl } from "../threatIntel/urlScanner";
import { loadNotificationGuidanceRecords } from "../scripts/importNotificationGuidance";

describe("GARUD Backend Pipeline Tests", () => {
  const retriever = new EvidenceRetriever();

  it("extracts security and brand entities correctly", () => {
    const text = "Your HDFC account OTP is pending. Install AnyDesk now.";
    const entities = retriever.extractEntities(text);
    assert.ok(entities.includes("otp"));
    assert.ok(entities.includes("hdfc"));
    assert.ok(entities.includes("anydesk"));
  });

  it("does not cite unreviewed local source summaries", () => {
    const text = "Share your OTP with our executive to avoid cancellation.";
    const retrieved = retriever.retrieve(text);
    assert.deepStrictEqual(retrieved, []);
  });

  it("parses all ten notification guidance chunks with exact hashes and synthetic provenance", () => {
    const records = loadNotificationGuidanceRecords();
    assert.strictEqual(records.length, 10);
    assert.deepStrictEqual(records.map(record => record.chunk_id), Array.from({ length: 10 }, (_, index) => `garud-notif-${String(index + 1).padStart(3, "0")}`));
    assert.ok(records.every(record => record.source_type === "synthetic_guidance" && record.review_status === "synthetic"));
    assert.ok(records.every(record => record.source_url === null && record.source_checked_at === null && record.source_license === null));
    assert.ok(records.every(record => record.ingested_at === null && record.embedding_model === null));
  });

  it("never treats synthetic notification guidance as citable external evidence", () => {
    const synthetic = [{ id: "garud-notif-002", title: "OTP guidance", publisher: "GARUD synthetic guidance", url: "", snippet: "Do not share OTPs.", sourceType: "synthetic_guidance" as const, reviewStatus: "synthetic" as const }];
    assert.strictEqual(CitationValidator.validate([{ id: synthetic[0].id, url: "" }], synthetic).isValid, false);
    assert.deepStrictEqual(CitationValidator.filterVerified([{ id: synthetic[0].id }], synthetic), []);
  });

  it("keeps local entity extraction available without fabricating cited retrieval", () => {
    const text = "Please download AnyDesk or TeamViewer to verify your identity.";
    const retrieved = retriever.retrieve(text);
    assert.ok(retriever.extractEntities(text).includes("anydesk"));
    assert.deepStrictEqual(retrieved, []);
  });

  it("validates authentic citations and rejects fabricated citation IDs", () => {
    const retrieved = [{ id: "TEST-SOURCE-1", title: "Test fixture source", publisher: "Test", url: "https://example.test/advisory", snippet: "Test fixture only." }];
    
    // Real citations match
    const validCheck = CitationValidator.validate(retrieved, retrieved);
    assert.strictEqual(validCheck.isValid, true);

    // Fabricated citations fail
    const fake = [{ id: "FAKE-CITATION-007", url: "https://unknown.fake" }];
    const fakeCheck = CitationValidator.validate(fake, retrieved);
    assert.strictEqual(fakeCheck.isValid, false);
    assert.ok(fakeCheck.invalidIds.includes("FAKE-CITATION-007"));
    const mismatchedHost = CitationValidator.validate(
      [{ id: retrieved[0].id, url: "https://evil.example/copy-of-test.example" }], retrieved
    );
    assert.strictEqual(mismatchedHost.isValid, false);
  });

  it("validates the GARUD metadata JSON Schema and language tag pattern", () => {
    const schema = JSON.parse(fs.readFileSync("src/rag/metadata.schema.json", "utf8"));
    assert.strictEqual(schema.$schema, "https://json-schema.org/draft/2020-12/schema");
    const languagePattern = new RegExp(schema.properties.language.pattern);
    assert.ok(languagePattern.test("en"));
    assert.ok(languagePattern.test("hi-IN"));
    assert.ok(!languagePattern.test("english"));
    const invalidVerified = KnowledgeChunkMetadataSchema.safeParse({
      chunk_id: "x", source_id: "s", title: "t", publisher: "p", source_url: "https://example.test/article",
      content_hash: "a".repeat(64), license: "reviewed", review_status: "VERIFIED", category: "test", text: "body"
    });
    assert.strictEqual(invalidVerified.success, false);
  });

  it("chunks reviewed source text with stable SHA-256 metadata and point IDs", () => {
    const source = {
      source_id: "TEST-SOURCE", title: "Test source", publisher: "Test publisher",
      source_url: "https://example.test/advisory", license: "Test fixture license",
      timestamp: 1_700_000_000_000, reviewed_at: 1_800_000_000_000,
      review_status: "VERIFIED" as const, language: "en", category: "TEST",
      text: "First reviewed sentence records the main source statement and its verified wording. Second sentence retains the next coherent clause from the same source paragraph. Third sentence adds enough source text to require another bounded retrieval chunk. Fourth sentence ensures the chunker splits rather than returning one oversized passage."
    };
    const first = chunkKnowledgeDocument(source, 120);
    const second = chunkKnowledgeDocument(source, 120);
    assert.ok(first.length > 1);
    assert.deepStrictEqual(first.map(chunk => chunk.chunk_id), second.map(chunk => chunk.chunk_id));
    assert.ok(first.every(chunk => /^[a-f0-9]{64}$/.test(chunk.content_hash)));
    assert.strictEqual(qdrantPointId(first[0].chunk_id), qdrantPointId(second[0].chunk_id));
    assert.ok(first.every(chunk => chunk.text.length <= 120));
    assert.ok(first.every(chunk => chunk.review_status === "VERIFIED" && chunk.reviewed_at === source.reviewed_at));
  });

  it("blocks SSRF attacks targeting localhost or cloud metadata", () => {
    const localhostCheck = SsrfValidator.isSafeDestination("http://127.0.0.1:8080/admin");
    assert.strictEqual(localhostCheck.isSafe, false);

    const metadataCheck = SsrfValidator.isSafeDestination("http://169.254.169.254/latest/meta-data");
    assert.strictEqual(metadataCheck.isSafe, false);

    const privateCheck = SsrfValidator.isSafeDestination("http://192.168.1.1/router");
    assert.strictEqual(privateCheck.isSafe, false);

    const publicCheck = SsrfValidator.isSafeDestination("https://rbi.org.in/press");
    assert.strictEqual(publicCheck.isSafe, true);
  });

  it("validates URLs before provider submission", async () => {
    assert.strictEqual(normalizeCandidateUrl("file:///etc/passwd"), undefined);
    const scanner = new ThreatIntelligenceService(undefined, async () => { throw new Error("must not be called"); });
    assert.strictEqual((await scanner.scanUrl("https://example.test")).status, "provider_not_configured");
    assert.strictEqual((await scanner.scanUrl("not a url")).status, "invalid_input");
  });

  it("keeps cloud message analysis disabled unless consent is explicit", () => {
    const parsed = AnalysisRequestSchema.parse({ content: "check this message" });
    assert.strictEqual(parsed.cloudConsent, false);
    assert.strictEqual(AnalysisRequestSchema.parse({ content: "check this message", cloudConsent: true }).cloudConsent, true);
  });

  it("returns actual VirusTotal detection counts from completed analysis", async () => {
    const calls: string[] = [];
    const fetchMock: typeof fetch = async (input) => {
      calls.push(String(input));
      if (calls.length === 1) return new Response(JSON.stringify({ data: { id: "analysis-123" } }), { status: 200 });
      return new Response(JSON.stringify({ data: { attributes: { status: "completed", stats: { malicious: 2, suspicious: 1, harmless: 5, undetected: 3 } } } }), { status: 200 });
    };
    const result = await new ThreatIntelligenceService("test-key", fetchMock).scanUrl("https://example.test/path");
    assert.deepStrictEqual(calls, ["https://www.virustotal.com/api/v3/urls", "https://www.virustotal.com/api/v3/analyses/analysis-123"]);
    assert.strictEqual(result.status, "completed");
    assert.strictEqual(result.isFlagged, true);
    assert.deepStrictEqual(result.stats, { malicious: 2, suspicious: 1, harmless: 5, undetected: 3 });
  });

  it("does not report provider errors as clean results", async () => {
    const scanner = new ThreatIntelligenceService("test-key", async () => new Response("", { status: 429 }));
    const result = await scanner.scanUrl("https://example.test");
    assert.strictEqual(result.status, "unavailable");
    assert.strictEqual(result.isFlagged, null);
  });
});
