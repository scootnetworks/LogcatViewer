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

import java.io.File;
import java.util.ArrayList;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseBooleanArray;
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
import com.fatangare.logcatviewer.service.LogcatViewerService.LocalBinder;
import com.fatangare.logcatviewer.service.LogcatViewerService.LogEntryListener;
import com.fatangare.logcatviewer.ui.adapter.LogRecordsListAdapter;
import com.fatangare.logcatviewer.ui.adapter.LogcatViewerListAdapter;

import wei.mark.standout.StandOutWindow;
import wei.mark.standout.constants.StandOutFlags;
import wei.mark.standout.ui.Window;

/**
 * Floating view to show logcat logs.
 */
public class LogcatViewerFloatingView extends StandOutWindow implements LogEntryListener {
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

    //Service
    LogcatViewerService mService;

    //Service connection
    private ServiceConnection mLogcatViewerServiceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {

            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            mService.setLogListener(LogcatViewerFloatingView.this);

            try {
                mService.restart();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Could not start LogcatViewerService service");
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.i(LOG_TAG, "onServiceDisconnected has been called");
            mService = null;
        }
    };

    @Override
    public String getAppName() {
        return getString(getApplicationInfo().labelRes);
    }

    @Override
    public int getAppIcon() {
        return getApplicationInfo().icon;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //bind service
        bindService(new Intent(this, LogcatViewerService.class), mLogcatViewerServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        //if recording, stop recording before unbinding service.
        if (mService != null) {
            mService.setLogListener(null);
            mService.stop();
        }

        unbindService(mLogcatViewerServiceConnection);
        super.onDestroy();
    }

    @Override
    public void createAndAttachView(int id, FrameLayout frame) {
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
     * Pause listening to logcat logs.
     */
    private void pauseLogging() {
        mService.pause();
    }

    /**
     * Resume listening to logcat logs.
     */
    private void resumeLogging() {
        mService.resume();
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
        //Pause button
        rootView.findViewById(R.id.pause).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pauseLogging();
                view.setVisibility(View.GONE);
                rootView.findViewById(R.id.play).setVisibility(View.VISIBLE);
            }
        });

        //Play button
        rootView.findViewById(R.id.play).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resumeLogging();
                view.setVisibility(View.GONE);
                rootView.findViewById(R.id.pause).setVisibility(View.VISIBLE);
            }
        });



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
                pauseLogging();
                mAdapter.reset();
                resumeLogging();
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

        //'Delete' button
        mRecordsBottombarLayout.findViewById(R.id.btnDelete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteRecordedLogFiles();

                if (mRecordsListView.getCount() == 0) {
                    resetMenuOptionLayout();
                    mNormalBottombarLayout.setVisibility(View.VISIBLE);
                    mRecordsBottombarLayout.setVisibility(View.GONE);
                }
            }
        });

        //'Share' button
        mRecordsBottombarLayout.findViewById(R.id.btnShare).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shareRecordedLogFiles();
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

                pauseLogging();
                mAdapter.setLogFilterText(filterText);
                resumeLogging();
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
     * Share selected 'Saved Logs' files.
     */
    private void shareRecordedLogFiles() {
        //If none is selected, return
        if (mRecordsListView.getCheckedItemCount() == 0) {
            Toast.makeText(getApplicationContext(), "First select log entry!", Toast.LENGTH_LONG).show();
            return;
        }

        Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);

        emailIntent.setData(Uri.parse("mailto:"));
        //      emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { "sandeep@fatangare.info" });
        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "[" + getAppName() + "] Logcat Logs");
        emailIntent.setType("text/plain");
        emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, "Please find attached logcat logs file.");

        //Loop through selected files and add to uri list.
        SparseBooleanArray checkedItemPositions = mRecordsListView.getCheckedItemPositions();
        int cnt = checkedItemPositions.size();

        LogRecordsListAdapter logRecordsListAdapter = (LogRecordsListAdapter) mRecordsListView.getAdapter();
        ArrayList<Uri> uris = new ArrayList<Uri>();
        for (int index = 0; index < cnt; index++) {
            if (checkedItemPositions.valueAt(index)) {
                File file = (File) logRecordsListAdapter.getItem(checkedItemPositions.keyAt(index));
                uris.add(Uri.fromFile(file));
            }

        }

        //add all files to intent data.
        emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        //Launching from service so set this flag.
        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        //Hide floating view; otherwise email client will be shown below it.
        hide(StandOutWindow.DEFAULT_ID);

        //Launch email client application.
        startActivity(emailIntent);
    }

    /**
     * Delete selected 'Saved Logs' files.
     */
    private void deleteRecordedLogFiles() {
        if (mListView.getCheckedItemCount() == 0) {
            Toast.makeText(getApplicationContext(), "First select log entry!", Toast.LENGTH_LONG).show();
            return;
        }

        //Loop through selected files and delete them.
        SparseBooleanArray checkedItemPositions = mRecordsListView.getCheckedItemPositions();
        int cnt = checkedItemPositions.size();

        LogRecordsListAdapter logRecordsListAdapter = (LogRecordsListAdapter) mRecordsListView.getAdapter();
        for (int index = 0; index < cnt; index++) {
            if (checkedItemPositions.valueAt(index)) {
                File file = (File) logRecordsListAdapter.getItem(checkedItemPositions.keyAt(index));
                if (file.delete()) {
                    Toast.makeText(getApplicationContext(), "File " + file.getName() + " deleted!", Toast.LENGTH_SHORT).show();
                }
            }
        }

        //Update list-view.
        logRecordsListAdapter.notifyDataSetChanged();
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

    @Override
    public void onLogEntryRead(final String entry) {
        mListView.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.addLogEntry(entry);
            }
        });
    }
}
