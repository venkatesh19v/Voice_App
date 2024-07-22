package com.example.local_bhasha;

// Working java code combined top bar and voice recorder

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.content.Intent;
import android.graphics.Color;
import android.view.Menu;
import android.view.MenuItem;

import android.widget.ArrayAdapter;
import android.widget.Spinner;


public class HomeActivity extends AppCompatActivity {
    private static final int MICROPHONE_PERMISSION_CODE = 200;
    private static final int INTERNET_PERMISSION_CODE = 201;
    private static final int STORAGE_PERMISSION_CODE = 202;
    MediaRecorder mediaRecorder;
    MediaPlayer recordedMediaPlayer;
    MediaPlayer ttsMediaPlayer;
    boolean isRecording = false;
    boolean isTtsPlaying = false;
    private static final String API_URL = "http://121.242.232.220:5002/translate_audio";
    private TextView tvTargetLanguage;
    private TextView tvSourceLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setContentView(R.layout.activity_home);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Local Bhasha");
        toolbar.setTitleTextColor(Color.WHITE);
        Objects.requireNonNull(toolbar.getNavigationIcon()).setTint(Color.WHITE);
        tvTargetLanguage = (TextView) findViewById(R.id.tvTargetLanguage);
        tvSourceLanguage = (TextView) findViewById(R.id.tvSourceLanguage);

        if (isMicrophonePresent()) {
            getMicrophonePermission();
        }
        if (!isInternetPermissionGranted()) {
            getInternetPermission();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            getStoragePermissions();
        }
    }

    private boolean isInternetPermissionGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED;
    }
    private void getInternetPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, INTERNET_PERMISSION_CODE);
        }
    }
    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private boolean isMicrophonePresent() {
        return this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
    }

    private void getMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_PERMISSION_CODE);
        }
    }

    private void getStoragePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_DENIED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, STORAGE_PERMISSION_CODE);
        }
    }

    public void btnRecordPressed(View v) {
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setOutputFile(getRecordingFilePath());
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("RecordingError", "Error starting recording", e);
        }
    }

    private void stopRecording() {
        mediaRecorder.stop();
        mediaRecorder.release();
        mediaRecorder = null;
        isRecording = false;
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
        String mp3FilePath = convertToMP3(getRecordingFilePath());
        if (mp3FilePath != null) {
            String base64Audio = encodeAudioToBase64(mp3FilePath);
            if (base64Audio != null && !base64Audio.isEmpty()) {
                Toast.makeText(this, "Sending Request", Toast.LENGTH_SHORT).show();
                if (isNetworkConnected()) {
                    sendTranslateRequestOkHttp(base64Audio);
                } else {
                    Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Error encoding audio to Base64", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Error converting audio to MP3", Toast.LENGTH_SHORT).show();
        }
    }

    public void btnPlayPressed(View v) {
        try {
            if (recordedMediaPlayer != null && recordedMediaPlayer.isPlaying()) {
                recordedMediaPlayer.stop();
                recordedMediaPlayer.release();
                recordedMediaPlayer = null;
            }
            recordedMediaPlayer = new MediaPlayer();
            recordedMediaPlayer.setDataSource(getRecordingFilePath());
            recordedMediaPlayer.prepare();
            recordedMediaPlayer.start();
            Toast.makeText(this, "Playing recorded audio", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getRecordingFilePath() {
        File musicDirectory = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (musicDirectory != null && !musicDirectory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            musicDirectory.mkdirs();
        }
        File file = new File(musicDirectory, "TestRecordingFile.3gp");
        return file.getPath();
    }

    private String convertToMP3(String filePath) {
        String mp3FilePath = filePath.replace(".3gp", ".mp3");
        String[] cmd = {"-y", "-i", filePath, mp3FilePath};
        int rc = FFmpeg.execute(cmd);
        if (rc == Config.RETURN_CODE_SUCCESS) {
            Log.i(Config.TAG, "Command execution completed successfully.");
            return mp3FilePath;
        } else {
            Log.e(Config.TAG, String.format("Command execution failed with rc=%d and the output below.", rc));
            Log.e(Config.TAG, Config.getLastCommandOutput());
            return null;
        }
    }

    private String encodeAudioToBase64(String filePath) {
        try {
            File file = new File(filePath);
            FileInputStream inputStream = new FileInputStream(file);
            byte[] audioBytes = new byte[(int) file.length()];
            //noinspection ResultOfMethodCallIgnored
            inputStream.read(audioBytes);
            inputStream.close();
            return Base64.encodeToString(audioBytes, Base64.NO_WRAP);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Base64EncodeError", "Error encoding audio to Base64", e);
            return null;
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item){
        if(item.getItemId() == android.R.id.home){
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_next) {
            startActivity(new Intent(HomeActivity.this, NextActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void sendTranslateRequestOkHttp(String base64Audio) {
        JSONObject postData = new JSONObject();
        try {
            postData.put("audio_base64", base64Audio);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/json");
        //noinspection deprecation
        RequestBody body = RequestBody.create(mediaType, postData.toString());

        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(HomeActivity.this, "Request failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    assert response.body() != null;
                    String responseData = response.body().string();
                    Log.d("API Response", responseData);  // Log the response
                    try {
                        JSONObject jsonResponse = new JSONObject(responseData);
                        String sourceLanguage = jsonResponse.getString("source_language");
                        String targetLanguage = jsonResponse.getString("target_language");
                        String base64TtsAudio = jsonResponse.getString("base64_tts_audio");
                        runOnUiThread(() -> {
                            Toast.makeText(HomeActivity.this, "Target Language: " + targetLanguage, Toast.LENGTH_LONG).show();
                            tvSourceLanguage.setText(sourceLanguage);
                            tvTargetLanguage.setText(targetLanguage);
                            playBase64Audio(base64TtsAudio);
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Toast.makeText(HomeActivity.this, "Error parsing response", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(HomeActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void playBase64Audio(String base64Audio) {
        try {
            byte[] audioBytes = Base64.decode(base64Audio, Base64.NO_WRAP);
            File audioFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "tts_audio.mp3");
            FileOutputStream fos = new FileOutputStream(audioFile);
            fos.write(audioBytes);
            fos.close();

            if (ttsMediaPlayer != null && ttsMediaPlayer.isPlaying()) {
                ttsMediaPlayer.stop();
                ttsMediaPlayer.release();
                ttsMediaPlayer = null;
            }

            ttsMediaPlayer = new MediaPlayer();
            ttsMediaPlayer.setDataSource(audioFile.getPath());
            ttsMediaPlayer.prepare();
            ttsMediaPlayer.start();
            isTtsPlaying = true;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("PlayAudioError", "Error playing Base64 audio", e);
        }
    }

    public void btnPlayPausePressed(View v) {
        if (ttsMediaPlayer != null) {
            if (isTtsPlaying) {
                ttsMediaPlayer.pause();
                isTtsPlaying = false;
            } else {
                ttsMediaPlayer.start();
                isTtsPlaying = true;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MICROPHONE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == INTERNET_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Internet permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Internet permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
