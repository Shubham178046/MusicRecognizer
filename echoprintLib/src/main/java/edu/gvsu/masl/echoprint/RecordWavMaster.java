package edu.gvsu.masl.echoprint;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.text.format.Time;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.ContentValues.TAG;

public class RecordWavMaster {
    private static final int samplingRates[] = {16000, 11025, 11000, 8000, 6000};
    public static int SAMPLE_RATE = 44100;
    private final int FREQUENCY = 11025;
    private final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private AudioRecord mRecorder;
    public RecordWaveData listner;
    private File mRecording;
    private short[] mBuffer;
    private String audioFilePath;
    public Context context;
    private boolean mIsRecording = false;
    private String RECORD_WAV_PATH = Environment.getExternalStorageDirectory() + File.separator + "AudioRecord";

    /* Initializing AudioRecording MIC */
    public RecordWavMaster(Context context, RecordWaveData listner) {
        this.context = context;
        this.listner = listner;
        StrictMode.ThreadPolicy policy = new
                StrictMode.ThreadPolicy.Builder().permitAll()
                .penaltyLog()
                .penaltyDeath()
                .build();
        StrictMode.setThreadPolicy(policy);
        initRecorder();
    }

    /* Get Supported Sample Rate */
    public static int getValidSampleRates() {
       /* for (int rate : samplingRates) {
            int bufferSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize > 0) {
                return rate;
            }
        }*/
        return SAMPLE_RATE;
    }

    /* Start AudioRecording */
    public void recordWavStart() {
        mIsRecording = true;
        mRecorder.startRecording();
        mRecording = getFile("raw");
        startBufferedWrite(mRecording);
    }

