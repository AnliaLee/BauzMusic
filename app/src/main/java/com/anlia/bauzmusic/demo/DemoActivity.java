package com.anlia.bauzmusic.demo;

import android.content.ComponentName;
import android.net.Uri;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anlia.bauzmusic.R;

import java.util.ArrayList;
import java.util.List;

public class DemoActivity extends AppCompatActivity {
    private static final String TAG = "DemoActivity";

    private Button btnPlay;
    private TextView textTitle;

    private RecyclerView recyclerView;
    private List<MediaBrowserCompat.MediaItem> list;
    private DemoAdapter demoAdapter;
    private LinearLayoutManager layoutManager;

    private MediaBrowserCompat mBrowser;
    private MediaControllerCompat mController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        mBrowser = new MediaBrowserCompat(
                this,
                new ComponentName(this, MusicService.class),
                BrowserConnectionCallback,
                null
        );

        btnPlay = (Button) findViewById(R.id.btn_play);
        textTitle = (TextView) findViewById(R.id.text_title);

        list = new ArrayList<>();
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        demoAdapter = new DemoAdapter(this,list);
        demoAdapter.setOnItemClickListener(new DemoAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                Bundle bundle = new Bundle();
                bundle.putString("title",list.get(position).getDescription().getTitle().toString());
                mController.getTransportControls().playFromUri(
                        rawToUri(Integer.valueOf(list.get(position).getMediaId())),
                        bundle
                );
            }

            @Override
            public void onItemLongClick(View view, int position) {

            }
        });

        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(demoAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBrowser.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mBrowser.disconnect();
    }

    public void clickEvent(View view) {
    	switch (view.getId()) {
            case R.id.btn_play:
                if(mController!=null){
                    handlerPlayEvent();
                }
                break;
    	}
    }

    /**
     * 处理播放按钮事件
     */
    private void handlerPlayEvent(){
        switch (mController.getPlaybackState().getState()){
            case PlaybackStateCompat.STATE_PLAYING:
                mController.getTransportControls().pause();
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                mController.getTransportControls().play();
                break;
            default:
                mController.getTransportControls().playFromSearch("", null);
                break;
        }
    }

    private MediaBrowserCompat.ConnectionCallback BrowserConnectionCallback =
            new MediaBrowserCompat.ConnectionCallback(){
                @Override
                public void onConnected() {
                    Log.e(TAG,"onConnected------");
                    if (mBrowser.isConnected()) {
                        String mediaId = mBrowser.getRoot();
                        mBrowser.unsubscribe(mediaId);
                        mBrowser.subscribe(mediaId, BrowserSubscriptionCallback);

                        try{
                            mController = new MediaControllerCompat(DemoActivity.this,mBrowser.getSessionToken());
                            mController.registerCallback(ControllerCallback);
                        }catch (RemoteException e){
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onConnectionFailed() {
                    Log.e(TAG,"连接失败！");
                }
            };
    /**
     * 向媒体流量服务(MediaBrowserService)发起媒体浏览请求的回调接口
     */
    private final MediaBrowserCompat.SubscriptionCallback BrowserSubscriptionCallback =
            new MediaBrowserCompat.SubscriptionCallback(){
                @Override
                public void onChildrenLoaded(@NonNull String parentId,
                                             @NonNull List<MediaBrowserCompat.MediaItem> children) {
                    Log.e(TAG,"onChildrenLoaded------");
                    for (MediaBrowserCompat.MediaItem item:children){
                        Log.e(TAG,item.getDescription().getTitle().toString());
                        list.add(item);
                    }
                    demoAdapter.notifyDataSetChanged();
                }
            };

    /**
     * 媒体控制器控制播放过程中的回调接口，可以用来根据播放状态更新UI
     */
    private final MediaControllerCompat.Callback ControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    switch (state.getState()){
                        case PlaybackStateCompat.STATE_NONE://无任何状态
                            textTitle.setText("");
                            btnPlay.setText("开始");
                            break;
                        case PlaybackStateCompat.STATE_PAUSED:
                            btnPlay.setText("开始");
                            break;
                        case PlaybackStateCompat.STATE_PLAYING:
                            btnPlay.setText("暂停");
                            break;
                    }
                }

                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    textTitle.setText(metadata.getDescription().getTitle());
                }
            };

    private Uri rawToUri(int id){
        String uriStr = "android.resource://" + getPackageName() + "/" + id;
        return Uri.parse(uriStr);
    }

}
