package com.garud.security.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.garud.security.model.NotificationData
import com.garud.security.ui.theme.GarudForestGreen
import com.garud.security.ui.theme.GarudMutedGray
import com.garud.security.ui.theme.GarudNearBlack

data class SampleThreatScenario(
    val title: String,
    val description: String,
    val notification: NotificationData
)

val SAMPLE_SCENARIOS = listOf(
    SampleThreatScenario(
        title = "Bank OTP Harvesting Scam",
        description = "Attacker impersonates bank support requesting OTP to cancel a fake transaction.",
        notification = NotificationData(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "SBI Customer Support Agent",
            text = "Dear customer, unauthorized debit of Rs 49,999 initiated. Share your OTP with our executive immediately to cancel this transaction."
        )
    ),
    SampleThreatScenario(
        title = "Courier Failed Delivery Phishing",
        description = "Deceptive courier alert with shortened tracking link.",
        notification = NotificationData(
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "FedEx Express Delivery Notice",
            text = "Package #FX-8921 delivery failed due to incomplete address. Update address within 12h at https://bit.ly/fedx-parcel-reupdate to avoid return penalty."
        )
    ),
    SampleThreatScenario(
        title = "Urgent Power Disconnection Threat",
        description = "Coercive utility scam demanding immediate payment.",
        notification = NotificationData(
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Electricity Department Notice",
            text = "URGENT: Your electricity power supply will be disconnected tonight at 9:30 PM due to unpaid bill. Transfer Rs 1,450 immediately via UPI to prevent cutoff."
        )
    ),
    SampleThreatScenario(
        title = "Legitimate Transaction OTP Notice",
        description = "Genuine bank authentication notice with warning not to share.",
        notification = NotificationData(
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "HDFC Bank Alert",
            text = "482019 is your secret OTP for transaction of Rs 1,200.00 at Amazon. Valid for 10 mins. Do not share with anyone."
        )
    ),
    SampleThreatScenario(
        title = "Benign Friend Conversation",
        description = "Standard casual message containing no suspicious patterns.",
        notification = NotificationData(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Priya Sharma",
            text = "Hey! Are you still up for coffee this evening? Let me know what time works best for you."
        )
    )
)

@Composable
fun TestSimulationDialog(
    onDismiss: () -> Unit,
    onSimulate: (NotificationData) -> Unit
) {
    var selectedIndex by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = GarudForestGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Simulate Threat Sample",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GarudNearBlack,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = GarudMutedGray
                        )
                    }
                }

                Text(
                    text = "Screen realistic notification samples on-device through GARUD's local rule engine.",
                    fontSize = 12.sp,
                    color = GarudMutedGray,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                SAMPLE_SCENARIOS.forEachIndexed { index, scenario ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedIndex = index }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = (selectedIndex == index),
                            onClick = { selectedIndex = index }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = scenario.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GarudNearBlack
                            )
                            Text(
                                text = scenario.description,
                                fontSize = 11.sp,
                                color = GarudMutedGray,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "Cancel", color = GarudMutedGray)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    androidx.compose.material3.Button(
                        onClick = {
                            val scenario = SAMPLE_SCENARIOS[selectedIndex]
                            onSimulate(scenario.notification)
                            onDismiss()
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = GarudForestGreen
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "Run Analysis", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
