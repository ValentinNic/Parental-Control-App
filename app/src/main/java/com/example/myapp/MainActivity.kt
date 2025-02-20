@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapp.ui.theme.MyAppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// Replace with your actual drawable resource name
import com.example.myapp.R

// ------------------ Data Class ------------------
data class ChildInfo(
    val username: String,
    val email: String,
    val lastLogin: Long,
    val sessionDuration: Long? = null,
    val location: String? = null
) {
    val lastLoginTimeFormatted: String
        get() = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault()).format(Date(lastLogin))
}

// ------------------ Constants ------------------
private const val JSON_FILE_NAME = "Dummy_Database.json"

// ------------------ Main Activity ------------------
class MainActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Copy the JSON file from assets to internal storage if it doesn't exist.
        copyAssetToInternalStorage(this, JSON_FILE_NAME)

        // Initialize Firebase Auth
        firebaseAuth = Firebase.auth

        setContent {
            MyAppTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") { MainScreen(navController) }
                    composable("login") { LoginScreen(navController, firebaseAuth) }
                    composable("signup") { SignupScreen(navController, firebaseAuth) }
                    composable("adminLogin") { AdminLoginScreen(navController) }
                    composable("adminDashboard") { AdminDashboardScreen(navController) }
                    composable("parentDashboard/{identifier}") { backStackEntry ->
                        val parentEmail = backStackEntry.arguments?.getString("identifier") ?: "Parent"
                        val parentUsername = getUsernameForEmail(applicationContext, parentEmail) ?: parentEmail
                        val childInfo = getLinkedChildInfo(applicationContext, parentEmail)
                        ParentDashboardScreen(navController, parentUsername, childInfo)
                    }
                    composable("childDashboard/{identifier}") { backStackEntry ->
                        val childEmail = backStackEntry.arguments?.getString("identifier") ?: "Child"
                        val childUsername = getUsernameForEmail(applicationContext, childEmail) ?: childEmail
                        ChildDashboardScreen(navController, childEmail, childUsername)
                    }
                }
            }
        }
    }
}

// ------------------ Helper: Network Check ------------------
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

// ------------------ Helper: Copy Asset to Internal Storage ------------------
fun copyAssetToInternalStorage(context: Context, fileName: String) {
    val file = File(context.filesDir, fileName)
    if (!file.exists()) {
        try {
            context.assets.open(fileName).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("AssetCopy", "$fileName copied to internal storage")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("AssetCopy", "Failed to copy $fileName: ${e.message}")
        }
    }
}

// ------------------ JSON & Firebase Helper Functions ------------------
fun getJsonFile(context: Context): File {
    val file = File(context.filesDir, JSON_FILE_NAME)
    if (!file.exists()) {
        file.writeText("[]")
    }
    return file
}

fun readUsersFromJson(context: Context): JSONArray {
    val file = getJsonFile(context)
    return try {
        JSONArray(file.readText())
    } catch (e: Exception) {
        e.printStackTrace()
        JSONArray()
    }
}

fun writeUsersToJson(context: Context, usersArray: JSONArray) {
    val file = getJsonFile(context)
    try {
        file.writeText(usersArray.toString())
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Helper to clear JSON fallback.
fun clearJsonFallback(context: Context) {
    writeUsersToJson(context, JSONArray())
}

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

fun emailToKey(email: String): String {
    return email.replace(".", ",")
}

fun addUser(
    context: Context,
    email: String,
    password: String,
    username: String,
    userRole: String,
    linkedParent: String? = null
) {
    val usersArray = readUsersFromJson(context)
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("email") == email) {
            Toast.makeText(context, "User already exists", Toast.LENGTH_LONG).show()
            return
        }
    }
    val newUser = JSONObject().apply {
        put("username", username)
        put("email", email)
        put("password", password)
        put("userRole", userRole)
        if (userRole == "Child" && linkedParent != null) {
            put("linkedParent", linkedParent)
            put("lastLogin", System.currentTimeMillis())
        }
    }
    usersArray.put(newUser)
    writeUsersToJson(context, usersArray)

    if (isNetworkAvailable(context)) {
        val dbRef = Firebase.database.getReference("users")
        val userMap = mutableMapOf<String, Any?>(
            "username" to username,
            "email" to email,
            "password" to password,
            "userRole" to userRole
        )
        if (userRole == "Child" && linkedParent != null) {
            userMap["linkedParent"] = linkedParent
            userMap["lastLogin"] = System.currentTimeMillis()
        }
        val key = emailToKey(email)
        dbRef.child(key).setValue(userMap).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("FirebaseDB", "User data saved to Firebase")
            } else {
                Log.e("FirebaseDB", "Failed to save user data to Firebase: ${task.exception?.message}")
            }
        }
    } else {
        Log.d("FirebaseDB", "Offline: User data saved locally only")
    }
}

