package com.moutamid.groupvoicecall;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.moutamid.groupvoicecall.Model.AGEventHandler;
import com.moutamid.groupvoicecall.Model.ConstantApp;
import com.moutamid.groupvoicecall.Model.EngineConfig;
import com.moutamid.groupvoicecall.Model.Model_Conversation;
import com.moutamid.groupvoicecall.Model.MyEngineEventHandler;
import com.moutamid.groupvoicecall.Model.UserModel;
import com.moutamid.groupvoicecall.Model.WorkerThread;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;

import de.hdodenhof.circleimageview.CircleImageView;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;

public class Dashboard extends AppCompatActivity implements AGEventHandler {

    private TextView leave_room;
    private TextView nameTxt;
    private CircleImageView profileImg;
    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private DatabaseReference db,userDB;
    private RecyclerView recyclerView;
    private ImageView micOn,micOff;
    private ArrayList<Model_Conversation> conversationArrayList = new ArrayList<>();
    private static final int PERMISSION_REQ_ID_RECORD_AUDIO = 22;
    private final static Logger log = LoggerFactory.getLogger(Dashboard.class);
    //    private volatile boolean mAudioMuted = false;
    private boolean micStatus;
    private volatile int mAudioRouting = -1; // Default
    private Adapter_Conversation adapter_conversation;
    private ImageView callBtn;
    private LinearLayout linearLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        leave_room = findViewById(R.id.leave_room);
        nameTxt = findViewById(R.id.name);
        profileImg = findViewById(R.id.profile);
        callBtn = findViewById(R.id.call);
        linearLayout = findViewById(R.id.layout_conversation);
        //micOn = findViewById(R.id.speakerOn);
        //micOff = findViewById(R.id.speakerOff);
        recyclerView = findViewById(R.id.recyclerView_convesation);
        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();
        db = FirebaseDatabase.getInstance().getReference().child("Rooms");
        userDB = FirebaseDatabase.getInstance().getReference().child("Users");
        callBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recyclerView.setVisibility(View.VISIBLE);
                linearLayout.setVisibility(View.VISIBLE);
                callBtn.setVisibility(View.GONE);
                checkRoomDB();
                worker().joinChannel("group1", config().mUid);
            }
        });
        leave_room.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doLeaveChannel();
            }
        });
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQ_ID_RECORD_AUDIO)) {
            ((AGApplication) getApplication()).initWorkerThread();
            Toast.makeText(Dashboard.this,"Permission Granted!",Toast.LENGTH_LONG).show();
        }
        getMyData();
        getRoomsUsers();
        getOtherUsers();
    }

    private void getOtherUsers() {
        db.child(user.getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()){
                            recyclerView.setVisibility(View.VISIBLE);
                            linearLayout.setVisibility(View.VISIBLE);
                            callBtn.setVisibility(View.GONE);
                            worker().joinChannel("group1", config().mUid);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
    }

    private void getMyData() {
        userDB.child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()){
                            UserModel model = snapshot.getValue(UserModel.class);
                            nameTxt.setText(model.getName());
                            if (model.getImageUrl().equals("")){
                                Picasso.with(Dashboard.this)
                                        .load(R.drawable.profile)
                                        .into(profileImg);
                            }else {
                                Picasso.with(Dashboard.this)
                                        .load(model.getImageUrl())
                                        .placeholder(R.drawable.profile)
                                        .into(profileImg);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
    }

    private boolean checkSelfPermission(String permission, String writeExternalStorage, int recordAudio) {
        if (ContextCompat.checkSelfPermission(this,
                permission)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{permission,writeExternalStorage},
                    recordAudio);
            return true;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQ_ID_RECORD_AUDIO: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,PERMISSION_REQ_ID_RECORD_AUDIO);
                    ((AGApplication) getApplication()).initWorkerThread();
                    Toast.makeText(Dashboard.this,"Permission Granted!",Toast.LENGTH_LONG).show();
                } else {
                    finish();
                }
                break;
            }
        }
    }

    private void getRoomsUsers() {
        db.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                    conversationArrayList.clear();
                    for (DataSnapshot ds : snapshot.getChildren()){
                        Model_Conversation conversation = ds.getValue(Model_Conversation.class);
                        if (!conversation.getId().equals(user.getUid())) {
                            conversationArrayList.add(conversation);
                        }
                    }
                    adapter_conversation = new Adapter_Conversation(Dashboard.this,
                            conversationArrayList);
                    recyclerView.setAdapter(adapter_conversation);
                    adapter_conversation.notifyDataSetChanged();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

    }


    // Tutorial Step 7
    public void onLocalAudioMuteClicked(View view) {
        ImageView iv = (ImageView) view;
        if (iv.isSelected()) {
            iv.setSelected(false);
            iv.setImageResource(R.drawable.ic_baseline_mic_none_24);
            iv.setBackgroundResource(R.drawable.shape_circle);
            // iv.clearColorFilter();
        } else {
            iv.setSelected(true);
            iv.setImageResource(R.drawable.ic_baseline_mic_off_24);
            iv.setBackgroundResource(R.drawable.shape_circle_white);
            //iv.setColorFilter(getResources().getColor(R.color.appClr2), PorterDuff.Mode.CLEAR);
        }

        // Stops/Resumes sending the local audio stream.
        rtcEngine().muteLocalAudioStream(iv.isSelected());
    }

    private void checkRoomDB() {

        userDB.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                    for (DataSnapshot ds : snapshot.getChildren()){
                        UserModel model = ds.getValue(UserModel.class);
                        HashMap<String, Object> hashMap = new HashMap<>();
                        hashMap.put("id",model.getId());
                        hashMap.put("mic_status",micStatus);
                        db.child(model.getId()).updateChildren(hashMap);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }


    @Override
    public void onBackPressed(){
        super.onBackPressed();
        finish();
    }

    private void doLeaveChannel() {
        worker().leaveChannel(config().mChannel);
        db.child(user.getUid()).removeValue();
        callBtn.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        linearLayout.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        doLeaveChannel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doLeaveChannel();

    }

    @Override
    public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
        String msg = "onJoinChannelSuccess " + channel + " " + (uid & 0xFFFFFFFFL) + " " + elapsed;
        log.debug(msg);


        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                rtcEngine().setEnableSpeakerphone(mAudioRouting != 3);
                rtcEngine().muteLocalAudioStream(false);
            }
        });

    }

    @Override
    public void onUserOffline(int uid, int reason) {

    }

    @Override
    public void onExtraCallback(int type, Object... data) {


        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                doHandleExtraCallback(type, data);
            }
        });
    }

    private void doHandleExtraCallback(int type, Object... data) {
        int peerUid;
        boolean muted;

        switch (type) {
            case AGEventHandler.EVENT_TYPE_ON_USER_AUDIO_MUTED: {
                peerUid = (Integer) data[0];
                muted = (boolean) data[1];

                notifyMessageChanged("mute: " + (peerUid & 0xFFFFFFFFL) + " " + muted);
                break;
            }

            case AGEventHandler.EVENT_TYPE_ON_AUDIO_QUALITY: {
                peerUid = (Integer) data[0];
                int quality = (int) data[1];
                short delay = (short) data[2];
                short lost = (short) data[3];

                notifyMessageChanged("quality: " + (peerUid & 0xFFFFFFFFL) + " " + quality + " " + delay + " " + lost);
                break;
            }

            case AGEventHandler.EVENT_TYPE_ON_SPEAKER_STATS: {
                IRtcEngineEventHandler.AudioVolumeInfo[] infos = (IRtcEngineEventHandler.AudioVolumeInfo[]) data[0];

                if (infos.length == 1 && infos[0].uid == 0) { // local guy, ignore it
                    break;
                }

                StringBuilder volumeCache = new StringBuilder();
                for (IRtcEngineEventHandler.AudioVolumeInfo each : infos) {
                    peerUid = each.uid;
                    int peerVolume = each.volume;

                    if (peerUid == 0) {
                        continue;
                    }

                    volumeCache.append("volume: ").append(peerUid & 0xFFFFFFFFL).append(" ").append(peerVolume).append("\n");
                }

                if (volumeCache.length() > 0) {
                    String volumeMsg = volumeCache.substring(0, volumeCache.length() - 1);
                    notifyMessageChanged(volumeMsg);

                    if ((System.currentTimeMillis() / 1000) % 10 == 0) {
                        log.debug(volumeMsg);
                    }
                }
                break;
            }

            case AGEventHandler.EVENT_TYPE_ON_APP_ERROR: {
                int subType = (int) data[0];

                if (subType == ConstantApp.AppError.NO_NETWORK_CONNECTION) {
                    showLongToast("No Internet ");
                }

                break;
            }

            case AGEventHandler.EVENT_TYPE_ON_AGORA_MEDIA_ERROR: {
                int error = (int) data[0];
                String description = (String) data[1];

                notifyMessageChanged(error + " " + description);

                break;
            }

            case AGEventHandler.EVENT_TYPE_ON_AUDIO_ROUTE_CHANGED: {
                notifyHeadsetPlugged((int) data[0]);
                break;
            }
        }
    }

    private void notifyMessageChanged(String msg) {
        Toast.makeText(Dashboard.this, msg, Toast.LENGTH_SHORT).show();

    }
    public RtcEngine rtcEngine() {
        return ((AGApplication) getApplication()).getWorkerThread().getRtcEngine();
    }

    protected final WorkerThread worker() {
        return ((AGApplication) getApplication()).getWorkerThread();
    }

    public final EngineConfig config() {
        return ((AGApplication) getApplication()).getWorkerThread().getEngineConfig();
    }

    protected final MyEngineEventHandler event() {
        return ((AGApplication) getApplication()).getWorkerThread().eventHandler();
    }

    public final void showLongToast(final String msg) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }
    public void notifyHeadsetPlugged(final int routing) {
        log.info("notifyHeadsetPlugged " + routing);

        mAudioRouting = routing;

    }

}