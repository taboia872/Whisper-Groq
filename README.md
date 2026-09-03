# Whisper-Groq

Android voice input (IME + RecognitionService) powered by the **Groq Whisper API** — fast online transcription with zero on-device models.

Based on [whisperIMEplus](https://github.com/woheller69/whisperIMEplus) by woheller69 (GPLv3).

## Features

- **Input Method (IME)**: tap microphone to record, tap again to stop → transcription inserted into any text field
- **RecognitionService**: system-wide voice input
- **RecognizerIntent activity**: other apps can call it for speech-to-text
- **Glass UI**: semi-transparent panels, rounded corners, accent colors
- **Tap-to-record** (no press-and-hold), 30s max, silence detection VAD in auto mode
- **Punctuation row**: `. , ? !` + Enter + Backspace
- **Auto language detection** via whisper-large-v3 / whisper-large-v3-turbo / distil-whisper-large-v3-en
- **No model download** — audio goes to Groq API, user supplies API key

## Setup

1. Install the APK
2. Open **Whisper-Groq Settings** (launcher icon) and paste your **Groq API key** from https://console.groq.com
3. Pick a model (default: `whisper-large-v3-turbo`)
4. Enable **Whisper-Groq** as keyboard/voice input in Android settings

## Requirements

- Internet connection
- Groq API key (free tier available)

## License

GPLv3 — see LICENSE. Original: © woheller69
