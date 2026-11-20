package com.deniz.eda.core

/**
 * Shared microphone selection preference lives in Settings.preferredMicKey.
 * Android's SpeechRecognizer does not expose a public API for forcing an
 * AudioDeviceInfo, so this preference is applied to the explicit AudioRecord
 * diagnostic/test capture. Normal SpeechRecognizer routing remains Android-managed.
 */
