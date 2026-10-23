package com.garud.security.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.garud.security.ui.components.GarudShieldLogo
import com.garud.security.ui.theme.GarudAmber
import com.garud.security.ui.theme.GarudForestGreen
import com.garud.security.ui.theme.GarudMutedGray
import com.garud.security.ui.theme.GarudNearBlack
import com.garud.security.ui.theme.GarudWarmCream

@Composable
fun WelcomeScreen(
    onSkip: () -> Unit,
    onAuthenticationSuccess: () -> Unit
) {
    var password by remember { mutableStateOf("123456") }
    var passwordError by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    if (showForgotPassword) AlertDialog(
        onDismissRequest = { showForgotPassword = false },
        title = { Text("Forgot password?") },
        text = { Text("This local demo has no account recovery service. The demo password is 123456.") },
        confirmButton = { TextButton(onClick = { showForgotPassword = false }) { Text("OK") } }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GarudWarmCream)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopEnd)
                .zIndex(2f)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onSkip, colors = ButtonDefaults.textButtonColors(contentColor = GarudNearBlack)) {
                Text("Skip", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(56.dp))
            GarudShieldLogo(size = 86.dp)
            Spacer(Modifier.height(16.dp))
            Text("GARUD", fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = GarudNearBlack)
            Spacer(Modifier.height(6.dp))
            Text("Your digital safety companion.", fontSize = 19.sp, fontWeight = FontWeight.Medium, color = GarudForestGreen, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Real-time, on-device analysis defending against OTP theft, malicious phishing links, and deceptive messages.",
                fontSize = 14.sp, color = GarudMutedGray, textAlign = TextAlign.Center, lineHeight = 20.sp
            )
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Demo password", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GarudNearBlack)
                    Text("Enter the local demo password to continue. No account or online sign-in is used.", fontSize = 12.sp, color = GarudMutedGray, lineHeight = 17.sp)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.take(32); passwordError = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Text("Demo password: 123456", fontSize = 11.sp, color = GarudMutedGray)
                    Button(
                        onClick = {
                            if (password == "123456") onAuthenticationSuccess() else passwordError = true
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GarudForestGreen)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Continue", fontWeight = FontWeight.Bold)
                    }
                    if (passwordError) Text("That demo password is incorrect.", color = GarudAmber, fontSize = 12.sp)
                    TextButton(onClick = { showForgotPassword = true }) { Text("Forgot password?") }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Skip to continue without entering a password.", fontSize = 12.sp, color = GarudMutedGray, textAlign = TextAlign.Center)
            Spacer(Modifier.height(30.dp))
        }
    }
}
