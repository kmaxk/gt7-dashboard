package de.bavaco.gt7.desktop

import de.bavaco.gt7.ui.Speaker
import de.bavaco.gt7.ui.TipLanguage
import java.io.IOException

private val NOVELTY = setOf(
    "Bad News", "Bahh", "Bells", "Boing", "Bubbles", "Cellos", "Good News", "Jester", "Organ", "Superstar", "Trinoids", "Whisper", "Wobble", "Zarvox",
)
private val VOICE_LINE = Regex("""^(.+?)\s+([a-z]{2})_[A-Z]{2}\s+#""")

/** Speaks through macOS `say`; without it (other systems) the call silently does nothing. */
class MacSpeaker : Speaker {
    private var current: Process? = null

    private val installed: List<Pair<String, String>> by lazy {
        try {
            val out = ProcessBuilder("say", "-v", "?").redirectErrorStream(true).start().inputStream.bufferedReader().readText()
            out.lines().mapNotNull { VOICE_LINE.find(it)?.destructured }
                .map { (name, lang) -> name to lang }
                .filter { (name, _) -> name.substringBefore(" (") !in NOVELTY }
                .distinct()
        } catch (e: IOException) {
            emptyList()
        }
    }

    override fun voices(language: TipLanguage): List<String> {
        val code = if (language == TipLanguage.DE) "de" else "en"
        return installed.filter { it.second == code }.map { it.first }
    }

    @Synchronized
    override fun speak(text: String, language: TipLanguage, voice: String?) {
        if (current?.isAlive == true) return
        val name = voice?.takeIf { it in voices(language) } ?: if (language == TipLanguage.DE) "Anna" else "Samantha"
        current = try {
            ProcessBuilder("say", "-v", name, text).start()
        } catch (e: IOException) {
            println("[GT7] speech failed: $e")
            null
        }
    }
}
