package com.library.live.file;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.text.TextUtils;

import com.library.common.WriteCallback;
import com.library.util.mLog;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by android1 on 2017/10/20.
 */

public class WriteMp4 {
    private MediaMuxer mMediaMuxer = null;
    public static final int video = 0;
    public static final int voice = 1;

    private final String dirpath = Environment.getExternalStorageDirectory().getPath() + File.separator + "VideoLive";
    private String path = null;
    private boolean isHasPath = false;
    private MediaFormat videoFormat = null;
    private MediaFormat voiceFormat = null;

    private WriteCallback writeCallback;

    private int videoTrackIndex;
    private int voiceTrackIndex;
    private long presentationTimeUsVD = 0;
    private long presentationTimeUsVE = 0;

    private boolean agreeWrite = false;
    private boolean isSendReady = true;
    private boolean isReady = false;

    private boolean shouldStop = false;
    private boolean isCanStop = false;
    private boolean isCanStar = true;

    private int frameNum = 0;

    public WriteMp4(String path) {
        if (!TextUtils.isEmpty(path) && !path.equals("")) {
            this.path = path;
            isHasPath = true;
        }
    }

    public void addTrack(MediaFormat mediaFormat, int flag) {
        if (flag == video) {
            videoFormat = mediaFormat;
        } else if (flag == voice) {
            voiceFormat = mediaFormat;
        }
        setReady();
    }

    private void setReady() {
        if (videoFormat != null && voiceFormat != null) {
            isReady = true;
            if (writeCallback != null && isSendReady) {
                isSendReady = false;
                writeCallback.isReady();
            }
        }
    }

    public void write(int flag, ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo) {
        if (agreeWrite) {
            if (flag == video) {
                if (bufferInfo.presentationTimeUs > presentationTimeUsVD) {//容错
                    presentationTimeUsVD = bufferInfo.presentationTimeUs;

                    mMediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo);
                    frameNum++;
                    if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                        isCanStop = true;
                        if (shouldStop) {
                            stop();
                        }
                    }
                }
            } else if (flag == voice) {
                if (bufferInfo.presentationTimeUs > presentationTimeUsVE) {//容错
                    presentationTimeUsVE = bufferInfo.presentationTimeUs;

                    mMediaMuxer.writeSampleData(voiceTrackIndex, outputBuffer, bufferInfo);
                }
            }
        }
    }

    public void start() {
        if (isReady) {
            if (isCanStar) {
                setPath();
                try {
                    mMediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    videoTrackIndex = mMediaMuxer.addTrack(videoFormat);
                    voiceTrackIndex = mMediaMuxer.addTrack(voiceFormat);
                    mMediaMuxer.start();
                    presentationTimeUsVE = 0;
                    presentationTimeUsVD = 0;
                    frameNum = 0;
                    agreeWrite = true;
                    isCanStar = false;
                    isCanStop = false;
                    shouldStop = false;
                    if (writeCallback != null) {
                        writeCallback.isStart();
                    }
                    mLog.log("app_WriteMp4", "文件录制启动");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                shouldStop = false;
            }
        }
    }

    private void setPath() {
        if (isHasPath) {
            File file = new File(path);
            if (file.exists()) {
                file.delete();
            }
        } else {
            File dirfile = new File(dirpath);
            if (!dirfile.exists()) {
                dirfile.mkdirs();
            }
            path = dirpath + File.separator + System.currentTimeMillis() + ".mp4";
        }
    }

    public void stop() {
        if (agreeWrite) {
            if (isCanStop) {
                agreeWrite = false;
                mMediaMuxer.release();
                mMediaMuxer = null;
                if (writeCallback != null) {
                    writeCallback.isDestroy();
                }
                mLog.log("app_WriteMp4", "文件录制关闭");
                isCanStar = true;
                //文件过短删除
                if (frameNum < 20) {
                    new File(path).delete();
                    if (writeCallback != null) {
                        writeCallback.fileShort();
                    }
                }
            } else {
                shouldStop = true;
            }
        }
    }

    public void destroy() {
        stop();
        writeCallback = null;
    }

    public void setWriteCallback(WriteCallback writeCallback) {
        this.writeCallback = writeCallback;
        if (isReady && isSendReady) {
            isSendReady = false;
            writeCallback.isReady();
        }
    }
}
