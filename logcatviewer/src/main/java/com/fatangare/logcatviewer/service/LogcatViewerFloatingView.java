/**
 * Copyright (C) 2016  Sandeep Fatangare <sandeep@fatangare.info>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.fatangare.logcatviewer.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import android.content.Intent;
import android.graphics.Color;
import android.support.annotation.WorkerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.fatangare.logcatviewer.R;
import com.fatangare.logcatviewer.ui.adapter.LogcatViewerListAdapter;
import com.fatangare.logcatviewer.utils.Constants;

import wei.mark.standout.StandOutWindow;
import wei.mark.standout.constants.StandOutFlags;
import wei.mark.standout.ui.Window;

/**
 * Floating view to show logcat logs.
 */
public class LogcatViewerFloatingView extends StandOutWindow {
    private static final String LOG_TAG = "LogcatFloatingView";

    //Views
    private ListView mListView;
    private LogcatViewerListAdapter mAdapter;

    private LinearLayout mMenuOptionLayout;
    private LinearLayout mFilterLayout;
    private RadioGroup mPriorityLevelRadioGroup;
    private ListView mRecordsListView;
    private LinearLayout mNormalBottombarLayout;
    private LinearLayout mRecordsBottombarLayout;

    private final Object syncLock = new Object();

    private volatile boolean mShouldLogcatRunnableBeKilled = false;

    private Thread logcatThread;

    @Override
    public String getAppName() {
        return getString(getApplicationInfo().labelRes);
    }

    @Override
    public int getAppIcon() {
        return getApplicationInfo().icon;
    }

    private Runnable mLogcatRunnable = new Runnable() {
        @Override
        public void run() {
            //Run logcat subscriber to subscribe for logcat log entries
            runLogcatSubscriber();
        }
    };

