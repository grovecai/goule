package com.goule.prototype

// Battery exemption cannot establish the state of a manufacturer's background control.
// Confirmation records the user's check, not a live reading of that control.
internal fun backgroundReady(restricted: Boolean, userConfirmed: Boolean): Boolean =
    !restricted && userConfirmed

// Old confirmations used ambiguous wording and could coexist with automatic false positives.
internal const val BACKGROUND_CONFIRMATION_KEY = "background_full_allow_confirmed_v2"
