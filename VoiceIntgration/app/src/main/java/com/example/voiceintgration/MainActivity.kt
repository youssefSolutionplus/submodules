package com.example.voiceintgration

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity(), RecognitionListener {
    private lateinit var btnStartRecording: ImageButton
    private lateinit var btnStopRecording: ImageButton
    private lateinit var tvResult: TextView
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var outputFilePath: String
    private lateinit var speechRecognizer: SpeechRecognizer

    @RequiresApi(Build.VERSION_CODES.O)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startRecording()
                startSpeechRecognition()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION
            )
        }

        btnStartRecording = findViewById(R.id.btnStartRecording)
        btnStopRecording = findViewById(R.id.btnStopRecording)
        tvResult = findViewById(R.id.tvResult)

        btnStartRecording.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startRecording()
                startSpeechRecognition()
                btnStartRecording.isEnabled = false
                btnStopRecording.visibility = View.VISIBLE
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        btnStopRecording.setOnClickListener {
            stopRecording()
            stopSpeechRecognition()
            btnStartRecording.isEnabled = true
            btnStopRecording.visibility = View.GONE
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startRecording() {
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val outputFile = File(outputDir, "snaptube_recording.3gp")
        outputFilePath = outputFile.absolutePath

        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile)
                prepare()
                start()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder.stop()
            mediaRecorder.release()
            uploadFile(outputFilePath)
        } catch (e: RuntimeException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to stop recording: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun uploadFile(filePath: String) {
        val file = File(filePath)
        val requestBody = file.asRequestBody("audio/*".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://api-inference.huggingface.co/models/tarteel-ai/whisper-base-ar-quran")
            .post(requestBody).build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread { tvResult.text = "Failed to upload file: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                runOnUiThread { tvResult.text = "API Response: $responseData" }
            }
        })
    }

    private fun startSpeechRecognition() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
        speechRecognizer.startListening(intent)
    }

    private fun stopSpeechRecognition() {
        speechRecognizer.stopListening()
        speechRecognizer.destroy()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Int) {
        Toast.makeText(this, "Speech recognition error: $error", Toast.LENGTH_SHORT).show()
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.let {
            val text = it[0]
            tvResult.text = text
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }
}