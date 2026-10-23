package com.garud.security.engine

import com.garud.security.model.AnalysisResult
import com.garud.security.model.IndicatorCategory
import com.garud.security.model.MessageSource
import com.garud.security.model.NotificationData
import com.garud.security.model.RiskLevel
import com.garud.security.model.TriggeredIndicator
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.regex.Pattern

/**
 * Real, offline, rule-based notification analysis engine.
 * Requires zero network calls or cloud APIs.
 */
object NotificationAnalysisEngine {

    // Common URL shortener hosts
    private val SHORTENER_DOMAINS = setOf(
        "bit.ly", "tinyurl.com", "is.gd", "t.co", "cutt.ly", "rb.gy",
        "ow.ly", "shorturl.at", "goo.gl", "qr.ae", "rebrand.ly", "buff.ly",
        "s.id", "v.gd", "clck.ru", "wa.me"
    )

    // Suspicious deceptive brand names frequently targeted
    private val SENSITIVE_BRANDS = listOf(
        "sbi", "state bank", "hdfc", "icici", "axis bank", "pnb", "punjab national bank",
        "paytm", "phonepe", "gpay", "google pay", "paypal", "chase", "wells fargo",
        "bank of america", "citibank", "fedex", "dhl", "ups", "india post",
        "income tax", "customs", "cbi", "police", "trai", "electricity department",
        "whatsapp support", "meta support", "microsoft support", "apple support"
    )

