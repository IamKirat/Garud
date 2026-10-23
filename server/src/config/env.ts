import dotenv from "dotenv";
dotenv.config();

import { z } from "zod";

export const EnvSchema = z.object({
  QDRANT_URL: z.string().url("QDRANT_URL must be a valid URL").optional(),
  QDRANT_API_KEY: z.string().optional(),
  QDRANT_COLLECTION: z.string().default("garud_knowledge"),
  GEMINI_API_KEY: z.string().optional(),
  GEMINI_MODEL: z.string().default("gemini-3.8-flash"),
  GEMINI_EMBEDDING_MODEL: z.string().default("gemini-embedding-001"),
  VIRUSTOTAL_API_KEY: z.string().optional(),
  PORT: z.coerce.number().default(3000)
});

export type EnvConfig = z.infer<typeof EnvSchema>;

export function getEnvConfig(): EnvConfig {
  const parsed = EnvSchema.safeParse({
    QDRANT_URL: process.env.QDRANT_URL?.trim() || undefined,
    QDRANT_API_KEY: process.env.QDRANT_API_KEY?.trim() || undefined,
    QDRANT_COLLECTION: process.env.QDRANT_COLLECTION?.trim() || "garud_knowledge",
    GEMINI_API_KEY: process.env.GEMINI_API_KEY?.trim() || undefined,
    GEMINI_MODEL: process.env.GEMINI_MODEL?.trim() || "gemini-3.8-flash",
    GEMINI_EMBEDDING_MODEL: process.env.GEMINI_EMBEDDING_MODEL?.trim() || "gemini-embedding-001",
    VIRUSTOTAL_API_KEY: process.env.VIRUSTOTAL_API_KEY?.trim() || undefined,
    PORT: process.env.PORT || 3000
  });

  if (!parsed.success) {
    console.error("[CONFIG_ERROR] Invalid environment configuration:", parsed.error.format());
    throw new Error(`Environment validation failed: ${JSON.stringify(parsed.error.format())}`);
  }

  return parsed.data;
}
