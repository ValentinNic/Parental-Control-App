# Parental Control App

A simple Android application built with Jetpack Compose that demonstrates Firebase Authentication with a JSON fallback.

## Requirements

- Android Studio (Arctic Fox or later recommended)
- Android device or emulator running Android 5.0 (API level 21) or above
- Kotlin 1.8.0 or later
- Jetpack Compose (latest stable version)
- Firebase Authentication (with proper Firebase project setup)

## Setup & Installation

1. **Clone the Repository:**

   ```bash
   git clone https://github.com/yourusername/parental-control-app.git
   cd parental-control-app
  ```
2. **Open in Android Studio:**

Open the project in Android Studio. The project is configured with Gradle and uses a version catalog for dependency management.

3.** Configure Firebase:**

Follow the Firebase documentation to add your google-services.json file to the app directory.
Enable Email/Password authentication in the Firebase Console.

4. **Build and Run:**

Build the project and run it on your device or emulator.

## Usage

Launch the app.

**Choose Login or Signup.**
Enter your credentials. If Firebase is unavailable, the JSON fallback will be used for authentication.

**Dashboard:**

After logging in, you'll be greeted on the dashboard with your login time and session duration.
Tap the blinking notifications for Facebook or Instagram to see dummy Toast messages simulating clickable links.

**Logout:**

Tap the Logout button to return to the main screen.
