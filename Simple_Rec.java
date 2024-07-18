package com.example.voicerecorder;

//Normal Recording file .java

import android.Manifest;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;
import android.util.Base64;
import java.io.FileInputStream;
import java.io.IOException;
import android.util.Base64;
import androidx.annotation.NonNull;

import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;

public class MainActivity extends AppCompatActivity {

   private static final int MICROPHONE_PERMISSION_CODE = 200;
   private static final int INTERNET_PERMISSION_CODE = 201; // Arbitrary code for internet permission

   MediaRecorder mediaRecorder;
   MediaPlayer mediaPlayer;
   boolean isRecording = false; // flag to track the state of recording
   String base64Audio = ""; // variable to store base64 encoded audio data
   private static final String API_URL = "API_URL"; // API endpoint URL

   @Override
   protected void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       setContentView(R.layout.activity_main);
       if (isMicrophonePresent()) {
           getMicrophonePermission();
       }
       if (!isInternetPermissionGranted()) {
           getInternetPermission();
       }
   }

   private boolean isInternetPermissionGranted() {
       return ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
               == PackageManager.PERMISSION_GRANTED;
   }

   private void getInternetPermission() {
       if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
               == PackageManager.PERMISSION_DENIED) {
           ActivityCompat.requestPermissions(this, new String[]
                   {Manifest.permission.INTERNET}, INTERNET_PERMISSION_CODE);
       }
   }

   public void btnRecordPressed(View v) {
       if (!isRecording) {
           try {
               mediaRecorder = new MediaRecorder();
               mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
               mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
               mediaRecorder.setOutputFile(getRecordingFilePath());
               mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
               mediaRecorder.prepare();
               mediaRecorder.start();
               isRecording = true; // set the flag to true
               Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
           } catch (Exception e) {
               e.printStackTrace();
           }
       } else {
           mediaRecorder.stop();
           mediaRecorder.release();
           mediaRecorder = null;
           isRecording = false; // set the flag to false
           Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
           base64Audio = encodeAudioToBase64(getRecordingFilePath());
           Toast.makeText(this, "Sending Request" + base64Audio, Toast.LENGTH_SHORT).show();
           sendTranslateRequest(base64Audio); // Send API request
       }
   }

   public void btnPlayPressed(View v) {
       try {
           mediaPlayer = new MediaPlayer();
           mediaPlayer.setDataSource(getRecordingFilePath());
           mediaPlayer.prepare();
           mediaPlayer.start();
           Toast.makeText(this, "Playing", Toast.LENGTH_SHORT).show();
       } catch (Exception e) {
           e.printStackTrace();
       }
   }

   private boolean isMicrophonePresent() {
       if (this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
           return true;
       } else {
           return false;
       }
   }

   private void getMicrophonePermission() {
       if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
               == PackageManager.PERMISSION_DENIED) {
           ActivityCompat.requestPermissions(this, new String[]
                   {Manifest.permission.RECORD_AUDIO}, MICROPHONE_PERMISSION_CODE);
       }
   }

   private String getRecordingFilePath() {
       ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
       File musicDirectory = contextWrapper.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
       File file = new File(musicDirectory, "TestRecordingFile" + ".wav");
       return file.getPath();
   }

   private String encodeAudioToBase64(String filePath) {
       String base64 = "";
       try {
           File file = new File(filePath);
           FileInputStream inputStream = new FileInputStream(file);
           byte[] audioBytes = new byte[(int) file.length()];
           inputStream.read(audioBytes);
           base64 = Base64.encodeToString(audioBytes, Base64.DEFAULT);
           inputStream.close();
       } catch (IOException e) {
           e.printStackTrace();
       }
       return base64;
   }

   private void sendTranslateRequest(String base64Audio) {
       // Create JSON object with base64 audio data
       JSONObject postData = new JSONObject();
       try {
           postData.put("audio_base64", base64Audio);
       } catch (JSONException e) {
           e.printStackTrace();
       }

       // Create a request queue
       RequestQueue requestQueue = Volley.newRequestQueue(this);

       // Create a JsonObjectRequest with POST method
       JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, API_URL, postData,
               response -> {
                   // Handle API response
                   Toast.makeText(MainActivity.this, "Response: " + response.toString(), Toast.LENGTH_LONG).show();
               },
               error -> {
                   // Handle error
                   if (error instanceof NoConnectionError) {
                       Toast.makeText(MainActivity.this, "No internet connection", Toast.LENGTH_SHORT).show();
                   } else {
                       Toast.makeText(MainActivity.this, "Error: " + error.toString(), Toast.LENGTH_SHORT).show();
                   }
               });

       // Add the request to the RequestQueue
       requestQueue.add(jsonObjectRequest);
   }

}
