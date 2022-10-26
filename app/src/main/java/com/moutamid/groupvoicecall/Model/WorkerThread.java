package com.moutamid.groupvoicecall.Model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;


import com.moutamid.groupvoicecall.R;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import io.agora.rtc.Constants;
import io.agora.rtc.IRtcChannelEventHandler;
import io.agora.rtc.RtcChannel;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.models.ChannelMediaOptions;
import okhttp3.OkHttpClient;

public class WorkerThread extends Thread {
    private final static Logger log = LoggerFactory.getLogger(WorkerThread.class);

    private final Context mContext;

    private RtcChannel rtcChannel;
    private static final int ACTION_WORKER_THREAD_QUIT = 0X1010; // quit this thread

    private static final int ACTION_WORKER_JOIN_CHANNEL = 0X2010;

    private static final int ACTION_WORKER_LEAVE_CHANNEL = 0X2011;

    private static final class WorkerThreadHandler extends Handler {

        private WorkerThread mWorkerThread;

        WorkerThreadHandler(WorkerThread thread) {
            this.mWorkerThread = thread;
        }

        public void release() {
            mWorkerThread = null;
        }

        @Override
        public void handleMessage(Message msg) {
            if (this.mWorkerThread == null) {
                log.warn("handler is already released! " + msg.what);
                return;
            }

            switch (msg.what) {
                case ACTION_WORKER_THREAD_QUIT:
                    mWorkerThread.exit();
                    break;
                case ACTION_WORKER_JOIN_CHANNEL:
                    String[] data = (String[]) msg.obj;
                    mWorkerThread.joinChannel(data[0], msg.arg1);
                    break;
                case ACTION_WORKER_LEAVE_CHANNEL:
                    String channel = (String) msg.obj;
                    mWorkerThread.leaveChannel(channel);
                    break;
            }
        }
    }

    private WorkerThreadHandler mWorkerHandler;

    private boolean mReady;

