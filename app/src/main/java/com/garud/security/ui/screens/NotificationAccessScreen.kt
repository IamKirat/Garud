package com.garud.security.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garud.security.ui.theme.GarudAmber
import com.garud.security.ui.theme.GarudForestGreen
import com.garud.security.ui.theme.GarudMutedGray
import com.garud.security.ui.theme.GarudNearBlack
import com.garud.security.ui.theme.GarudWarmCream
import com.garud.security.ui.theme.GarudWarningRed

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun NotificationAccessScreen(
    isAccessGranted: Boolean,
    isServiceConnected: Boolean,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarudWarmCream)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GarudNearBlack
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Notification Access",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = GarudNearBlack
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Current Permission Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAccessGranted) Color(0xFFE9F4EE) else Color(0xFFFBECEC)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (isAccessGranted) GarudForestGreen else GarudWarningRed,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAccessGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = if (isAccessGranted) "Access Granted" else "Access Not Granted",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAccessGranted) GarudForestGreen else GarudWarningRed
                        )
                        Text(
                            text = if (isAccessGranted)
                                if (isServiceConnected) "GARUD's notification listener is connected." else "Android access is granted; GARUD's listener is not connected right now."
                            else
                                "Android has not authorized notification listener access.",
                            fontSize = 12.sp,
                            color = GarudNearBlack
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Why access is required
            Text(
                text = "Why GARUD Needs Notification Access",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GarudNearBlack
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Modern phishing, OTP theft, and financial scams frequently arrive via incoming app notifications (SMS, WhatsApp, Banking alerts). Android provides NotificationListenerService as the official, sandboxed API for security apps to inspect incoming notifications as they arrive.",
                fontSize = 13.sp,
                color = GarudMutedGray,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Privacy Transparency
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = GarudForestGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Privacy & Data Guarantees",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = GarudNearBlack
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Local first: Screening rules run on this device.\n" +
                               "• Optional cloud analysis: If enabled in Settings, exposed notification text can be sent to your configured HTTPS backend.\n" +
                               "• Minimal storage: GARUD stores finding metadata only when history is enabled; raw notification text is not saved.\n" +
                               "• Your control: Pause monitoring or revoke notification access in Android Settings at any time.",
                        fontSize = 12.sp,
                        color = GarudNearBlack,
                        lineHeight = 19.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: How to enable or revoke
            Text(
                text = "How to Enable or Revoke Access",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = GarudNearBlack
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap the button below to open Android's Device & App Notifications screen. Find 'GARUD' and toggle access on or off as desired.",
                fontSize = 12.sp,
                color = GarudMutedGray,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Button: Open Android Notification Access Settings
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GarudNearBlack)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open Notification Access Settings",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Button: Continue to Dashboard
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GarudForestGreen)
            ) {
                Text(
                    text = "Continue to Dashboard",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