fun updateLastLogin(context: Context, email: String) {
    val usersArray = readUsersFromJson(context)
    val currentTime = System.currentTimeMillis()
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("email") == email) {
            user.put("lastLogin", currentTime)
            break
        }
    }
    writeUsersToJson(context, usersArray)

    if (isNetworkAvailable(context)) {
        val dbRef = Firebase.database.getReference("users")
        val key = emailToKey(email)
        dbRef.child(key).child("lastLogin").setValue(currentTime).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("FirebaseDB", "Last login updated in Firebase")
            } else {
                Log.e("FirebaseDB", "Failed to update last login in Firebase: ${task.exception?.message}")
            }
        }
    } else {
        Log.d("FirebaseDB", "Offline: Skipping Firebase update for last login")
    }
}

fun updateSessionDuration(context: Context, email: String, sessionDuration: Long) {
    val usersArray = readUsersFromJson(context)
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("email") == email) {
            user.put("sessionDuration", sessionDuration)
            break
        }
    }
    writeUsersToJson(context, usersArray)

    if (isNetworkAvailable(context)) {
        val dbRef = Firebase.database.getReference("users")
        val key = emailToKey(email)
        dbRef.child(key).child("sessionDuration").setValue(sessionDuration).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("FirebaseDB", "Session duration updated in Firebase")
            } else {
                Log.e("FirebaseDB", "Failed to update session duration in Firebase: ${task.exception?.message}")
            }
        }
    } else {
        Log.d("FirebaseDB", "Offline: Skipping Firebase update for session duration")
    }
}

fun updateChildLocation(context: Context, email: String) {
    val fixedLocation = "Hatfield, UK"
    val usersArray = readUsersFromJson(context)
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("email") == email) {
            user.put("location", fixedLocation)
            break
        }
    }
    writeUsersToJson(context, usersArray)

    if (isNetworkAvailable(context)) {
        val dbRef = Firebase.database.getReference("users")
        val key = emailToKey(email)
        dbRef.child(key).child("location").setValue(fixedLocation).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("FirebaseDB", "Child location updated in Firebase")
            } else {
                Log.e("FirebaseDB", "Failed to update child location in Firebase: ${task.exception?.message}")
            }
        }
    } else {
        Log.d("FirebaseDB", "Offline: Skipping Firebase update for child location")
    }
}

// New helper: Update the child's notifications lock status.
fun updateChildNotificationsLock(context: Context, email: String, locked: Boolean) {
    val usersArray = readUsersFromJson(context)
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("email") == email && user.getString("userRole") == "Child") {
            user.put("notificationsLocked", locked)
            break
        }
    }
    writeUsersToJson(context, usersArray)

    if (isNetworkAvailable(context)) {
        val dbRef = Firebase.database.getReference("users")
        val key = emailToKey(email)
        dbRef.child(key).child("notificationsLocked").setValue(locked).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("FirebaseDB", "Child notifications lock updated in Firebase")
            } else {
                Log.e("FirebaseDB", "Failed to update child notifications lock in Firebase: ${task.exception?.message}")
            }
        }
    }
}

// New helper: Check if a child's notifications are locked.
fun isChildNotificationsLocked(context: Context, email: String): Boolean {
    val usersArray = readUsersFromJson(context)
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("email") == email && user.getString("userRole") == "Child") {
            return user.optBoolean("notificationsLocked", false)
        }
    }
    return false
}

