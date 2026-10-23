# Deploy the GARUD backend

The VirusTotal API key belongs in the backend host's secret environment, never in Android Settings or the APK. Android Settings needs the public HTTPS base URL generated after the backend is deployed.

## Render setup

1. Push this project to a GitHub repository you control. The repository must include `render.yaml` and the `server/` directory.
2. In Render, choose **New → Blueprint** and connect that repository. Render reads `render.yaml` and creates the `garud-api` web service. Render's Blueprint setup asks for the secret `VIRUSTOTAL_API_KEY`; enter a newly generated VirusTotal key there.
3. Wait for the service deployment to finish. Open `https://<your-service>.onrender.com/health`. A healthy response has `"status":"ok"` and `"threatIntelConfigured":true`.
4. Copy only the base address, such as `https://garud-api.onrender.com`, into GARUD → Settings → GARUD Backend → Public HTTPS base URL. Do not append `/health` or `/api/analyze/url`, and do not paste the VirusTotal key there.
5. Open Link Analysis and scan a URL. GARUD shows a consent prompt before submitting the URL to VirusTotal.

The backend's VirusTotal route is `POST /api/analyze/url`. If the health response says `threatIntelConfigured: false`, set `VIRUSTOTAL_API_KEY` in Render's service environment and redeploy. Render's free web services may sleep when idle, so the first request after inactivity can take longer.

Deployment requires a GitHub repository connected to a Render account. This checkout has no Git remote configured, so it cannot be published to Render directly from this workspace.
