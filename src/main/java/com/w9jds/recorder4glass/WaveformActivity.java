package com.w9jds.recorder4glass;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Receives audio input from the microphone and displays a visualization of that data as a waveform
 * on the screen.
 */
public class WaveformActivity extends Activity
{

    // The sampling rate for the audio recorder.
    private static final int SAMPLING_RATE = 44100;

    private final String MUSIC_BUCKET_NAME = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();

    private WaveformView mWaveformView;
    private TextView mDecibelView;

    private RecordingThread mRecordingThread;
    private int mBufferSize;
    private short[] mAudioBuffer;
    private String mDecibelFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_waveform);

        mWaveformView = (WaveformView) findViewById(R.id.waveform_view);
        mDecibelView = (TextView) findViewById(R.id.decibel_view);

        // Compute the minimum required audio buffer size and allocate the buffer.
        mBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mAudioBuffer = new short[mBufferSize / 2];

        mDecibelFormat = getResources().getString(R.string.decibel_format);
    }

    @Override
    public boolean onKeyDown(int keycode, KeyEvent event)
    {
        if (keycode == KeyEvent.KEYCODE_DPAD_CENTER)
        {
            openOptionsMenu();
            return true;
        }

        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem iItem)
    {

        switch (iItem.getItemId())
        {
            case R.id.stop_menu_item:
                mRecordingThread.stopRunning();
                return true;

            default:
                return super.onOptionsItemSelected(iItem);
        }

    }


    @Override
    protected void onResume()
    {
        super.onResume();

        mRecordingThread = new RecordingThread();
        mRecordingThread.start();
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if (mRecordingThread != null)
        {
            mRecordingThread.stopRunning();
            mRecordingThread = null;
        }
    }

    /**
     * A background thread that receives audio from the microphone and sends it to the waveform
     * visualizing view.
     */
    private class RecordingThread extends Thread
    {

        private boolean mShouldContinue = true;

        @Override
        public void run()
        {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            Log.d("Recorder4Glass", "Setting Priority to Audio");

            AudioRecord arRecorder = new AudioRecord(AudioSource.MIC, SAMPLING_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mBufferSize);
            Log.d("Recorder4Glass", "Creating new Recorder");
            arRecorder.startRecording();
            Log.d("Recorder4Glass", "Starting Recording");

            OutputStream fosStream = null;
            BufferedOutputStream bosStream;
            DataOutputStream dosStream = null;

            try
            {
                fosStream = new FileOutputStream(new File(MUSIC_BUCKET_NAME + "/record.pcm"));
                Log.d("Recorder4Glass", "Created File " + MUSIC_BUCKET_NAME + "/record.pcm");
                bosStream = new BufferedOutputStream(fosStream);
                Log.d("recorder4Glass", "Created bosStream");
                dosStream = new DataOutputStream(bosStream);
                Log.d("recorder4Glass", "Created dosStream");
            }
            catch(FileNotFoundException e)
            {
                e.printStackTrace();
            }

            while (shouldContinue())
            {
                int bufferReadResult = arRecorder.read(mAudioBuffer, 0, mBufferSize);
                Log.d("recorder4Glass", "Read buffer Result");

                for (int i = 0; i < bufferReadResult; i++)
                {
                    try
                    {
                        dosStream.writeShort(mAudioBuffer[i]);
                        Log.d("recorder4Glass", "Writing Buffer " + i);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                        Log.d("recorder4Glass", e.getCause().toString());
                    }
                }

                mWaveformView.updateAudioData(mAudioBuffer);
                Log.d("recorder4Glass", "Updating Audio Wave");

                updateDecibelLevel();
                Log.d("recorder4Glass", "Updating Decibel Level");
            }

            try
            {
                fosStream.close();
                Log.d("recorder4Glass", "Closed File Stream");
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            arRecorder.stop();
            Log.d("recorder4Glass", "Stopped Recording");
            arRecorder.release();
            Log.d("recorder4Glass", "Released Recorder");

        }

        /**
         * Gets a value indicating whether the thread should continue running.
         *
         * @return true if the thread should continue running or false if it should stop
         */
        private synchronized boolean shouldContinue()
        {
            return mShouldContinue;
        }

        /** Notifies the thread that it should stop running at the next opportunity. */
        public synchronized void stopRunning()
        {
            mShouldContinue = false;
        }

        /**
         * Computes the decibel level of the current sound buffer and updates the appropriate text
         * view.
         */
        private void updateDecibelLevel()
        {
            // Compute the root-mean-squared of the sound buffer and then apply the formula for
            // computing the decibel level, 20 * log_10(rms). This is an uncalibrated calculation
            // that assumes no noise in the samples; with 16-bit recording, it can range from
            // -90 dB to 0 dB.
            double sum = 0;

            for (short rawSample : mAudioBuffer)
            {
                double sample = rawSample / 32768.0;
                sum += sample * sample;
            }

            double rms = Math.sqrt(sum / mAudioBuffer.length);
            final double db = 20 * Math.log10(rms);

            // Update the text view on the main thread.
            mDecibelView.post(new Runnable()
            {
                @Override
                public void run()
                {
                    mDecibelView.setText(String.format(mDecibelFormat, db));
                }
            });
        }
    }
}