fun getUserRole(context: Context, email: String): String? {
    val usersArray = readUsersFromJson(context)
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("email") == email) {
            return user.getString("userRole")
        }
    }
    return null
}

fun getUsernameForEmail(context: Context, email: String): String? {
    val usersArray = readUsersFromJson(context)
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("email") == email) {
            return user.optString("username", null)
        }
    }
    return null
}

// Updated to also include the child's email.
fun getLinkedChildInfo(context: Context, parentEmail: String): ChildInfo? {
    val usersArray = readUsersFromJson(context)
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("userRole") == "Child" && user.optString("linkedParent") == parentEmail) {
            val username = user.getString("username")
            val email = user.getString("email")
            val lastLogin = user.optLong("lastLogin", 0L)
            val sessionDuration = if (user.has("sessionDuration")) user.optLong("sessionDuration") else null
            val location = user.optString("location", null)
            return ChildInfo(username, email, lastLogin, sessionDuration, location)
        }
    }
    return null
}

fun navigateBasedOnRole(navController: NavController, context: Context, email: String, username: String) {
    val userRole = getUserRole(context, email)
    Log.d("DebugRole", "Email: $email, Role: $userRole")
    if (userRole == "Parent") {
        navController.navigate("parentDashboard/${Uri.encode(email)}")
    } else {
        navController.navigate("childDashboard/${Uri.encode(email)}")
    }
}

// ------------------ Admin Helper Functions ------------------
fun deleteUser(context: Context, email: String) {
    val usersArray = readUsersFromJson(context)
    val newArray = JSONArray()
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("email") != email) {
            newArray.put(user)
        }
    }
    writeUsersToJson(context, newArray)

    if (isNetworkAvailable(context)) {
        val dbRef = Firebase.database.getReference("users")
        dbRef.child(emailToKey(email)).removeValue().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("FirebaseDB", "User $email deleted from Firebase")
            } else {
                Log.e("FirebaseDB", "Failed to delete user from Firebase: ${task.exception?.message}")
            }
        }
    }
}

fun updateUser(context: Context, originalEmail: String, updatedUser: JSONObject) {
    val usersArray = readUsersFromJson(context)
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("email") == originalEmail) {
            usersArray.put(i, updatedUser)
            break
        }
    }
    writeUsersToJson(context, usersArray)

    if (isNetworkAvailable(context)) {
        val userMap = mutableMapOf<String, Any?>(
            "username" to updatedUser.optString("username"),
            "email" to updatedUser.optString("email"),
            "password" to updatedUser.optString("password"),
            "userRole" to updatedUser.optString("userRole")
        )
        if (updatedUser.optString("userRole") == "Child") {
            userMap["linkedParent"] = updatedUser.optString("linkedParent", null)
            userMap["lastLogin"] = updatedUser.optLong("lastLogin", System.currentTimeMillis())
            if (updatedUser.has("sessionDuration"))
                userMap["sessionDuration"] = updatedUser.optLong("sessionDuration")
        }
        val dbRef = Firebase.database.getReference("users")
        dbRef.child(emailToKey(updatedUser.getString("email"))).setValue(userMap)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FirebaseDB", "User updated in Firebase")
                } else {
                    Log.e("FirebaseDB", "Failed to update user in Firebase: ${task.exception?.message}")
                }
            }
    }
}

fun loadUsers(context: Context): List<JSONObject> {
    val usersArray = readUsersFromJson(context)
    val list = mutableListOf<JSONObject>()
    for (i in 0 until usersArray.length()) {
        list.add(usersArray.getJSONObject(i))
    }
    return list
}

