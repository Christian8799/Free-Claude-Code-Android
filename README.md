# Free Claude Code Android

An Android app that runs [Free Claude Code (FCC)](https://github.com/alishahryar1/free-claude-code) on Android via [Termux](https://termux.dev).

## Overview

This app provides a native Android UI to:
- Check if Termux is installed
- Install Free Claude Code in Termux
- Start/stop the FCC server
- Open the Admin UI to configure providers
- View server logs

## Prerequisites

1. **Android 7.0+ (API 24+)**
2. **Termux** installed from [F-Droid](https://f-droid.org/packages/com.termux/) or [GitHub](https://github.com/termux/termux-app/releases)
3. **Termux:API** (optional, for better integration) from [F-Droid](https://f-droid.org/packages/com.termux.api/)

## Installation

### Option 1: Build from Source

```bash
# Clone the repository
git clone <this-repo>
cd FreeClaudeCodeAndroid

# Build debug APK
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Option 2: Download APK

Download the latest APK from the releases page (when available).

## Usage

1. **Install Termux**: If not installed, tap "Check Termux" → "Install Termux" to open the F-Droid page
2. **Install FCC**: Tap "Install FCC" - this runs `pkg install -y python uv && uv tool install free-claude-code` in Termux
3. **Start Server**: Tap "Start Server" - this runs `fcc-server` in Termux
4. **Open Admin UI**: Tap "Open Admin" to configure providers (NVIDIA NIM, OpenRouter, Groq, Ollama, etc.)
5. **Use with Claude Code**: Configure your coding agent to use `http://127.0.0.1:8080` as the API endpoint

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Android App                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ MainActivity│  │ MainViewModel│  │ TermuxManager       │ │
│  │ (UI)        │◄─►│ (State)     │◄─►│ (Termux bridge)     │ │
│  └─────────────┘  └─────────────┘  └──────────┬──────────┘ │
└─────────────────────────────────────────────────┼────────────┘
                                                  │
                    ┌─────────────────────────────┘
                    ▼
┌─────────────────────────────────────────────────────────────┐
│                      Termux (Linux env)                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  fcc-server (Python FastAPI)                        │   │
│  │  - Port 8080 (configurable)                         │   │
│  │  - Admin UI at /admin                               │   │
│  │  - API endpoints: /v1/messages, /v1/responses, etc. │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## How It Works

1. The Android app uses `Runtime.exec()` to run shell commands in Termux's environment
2. Commands like `fcc-server` are executed in the Termux shell
3. The server runs on localhost:8080 (configurable)
4. Admin UI is accessible via Android's browser or WebView
5. Other apps (like Termux Claude Code) can connect to the local proxy

## Configuration

Settings (accessible via menu → Settings):
- **Server Port**: Default 8080
- **Server Host**: Default 127.0.0.1
- **Auto-start**: Start server when app launches
- **Show Notifications**: Show server status in notification shade

## Provider Configuration

After starting the server, open Admin UI to configure providers:

1. **NVIDIA NIM** - Free tier available
2. **OpenRouter** - Multiple models
3. **Groq** - Fast inference
4. **Ollama/LM Studio** - Local models
5. **GitHub Copilot** - OAuth integration
6. **ChatGPT** - OAuth integration
7. And 50+ more providers

## Troubleshooting

### Termux not detected
- Ensure Termux is installed from F-Droid (not Play Store)
- Grant storage permission to Termux: `termux-setup-storage`

### FCC installation fails
- Run `pkg update && pkg upgrade` in Termux first
- Ensure Python 3.14+ is available (FCC requires Python 3.14.10)
- Check internet connection for downloading packages

### Server won't start
- Check if port 8080 is already in use
- View logs in the app for error details
- Try running `fcc-server` manually in Termux to see full output

### Can't access Admin UI
- Ensure server is running (green status)
- Try `http://127.0.0.1:8080/admin` in Chrome/Firefox
- Check Android's "Cleartext traffic" settings if using HTTP

## Building

```bash
# Requirements:
# - JDK 17+
# - Android SDK (API 35)
# - Gradle 8.7+

./gradlew clean assembleDebug
```

## License

MIT License - see LICENSE file

## Credits

- [Free Claude Code](https://github.com/alishahryar1/free-claude-code) by Alishahryar
- [Termux](https://termux.dev) - Linux environment for Android
- Material Design 3 components