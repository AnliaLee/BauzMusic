package com.anlia.bauzmusic.demo;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.anlia.bauzmusic.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by anlia on 2018/3/8.
 */

public class MusicService extends MediaBrowserServiceCompat {
    private MediaSessionCompat mSession;
    private MediaPlayer mMediaPlayer;
    private PlaybackStateCompat mPlaybackState;

    private static final String TAG = "MusicService";
    public static final String MEDIA_ID_ROOT = "__ROOT__";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG,"onCreate-----------");

        mPlaybackState = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE,0,1.0f)
                .build();

        mSession = new MediaSessionCompat(this,"MusicService");
        mSession.setCallback(SessionCallback);
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mSession.setPlaybackState(mPlaybackState);

        //设置token后会触发MediaBrowserCompat.ConnectionCallback的回调方法
        //表示MediaBrowser与MediaBrowserService连接成功
        setSessionToken(mSession.getSessionToken());

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(PreparedListener);
        mMediaPlayer.setOnCompletionListener(CompletionListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mSession != null) {
            mSession.release();
            mSession = null;
        }
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        Log.e(TAG,"onGetRoot-----------");
        return new BrowserRoot(MEDIA_ID_ROOT, null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
        Log.e(TAG,"onLoadChildren--------");
        //将信息从当前线程中移除，允许后续调用sendResult方法
        result.detach();

        //我们模拟获取数据的过程，真实情况应该是异步从网络或本地读取数据
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, ""+R.raw.jinglebells)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "圣诞歌")
                .build();
        ArrayList<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        mediaItems.add(createMediaItem(metadata));

        //向Browser发送数据
        result.sendResult(mediaItems);
    }

    private MediaBrowserCompat.MediaItem createMediaItem(MediaMetadataCompat metadata){
        return new MediaBrowserCompat.MediaItem(
                metadata.getDescription(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        );
    }

    /**
     * 响应控制器指令的回调
     */
    private android.support.v4.media.session.MediaSessionCompat.Callback SessionCallback = new MediaSessionCompat.Callback(){
        /**
         * 响应MediaController.getTransportControls().play
         */
        @Override
        public void onPlay() {
            Log.e(TAG,"onPlay");
            if(mPlaybackState.getState() == PlaybackStateCompat.STATE_PAUSED){
                mMediaPlayer.start();
                mPlaybackState = new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PLAYING,0,1.0f)
                        .build();
                mSession.setPlaybackState(mPlaybackState);
            }
        }

        /**
         * 响应MediaController.getTransportControls().onPause
         */
        @Override
        public void onPause() {
            Log.e(TAG,"onPause");
            if(mPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING){
                mMediaPlayer.pause();
                mPlaybackState = new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PAUSED,0,1.0f)
                        .build();
                mSession.setPlaybackState(mPlaybackState);
            }
        }

        /**
         * 响应MediaController.getTransportControls().playFromUri
         * @param uri
         * @param extras
         */
        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            Log.e(TAG,"onPlayFromUri");
            try {
                switch (mPlaybackState.getState()){
                    case PlaybackStateCompat.STATE_PLAYING:
                    case PlaybackStateCompat.STATE_PAUSED:
                    case PlaybackStateCompat.STATE_NONE:
                        mMediaPlayer.reset();
                        mMediaPlayer.setDataSource(MusicService.this,uri);
                        mMediaPlayer.prepare();//准备同步
                        mPlaybackState = new PlaybackStateCompat.Builder()
                                .setState(PlaybackStateCompat.STATE_CONNECTING,0,1.0f)
                                .build();
                        mSession.setPlaybackState(mPlaybackState);
                        //我们可以保存当前播放音乐的信息，以便客户端刷新UI
                        mSession.setMetadata(new MediaMetadataCompat.Builder()
                                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,extras.getString("title"))
                                .build()
                        );
                        break;
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }

        @Override
        public void onPlayFromSearch(String query, Bundle extras) {
        }
    };

    /**
     * 监听MediaPlayer.prepare()
     */
    private MediaPlayer.OnPreparedListener PreparedListener = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mediaPlayer) {
            mMediaPlayer.start();
            mPlaybackState = new PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING,0,1.0f)
                    .build();
            mSession.setPlaybackState(mPlaybackState);
        }
    } ;

    /**
     * 监听播放结束的事件
     */
    private MediaPlayer.OnCompletionListener CompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            mPlaybackState = new PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE,0,1.0f)
                    .build();
            mSession.setPlaybackState(mPlaybackState);
            mMediaPlayer.reset();
        }
    };
}