// ------------------ Composables ------------------
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
            "Parental Control App",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Login option
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { navController.navigate("login") }) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Login",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Text("Login", color = Color.Black)
            }
            // Signup option
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { navController.navigate("signup") }) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Signup",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Text("Signup", color = Color.Black)
            }
            // Admin option
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { navController.navigate("adminLogin") }) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Admin",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Text("Admin", color = Color.Black)
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
        Text("Login", style = MaterialTheme.typography.headlineMedium, color = Color.Black)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email", color = Color.Black) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password", color = Color.Black) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(context, "Please enter a valid email", Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (password.length < 7) {
                    Toast.makeText(context, "Password must be at least 7 characters", Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (!isNetworkAvailable(context)) {
                    if (validateCredentials(context, email, password)) {
                        Toast.makeText(context, "Login Successful (JSON fallback - offline)", Toast.LENGTH_LONG).show()
                        if (getUserRole(context, email) == "Child") {
                            updateLastLogin(context, email)
                        }
                        val username = getUsernameForEmail(context, email) ?: email
                        navigateBasedOnRole(navController, context, email, username)
                    } else {
                        Toast.makeText(context, "Login Failed: No network and invalid credentials", Toast.LENGTH_LONG).show()
                    }
                } else {
                    firebaseAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            val username = getUsernameForEmail(context, email) ?: email
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Login Successful", Toast.LENGTH_LONG).show()
                                if (getUserRole(context, email) == "Child") {
                                    updateLastLogin(context, email)
                                }
                                navigateBasedOnRole(navController, context, email, username)
                            } else {
                                if (validateCredentials(context, email, password)) {
                                    Toast.makeText(context, "Login Successful (JSON fallback)", Toast.LENGTH_LONG).show()
                                    if (getUserRole(context, email) == "Child") {
                                        updateLastLogin(context, email)
                                    }
                                    navigateBasedOnRole(navController, context, email, username)
                                } else {
                                    Toast.makeText(context, "Login Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login", color = Color.Black)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Back", color = Color.Black)
        }
    }
}

@Composable
fun SignupScreen(navController: NavController, firebaseAuth: FirebaseAuth) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var userRole by remember { mutableStateOf("Parent") }
    var linkedParentEmail by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Signup", style = MaterialTheme.typography.headlineMedium, color = Color.Black)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username", color = Color.Black) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email", color = Color.Black) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password", color = Color.Black) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Select User Role", style = MaterialTheme.typography.titleMedium, color = Color.Black)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = userRole == "Parent", onClick = { userRole = "Parent" })
            Text("Parent", color = Color.Black)
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(selected = userRole == "Child", onClick = { userRole = "Child" })
            Text("Child", color = Color.Black)
        }
        if (userRole == "Child") {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = linkedParentEmail,
                onValueChange = { linkedParentEmail = it },
                label = { Text("Parent Email", color = Color.Black) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(context, "Please enter a valid email", Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (password.length < 7) {
                    Toast.makeText(context, "Password must be at least 7 characters", Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (userRole == "Child" && !Patterns.EMAIL_ADDRESS.matcher(linkedParentEmail).matches()) {
                    Toast.makeText(context, "Please enter a valid parent email", Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (!isNetworkAvailable(context)) {
                    addUser(context, email, password, username, userRole, if (userRole == "Child") linkedParentEmail else null)
                    Toast.makeText(context, "Signup Successful (JSON fallback - offline)", Toast.LENGTH_LONG).show()
                    val uname = getUsernameForEmail(context, email) ?: email
                    navigateBasedOnRole(navController, context, email, uname)
                } else {
                    firebaseAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                addUser(context, email, password, username, userRole, if (userRole == "Child") linkedParentEmail else null)
                                Toast.makeText(context, "Signup Successful", Toast.LENGTH_LONG).show()
                                Log.d("SignupScreen", "Signup Successful")
                            } else {
                                addUser(context, email, password, username, userRole, if (userRole == "Child") linkedParentEmail else null)
                                Toast.makeText(context, "Signup Successful (JSON fallback)", Toast.LENGTH_LONG).show()
                                Log.d("SignupScreen", "Signup Successful via JSON fallback")
                            }
                            val uname = getUsernameForEmail(context, email) ?: email
                            navigateBasedOnRole(navController, context, email, uname)
                        }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Signup", color = Color.Black)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Back", color = Color.Black)
        }
    }
}

@Composable
fun ParentDashboardScreen(navController: NavController, parentUsername: String, childInfo: ChildInfo?) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var notificationsLocked by remember {
        mutableStateOf(childInfo?.let { isChildNotificationsLocked(context, it.email) } ?: false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Welcome, $parentUsername",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (childInfo != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Child: ${childInfo.username}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Last Login: ${childInfo.lastLoginTimeFormatted}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (childInfo.sessionDuration != null) {
                                Text(
                                    "Session Duration: ${childInfo.sessionDuration} seconds",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Text(
                                    "Session duration not recorded.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)

                            Text(
                                "Lock/Unlock child's Facebook and Instagram account",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Unlock",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Start,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = notificationsLocked,
                                    onCheckedChange = { checked ->
                                        updateChildNotificationsLock(context, childInfo.email, checked)
                                        notificationsLocked = checked
                                        Toast.makeText(
                                            context,
                                            if (checked) "Notifications locked" else "Notifications unlocked",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        uncheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                )
                                Text(
                                    "Lock",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.End,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                "Last known location: ${childInfo.location ?: "Unknown"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Display the map image directly without a Card.
                    Image(
                        painter = painterResource(id = R.drawable.hatfield_map),
                        contentDescription = "Hatfield Map",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Text(
                            "No linked child account found.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = {
                        navController.navigate("main") {
                            popUpTo("main") { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    )
}

@Composable
fun ChildDashboardScreen(navController: NavController, childEmail: String, childUsername: String) {
    val context = LocalContext.current
    val loginTime = remember { System.currentTimeMillis() }
    var elapsedTime by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            elapsedTime = (System.currentTimeMillis() - loginTime) / 1000
        }
    }
    val formattedLoginTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(loginTime))
    LaunchedEffect(childEmail) {
        updateChildLocation(context, childEmail)
    }
    val notificationsLocked = isChildNotificationsLocked(context, childEmail)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Welcome, $childUsername")
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Login Time: $formattedLoginTime", color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Logged in for: $elapsedTime seconds", color = Color.Black)
                Spacer(modifier = Modifier.height(24.dp))
                if (notificationsLocked) {
                    Text("Facebook", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Your parent has locked your Facebook account", style = MaterialTheme.typography.bodyMedium, color = Color.Red)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Instagram", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Your parent has locked your Instagram account", style = MaterialTheme.typography.bodyMedium, color = Color.Red)
                } else {
                    Text("Facebook Notifications", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("• You have a new friend request!", color = Color.Black)
                            Text("• Your post received 5 new likes.", color = Color.Black)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Instagram Notifications", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("• New comment on your photo.", color = Color.Black)
                            Text("• 3 new followers joined.", color = Color.Black)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        updateSessionDuration(context, childEmail, elapsedTime)
                        navController.navigate("main") {
                            popUpTo("main") { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout", color = Color.Black)
                }
            }
        }
    )
}

@Composable
fun AdminLoginScreen(navController: NavController) {
    val context = LocalContext.current
    var adminEmail by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Admin Login", style = MaterialTheme.typography.headlineMedium, color = Color.Black)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = adminEmail,
            onValueChange = { adminEmail = it },
            label = { Text("Admin Email", color = Color.Black) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = adminPassword,
            onValueChange = { adminPassword = it },
            label = { Text("Password", color = Color.Black) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (adminEmail == "Admin@Admin" && adminPassword == "Admin") {
                    Toast.makeText(context, "Admin Login Successful", Toast.LENGTH_SHORT).show()
                    navController.navigate("adminDashboard") {
                        popUpTo("main") { inclusive = true }
                    }
                } else {
                    Toast.makeText(context, "Invalid Admin Credentials", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login", color = Color.Black)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back", color = Color.Black)
        }
    }
}

@Composable
fun AddEditUserDialog(
    initialUser: JSONObject?,
    onDismiss: () -> Unit,
    onSubmit: (JSONObject) -> Unit
) {
    var username by remember { mutableStateOf(initialUser?.optString("username") ?: "") }
    var email by remember { mutableStateOf(initialUser?.optString("email") ?: "") }
    var password by remember { mutableStateOf(initialUser?.optString("password") ?: "") }
    var userRole by remember { mutableStateOf(initialUser?.optString("userRole") ?: "Parent") }
    var linkedParent by remember { mutableStateOf(initialUser?.optString("linkedParent") ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (initialUser != null) "Edit User" else "Add User") },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select User Role")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = userRole == "Parent", onClick = { userRole = "Parent" })
                    Text("Parent")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = userRole == "Child", onClick = { userRole = "Child" })
                    Text("Child")
                }
                if (userRole == "Child") {
                    OutlinedTextField(
                        value = linkedParent,
                        onValueChange = { linkedParent = it },
                        label = { Text("Linked Parent Email") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val newUser = JSONObject().apply {
                    put("username", username)
                    put("email", email)
                    put("password", password)
                    put("userRole", userRole)
                    if (userRole == "Child") {
                        put("linkedParent", linkedParent)
                        put("lastLogin", System.currentTimeMillis())
                    }
                }
                onSubmit(newUser)
            }) {
                Text("Submit")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AdminDashboardScreen(navController: NavController) {
    val context = LocalContext.current
    var userList by remember { mutableStateOf(loadUsers(context)) }
    var showDialog by remember { mutableStateOf(false) }
    var editingUser: JSONObject? by remember { mutableStateOf(null) }
    fun refreshUserList() {
        userList = loadUsers(context)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Admin Dashboard", style = MaterialTheme.typography.headlineMedium, color = Color.Black)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(userList) { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Username: ${user.optString("username", "N/A")}", color = Color.Black)
                            Text("Email: ${user.optString("email", "N/A")}", color = Color.Black)
                            Text("Role: ${user.optString("userRole", "N/A")}", color = Color.Black)
                            if (user.optString("userRole") == "Child") {
                                Text("Linked Parent: ${user.optString("linkedParent", "N/A")}", color = Color.Black)
                            }
                        }
                        Row {
                            IconButton(onClick = {
                                editingUser = user
                                showDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit User",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = {
                                deleteUser(context, user.optString("email"))
                                refreshUserList()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete User",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                editingUser = null
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add New User", color = Color.Black)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                clearJsonFallback(context)
                refreshUserList()
                Toast.makeText(context, "All JSON fallback content deleted", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete All JSON Fallback Data", color = Color.Black)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { navController.navigate("main") { popUpTo("main") { inclusive = true } } },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout", color = Color.Black)
        }
    }
    if (showDialog) {
        AddEditUserDialog(
            initialUser = editingUser,
            onDismiss = { showDialog = false },
            onSubmit = { newUser ->
                if (editingUser != null) {
                    updateUser(context, editingUser!!.getString("email"), newUser)
                } else {
                    val usersArray = readUsersFromJson(context)
                    var exists = false
                    for (i in 0 until usersArray.length()) {
                        if (usersArray.getJSONObject(i).getString("email") == newUser.getString("email")) {
                            exists = true
                            break
                        }
                    }
                    if (!exists) {
                        usersArray.put(newUser)
                        writeUsersToJson(context, usersArray)
                        if (isNetworkAvailable(context)) {
                            val dbRef = Firebase.database.getReference("users")
                            val userMap = mutableMapOf<String, Any?>(
                                "username" to newUser.optString("username"),
                                "email" to newUser.optString("email"),
                                "password" to newUser.optString("password"),
                                "userRole" to newUser.optString("userRole")
                            )
                            if (newUser.optString("userRole") == "Child") {
                                userMap["linkedParent"] = newUser.optString("linkedParent", null)
                                userMap["lastLogin"] = newUser.optLong("lastLogin", System.currentTimeMillis())
                            }
                            dbRef.child(emailToKey(newUser.getString("email"))).setValue(userMap)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Log.d("FirebaseDB", "User added to Firebase")
                                    } else {
                                        Log.e("FirebaseDB", "Failed to add user to Firebase: ${task.exception?.message}")
                                    }
                                }
                        }
                    } else {
                        Toast.makeText(context, "User with email ${newUser.getString("email")} already exists", Toast.LENGTH_SHORT).show()
                    }
                }
                refreshUserList()
                showDialog = false
            }
        )
    }
}
