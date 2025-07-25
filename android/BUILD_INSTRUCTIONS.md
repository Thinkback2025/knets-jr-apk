# Knets Jr Android APK - Build Instructions

## Overview

This native Android APK version of Knets Jr provides actual device locking capabilities using Android's Device Policy Manager. When installed on a child's device, it can physically lock/unlock the device based on schedules created in the parent dashboard.

## Prerequisites

1. **Android Studio**: Download and install Android Studio (latest version)
2. **Android SDK**: API Level 21+ (Android 5.0 Lollipop or higher)
3. **Java/Kotlin**: Included with Android Studio
4. **Physical Android Device**: For testing device admin features (emulator has limitations)

## Build Steps

### 1. Open Project in Android Studio

```bash
# Navigate to the android directory
cd android

# Open in Android Studio
# File → Open → Select the 'android' folder
```

### 2. Configure API Base URL

Edit `app/src/main/java/com/knets/jr/api/RetrofitClient.kt`:

```kotlin
private const val BASE_URL = "https://your-replit-domain.replit.app/"
```

Replace with your actual Replit app domain.

### 3. Build the APK

#### Option A: Android Studio GUI
1. Select `Build → Build Bundle(s) / APK(s) → Build APK(s)`
2. Wait for build to complete
3. APK will be located in `app/build/outputs/apk/debug/app-debug.apk`

#### Option B: Command Line
```bash
# Navigate to android directory
cd android

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing)
./gradlew assembleRelease
```

### 4. Install on Device

#### Using ADB (Android Debug Bridge):
```bash
# Enable USB Debugging on your Android device first
# Settings → Developer Options → USB Debugging

# Install the APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### Using Android Studio:
1. Connect device via USB
2. Click "Run" button in Android Studio
3. Select your device from the list

## Device Setup Process

### 1. Grant Device Admin Permissions

When you first open Knets Jr:

1. **Enable Device Admin**: Tap "Enable Device Admin" button
2. **System Prompt**: Android will show a security prompt
3. **Grant Permission**: Tap "Activate" to grant device administrator privileges
4. **Required for Locking**: This permission is essential for device locking functionality

### 2. Register Device

1. **Tap "Register Device"**: Fill in child's name and parent's phone number
2. **Device Registration**: App automatically detects device IMEI and model
3. **Server Communication**: Registers device with Knets backend
4. **Success Confirmation**: You'll see "Device registered successfully"

### 3. Verify Functionality

1. **Test Lock**: Use "Test Device Lock" button to verify locking works
2. **Check Status**: All indicators should show green checkmarks
3. **Service Running**: Background service starts automatically

## Features

### 🔒 **Actual Device Locking**
- Uses Android Device Policy Manager
- Physically locks the device screen
- Cannot be bypassed by child users
- Immediate lock/unlock response

### 📅 **Schedule Enforcement**
- Monitors schedules every 30 seconds
- Automatically locks during restricted hours
- Supports overnight schedules (e.g., 10 PM - 6 AM)
- Day-of-week validation

### 🔄 **Real-time Communication**
- Sends heartbeat signals to parent dashboard
- Reports device status (locked/unlocked, battery level)
- Receives remote lock/unlock commands
- Activity logging for all actions

### 🛡️ **Background Protection**
- Runs as foreground service
- Auto-starts after device reboot
- Persistent notification (cannot be disabled)
- Admin permission prevents uninstallation

## Troubleshooting

### Issue: Device Admin Not Working
**Solution**: 
- Go to Settings → Security → Device Administrators
- Ensure "Knets Jr" is enabled
- If missing, reinstall app and re-grant permissions

### Issue: App Not Locking Device
**Check**:
1. Device admin permissions granted
2. App is registered with server
3. Network connectivity
4. Check app logs for errors

### Issue: Service Stops Running
**Solution**:
- Disable battery optimization for Knets Jr
- Settings → Apps → Knets Jr → Battery → Don't optimize
- Ensure "Auto-start" permission is granted (if available)

### Issue: Cannot Connect to Server
**Fix**:
1. Update API base URL in RetrofitClient.kt
2. Ensure Replit app is running
3. Check network connectivity
4. Verify firewall settings

## Security Notes

### Device Administrator Privileges
- **Required for functionality**: Cannot lock device without these permissions
- **High security level**: Equivalent to enterprise device management
- **Cannot be bypassed**: Child cannot disable without parent knowledge
- **Uninstall protection**: App cannot be uninstalled while admin is active

### Network Security
- **HTTPS only**: All communication encrypted
- **IMEI-based auth**: Device identification via IMEI
- **No personal data**: Only device status and scheduling data transmitted
- **Local storage**: Minimal data stored on device

## Parent Dashboard Integration

Once installed and registered:

1. **Device appears in dashboard**: Shows as "online" in parent web interface
2. **Real-time status**: Lock status, battery, last seen updates
3. **Remote control**: Lock/unlock from dashboard works immediately
4. **Schedule compliance**: Device automatically follows parent-created schedules

## Production Deployment

For production use:

1. **Sign APK**: Create release build with proper signing key
2. **Google Play**: Consider publishing to Play Store for easier distribution
3. **Custom domain**: Use your own domain instead of Replit URLs
4. **Error tracking**: Implement crash reporting (Firebase Crashlytics)
5. **Updates**: Implement automatic update mechanism

## Architecture

```
┌─────────────────┐    HTTPS/REST API    ┌──────────────────┐
│   Parent Web    │ ←──────────────────→ │     Backend      │
│   Dashboard     │                      │   (Replit App)   │
└─────────────────┘                      └──────────────────┘
                                                   ↕
                                           HTTPS/REST API
                                                   ↕
┌─────────────────┐                      ┌──────────────────┐
│   Knets Jr      │                      │  Device Policy   │
│  Android APK    │ ←──────────────────→ │    Manager       │
│ (Child Device)  │    System API Call   │ (Android System) │
└─────────────────┘                      └──────────────────┘
```

The Android APK bridges the gap between your web-based parent dashboard and actual device control through Android's system-level Device Policy Manager.

## Support

For technical issues:
- Check Android Studio logs
- Enable verbose logging in app
- Test on multiple Android versions
- Verify device admin permissions