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
                new ComponentName(this, MusicService.class),//绑定浏览器服务
                BrowserConnectionCallback,//设置连接回调
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
        //Browser发送连接请求
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

    /**
     * 连接状态的回调接口，连接成功时会调用onConnected()方法
     */
    private MediaBrowserCompat.ConnectionCallback BrowserConnectionCallback =
            new MediaBrowserCompat.ConnectionCallback(){
                @Override
                public void onConnected() {
                    Log.e(TAG,"onConnected------");
                    if (mBrowser.isConnected()) {
                        //mediaId即为MediaBrowserService.onGetRoot的返回值
                        //若Service允许客户端连接，则返回结果不为null，其值为数据内容层次结构的根ID
                        //若拒绝连接，则返回null
                        String mediaId = mBrowser.getRoot();

                        //Browser通过订阅的方式向Service请求数据，发起订阅请求需要两个参数，其一为mediaId
                        //而如果该mediaId已经被其他Browser实例订阅，则需要在订阅之前取消mediaId的订阅者
                        //虽然订阅一个 已被订阅的mediaId 时会取代原Browser的订阅回调，但却无法触发onChildrenLoaded回调

                        //ps：虽然基本的概念是这样的，但是Google在官方demo中有这么一段注释...
                        // This is temporary: A bug is being fixed that will make subscribe
                        // consistently call onChildrenLoaded initially, no matter if it is replacing an existing
                        // subscriber or not. Currently this only happens if the mediaID has no previous
                        // subscriber or if the media content changes on the service side, so we need to
                        // unsubscribe first.
                        //大概的意思就是现在这里还有BUG，即只要发送订阅请求就会触发onChildrenLoaded回调
                        //所以无论怎样我们发起订阅请求之前都需要先取消订阅
                        mBrowser.unsubscribe(mediaId);
                        //之前说到订阅的方法还需要一个参数，即设置订阅回调SubscriptionCallback
                        //当Service获取数据后会将数据发送回来，此时会触发SubscriptionCallback.onChildrenLoaded回调
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
     * 向媒体浏览器服务(MediaBrowserService)发起数据订阅请求的回调接口
     */
    private final MediaBrowserCompat.SubscriptionCallback BrowserSubscriptionCallback =
            new MediaBrowserCompat.SubscriptionCallback(){
                @Override
                public void onChildrenLoaded(@NonNull String parentId,
                                             @NonNull List<MediaBrowserCompat.MediaItem> children) {
                    Log.e(TAG,"onChildrenLoaded------");
                    //children 即为Service发送回来的媒体数据集合
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
                /***
                 * 音乐播放状态改变的回调
                 * @param state
                 */
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

                /**
                 * 播放音乐改变的回调
                 * @param metadata
                 */
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