    /**
     * Subscribe logcat to listen logcat log entries.
     */
    @WorkerThread
    private void runLogcatSubscriber() {
        Process process;

        //Execute logcat system command
        try {
            process = Runtime.getRuntime().exec("/system/bin/logcat -b " + Constants.LOGCAT_SOURCE_BUFFER_MAIN);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Exception trying to exec logcat in a process", e);
            return;
        }

        //Read logcat log entries
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            //Till request to kill thread is not received, keep reading log entries
            while (!mShouldLogcatRunnableBeKilled) {

                //Read log entry.
                final String logEntry = reader.readLine();

                if (logEntry == null) {
                    Log.e(LOG_TAG, "process buffer read line was null.");
                    if (mListView != null) {
                        mListView.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(LogcatViewerFloatingView.this, "Error with logcat process.", Toast.LENGTH_LONG)
                                    .show();
                            }
                        });
                    }
                    stopSelf();
                    return;
                }

                //Send log entry to view.
                synchronized (syncLock) {
                    mListView.post(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.addLogEntry(logEntry);
                        }
                    });
                }
            }

        } catch (IOException e) { //  interupted is an io exception
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        mShouldLogcatRunnableBeKilled = true;
        killThread();
    }

    @Override
    public void createAndAttachView(int id, FrameLayout frame) {
        Log.d(LOG_TAG, "createAndAttachView");

        // create a new layout from body.xml
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        final View rootView = inflater.inflate(R.layout.main, frame, true);

        //Main layout for showing views specific to menu-action e.g. filterLayout or PriorityLevelRadioGroup.
        mMenuOptionLayout = (LinearLayout) rootView.findViewById(R.id.menuOptionsLayout);
        //Layout for "Enter filter text"
        mFilterLayout = (LinearLayout) rootView.findViewById(R.id.filterLayout);
        //Radio group containing different priority levels.
        mPriorityLevelRadioGroup = (RadioGroup) rootView.findViewById(R.id.rgPriorityLevels);
        //View for showing recorded logs.
        mRecordsListView = (ListView) mMenuOptionLayout.findViewById(R.id.recordList);
        mRecordsListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        // Bottombar layouts
        mNormalBottombarLayout = (LinearLayout) rootView.findViewById(R.id.normalbottombar);
        mRecordsBottombarLayout = (LinearLayout) rootView.findViewById(R.id.recordsbottombar);

        setupLogListView(rootView);
        setupBottomBarView(rootView);

        setupRecordListView();
        setupFilterTextView(rootView);
        setupPriorityLevelView();

        killThread();

        logcatThread = new Thread(mLogcatRunnable);
        logcatThread.start();
    }

    void killThread() {
        if (logcatThread != null && logcatThread.isAlive()) {
            logcatThread.interrupt();
        }
    }

    // the window will be centered
    @Override
    public StandOutLayoutParams getParams(int id, Window window) {
        DisplayMetrics displayMetrics = getApplicationContext().getResources().getDisplayMetrics();
        return new StandOutLayoutParams(id, displayMetrics.widthPixels * 5 / 6, displayMetrics.heightPixels / 2,
            StandOutLayoutParams.RIGHT, StandOutLayoutParams.BOTTOM, 400, 300);
    }

    // move the window by dragging the view
    @Override
    public int getFlags(int id) {
        return StandOutFlags.FLAG_DECORATION_SYSTEM | StandOutFlags.FLAG_BODY_MOVE_ENABLE | StandOutFlags
            .FLAG_WINDOW_HIDE_ENABLE | StandOutFlags.FLAG_WINDOW_BRING_TO_FRONT_ON_TAP | StandOutFlags
            .FLAG_WINDOW_EDGE_LIMITS_ENABLE | StandOutFlags.FLAG_WINDOW_PINCH_RESIZE_ENABLE;
    }

    @Override
    public String getPersistentNotificationMessage(int id) {
        return "Show LogcatViewer floating view.";
    }

    @Override
    public Intent getPersistentNotificationIntent(int id) {
        return StandOutWindow.getShowIntent(this, LogcatViewerFloatingView.class, id);
    }

    /**
     * Hide all menu specific views.
     */
    private void resetMenuOptionLayout() {
        mFilterLayout.setVisibility(View.GONE);
        mPriorityLevelRadioGroup.setVisibility(View.GONE);
        mRecordsListView.setVisibility(View.GONE);
        mMenuOptionLayout.setVisibility(View.GONE);
    }

    /**
     * Setup list view to show logcat log-entries.
     *
     * @param rootView root view.
     */
    private void setupLogListView(final View rootView) {
        //Log entry list view
        mListView = (ListView) rootView.findViewById(R.id.list);
        mListView.setStackFromBottom(true);
        mListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        mAdapter = new LogcatViewerListAdapter(getApplicationContext());
        mListView.setAdapter(mAdapter);
    }

    /**
     * Setup bottombar view to show action buttons.
     *
     * @param rootView root view.
     */
    private void setupBottomBarView(final View rootView) {

        //'Enter filter text' button
        rootView.findViewById(R.id.find).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int filterLayoutVisibility = mFilterLayout.getVisibility();
                resetMenuOptionLayout();
                if (filterLayoutVisibility == View.GONE) {
                    mFilterLayout.setVisibility(View.VISIBLE);
                    mMenuOptionLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        //'Select priority level' button
        rootView.findViewById(R.id.btnPriorityLevel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int priorityLevelRadioGroupVisibility = mPriorityLevelRadioGroup.getVisibility();
                resetMenuOptionLayout();

                if (priorityLevelRadioGroupVisibility == View.GONE) {
                    mPriorityLevelRadioGroup.setVisibility(View.VISIBLE);
                    mMenuOptionLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        //'Reset log-entries' button
        rootView.findViewById(R.id.btnReset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                synchronized (syncLock) {
                    mAdapter.reset();
                }
                resetMenuOptionLayout();
            }
        });
    }

    /**
     * Setup 'Saved Logs' layout.
     */
    private void setupRecordListView() {
        mRecordsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (mRecordsListView.getCheckedItemPositions().get(i, false)) {
                    view.setBackgroundColor(Color.LTGRAY);
                } else {
                    view.setBackgroundColor(Color.WHITE);
                }
            }
        });

        //'Back' button
        mRecordsBottombarLayout.findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int cnt = mRecordsListView.getAdapter().getCount();

                for (int index = 0; index < cnt; index++) {
                    mRecordsListView.setItemChecked(index, false);
                    getViewByPosition(index, mRecordsListView).setBackgroundColor(Color.WHITE);
                }

                resetMenuOptionLayout();
                mNormalBottombarLayout.setVisibility(View.VISIBLE);
                mRecordsBottombarLayout.setVisibility(View.GONE);
            }
        });

        //'Select All' button
        mRecordsBottombarLayout.findViewById(R.id.btnSelectAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean isSelectingAll = mRecordsListView.getCheckedItemCount() != mRecordsListView.getAdapter().getCount();
                int color = isSelectingAll ? Color.LTGRAY : Color.WHITE;
                int cnt = mRecordsListView.getAdapter().getCount();
                for (int index = 0; index < cnt; index++) {
                    mRecordsListView.setItemChecked(index, isSelectingAll);
                    getViewByPosition(index, mRecordsListView).setBackgroundColor(color);
                }
            }
        });

    }

    /**
     * Setup 'Enter filter text' layout.
     *
     * @param rootView root view.
     */
    private void setupFilterTextView(final View rootView) {
        rootView.findViewById(R.id.btnLogFilter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String filterText = ((EditText) rootView.findViewById(R.id.etLogFilter)).getText().toString().trim();
                resetMenuOptionLayout();
                synchronized (syncLock) {
                    mAdapter.setLogFilterText(filterText);
                }
            }
        });
    }

    /**
     * Setup 'Select priority level' view
     */
    private void setupPriorityLevelView() {
        mPriorityLevelRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                //Get priority level based on selection.
                String priorityLevel;
                if (checkedId == R.id.radioDebug) {
                    priorityLevel = LogcatViewerListAdapter.PRIORITY_LEVEL_DEBUG;

                } else if (checkedId == R.id.radioInfo) {
                    priorityLevel = LogcatViewerListAdapter.PRIORITY_LEVEL_INFO;

                } else if (checkedId == R.id.radioWarning) {
                    priorityLevel = LogcatViewerListAdapter.PRIORITY_LEVEL_WARNING;

                } else if (checkedId == R.id.radioError) {
                    priorityLevel = LogcatViewerListAdapter.PRIORITY_LEVEL_ERROR;

                } else {
                    priorityLevel = "";

                }
                //Set current priority level.
                mAdapter.setLogPriorityLevel(priorityLevel);
                //Hide all menu option layouts.
                resetMenuOptionLayout();
            }
        });
    }

    /**
     * Get list item view by position
     *
     * @param pos      position of list item.
     * @param listView listview object.
     * @return list item view associated with position in the listview.
     */
    private View getViewByPosition(int pos, ListView listView) {
        final int firstListItemPosition = listView.getFirstVisiblePosition();
        final int lastListItemPosition = firstListItemPosition + listView.getChildCount() - 1;

        if (pos < firstListItemPosition || pos > lastListItemPosition) {
            return listView.getAdapter().getView(pos, null, listView);
        } else {
            final int childIndex = pos - firstListItemPosition;
            return listView.getChildAt(childIndex);
        }
    }

}
