/**
 * EchoprintTestActivity.java
 * EchoprintTest
 * <p>
 * Created by Alex Restrepo on 1/22/12.
 * Copyright (C) 2012 Grand Valley State University (http://masl.cis.gvsu.edu/)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package edu.gvsu.masl;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Hashtable;

import edu.gvsu.masl.echoprint.AudioFingerprinter;
import edu.gvsu.masl.echoprint.AudioFingerprinter.AudioFingerprinterListener;
import edu.gvsu.masl.echoprint.RecordWavMaster;

/**
 * EchoprintTestActivity<br>
 * This class demos how to use the AudioFingerprinter class
 *
 * @author Alex Restrepo (MASL)
 */
public class EchoprintTestActivity extends Activity implements AudioFingerprinterListener, RecordWavMaster.RecordWaveData {
    boolean recording, resolved;
    AudioFingerprinter fingerprinter;
    RecordWavMaster recordWavMaster;
    TextView status;
    TextView results;
    Button btn;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        btn = (Button) findViewById(R.id.recordButton);
        recordWavMaster = new RecordWavMaster(this, this);
        status = (TextView) findViewById(R.id.status);
        results = (TextView) findViewById(R.id.txtResult);

        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(getApplicationContext(),
                        Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(getApplicationContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(getApplicationContext(),
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(EchoprintTestActivity.this,
                            new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                            1234);
                } else {
                    recordWavMaster.recordWavStart();
                    recordWavMaster.fingerprint(3000);

                    // recordWavMaster.getFileName("210715");
                    //recognizeAudio();
                }
                // Perform action on click

            }
        });
    }

    public static String formatString(String text) {

        StringBuilder json = new StringBuilder();
        String indentString = "";

        boolean inQuotes = false;
        boolean isEscaped = false;

        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);

            switch (letter) {
                case '\\':
                    isEscaped = !isEscaped;
                    break;
                case '"':
                    if (!isEscaped) {
                        inQuotes = !inQuotes;
                    }
                    break;
                default:
                    isEscaped = false;
                    break;
            }

            if (!inQuotes && !isEscaped) {
                switch (letter) {
                    case '{':
                    case '[':
                        json.append("\n" + indentString + letter + "\n");
                        indentString = indentString + "\t";
                        json.append(indentString);
                        break;
                    case '}':
                    case ']':
                        indentString = indentString.replaceFirst("\t", "");
                        json.append("\n" + indentString + letter);
                        break;
                    case ',':
                        json.append(letter + "\n" + indentString);
                        break;
                    default:
                        json.append(letter);
                        break;
                }
            } else {
                json.append(letter);
            }
        }

        return json.toString();
    }

    void recognizeAudio() {
        if (recording) {
            fingerprinter.stop();
        } else {
            if (fingerprinter == null)
                fingerprinter = new AudioFingerprinter(EchoprintTestActivity.this);

            fingerprinter.fingerprint(5);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1234: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //recognizeAudio();
                    recordWavMaster.recordWavStart();
                    recordWavMaster.fingerprint(15000);
                } else {
                    Log.d("TAG", "permission denied by user");
                }
                return;
            }
        }
    }

    public void didFinishListening() {
        btn.setText("Start");

        if (!resolved)
            status.setText("Idle...");

        recording = false;
    }

    public void didFinishListeningPass() {
    }

    public void willStartListening() {
        status.setText("Listening...");
        btn.setText("Stop");
        recording = true;
        resolved = false;
    }

    public void willStartListeningPass() {
    }

    public void didGenerateFingerprintCode(String code) {
        status.setText("Will fetch info for code starting:\n" + code.substring(0, Math.min(50, code.length())));
    }

    public void didFindMatchForCode(final Hashtable<String, String> table,
                                    String code) {
        resolved = true;
        status.setText("Match: \n" + table);
    }

    public void didNotFindMatchForCode(String code) {
        resolved = true;
        status.setText("No match for code starting with: \n" + code.substring(0, Math.min(50, code.length())));
    }

    public void didFailWithException(Exception e) {
        resolved = true;
        status.setText("Error: " + e);
    }

    @Override
    public void didFinishListning(String result) {
        results.setText(formatString(result));
    }
}