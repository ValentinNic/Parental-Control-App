package com.example.myapp

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapp.ui.theme.MyAppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    // Initialize FirebaseAuth
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAuth = Firebase.auth

        setContent {
            MyAppTheme {
                // Set up navigation with screens: main, login, signup, and dashboard.
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") { MainScreen(navController) }
                    composable("login") { LoginScreen(navController, firebaseAuth) }
                    composable("signup") { SignupScreen(navController, firebaseAuth) }
                    // Dashboard now receives a username parameter.
                    composable("dashboard/{username}") { backStackEntry ->
                        val username = backStackEntry.arguments?.getString("username") ?: "User"
                        DashboardScreen(navController, username = username)
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Parental Control App",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Login Column: Icon + Text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { navController.navigate("login") }) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Login",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Text(text = "Login")
            }
            // Signup Column: Icon + Text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { navController.navigate("signup") }) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Signup",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Text(text = "Signup")
            }
        }
    }
}

@Composable
fun LoginScreen(navController: NavController, firebaseAuth: FirebaseAuth) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Login",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                firebaseAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        val username = getUsernameForUser(context, email, password) ?: email
                        if (task.isSuccessful) {
                            Toast.makeText(context, "Login Successful", Toast.LENGTH_LONG).show()
                            Log.d("LoginScreen", "Login Successful")
                            // URL-encode the username to safely pass it in the route
                            val encodedUsername = Uri.encode(username)
                            navController.navigate("dashboard/$encodedUsername") {
                                popUpTo("main") { inclusive = true }
                            }
                        } else {
                            // Firebase login failed; fallback to JSON file authentication
                            if (validateCredentials(context, email, password)) {
                                Toast.makeText(
                                    context,
                                    "Login Successful (JSON fallback)",
                                    Toast.LENGTH_LONG
                                ).show()
                                Log.d("LoginScreen", "Login Successful via JSON fallback")
                                val encodedUsername = Uri.encode(username)
                                navController.navigate("dashboard/$encodedUsername") {
                                    popUpTo("main") { inclusive = true }
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Login Failed: ${task.exception?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                                Log.e(
                                    "LoginScreen",
                                    "Login Failed: ${task.exception?.javaClass?.simpleName} - ${task.exception?.message}"
                                )
                            }
                        }
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
fun SignupScreen(navController: NavController, firebaseAuth: FirebaseAuth) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Signup",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        // New text field for Username
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Save the user's details (username, email, password) in JSON fallback.
                            addUser(context, email, password, username)
                            Toast.makeText(context, "Signup Successful", Toast.LENGTH_LONG).show()
                            Log.d("SignupScreen", "Signup Successful")
                        } else {
                            // Firebase signup failed; fallback to JSON file signup
                            addUser(context, email, password, username)
                            Toast.makeText(
                                context,
                                "Signup Successful (JSON fallback)",
                                Toast.LENGTH_LONG
                            ).show()
                            Log.d("SignupScreen", "Signup Successful via JSON fallback")
                        }
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Signup")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
fun DashboardScreen(navController: NavController, username: String) {
    // Record the login time when the DashboardScreen is first composed.
    val loginTime = remember { System.currentTimeMillis() }
    var elapsedTime by remember { mutableLongStateOf(0L) }

    // Notification counts (for example purposes; replace with dynamic data as needed)
    var facebookNotifications by remember { mutableIntStateOf(5) }
    var instagramNotifications by remember { mutableIntStateOf(3) }

    // Update the elapsed time every second.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            elapsedTime = (System.currentTimeMillis() - loginTime) / 1000
        }
    }

    // Format the login time to a readable format.
    val formattedLoginTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(loginTime))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome, $username",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Login Time: $formattedLoginTime")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Logged in for: $elapsedTime seconds")
        Spacer(modifier = Modifier.height(16.dp))

        // New section for notifications
        Text(
            text = "You have $facebookNotifications Facebook notifications",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You have $instagramNotifications Instagram notifications",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                // Navigate back to main screen (simulate logout)
                navController.navigate("main") {
                    popUpTo("main") { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }
    }
}

// ---------- JSON Fallback Helper Functions ----------

private const val JSON_FILE_NAME = "Dummy_Database.json"

// Returns the JSON file; creates one if it doesn't exist.
fun getJsonFile(context: Context): File {
    val file = File(context.filesDir, JSON_FILE_NAME)
    if (!file.exists()) {
        // Create an empty JSON array as the initial content.
        file.writeText("[]")
    }
    return file
}

// Reads and returns a JSONArray of users from the JSON file.
fun readUsersFromJson(context: Context): JSONArray {
    val file = getJsonFile(context)
    return try {
        JSONArray(file.readText())
    } catch (e: Exception) {
        e.printStackTrace()
        JSONArray()
    }
}

// Writes the JSONArray of users back to the JSON file.
fun writeUsersToJson(context: Context, usersArray: JSONArray) {
    val file = getJsonFile(context)
    try {
        file.writeText(usersArray.toString())
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Validates credentials by checking if a user with the given email and password exists.
fun validateCredentials(context: Context, email: String, password: String): Boolean {
    val usersArray = readUsersFromJson(context)
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("email") == email && user.getString("password") == password) {
            return true
        }
    }
    return false
}

// Adds a new user to the JSON file with the provided username, email, and password.
fun addUser(context: Context, email: String, password: String, username: String) {
    val usersArray = readUsersFromJson(context)
    // Optionally, check if the user already exists.
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("email") == email) {
            // User already exists; handle as needed.
            Toast.makeText(context, "User already exists", Toast.LENGTH_LONG).show()
            return
        }
    }
    val newUser = JSONObject().apply {
        put("username", username)
        put("email", email)
        put("password", password)
    }
    usersArray.put(newUser)
    writeUsersToJson(context, usersArray)
}

// Retrieves the username for the given email and password from the JSON file.
// Returns null if no matching user is found.
fun getUsernameForUser(context: Context, email: String, password: String): String? {
    val usersArray = readUsersFromJson(context)
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("email") == email && user.getString("password") == password) {
            return user.optString("username", null)
        }
    }
    return null
}
