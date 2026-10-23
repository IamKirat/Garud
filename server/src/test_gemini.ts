import dotenv from "dotenv";
dotenv.config();

import { GoogleGenerativeAI } from "@google/generative-ai";

async function run() {
  const apiKey = process.env.GEMINI_API_KEY?.trim() || "";
  const genAI = new GoogleGenerativeAI(apiKey);

  const testModels = ["gemini-3.8-flash", "gemini-2.5-flash-lite", "gemini-flash-latest"];
  for (const m of testModels) {
    try {
      console.log(`Testing model: ${m}...`);
      const model = genAI.getGenerativeModel({ model: m });
      const res = await model.generateContent("Respond with: GARUD_OK");
      console.log(`Model ${m} SUCCESS:`, res.response.text().trim());
      return m;
    } catch (e: any) {
      console.log(`Model ${m} FAILED:`, e.message);
    }
  }
}

run();
