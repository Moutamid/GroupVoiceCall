package com.moutamid.groupvoicecall;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.moutamid.groupvoicecall.Model.Model_Conversation;
import com.moutamid.groupvoicecall.Model.UserModel;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.HashMap;

import de.hdodenhof.circleimageview.CircleImageView;

public class Adapter_Conversation extends RecyclerView.Adapter<Adapter_Conversation.HolderAndroid> {

    private Context context;
    private ArrayList<Model_Conversation> androidArrayList;
   // private String id;

    public Adapter_Conversation(Context context, ArrayList<Model_Conversation> androidArrayList) {
        this.context = context;
        this.androidArrayList = androidArrayList;
    }

    @NonNull
    @Override
    public HolderAndroid onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.row_coversation, parent, false);
        return new HolderAndroid(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HolderAndroid holder, int position) {
        Model_Conversation modelAndroid = androidArrayList.get(position);

        boolean mic_status = modelAndroid.isMic_status();

        DatabaseReference userDB = FirebaseDatabase.getInstance().getReference().child("Users").child(modelAndroid.getId());
        userDB.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                    UserModel model = snapshot.getValue(UserModel.class);
                    if (model.getImageUrl().equals("")){
                        Picasso.with(context)
                                .load(R.drawable.profile)
                                .into(holder.image1);
                    }else {
                        Picasso.with(context)
                                .load(model.getImageUrl())
                                .into(holder.image1);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

      /*  holder.mic_on.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveMicStatus(false,modelAndroid.getId());
                holder.mic_off.setVisibility(View.VISIBLE);
                holder.mic_on.setVisibility(View.GONE);
                ((Conversation_Activity) context).rtcEngine().muteRemoteAudioStream(((Conversation_Activity) context).config().mUid,true);
            }
        });
        holder.mic_off.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveMicStatus(true,modelAndroid.getId());
                ((Conversation_Activity) context).rtcEngine().muteRemoteAudioStream(((Conversation_Activity) context).config().mUid,false);
                holder.mic_on.setVisibility(View.VISIBLE);
                holder.mic_off.setVisibility(View.GONE);
            }
        });*/
        holder.mic_on.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onLocalAudioMuteClicked(view,modelAndroid.getId());
            }
        });
        if (mic_status){
            //holder.mic_on.setVisibility(View.VISIBLE);
            //holder.mic_off.setVisibility(View.GONE);
            ((Dashboard) context).rtcEngine().muteRemoteAudioStream(((Dashboard) context)
                    .config().mUid,true);
        }else {
          //  holder.mic_off.setVisibility(View.VISIBLE);
            //holder.mic_on.setVisibility(View.GONE);
            ((Dashboard) context).rtcEngine().muteRemoteAudioStream(((Dashboard) context)
                    .config().mUid,false);
        }
    }

    // Tutorial Step 7
    private void onLocalAudioMuteClicked(View view, String id) {
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

        saveMicStatus(iv.isSelected(),id);
        // Stops/Resumes sending the local audio stream.
        ((Dashboard) context).rtcEngine().muteRemoteAudioStream(((Dashboard) context)
                .config().mUid,iv.isSelected());

    }

    private void saveMicStatus(boolean b, String uid) {
        DatabaseReference db = FirebaseDatabase.getInstance().getReference().child("Rooms");
        HashMap<String,Object> hashMap= new HashMap<>();
        hashMap.put("mic_status",b);
        db.child(uid).updateChildren(hashMap);
    }

    @Override
    public int getItemCount() {
        return androidArrayList.size();
    }

    class HolderAndroid extends RecyclerView.ViewHolder {

        private CircleImageView image1;
        private ImageView mic_on;

        HolderAndroid(@NonNull View itemView) {
            super(itemView);

            image1 = itemView.findViewById(R.id.conversation_img);
            mic_on = itemView.findViewById(R.id.mic_on);
         //   mic_off = itemView.findViewById(R.id.mic_off);

        }
    }

}