    public void fingerprint(int seconds) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                recordWavStop();
            }
        }, seconds);
    }

    /* Stop AudioRecording */
    public String recordWavStop() {
        try {
            mIsRecording = false;
            mRecorder.stop();
            byte[] soundBytes;
            InputStream inputStream =
                    context.getContentResolver().openInputStream(Uri.fromFile(mRecording));
            InputStream inStream = context.getResources().openRawResource(R.raw.punjabi);
            //   File file = new File(context.getResources().openRawResource(R.raw.song));
            soundBytes = new byte[inputStream.available()];
            soundBytes = toByteArray(inputStream);
            String base64Str = Base64.encodeToString(soundBytes, Base64.NO_WRAP);
            Log.d(TAG, "recordWavStopBase64: " + base64Str.trim().toString());
            OkHttpClient client = new OkHttpClient();
            MediaType mediaType = MediaType.parse("text/plain");
            RequestBody body = RequestBody.create(mediaType, base64Str.trim());
            Request request = new Request.Builder()
                    .url("https://shazam.p.rapidapi.com/songs/detect")
                    .post(body)
                    .addHeader("content-type", "text/plain")
                    .addHeader("x-rapidapi-key", "39d2241238msh53f7f18a81e38fcp13bd5ajsnfa0684ebc23e")
                    .addHeader("x-rapidapi-host", "shazam.p.rapidapi.com")
                    .build();
            Response response = client.newCall(request).execute();
            Log.d("TAG", "Result: " + response.peekBody(2048).string());
            String result = response.peekBody(2048).string();
            listner.didFinishListning(result);
            File waveFile = getFile("wav");
            rawToWave(mRecording, waveFile, soundBytes);
            Log.e("path_audioFilePath", audioFilePath);
            return audioFilePath;
        } catch (Exception e) {
            Log.e("Error saving file : ", e.getMessage());
        }
        return null;
    }

    private class NetworkTask extends AsyncTask<String, String, String> {
        protected String doInBackground(String... urls) {

            OkHttpClient client = new OkHttpClient();
            MediaType mediaType = MediaType.parse("text/plain");
            RequestBody body = RequestBody.create(mediaType, urls[0]);
            Request request = new Request.Builder()
                    .url("https://shazam.p.rapidapi.com/songs/detect")
                    .post(body)
                    .addHeader("content-type", "text/plain")
                    .addHeader("x-rapidapi-key", "39d2241238msh53f7f18a81e38fcp13bd5ajsnfa0684ebc23e")
                    .addHeader("x-rapidapi-host", "shazam.p.rapidapi.com")
                    .build();
            Response response = null;
            try {
                response = client.newCall(request).execute();
                Log.d("TAG", "Result: " + response.body().string());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

    }

    public interface RecordWaveData {
        void didFinishListning(String result);
    }

    public byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int read = 0;
        byte[] buffer = new byte[1024];
        while (read != -1) {
            read = in.read(buffer);
            if (read != -1)
                out.write(buffer, 0, read);
        }
        out.close();
        return out.toByteArray();
    }

    /* Release device MIC */
    public void releaseRecord() {
        mRecorder.release();
    }

    /* Initializing AudioRecording MIC */
    private void initRecorder() {
        SAMPLE_RATE = getValidSampleRates();
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        mBuffer = new short[bufferSize];
        mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        new File(RECORD_WAV_PATH).mkdir();
    }

    /* Writing RAW file */
    private void startBufferedWrite(final File file) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                DataOutputStream output = null;
                try {
                    output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
                    while (mIsRecording) {
                        double sum = 0;
                        int readSize = mRecorder.read(mBuffer, 0, mBuffer.length);
                        for (int i = 0; i < readSize; i++) {
                            output.writeShort(mBuffer[i]);
                            sum += mBuffer[i] * mBuffer[i];
                        }
                        if (readSize > 0) {
                            final double amplitude = sum / readSize;
                        }
                    }
                } catch (IOException e) {
                    Log.e("Error writing file : ", e.getMessage());
                } finally {

                    if (output != null) {
                        try {
                            output.flush();
                        } catch (IOException e) {
                            Log.e("Error writing file : ", e.getMessage());
                        } finally {
                            try {
                                output.close();
                            } catch (IOException e) {
                                Log.e("Error writing file : ", e.getMessage());
                            }
                        }
                    }
                }
            }
        }).start();
    }

    /* Converting RAW format To WAV Format*/
    private void rawToWave(final File rawFile, final File waveFile, byte[] raw) throws IOException {

        byte[] rawData = new byte[(int) rawFile.length()];
        DataInputStream input = null;
        try {
            input = new DataInputStream(new FileInputStream(rawFile));
            input.read(rawData);
        } finally {
            if (input != null) {
                input.close();
            }
        }
        DataOutputStream output = null;
        try {
            output = new DataOutputStream(new FileOutputStream(waveFile));
            // WAVE header
            writeString(output, "RIFF"); // chunk id
            writeInt(output, 36 + rawData.length); // chunk size
            writeString(output, "WAVE"); // format
            writeString(output, "fmt "); // subchunk 1 id
            writeInt(output, 16); // subchunk 1 size
            writeShort(output, (short) 1); // audio format (1 = PCM)
            writeShort(output, (short) 1); // number of channels
            writeInt(output, SAMPLE_RATE); // sample rate
            writeInt(output, SAMPLE_RATE * 2); // byte rate
            writeShort(output, (short) 2); // block align
            writeShort(output, (short) 16); // bits per sample
            writeString(output, "data"); // subchunk 2 id
            writeInt(output, rawData.length); // subchunk 2 size
            // Audio data (conversion big endian -> little endian)
            short[] shorts = new short[rawData.length / 2];
            ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
            ByteBuffer bytes = ByteBuffer.allocate(shorts.length * 2);
            for (short s : shorts) {
                bytes.putShort(s);
            }
            output.write(bytes.array());
        } finally {
            if (output != null) {
                output.close();
                rawFile.delete();
            }
        }


    }

    /* Get file name */
    private File getFile(final String suffix) {
        Time time = new Time();
        time.setToNow();
        audioFilePath = time.format("%Y%m%d%H%M%S");
        File file = new File(RECORD_WAV_PATH, time.format("%Y%m%d%H%M%S") + "." + suffix);
        return file;
    }

    private void writeInt(final DataOutputStream output, final int value) throws IOException {
        output.write(value >> 0);
        output.write(value >> 8);
        output.write(value >> 16);
        output.write(value >> 24);
    }

    private void writeShort(final DataOutputStream output, final short value) throws IOException {
        output.write(value >> 0);
        output.write(value >> 8);
    }

    private void writeString(final DataOutputStream output, final String value) throws IOException {
        for (int i = 0; i < value.length(); i++) {
            output.write(value.charAt(i));
        }
    }

    public String getFileName(final String time_suffix) {
        return (RECORD_WAV_PATH + time_suffix + "." + "wav");
    }

    public Boolean getRecordingState() {
        if (mRecorder.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED) {
            return false;
        }
        return true;
    }
}
