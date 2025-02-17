/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media2.widget;

import static android.content.Context.KEYGUARD_SERVICE;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;
import androidx.media2.common.FileMediaItem;
import androidx.media2.common.MediaItem;
import androidx.media2.common.SessionPlayer;
import androidx.media2.common.UriMediaItem;
import androidx.media2.player.MediaPlayer;
import androidx.media2.widget.test.R;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;

/**
 * Test {@link VideoView}.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class VideoViewTest {

    /** Debug TAG. **/
    private static final String TAG = "VideoViewTest";
    /** The maximum time to wait for an operation. */
    private static final long TIME_OUT = 1000L;

    private Context mContext;
    private Executor mMainHandlerExecutor;
    private Instrumentation mInstrumentation;

    private Activity mActivity;
    private VideoView mVideoView;
    private MediaItem mMediaItem;
    private SessionPlayer.PlayerCallback mPlayerCallback;
    private SessionPlayer mPlayer;

    @Rule
    public ActivityTestRule<VideoViewTestActivity> mActivityRule =
            new ActivityTestRule<>(VideoViewTestActivity.class);

    @Before
    public void setup() throws Throwable {
        mContext = ApplicationProvider.getApplicationContext();
        mMainHandlerExecutor = ContextCompat.getMainExecutor(mContext);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();

        mActivity = mActivityRule.getActivity();
        mVideoView = mActivity.findViewById(R.id.videoview);
        mMediaItem = createTestMediaItem2();

        setKeepScreenOn();
        checkAttachedToWindow();

        mPlayerCallback = mock(SessionPlayer.PlayerCallback.class);
        mPlayer = new MediaPlayer(mContext);
        mPlayer.registerPlayerCallback(mMainHandlerExecutor, mPlayerCallback);
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVideoView.setPlayer(mPlayer);
            }
        });
    }

    @After
    public void tearDown() throws Throwable {
        mPlayer.close();
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new VideoView(mActivity);
        new VideoView(mActivity, null);
        new VideoView(mActivity, null, 0);
    }

    @Test
    public void testPlayVideo() throws Throwable {
        waitToPrepare(mMediaItem);
        verify(mPlayerCallback, timeout(TIME_OUT).atLeastOnce()).onCurrentMediaItemChanged(
                any(SessionPlayer.class), any(MediaItem.class));
        verify(mPlayerCallback, timeout(TIME_OUT).atLeastOnce()).onPlayerStateChanged(
                any(SessionPlayer.class), eq(SessionPlayer.PLAYER_STATE_PAUSED));
        verify(mPlayerCallback, after(TIME_OUT).never()).onPlayerStateChanged(
                any(SessionPlayer.class), eq(SessionPlayer.PLAYER_STATE_PLAYING));
        assertEquals(SessionPlayer.PLAYER_STATE_PAUSED, mPlayer.getPlayerState());

        mPlayer.play();
        verify(mPlayerCallback, timeout(TIME_OUT).atLeastOnce()).onPlayerStateChanged(
                any(SessionPlayer.class), eq(SessionPlayer.PLAYER_STATE_PLAYING));
    }

    @Test
    public void testPlayVideoWithMediaItemFromFileDescriptor() throws Throwable {
        AssetFileDescriptor afd = mContext.getResources()
                .openRawResourceFd(R.raw.testvideo_with_2_subtitle_tracks);
        final MediaItem item = new FileMediaItem.Builder(
                ParcelFileDescriptor.dup(afd.getFileDescriptor()))
                .setFileDescriptorOffset(afd.getStartOffset())
                .setFileDescriptorLength(afd.getLength())
                .build();
        afd.close();

        waitToPrepare(item);
        verify(mPlayerCallback, timeout(TIME_OUT).atLeastOnce()).onCurrentMediaItemChanged(
                any(SessionPlayer.class), eq(item));
        verify(mPlayerCallback, timeout(TIME_OUT).atLeastOnce()).onPlayerStateChanged(
                any(SessionPlayer.class), eq(SessionPlayer.PLAYER_STATE_PAUSED));

        mPlayer.play();
        verify(mPlayerCallback, timeout(TIME_OUT).atLeastOnce()).onPlayerStateChanged(
                any(SessionPlayer.class), eq(SessionPlayer.PLAYER_STATE_PLAYING));
    }

    @Test
    public void testPlayVideoOnTextureView() throws Throwable {
        final VideoView.OnViewTypeChangedListener mockViewTypeListener =
                mock(VideoView.OnViewTypeChangedListener.class);

        // The default view type is surface view.
        assertEquals(mVideoView.getViewType(), mVideoView.VIEW_TYPE_SURFACEVIEW);

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVideoView.setOnViewTypeChangedListener(mockViewTypeListener);
                mVideoView.setViewType(mVideoView.VIEW_TYPE_TEXTUREVIEW);
            }
        });
        waitToPrepare(mMediaItem);
        verify(mockViewTypeListener, timeout(TIME_OUT))
                .onViewTypeChanged(mVideoView, VideoView.VIEW_TYPE_TEXTUREVIEW);
        verify(mPlayerCallback, timeout(TIME_OUT).atLeastOnce()).onCurrentMediaItemChanged(
                any(SessionPlayer.class), any(MediaItem.class));
        verify(mPlayerCallback, timeout(TIME_OUT).atLeast(1)).onPlayerStateChanged(
                any(SessionPlayer.class), eq(SessionPlayer.PLAYER_STATE_PAUSED));

        mPlayer.play();
        verify(mPlayerCallback, timeout(TIME_OUT).atLeast(1)).onPlayerStateChanged(
                any(SessionPlayer.class), eq(SessionPlayer.PLAYER_STATE_PLAYING));
    }

    @Test
    public void testSetViewType() throws Throwable {
        final VideoView.OnViewTypeChangedListener mockViewTypeListener =
                mock(VideoView.OnViewTypeChangedListener.class);

        // The default view type is surface view.
        assertEquals(mVideoView.getViewType(), mVideoView.VIEW_TYPE_SURFACEVIEW);

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVideoView.setOnViewTypeChangedListener(mockViewTypeListener);
                mVideoView.setViewType(mVideoView.VIEW_TYPE_TEXTUREVIEW);
                mVideoView.setViewType(mVideoView.VIEW_TYPE_SURFACEVIEW);
                mVideoView.setViewType(mVideoView.VIEW_TYPE_TEXTUREVIEW);
                mVideoView.setViewType(mVideoView.VIEW_TYPE_SURFACEVIEW);
            }
        });

        waitToPrepare(mMediaItem);
        verify(mPlayerCallback, timeout(TIME_OUT).atLeastOnce()).onCurrentMediaItemChanged(
                any(SessionPlayer.class), any(MediaItem.class));
        // TIME_OUT multiplied by the number of operations.
        verify(mPlayerCallback, timeout(TIME_OUT * 5).atLeast(1)).onPlayerStateChanged(
                any(SessionPlayer.class), eq(SessionPlayer.PLAYER_STATE_PAUSED));
        assertEquals(mVideoView.getViewType(), mVideoView.VIEW_TYPE_SURFACEVIEW);

        mPlayer.play();
        verify(mPlayerCallback, timeout(TIME_OUT).atLeastOnce()).onPlayerStateChanged(
                any(SessionPlayer.class), eq(SessionPlayer.PLAYER_STATE_PLAYING));

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVideoView.setViewType(mVideoView.VIEW_TYPE_TEXTUREVIEW);
            }
        });
        verify(mockViewTypeListener, timeout(TIME_OUT))
                .onViewTypeChanged(mVideoView, VideoView.VIEW_TYPE_TEXTUREVIEW);
    }

    private void setKeepScreenOn() throws Throwable {
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= 27) {
                    mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    mActivity.setTurnScreenOn(true);
                    mActivity.setShowWhenLocked(true);
                    KeyguardManager keyguardManager = (KeyguardManager)
                            mInstrumentation.getTargetContext().getSystemService(KEYGUARD_SERVICE);
                    keyguardManager.requestDismissKeyguard(mActivity, null);
                } else {
                    mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
                }
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    private void checkAttachedToWindow() {
        final View.OnAttachStateChangeListener mockAttachListener =
                mock(View.OnAttachStateChangeListener.class);
        if (!mVideoView.isAttachedToWindow()) {
            mVideoView.addOnAttachStateChangeListener(mockAttachListener);
            verify(mockAttachListener, timeout(TIME_OUT)).onViewAttachedToWindow(same(mVideoView));
        }
    }

    private void waitToPrepare(MediaItem item) throws Exception {
        mPlayer.setMediaItem(item);
        mPlayer.prepare().get();
    }

    private MediaItem createTestMediaItem2() {
        Uri testVideoUri = Uri.parse(
                "android.resource://" + mContext.getPackageName() + "/"
                        + R.raw.testvideo_with_2_subtitle_tracks);
        return new UriMediaItem.Builder(testVideoUri).build();
    }
}
