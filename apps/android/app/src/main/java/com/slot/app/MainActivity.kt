package com.slot.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.slot.app.core.design.SlotTheme
import com.slot.app.core.design.Tokens
import com.slot.app.data.SlotViewModel
import com.slot.app.ui.FocusScreen
import com.slot.app.ui.SettingsScreen
import com.slot.app.ui.StatsScreen
import com.slot.app.ui.SubjectsScreen
import com.slot.app.ui.TasksScreen
import com.slot.app.ui.TimetableScreen
import com.slot.app.ui.TodayScreen
import com.slot.app.ui.TrainingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: SlotViewModel = viewModel()
            val user by vm.user.collectAsStateWithLifecycle()
            val profile by vm.profile.collectAsStateWithLifecycle()

            val dark = when (profile.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            SlotTheme(darkTheme = dark) {
                if (user == null) AuthScreen(vm) else SlotRoot(vm)
            }
        }
    }
}

private data class Destination(val route: String, val label: String)

private val DESTINATIONS = listOf(
    Destination("today", "Today"),
    Destination("timetable", "Timetable"),
    Destination("tasks", "Tasks"),
    Destination("training", "Training"),
    Destination("stats", "Stats"),
)

@Composable
private fun SlotRoot(vm: SlotViewModel) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                DESTINATIONS.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(destination.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                    ),
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = "today",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("today") {
                TodayScreen(
                    vm = vm,
                    onOpenSubjects = { nav.navigate("subjects") },
                    onOpenFocus = { nav.navigate("focus") },
                    onOpenSettings = { nav.navigate("settings") },
                )
            }
            composable("timetable") { TimetableScreen(vm) }
            composable("tasks") { TasksScreen(vm) }
            composable("training") { TrainingScreen(vm) }
            composable("stats") { StatsScreen(vm) }
            composable("focus") { FocusScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
            composable("subjects") { SubjectsScreen(vm) }
        }
    }
}

@Composable
private fun AuthScreen(vm: SlotViewModel) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("signin") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val configured = BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s10),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("SL", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                }
                Text("Slot", style = MaterialTheme.typography.headlineSmall)
            }

            Spacer(Modifier.height(Tokens.Space.s6))

            Text(
                text = if (mode == "signin") "Sign in" else "Create your account",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Your timetable and tasks, on every device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Tokens.Space.s6))

            if (!configured) {
                Text(
                    text = "Firebase is not configured. Add the keys to apps/android/local.properties and rebuild.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = Tokens.Space.s3),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
            ) {
                OutlinedButton(
                    onClick = { vm.signInWithGoogle(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Continue with Google") }

                if (mode == "signup") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        if (mode == "signin") vm.signIn(email, password)
                        else vm.register(name, email, password)
                    },
                    enabled = email.isNotBlank() && password.length >= 6,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (mode == "signin") "Sign in" else "Create account")
                }

                TextButton(
                    onClick = { mode = if (mode == "signin") "signup" else "signin" },
                ) {
                    Text(
                        if (mode == "signin") "No account yet? Create one"
                        else "Already have an account? Sign in",
                    )
                }

                if (mode == "signin") {
                    TextButton(onClick = { vm.resetPassword(email) }, enabled = email.isNotBlank()) {
                        Text("Forgot password?")
                    }
                }
            }

            Spacer(Modifier.height(Tokens.Space.s6))

            Text(
                text = "Reminders fire on your phone. The web dashboard shows them while it is open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