    // Regex for extracting URLs
    private val URL_PATTERN = Pattern.compile(
        """(https?://[^\s<>"{}|\\^`]+)""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex for IP address hostnames
    private val IP_HOST_PATTERN = Pattern.compile(
        """^https?://(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?(?:/.*)?$""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Computes a deterministic deduplication hash for notification content.
     */
    fun computeDedupKey(packageName: String, title: String?, text: String?): String {
        val raw = "${packageName.trim().lowercase()}|${title?.trim().orEmpty()}|${text?.trim().orEmpty()}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Inspects a notification and produces an explainable [AnalysisResult].
     */
    fun analyze(data: NotificationData): AnalysisResult {
        val title = data.title?.trim().orEmpty()
        val text = data.text?.trim().orEmpty()
        val subText = data.subText?.trim().orEmpty()
        val combinedContent = listOf(title, text, subText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()

        val dedupKey = computeDedupKey(data.packageName, data.title, data.text)

        // 1. Check for missing or incomplete text
        if (combinedContent.length < 4) {
            return AnalysisResult(
                sourcePackage = data.packageName,
                sourceAppName = data.appName.ifBlank { data.packageName },
                riskLevel = RiskLevel.INSUFFICIENT_INFORMATION,
                triggeredIndicators = emptyList(),
                explanation = "Notification content is empty or contains insufficient text to evaluate safety.",
                safeActions = listOf(
                    "View the notification directly within the official application.",
                    "Do not act on notifications whose origin or full details cannot be seen."
                ),
                titleSnippet = if (title.isNotBlank()) title else "Unspecified notification",
                dedupKey = dedupKey
            )
        }

        val contentLower = combinedContent.lowercase(Locale.ROOT)
        val indicators = mutableListOf<TriggeredIndicator>()

        // 2. Check for OTP / Credential Theft Solicitation
        checkCredentialTheft(contentLower, indicators)

        // 3. Check for Suspicious Payment / Fee Demands
        checkPaymentDemands(contentLower, indicators)

        // 4. Check for Urgent Threats / Coercion
        checkUrgentCoercion(contentLower, indicators)

        // 5. Check for Brand / Institutional Impersonation
        checkImpersonation(contentLower, data.packageName, indicators)

        // 6. Check for Malicious App / APK / Remote Access Installation
        checkMaliciousAppInstall(contentLower, indicators)

        // 7. Check for Suspicious / Shortened URLs
        checkUrls(combinedContent, indicators)

        // 8. Synthesize Risk Level and Explanation
        val (finalRiskLevel, finalExplanation, finalSafeActions) = synthesizeVerdict(
            indicators = indicators,
            contentLower = contentLower
        )

        val snippet = if (title.isNotBlank()) {
            if (title.length > 40) title.take(37) + "..." else title
        } else {
            if (text.length > 40) text.take(37) + "..." else text
        }

        return AnalysisResult(
            sourcePackage = data.packageName,
            sourceAppName = data.appName.ifBlank { data.packageName },
            riskLevel = finalRiskLevel,
            triggeredIndicators = indicators,
            explanation = finalExplanation,
            safeActions = finalSafeActions,
            titleSnippet = snippet,
            dedupKey = dedupKey
        )
    }

    private fun checkCredentialTheft(text: String, indicators: MutableList<TriggeredIndicator>) {
        val shareOtpRegex = Regex("""(share|send|provide|give|reply with|enter)\s+(your\s+)?(otp|one[-\s]time\s+password|pin|password|cvv|secret code)""")
        val executiveOtpRegex = Regex("""(executive|agent|representative|caller|support)\s+.*(otp|verification code|pin)""")
        val otpUrgentVerifyRegex = Regex("""(otp|code)\s+to\s+(unblock|verify|activate|cancel transaction)""")

        if (shareOtpRegex.containsMatchIn(text) || executiveOtpRegex.containsMatchIn(text) || otpUrgentVerifyRegex.containsMatchIn(text)) {
            indicators.add(
                TriggeredIndicator(
                    category = IndicatorCategory.OTP_CREDENTIAL_THEFT,
                    title = "Request to Share OTP / Sensitive Credentials",
                    detail = "The message instructs or prompts you to share an OTP, PIN, password, or security code. Legitimate institutions NEVER request your OTP.",
                    severity = RiskLevel.HIGH
                )
            )
        } else if (text.contains("otp") && (text.contains("do not share") || text.contains("valid for"))) {
            // Legitimate transaction or authentication OTP notice
            indicators.add(
                TriggeredIndicator(
                    category = IndicatorCategory.OTP_CREDENTIAL_THEFT,
                    title = "Security Verification Code Notice",
                    detail = "Contains an authentication code. If you did not initiate this request, someone may be attempting unauthorized access.",
                    severity = RiskLevel.CAUTION
                )
            )
        }
    }

    private fun checkPaymentDemands(text: String, indicators: MutableList<TriggeredIndicator>) {
        val lotteryFeeRegex = Regex("""(won|winner|lottery|prize|reward|gift\s+card|selected\s+for)\s+.*(fee|charge|transfer|deposit|pay|processing)""")
        val disconnectPayRegex = Regex("""(electricity|power|sim|card)\s+.*(disconnected|blocked|suspended)\s+.*(pay|call|contact|transfer)""")
        val urgentFineRegex = Regex("""(fine|penalty|court|warrant|challan|arrest)\s+.*(pay|settle|clear\s+dues)""")

        if (lotteryFeeRegex.containsMatchIn(text)) {
            indicators.add(
                TriggeredIndicator(
                    category = IndicatorCategory.SUSPICIOUS_PAYMENT,
                    title = "Advance-Fee or Prize Scam Pattern",
                    detail = "Prompts you to pay upfront fees or charges to claim an unexpected lottery, prize, or loan.",
                    severity = RiskLevel.HIGH
                )
            )
        } else if (disconnectPayRegex.containsMatchIn(text) || urgentFineRegex.containsMatchIn(text)) {
            indicators.add(
                TriggeredIndicator(
                    category = IndicatorCategory.SUSPICIOUS_PAYMENT,
                    title = "Coercive Payment Demand",
                    detail = "Demands immediate payment under threat of service cutoff, fine, or arrest.",
                    severity = RiskLevel.HIGH
                )
            )
        } else if (text.contains("transfer immediately") || text.contains("pay immediately") || text.contains("send money urgently")) {
            indicators.add(
                TriggeredIndicator(
                    category = IndicatorCategory.SUSPICIOUS_PAYMENT,
                    title = "Urgent Money Transfer Request",
                    detail = "Appeals for immediate fund transfer, which is a frequent indicator of social engineering.",
                    severity = RiskLevel.CAUTION
                )
            )
        }
    }

    private fun checkUrgentCoercion(text: String, indicators: MutableList<TriggeredIndicator>) {
        val suspensionThreat = Regex("""(account|sim|card|service|pan|kyc|profile)\s+.*(blocked|suspended|terminated|deactivated|frozen|expires?)\s+(today|immediately|within\s+\d+\s+hours?|by\s+\d+)""")
        val legalThreat = Regex("""(arrest\s+warrant|police\s+case|legal\s+notice|cbi|court\s+order|customs\s+seizure)""")

        if (suspensionThreat.containsMatchIn(text) || legalThreat.containsMatchIn(text)) {
            indicators.add(
                TriggeredIndicator(
                    category = IndicatorCategory.URGENT_COERCION,
                    title = "Urgent Threat or Pressure Tactic",
                    detail = "Threatens immediate account suspension, service disconnection, or legal repercussions to induce panic.",
                    severity = RiskLevel.CAUTION
                )
            )
        }
    }

    private fun checkImpersonation(text: String, packageName: String, indicators: MutableList<TriggeredIndicator>) {
        val matchedBrand = SENSITIVE_BRANDS.firstOrNull { brand -> text.contains(brand) }
        val packageLower = packageName.lowercase(Locale.ROOT)

        if (matchedBrand != null) {
            // Check if package looks like the genuine official app
            val isLikelyOfficial = packageLower.contains(matchedBrand.replace(" ", ""))

            val deliveryScam = (matchedBrand.contains("fedex") || matchedBrand.contains("dhl") ||
                    matchedBrand.contains("ups") || matchedBrand.contains("post")) &&
                    (text.contains("address incomplete") || text.contains("delivery failed") || text.contains("update address") || text.contains("reschedule"))

            val bankAlert = (matchedBrand.contains("sbi") || matchedBrand.contains("hdfc") ||
                    matchedBrand.contains("icici") || matchedBrand.contains("bank") || matchedBrand.contains("paytm")) &&
                    (text.contains("kyc") || text.contains("pan update") || text.contains("rewards point") || text.contains("redeem"))

            if (deliveryScam) {
                indicators.add(
                    TriggeredIndicator(
                        category = IndicatorCategory.IMPERSONATION,
                        title = "Courier / Delivery Impersonation Scam",
                        detail = "Mimics a delivery provider ($matchedBrand) claiming failed delivery to bait you into clicking an unverified link.",
                        severity = RiskLevel.HIGH
                    )
                )
            } else if (bankAlert && !isLikelyOfficial) {
                indicators.add(
                    TriggeredIndicator(
                        category = IndicatorCategory.IMPERSONATION,
                        title = "Financial Entity Impersonation",
                        detail = "References $matchedBrand and prompts for urgent KYC/PAN verification or reward redemption from outside the official app.",
                        severity = RiskLevel.HIGH
                    )
                )
            } else if (!isLikelyOfficial && (text.contains("support") || text.contains("official") || text.contains("helpdesk"))) {
                indicators.add(
                    TriggeredIndicator(
                        category = IndicatorCategory.IMPERSONATION,
                        title = "Customer Support Impersonation",
                        detail = "Claims to be official support or helpdesk for $matchedBrand.",
                        severity = RiskLevel.CAUTION
                    )
                )
            }
        }
    }

    private fun checkMaliciousAppInstall(text: String, indicators: MutableList<TriggeredIndicator>) {
        val apkDownloadRegex = Regex("""(download|install)\s+.*(\.apk|application|update\s+app|security\s+patch)""")
        val remoteAccessRegex = Regex("""(install|download)\s+.*(anydesk|teamviewer|quicksupport|rustdesk|screen\s+share)""")

        if (remoteAccessRegex.containsMatchIn(text)) {
            indicators.add(
                TriggeredIndicator(
                    category = IndicatorCategory.MALICIOUS_APP_INSTALL,
                    title = "Dangerous Remote Access Tool Prompt",
                    detail = "Instructs you to install remote control or screen-sharing tools (AnyDesk, TeamViewer, QuickSupport), a notorious banking fraud technique.",
                    severity = RiskLevel.HIGH
                )
            )
        } else if (apkDownloadRegex.containsMatchIn(text) || text.contains(".apk")) {
            indicators.add(
                TriggeredIndicator(
                    category = IndicatorCategory.MALICIOUS_APP_INSTALL,
                    title = "Third-Party App / APK Installation Request",
                    detail = "Prompts installing software or an APK file directly, bypassing Google Play Protect.",
                    severity = RiskLevel.HIGH
                )
            )
        }
    }

    private fun checkUrls(content: String, indicators: MutableList<TriggeredIndicator>) {
        val matcher = URL_PATTERN.matcher(content)
        while (matcher.find()) {
            val urlString = matcher.group(1).orEmpty()
            try {
                val uri = URI(urlString)
                val host = uri.host?.lowercase(Locale.ROOT).orEmpty()

                if (SHORTENER_DOMAINS.contains(host)) {
                    indicators.add(
                        TriggeredIndicator(
                            category = IndicatorCategory.SUSPICIOUS_URL,
                            title = "Obfuscated / Shortened Link",
                            detail = "Contains a shortened link ($host) that conceals the true destination server.",
                            severity = RiskLevel.CAUTION
                        )
                    )
                }

                if (IP_HOST_PATTERN.matcher(urlString).matches()) {
                    indicators.add(
                        TriggeredIndicator(
                            category = IndicatorCategory.SUSPICIOUS_URL,
                            title = "Raw IP Address Destination",
                            detail = "Points directly to an IP address ($host) rather than a verified domain name.",
                            severity = RiskLevel.HIGH
                        )
                    )
                }

                if (host.contains("@") || urlString.contains("@")) {
                    indicators.add(
                        TriggeredIndicator(
                            category = IndicatorCategory.SUSPICIOUS_URL,
                            title = "Deceptive URL Syntax",
                            detail = "Contains '@' symbol in URL, often used to disguise deceptive hostnames.",
                            severity = RiskLevel.HIGH
                        )
                    )
                }

                // Check deceptive domain typo-squatting
                val deceptiveHosts = listOf("paypa1", "arnazon", "g00gle", "sbi-kyc", "hdfc-bank", "icici-alert")
                if (deceptiveHosts.any { host.contains(it) }) {
                    indicators.add(
                        TriggeredIndicator(
                            category = IndicatorCategory.SUSPICIOUS_URL,
                            title = "Deceptive Typo-Squatting Domain",
                            detail = "Host '$host' closely resembles a known entity to trick visitors.",
                            severity = RiskLevel.HIGH
                        )
                    )
                }

            } catch (_: Exception) {
                // Invalid URI structure
            }
        }
    }

    private fun synthesizeVerdict(
        indicators: List<TriggeredIndicator>,
        contentLower: String
    ): Triple<RiskLevel, String, List<String>> {
        val highIndicators = indicators.filter { it.severity == RiskLevel.HIGH }
        val cautionIndicators = indicators.filter { it.severity == RiskLevel.CAUTION }

        // Multiple distinct categories elevate risk
        val distinctCategories = indicators.map { it.category }.distinct()

        val finalRiskLevel: RiskLevel
        val explanation: String
        val safeActions = mutableListOf<String>()

        when {
            // Case 1: High severity indicator or multiple compound indicators
            highIndicators.isNotEmpty() || (distinctCategories.size >= 2 && cautionIndicators.isNotEmpty()) -> {
                finalRiskLevel = RiskLevel.HIGH
                if (distinctCategories.size >= 2) {
                    explanation = "High risk: Multiple corroborating threat indicators detected (${distinctCategories.joinToString { it.displayName }})."
                } else {
                    explanation = "High risk: ${highIndicators.first().detail}"
                }
                safeActions.add("DO NOT share any OTP, PIN, password, or bank details.")
                safeActions.add("DO NOT click any link or install any recommended applications.")
                safeActions.add("If concerned, open the official app or website directly from your browser.")
                safeActions.add("Block and report the sender if this was an unsolicited message.")
            }

            // Case 2: Caution indicators
            cautionIndicators.isNotEmpty() -> {
                finalRiskLevel = RiskLevel.CAUTION
                explanation = "Caution advised: ${cautionIndicators.first().detail}"
                safeActions.add("Verify the identity of the sender through official channels.")
                safeActions.add("Avoid clicking shortened or unfamiliar links.")
                safeActions.add("Do not feel pressured by urgent countdowns or threats.")
            }

            // Case 3: No indicators found
            else -> {
                finalRiskLevel = RiskLevel.LOW
                explanation = "No known suspicious patterns or indicators were detected in this notification. Note: Absence of indicators does not guarantee absolute safety."
                safeActions.add("Exercise normal digital hygiene.")
                safeActions.add("Always verify unexpected payment requests before approving.")
            }
        }

        return Triple(finalRiskLevel, explanation, safeActions)
    }

    /**
     * Analyzes user-pasted SMS or message content with optional grounded RAG evidence retrieval.
     */
    fun analyzeCustomMessage(
        text: String,
        sender: String = "",
        source: MessageSource = MessageSource.SMS,
        includeRagEvidence: Boolean = true
    ): AnalysisResult {
        val pkg = source.defaultPackage
        val appName = source.displayName
        val title = sender.ifBlank { source.displayName }
        val data = NotificationData(
            packageName = pkg,
            appName = appName,
            title = title,
            text = text
        )
        val localResult = analyze(data)
        if (!includeRagEvidence || text.isBlank()) {
            return localResult
        }
        val ragReport = RagEvidenceEngine.generateGroundedReport(
            content = "$title $text",
            localResult = localResult
        )
        return localResult.copy(ragReport = ragReport)
    }
}

