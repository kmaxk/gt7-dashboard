package de.bavaco.gt7.ui

/** Reads live tips aloud; an announcement that arrives while another is still playing is dropped. */
interface Speaker {
    /** Names of the voices the platform offers for [language]. */
    fun voices(language: TipLanguage): List<String>

    /** Speaks with the voice named [voice], or the platform's default for [language] if null or unknown. */
    fun speak(text: String, language: TipLanguage, voice: String?)
}

const val SPEECH_SETTING = "liveSpeech"

fun speechVoiceSetting(language: TipLanguage) = "speechVoice${language.name}"

/** What the settings screen shows of the voice output for the current tip language. */
class SpeechSettings(val enabled: Boolean, val voices: List<String>, val voice: String?)
