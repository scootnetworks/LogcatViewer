/**
 * Copyright (C) 2016  Sandeep Fatangare <sandeep@fatangare.info>
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.fatangare.logcatviewer.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Vector;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.fatangare.logcatviewer.utils.Constants;

/**
 * Service to listen logcat logs.
 */
public class LogcatViewerService extends Service {
    private static final String LOG_TAG = "LogcatViewerService";

    private Handler mHandler;

    public interface LogEntryListener {
        void onLogEntryRead(String entry);
    }

    private LogEntryListener mLogEntryListener;

    /**
     * Logcat source buffer.
     */
    private String mLogcatSource = Constants.LOGCAT_SOURCE_BUFFER_MAIN;

    //Saving logs to file
    /**
     * File to which logs to be saved.
     */
    private String mRecordingFilename;

    /**
     * Logs are saved to file after every {@link LogcatViewerService#LOG_SAVING_INTERVAL} interval.
     * Hence log data should be saved for {@link LogcatViewerService#LOG_SAVING_INTERVAL} interval.
     * It should be cleared when data is saved to file.
     */
    private Vector<String> mRecordingData;

    /**
     * Log entries saved in log file.
     */
    private int mRecordedLogEntriesCount;

    //Threads
    private volatile static boolean mShouldLogcatRunnableBeKilled = false;
    private volatile boolean mIsLogcatRunnableRunning = false;

    //Status
    /**
     * Is recording logs active?
     */
    private boolean mIsRecording = false;

    /**
     * Is listening to logcat paused?
     */
    private boolean mIsPaused = false;

    private String mFilterText;

    /**
     * Interval after which logs are saved to file.
     */
    private static final int LOG_SAVING_INTERVAL = 5000; //5s

