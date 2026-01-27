package com.example.aiagentchat.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SpeechRecognizerManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
    
    fun startListening(): Flow<SpeechResult> = callbackFlow {
        if (!isAvailable()) {
            trySend(SpeechResult.Error("Speech recognition is not available"))
            close()
            return@callbackFlow
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        
        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechResult.Listening)
            }
            
            override fun onBeginningOfSpeech() {
                trySend(SpeechResult.Speaking)
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                trySend(SpeechResult.AudioLevel(rmsdB))
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
            }
            
            override fun onEndOfSpeech() {
                trySend(SpeechResult.Processing)
            }
            
            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer service is busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error: $error"
                }
                trySend(SpeechResult.Error(errorMessage))
                close()
            }
            
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidenceScores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                
                if (matches != null && matches.isNotEmpty()) {
                    val bestMatch = matches[0]
                    val confidence = confidenceScores?.getOrNull(0) ?: 0f
                    trySend(SpeechResult.Success(bestMatch, confidence))
                } else {
                    trySend(SpeechResult.Error("No results found"))
                }
                close()
            }
            
            override fun onPartialResults(partialResults: Bundle) {
                val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    trySend(SpeechResult.PartialResult(matches[0]))
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
            }
        }
        
        speechRecognizer?.setRecognitionListener(recognitionListener)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        speechRecognizer?.startListening(intent)
        
        awaitClose {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }
    
    fun stopListening() {
        speechRecognizer?.stopListening()
    }
    
    fun cancel() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}

sealed class SpeechResult {
    data object Listening : SpeechResult()
    data object Speaking : SpeechResult()
    data class AudioLevel(val rmsdB: Float) : SpeechResult()
    data object Processing : SpeechResult()
    data class PartialResult(val text: String) : SpeechResult()
    data class Success(val text: String, val confidence: Float) : SpeechResult()
    data class Error(val message: String) : SpeechResult()
}
