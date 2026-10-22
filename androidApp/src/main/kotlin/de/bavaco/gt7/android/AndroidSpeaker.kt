package de.bavaco.gt7.android

import android.content.Context
import android.speech.tts.TextToSpeech
import de.bavaco.gt7.ui.Speaker
import de.bavaco.gt7.ui.TipLanguage
import java.util.Locale

/** Speaks through Android's [TextToSpeech]; until the engine is ready, or if it fails to start, nothing is said. */
class AndroidSpeaker(context: Context) : Speaker {
    @Volatile private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }

    private fun locale(language: TipLanguage) = if (language == TipLanguage.DE) Locale.GERMAN else Locale.ENGLISH

    override fun voices(language: TipLanguage): List<String> {
        if (!ready) return emptyList()
        val code = locale(language).language
        return tts.voices.orEmpty().filter { it.locale.language == code && !it.isNetworkConnectionRequired }.map { it.name }.sorted()
    }

    override fun speak(text: String, language: TipLanguage, voice: String?) {
        if (!ready || tts.isSpeaking) return
        val chosen = voice?.let { name -> tts.voices.orEmpty().firstOrNull { it.name == name && it.locale.language == locale(language).language } }
        if (chosen != null) tts.voice = chosen else tts.language = locale(language)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "coach")
    }

    fun shutdown() = tts.shutdown()
}