    private final IBinder mBinder = new LocalBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        LogcatViewerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return LogcatViewerService.this;
        }
    }

    // Handler Messages

    /**
     * Message indicating that executing logcat command is failed.
     */
    public static final int MSG_LOGCAT_RUN_FAILURE = 1;

    /**
     * Message indicating that reading logs for logcat is failed.
     */
    public static final int MSG_LOGCAT_READ_FAILURE = 2;

    /**
     * Message indicating that new log entry from logcat is received.
     */
    public static final int MSG_NEW_LOG_ENTRY = 3;

    private Runnable mLogcatRunnable = new Runnable() {
        @Override
        public void run() {
            mIsLogcatRunnableRunning = true;
            //Run logcat subscriber to subscribe for logcat log entries
            runLogcatSubscriber();
            //If reached here, it means thread is killed.
            mIsLogcatRunnableRunning = false;
        }
    };

    private Runnable mRecordLogEntryRunnable = new Runnable() {
        @Override
        public void run() {
            //save log entries
//            recordLogData();
            //wait for LOG_SAVING_INTERVAL before next 'record' operation.
//            mHandler.postDelayed(mRecordLogEntryRunnable, LOG_SAVING_INTERVAL);
        }
    };

    public void setLogListener(LogEntryListener listener){
        mLogEntryListener = listener;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int i = super.onStartCommand(intent, flags, startId);
        Log.i(LOG_TAG, "onStartCommand:service is started.");
        return i;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        //Kill mLogcatRunnable thread.
        requestToKillLogcatRunnableThread();

        //Till thread is not killed, wait
        while (mIsLogcatRunnableRunning) {
            Log.d(LOG_TAG, "onUnbind:Waiting to kill LogcatRunnable thread");
        }

        //Stop LogcatViewerService service.
        stopSelf();
        return false;
    }


    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Subscribe logcat to listen logcat log entries.
     */
    private void runLogcatSubscriber() {
        Process process = null;

        //Execute logcat system command
        try {
            process = Runtime.getRuntime().exec("/system/bin/logcat -b " + mLogcatSource);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Exception trying to exec logcat in a process", e);
//            sendMessage(MSG_LOGCAT_RUN_FAILURE);
            return;
        }

        //Read logcat log entries
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String logEntry;

            //Till request to kill thread is not received, keep reading log entries
            while (!shouldLogcatRunnableBeKilled()) {
                //if paused, ignore entries.
                if (mIsPaused) {
                    continue;
                }

                //Read log entry.
                logEntry = reader.readLine();

                if (logEntry == null) {
                    Log.d(LOG_TAG, "process buffer read line was null.");
                    continue;
                }

                //Send log entry to view.
//                sendLogEntry(logEntry);
                if(mLogEntryListener != null) {
                    mLogEntryListener.onLogEntryRead(logEntry);
                }

                //If recording is on, save log entries in mRecordingData in order to save them
                // after every LOG_SAVING_INTERVAL interval
                if (mIsRecording) {
                    if (TextUtils.isEmpty(mFilterText) || (!TextUtils.isEmpty(mFilterText) && logEntry.toLowerCase().contains
                        (mFilterText.toLowerCase()))) {
                        mRecordingData.add(logEntry);
                    }
                }
            }

            Log.d(LOG_TAG, "Preparing to terminate LogcatRunnable thread");
            //If recording is on, save log entries and reset recording related fields.
            if (mIsRecording) {
//                recordLogData();

//                mHandler.removeCallbacks(mRecordLogEntryRunnable);
                mIsRecording = false;
                mRecordingData.removeAllElements();
                mRecordingFilename = null;

            }

        } catch (IOException e) {
            //Fail to read logcat log entries
            Log.e(LOG_TAG, "Exception trying to read/parse the logcat process output", e);
        } finally {
            //Release resources
            try {
                reader.close();
                process.destroy();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Exception trying to clean up resources", e);
            }
        }

        Log.d(LOG_TAG, "Terminating LogcatRunnable thread");
    }

    /**
     * Make request to kill LogcatRunnable thread.
     */
    private synchronized void requestToKillLogcatRunnableThread() {
        mShouldLogcatRunnableBeKilled = true;
    }

    /**
     * Check if request to kill LogcatRunnable thread is made.
     *
     * @return true if request is made else false.
     */
    private synchronized boolean shouldLogcatRunnableBeKilled() {
        return mShouldLogcatRunnableBeKilled;
    }


    public void changeLogcatSource(String logcatSource) {
        mLogcatSource = logcatSource;
        restart();
    }

    public void restart() {
        //request to kill thread
        requestToKillLogcatRunnableThread();

        //Till thread is not killed, wait
        while (mIsLogcatRunnableRunning) {
            Log.d(LOG_TAG, "restart:Waiting to kill LogcatRunnable thread");
        }

        //since request to kill is completed, set mShouldLogcatRunnableBeKilled to false
        mShouldLogcatRunnableBeKilled = false;

        //Start new LogcatRunnable thread.
        Thread thr = new Thread(mLogcatRunnable);
        thr.start();
    }

    public void stop() {
        Log.d(LOG_TAG, "stop:request to stop LogcatViewerService service is made.");
        //Kill mLogcatRunnable thread.
        requestToKillLogcatRunnableThread();

        //Till thread is not killed, wait
        while (mIsLogcatRunnableRunning) {
            Log.d(LOG_TAG, "stop:Waiting to kill LogcatRunnable thread");
        }

        //Stop LogcatViewerService service.
        stopSelf();
    }

    /*public void startRecording(String recordingFilename, String filterText) {
        mRecordingData = new Vector<>();
        mIsRecording = true;
        mRecordingFilename = recordingFilename;
        mFilterText = filterText;
        mHandler.postDelayed(mRecordLogEntryRunnable, LOG_SAVING_INTERVAL);
    }

    public void stopRecording() {
        mHandler.removeCallbacks(mRecordLogEntryRunnable);
        mIsRecording = false;
        mRecordingData.removeAllElements();
        mRecordingFilename = null;
    }*/

    public boolean isRecording() {
        return mIsRecording;
    }

    public void pause() {
        mIsPaused = true;
    }

    public void resume() {
        mIsPaused = false;
    }
}