    public final void waitForReady() {
        while (!mReady) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.debug("wait for " + WorkerThread.class.getSimpleName());
        }
    }

    @Override
    public void run() {
        log.trace("start to run");
        Looper.prepare();

        mWorkerHandler = new WorkerThreadHandler(this);

        ensureRtcEngineReadyLock();

        mReady = true;

        // enter thread looper
        Looper.loop();
    }

    private RtcEngine mRtcEngine;

    public final void joinChannel(final String channel, int uid) {
        if (Thread.currentThread() != this) {
            log.warn("joinChannel() - worker thread asynchronously " + channel + " " + uid);
            Message envelop = new Message();
            envelop.what = ACTION_WORKER_JOIN_CHANNEL;
            envelop.obj = new String[]{channel};
            envelop.arg1 = uid;
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        ensureRtcEngineReadyLock();

        String newToken = fetchToken("https://groupvoicecall.herokuapp.com/access_token?", channel, uid);
      //  String newToken = fetchToken("http://10.0.2.2:8080", channel, uid);
        mRtcEngine.joinChannel(newToken, channel, "OpenVCall", uid);

        Log.d("token",newToken);

        mRtcEngine.setEnableSpeakerphone(true);
        mEngineConfig.mChannel = channel;
    }

    String fetchToken(String urlBase, String channelName, int userId) {
        Logger log = LoggerFactory.getLogger("AgoraTokenRequester");

        OkHttpClient client = new OkHttpClient();
        String url = urlBase + "channel=" + channelName + "&uid=" + userId;

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .method("GET", null)
                .build();

        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.debug("Unexpected code " + response);
            } else {
                JSONObject jObject = new JSONObject(response.body().string());
                return jObject.getString("token");
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return "";
    }


    private void initAgoraRtcChannelEngineAndJoinChannel(String channel){
        rtcChannel = mRtcEngine.createRtcChannel(channel);

        rtcChannel.setRtcChannelEventHandler(new IRtcChannelEventHandler(){
            @Override
            // Listen for the onJoinChannelSuccess callback.
            // This callback occurs when the local user successfully joins the channel.
            public void onJoinChannelSuccess(RtcChannel rtcChannel, int uid, int elapsed) {
                super.onJoinChannelSuccess(rtcChannel, uid, elapsed);
                Log.i("TAG", String.format("onJoinChannelSuccess channel %s uid %d", "demoChannel2", uid));

            }
            @Override
            // Listen for the onUserJoinedcallback.
            // This callback occurs when a remote host joins a channel.
            public void onUserJoined(RtcChannel rtcChannel, final int uid, int elapsed) {
                Toast.makeText(mContext,String.valueOf(uid),Toast.LENGTH_LONG).show();
                super.onUserJoined(rtcChannel, uid, elapsed);
            }

        });

        ChannelMediaOptions option = new ChannelMediaOptions();
        option.autoSubscribeAudio = true;

        rtcChannel.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);
        rtcChannel.joinChannel(null, " ", 0, option);
        rtcChannel.publish();
    }
    public final void leaveChannel(String channel) {
        if (Thread.currentThread() != this) {
            log.warn("leaveChannel() - worker thread asynchronously " + channel);
            Message envelop = new Message();
            envelop.what = ACTION_WORKER_LEAVE_CHANNEL;
            envelop.obj = channel;
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        if (mRtcEngine != null) {
            mRtcEngine.leaveChannel();
        }

        mEngineConfig.reset();
        log.debug("leaveChannel " + channel);
    }

    private EngineConfig mEngineConfig;

    public final EngineConfig getEngineConfig() {
        return mEngineConfig;
    }

    private final MyEngineEventHandler mEngineEventHandler;

    public static String getDeviceID(Context context) {
        // XXX according to the API docs, this value may change after factory reset
        // use Android id as device id
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private RtcEngine ensureRtcEngineReadyLock() {
        if (mRtcEngine == null) {
            String appId = mContext.getString(R.string.private_app_id);
            if (TextUtils.isEmpty(appId)) {
                throw new RuntimeException("NEED TO use your App ID, get your own ID at https://dashboard.agora.io/");
            }
            try {
                // Creates an RtcEngine instance
                mRtcEngine = RtcEngine.create(mContext, appId, mEngineEventHandler.mRtcEventHandler);
            } catch (Exception e) {
                log.error(Log.getStackTraceString(e));
                throw new RuntimeException("NEED TO check rtc sdk init fatal error\n" + Log.getStackTraceString(e));
            }

            /*
              Sets the channel profile of the Agora RtcEngine.
              The Agora RtcEngine differentiates channel profiles and applies different optimization
              algorithms accordingly. For example, it prioritizes smoothness and low latency for a
              video call, and prioritizes video quality for a video broadcast.
             */
            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);

            /*
              Enables the onAudioVolumeIndication callback at a set time interval to report on which
              users are speaking and the speakers' volume.
              Once this method is enabled, the SDK returns the volume indication in the
              onAudioVolumeIndication callback at the set time interval, regardless of whether any user
              is speaking in the channel.
            */
            mRtcEngine.enableAudioVolumeIndication(200, 3, true); // 200 ms
            mRtcEngine.setLogFile(Environment.getExternalStorageDirectory()
                    + File.separator + mContext.getPackageName() + "/log/agora-rtc.log");
        }
        return mRtcEngine;
    }

    public MyEngineEventHandler eventHandler() {
        return mEngineEventHandler;
    }

    public RtcEngine getRtcEngine() {
        return mRtcEngine;
    }

    /**
     * call this method to exit
     * should ONLY call this method when this thread is running
     */
    public final void exit() {
        if (Thread.currentThread() != this) {
            log.warn("exit() - exit app thread asynchronously");
            mWorkerHandler.sendEmptyMessage(ACTION_WORKER_THREAD_QUIT);
            return;
        }

        mReady = false;

        // TODO should remove all pending(read) messages

        log.debug("exit() > start");

        // exit thread looper
        Looper.myLooper().quit();

        mWorkerHandler.release();

        log.debug("exit() > end");
    }

    public WorkerThread(Context context) {
        this.mContext = context;

        this.mEngineConfig = new EngineConfig();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        this.mEngineConfig.mUid = pref.getInt(ConstantApp.PrefManager.PREF_PROPERTY_UID, 0);

        this.mEngineEventHandler = new MyEngineEventHandler(mContext, this.mEngineConfig);
    }
}
