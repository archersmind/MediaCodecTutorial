/**
 * MediaCodecTutorial
 *
 * @author Alan Wang <alan.wang@csr.com>
 * @version 1.0
 */
package archersmind.mediacodectutorial;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.nio.ByteBuffer;


public class MainActivity extends Activity implements SurfaceHolder.Callback {

    private static final int START_DECODING = 1;
    private static final String LOG_TAG = "MediaCodecTutorial";
    private static final int TIME_OUT = 10000;
    private static final String VideoPath = Environment.getExternalStorageDirectory()
            + "/TestVideo.mp4";

    private Handler mHandler = null;
    private MediaExtractor mExtractor = null;
    private MediaCodec mCodec = null;
    private Surface mSurface = null;
    private boolean mDecodingDone = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SurfaceView view = new SurfaceView(this);
        view.getHolder().addCallback(this);
        setContentView(view);
    }

    @Override
    protected void onPause() {
        Log.i(LOG_TAG, "onPause...");
        mDecodingDone = true;
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.i(LOG_TAG, "onStop...");
        mDecodingDone = true;
        super.onStop();
    }

    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(LOG_TAG, "surfaceCreated..");
        HandlerThread ht = new HandlerThread("DecodingTest");
        ht.start();
        mHandler = new MyHandle(ht.getLooper());

    }

    private void DoActualWork() throws IOException {
        Log.i(LOG_TAG, "Doing actual Work...");

        MediaFormat format = null;
        String mimeType = null;

        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(VideoPath);

        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            format = mExtractor.getTrackFormat(i);
            mimeType = format.getString(MediaFormat.KEY_MIME);

            // Handle Video only
            if (mimeType.startsWith("video/")) {
                mExtractor.selectTrack(i);
                mCodec = MediaCodec.createDecoderByType(mimeType);
                mCodec.configure(format, mSurface, null, 0);
                break;
            }
        }

        if (mCodec == null) {
            Log.e(LOG_TAG, "Codec for " + mimeType + "is not found!!!");
            return;
        }

        Log.i(LOG_TAG, "Codec " + mCodec.getName() + "Staring...");
        mCodec.start();

        // Below methods are deprecated
        ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mCodec.getOutputBuffers();

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean isEOS = false;
        long startMs = System.currentTimeMillis();

        while (!mDecodingDone) {
            if (!isEOS) {
                int inputBufIndex = mCodec.dequeueInputBuffer(TIME_OUT);

                if (inputBufIndex >= 0) {
                    Log.i(LOG_TAG, "Dequeue an Input Buffer of index " + inputBufIndex);
                    ByteBuffer inputBuffer = inputBuffers[inputBufIndex];

                    int bufferSize = mExtractor.readSampleData(inputBuffer, 0);
                    Log.i(LOG_TAG, "Sample Size = " + bufferSize);

                    if (bufferSize >= 0) {
                        mCodec.queueInputBuffer(inputBufIndex, 0,
                                                bufferSize, mExtractor.getSampleTime(), 0);
                        mExtractor.advance();
                    } else {

                        mCodec.queueInputBuffer(inputBufIndex, 0,
                                                0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    }
                }
            }

            int outputBufIndex = mCodec.dequeueOutputBuffer(info, TIME_OUT);

            switch (outputBufIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.i(LOG_TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    outputBuffers = mCodec.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.i(LOG_TAG, "New format " + mCodec.getOutputFormat());
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.i(LOG_TAG, "dequeueOutputBuffer timed out!");
                    break;
                default:
                    ByteBuffer buffer = outputBuffers[outputBufIndex];
                    Log.v(LOG_TAG, "We can't use this buffer but render it due to the API limit, " + buffer);

                    // We use a very simple clock to keep the video FPS, or the video
                    // playback will be too fast
                    while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    mCodec.releaseOutputBuffer(outputBufIndex, true);
                    break;
            }

            // Got EOS
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.i(LOG_TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                mDecodingDone = true;
            }
        }

        Log.i(LOG_TAG, "releasing codec...");
        mCodec.stop();
        mCodec.release();
        mExtractor.release();

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(LOG_TAG, "surface Changed...");
        if (mHandler != null) {
            Log.i(LOG_TAG, "Send Message START_DECODING...");
            mHandler.sendEmptyMessage(START_DECODING);
        }
        mSurface = holder.getSurface();

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    private class MyHandle extends Handler {
        MyHandle(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case START_DECODING:
                    try {
                        DoActualWork();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }


    }
}
