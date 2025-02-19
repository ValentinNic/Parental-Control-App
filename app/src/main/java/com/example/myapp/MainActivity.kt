package com.example.myapp

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Replace with your actual drawable resource name
import com.example.myapp.R

// ------------------ Data Class ------------------

data class ChildInfo(
    val username: String,
    val lastLogin: Long,
    val sessionDuration: Long? = null,
    val location: String? = null
) {
    val lastLoginTimeFormatted: String
        get() = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault()).format(Date(lastLogin))
}

// ------------------ Main Activity ------------------

class MainActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        firebaseAuth = Firebase.auth

        setContent {
            MyAppTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") { MainScreen(navController) }
                    composable("login") { LoginScreen(navController, firebaseAuth) }
                    composable("signup") { SignupScreen(navController, firebaseAuth) }
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
                // Try Firebase sign in.
                firebaseAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        val username = getUsernameForEmail(context, email) ?: email
                        if (task.isSuccessful) {
                            Toast.makeText(context, "Login Successful", Toast.LENGTH_LONG).show()
                            // If user is child, update last login.
                            if (getUserRole(context, email) == "Child") {
                                updateLastLogin(context, email)
                            }
                            navigateBasedOnRole(navController, context, email, username)
                        } else {
                            // Firebase failed; try JSON fallback.
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
                firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            addUser(
                                context,
                                email,
                                password,
                                username,
                                userRole,
                                if (userRole == "Child") linkedParentEmail else null
                            )
                            Toast.makeText(context, "Signup Successful", Toast.LENGTH_LONG).show()
                            Log.d("SignupScreen", "Signup Successful")
                        } else {
                            addUser(
                                context,
                                email,
                                password,
                                username,
                                userRole,
                                if (userRole == "Child") linkedParentEmail else null
                            )
                            Toast.makeText(context, "Signup Successful (JSON fallback)", Toast.LENGTH_LONG).show()
                            Log.d("SignupScreen", "Signup Successful via JSON fallback")
                        }
                        navigateBasedOnRole(navController, context, email, username)
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Welcome, $parentUsername",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (childInfo != null) {
            Text("Your child, ${childInfo.username}, last logged in at:", color = Color.Black)
            Text(childInfo.lastLoginTimeFormatted, color = Color.Black)
            Spacer(modifier = Modifier.height(8.dp))
            if (childInfo.sessionDuration != null) {
                Text("Child was logged in for: ${childInfo.sessionDuration} seconds", color = Color.Black)
            } else {
                Text("Child is currently logged in or session duration not recorded.", color = Color.Black)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Last known location: ${childInfo.location ?: "Unknown"}", color = Color.Black)
            Spacer(modifier = Modifier.height(16.dp))
            // Display a static image (ensure hatfield_map.png is in res/drawable)
            Image(
                painter = painterResource(id = R.drawable.hatfield_map),
                contentDescription = "Hatfield Map",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        } else {
            Text("No linked child account found.", color = Color.Black)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
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

    // Update child's location to "Hatfield, UK"
    LaunchedEffect(childEmail) {
        updateChildLocation(context, childEmail)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome, $childUsername", style = MaterialTheme.typography.headlineMedium, color = Color.Black)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Login Time: $formattedLoginTime", color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Logged in for: $elapsedTime seconds", color = Color.Black)
        Spacer(modifier = Modifier.height(24.dp))
        // Dummy Facebook Notifications
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
        // Dummy Instagram Notifications
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

// ------------------ JSON & Firebase Helper Functions ------------------

private const val JSON_FILE_NAME = "Dummy_Database.json"

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
        }
        if (userRole == "Child") {
            put("lastLogin", System.currentTimeMillis())
        }
    }
    usersArray.put(newUser)
    writeUsersToJson(context, usersArray)

    val dbRef = Firebase.database.getReference("users")
    val userMap = mutableMapOf<String, Any?>(
        "username" to username,
        "email" to email,
        "password" to password,
        "userRole" to userRole
    )
    if (userRole == "Child" && linkedParent != null) {
        userMap["linkedParent"] = linkedParent
    }
    if (userRole == "Child") {
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

    val dbRef = Firebase.database.getReference("users")
    val key = emailToKey(email)
    dbRef.child(key).child("lastLogin").setValue(currentTime).addOnCompleteListener { task ->
        if (task.isSuccessful) {
            Log.d("FirebaseDB", "Last login updated in Firebase")
        } else {
            Log.e("FirebaseDB", "Failed to update last login in Firebase: ${task.exception?.message}")
        }
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

    val dbRef = Firebase.database.getReference("users")
    val key = emailToKey(email)
    dbRef.child(key).child("sessionDuration").setValue(sessionDuration).addOnCompleteListener { task ->
        if (task.isSuccessful) {
            Log.d("FirebaseDB", "Session duration updated in Firebase")
        } else {
            Log.e("FirebaseDB", "Failed to update session duration in Firebase: ${task.exception?.message}")
        }
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

    val dbRef = Firebase.database.getReference("users")
    val key = emailToKey(email)
    dbRef.child(key).child("location").setValue(fixedLocation).addOnCompleteListener { task ->
        if (task.isSuccessful) {
            Log.d("FirebaseDB", "Child location updated in Firebase")
        } else {
            Log.e("FirebaseDB", "Failed to update child location in Firebase: ${task.exception?.message}")
        }
    }
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

fun getLinkedChildInfo(context: Context, parentEmail: String): ChildInfo? {
    val usersArray = readUsersFromJson(context)
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("userRole") == "Child" && user.optString("linkedParent") == parentEmail) {
            val username = user.getString("username")
            val lastLogin = user.optLong("lastLogin", 0L)
            val sessionDuration = if (user.has("sessionDuration")) user.optLong("sessionDuration") else null
            val location = user.optString("location", null)
            return ChildInfo(username, lastLogin, sessionDuration, location)
        }
    }
    return null
}

fun navigateBasedOnRole(navController: NavController, context: Context, email: String, username: String) {
    val userRole = getUserRole(context, email)
    Log.d("DebugRole", "Email: $email, Role: $userRole") // Debug log to verify user role.
    if (userRole == "Parent") {
        navController.navigate("parentDashboard/${Uri.encode(email)}")
    } else {
        navController.navigate("childDashboard/${Uri.encode(email)}")
    }
}
