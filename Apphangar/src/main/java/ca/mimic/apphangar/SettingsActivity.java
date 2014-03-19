package ca.mimic.apphangar;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.v13.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.margaritov.preference.colorpicker.ColorPickerPreference;

public class SettingsActivity extends Activity implements ActionBar.TabListener {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v13.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;
    private WatchfulService s;
    private TasksDataSource db;
    private Context mContext;

    static String TAG = "Apphangar";
    static String DIVIDER_PREFERENCE = "divider_preference";
    static String APPSNO_PREFERENCE = "appsno_preference";
    static String PRIORITY_PREFERENCE = "priority_preference";
    static String TOGGLE_PREFERENCE = "toggle_preference";
    static String BOOT_PREFERENCE = "boot_preference";
    static String WEIGHTED_RECENTS_PREFERENCE = "weighted_recents_preference";
    static String WEIGHT_PRIORITY_PREFERENCE = "weight_priority_preference";
    static String COLORIZE_PREFERENCE = "colorize_preference";
    static String ICON_COLOR_PREFERENCE = "icon_color_preference";

    protected View appsView;
    protected boolean isBound = false;
    boolean newStart;

    static boolean DIVIDER_DEFAULT = true;
    static boolean TOGGLE_DEFAULT = true;
    static boolean BOOT_DEFAULT = true;
    static boolean WEIGHTED_RECENTS_DEFAULT = true;
    static boolean COLORIZE_DEFAULT = false;
    static int WEIGHT_PRIORITY_DEFAULT = 0;
    static int APPSNO_DEFAULT = 7;
    static int PRIORITY_DEFAULT = 2;
    static int ICON_COLOR_DEFAULT = 0xffffffff;

    static int displayWidth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        if (prefs.getBoolean(TOGGLE_PREFERENCE, TOGGLE_DEFAULT)) {
            startService(new Intent(this, WatchfulService.class));
        }

        db = new TasksDataSource(this);
        db.open();

        mContext = this;

        Display display = getWindowManager().getDefaultDisplay();

        Point size = new Point();
        display.getSize(size);
        displayWidth = size.x;

        // Set up the action bar.
        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setOffscreenPageLimit(3);

        ViewPager.OnPageChangeListener pageChangeListener = new ViewPager.SimpleOnPageChangeListener() {
            int pagePosition;
            @Override
            public void onPageScrollStateChanged(int state) {
                try {
                    if (pagePosition == 2 && newStart && state == ViewPager.SCROLL_STATE_IDLE) {
                        newStart = false;
                        drawTasks(appsView);
                    }
                } catch (NullPointerException e) {
                    // Not yet created
                }
            }
            @Override
            public void onPageSelected(int position) {
                pagePosition = position;
                actionBar.setSelectedNavigationItem(position);
            }
        };

        mViewPager.setOnPageChangeListener(pageChangeListener);

        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }
        int allTasksSize = db.getAllTasks().size();
        if (allTasksSize == 0) {
            newStart = true;
        }
        pageChangeListener.onPageSelected(0);

    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        if (!isBound) {
            Intent intent = new Intent(this, WatchfulService.class);
            startService(intent);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (isBound) {
                unbindService(mConnection);
                isBound = false;
            }
        } catch (RuntimeException e) {
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder binder) {
            WatchfulService.WatchfulBinder b = (WatchfulService.WatchfulBinder) binder;
            s = b.getService();
            isBound = true;
            s.buildTasks();
        }

        public void onServiceDisconnected(ComponentName className) {
            s = null;
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    public boolean fadeTask(View view, TextView text) {
        if ((text.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) > 0) {
            view.setAlpha(1);
            // view.clearAnimation();
            text.setPaintFlags(text.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            return false;
        } else {
            text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            AlphaAnimation aa = new AlphaAnimation(1f, 0.3f);
            aa.setDuration(0);
            aa.setFillAfter(true);
            // view.startAnimation(aa);
            view.setAlpha((float) 0.5);
            return true;
        }
    }
    public int dpToPx(int dp) {
        Resources r = getResources();
        return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());
    }
    public static int[] splitToComponentTimes(int longVal) {
        int hours = longVal / 3600;
        int remainder = longVal - hours * 3600;
        int mins = remainder / 60;
        remainder = remainder - mins * 60;
        int secs = remainder;

        int[] ints = {hours , mins , secs};
        return ints;
    }
    class TasksComparator implements Comparator<TasksModel> {
        String mType = "seconds";
        TasksComparator(String type) {
            mType = type;
        }
        @Override
        public int compare(TasksModel t1, TasksModel t2) {
            Integer o1 = 0;
            Integer o2 = 0;
            if (mType.equals("seconds")) {
                o1 = t1.getSeconds();
                o2 = t2.getSeconds();
            }
            int firstCompare = o2.compareTo(o1);
            if (firstCompare == 0) {
                return t1.getBlacklisted().compareTo(t2.getBlacklisted());
            }
            return firstCompare;
        }
    }
    public class RebuildTasks extends AsyncTask<Void, Void, Void> {
        @Override protected Void doInBackground(Void... params) {
            if (isBound) {
                s.topPackage = null;
                s.taskList.clear();
                s.runScan();
            }
            return null;
        }
    }
    public void drawTasks(View view) {
        final View v = view;
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                LinearLayout taskRoot = (LinearLayout) v.findViewById(R.id.taskRoot);
                taskRoot.removeAllViews();
                int highestSeconds = db.getHighestSeconds();
                List<TasksModel> tasks = db.getAllTasks();
                Collections.sort(tasks, new TasksComparator("seconds"));
                for (TasksModel task : tasks) {
                    LinearLayout taskRL = new LinearLayout(mContext);

                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    params.topMargin = dpToPx(6);
                    taskRL.setLayoutParams(params);
                    taskRL.setTag(task);
                    taskRL.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            TasksModel task = (TasksModel)view.getTag();
                            TextView text = (TextView)view.findViewWithTag("text");
                            db.blacklistTask(task, fadeTask(view, text));
                            new RebuildTasks().execute();
                        }
                    });

                    TextView useStats = new TextView(mContext);
                    LinearLayout.LayoutParams useStatsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    useStatsParams.topMargin = dpToPx(30);
                    useStatsParams.leftMargin = dpToPx(10);
                    useStatsParams.rightMargin = dpToPx(4);
                    useStats.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    useStats.setTag("usestats");
                    useStats.setLayoutParams(useStatsParams);
                    useStats.setTypeface(null, Typeface.BOLD);

                    LinearLayout textCont = new LinearLayout(mContext);
                    LinearLayout.LayoutParams useStatsLayout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    textCont.setLayoutParams(useStatsLayout);
                    textCont.setOrientation(LinearLayout.VERTICAL);

                    TextView taskName = new TextView(mContext);
                    LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    nameParams.leftMargin = dpToPx(10);
                    nameParams.topMargin = dpToPx(4);
                    taskName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                    taskName.setTag("text");
                    taskName.setLayoutParams(nameParams);

                    RelativeLayout barCont = new RelativeLayout(mContext);
                    LinearLayout.LayoutParams barContLayout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    barContLayout.topMargin = dpToPx(10);
                    barContLayout.leftMargin = dpToPx(10);
                    barContLayout.height = dpToPx(5);
                    barCont.setLayoutParams(barContLayout);


                    ImageView taskIcon = new ImageView(mContext);
                    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dpToPx(46),
                            dpToPx(46));

                    iconParams.leftMargin = dpToPx(6);
                    iconParams.rightMargin = dpToPx(6);
                    iconParams.bottomMargin = dpToPx(6);
                    taskIcon.setLayoutParams(iconParams);

                    try {
                        PackageManager pm = getApplicationContext().getPackageManager();
                        // Log.d(TAG, "Trying to grab AppInfo class[" + task.getClassName()+ "] package[" + task.getPackageName() + "]");
                        ComponentName componentTask = ComponentName.unflattenFromString(task.getPackageName() + "/" + task.getClassName());
                        ApplicationInfo appInfo = pm.getApplicationInfo(componentTask.getPackageName(), 0);

                        taskName.setText(task.getName());
                        taskIcon.setImageDrawable(appInfo.loadIcon(pm));
                    } catch (Exception e) {
                        Log.d(TAG, "Could not find Application info for [" + task.getName() + "]");
                        continue;
                    }

                    textCont.addView(taskName);
                    textCont.addView(barCont);
                    taskRL.addView(textCont);
                    taskRL.addView(useStats);
                    taskRL.addView(taskIcon);
                    taskRoot.addView(taskRL);

                    int maxWidth = displayWidth - dpToPx(46+14) - useStats.getWidth();
                    float secondsRatio = (float) task.getSeconds() / highestSeconds;
                    int barColor;
                    int secondsColor = (Math.round(secondsRatio * 100));
                    if (secondsColor >= 80 ) {
                        barColor = 0xFF34B5E2;
                    } else if (secondsColor >= 60) {
                        barColor = 0xFFAA66CC;
                    } else if (secondsColor >= 40) {
                        barColor = 0xFF74C353;
                    } else if (secondsColor >= 20) {
                        barColor = 0xFFFFBB33;
                    } else {
                        barColor = 0xFFFF4444;
                    }
                    float adjustedWidth = maxWidth * secondsRatio;
                    barContLayout.width = Math.round(adjustedWidth);
                    int[] statsTime = splitToComponentTimes(task.getSeconds());
                    String statsString = ((statsTime[0] > 0) ? statsTime[0] + "h " : "") + ((statsTime[1] > 0) ? statsTime[1] + "m " : "") + ((statsTime[2] > 0) ? statsTime[2] + "s " : "");
                    useStats.setText(statsString);
                    barCont.setBackgroundColor(barColor);
                    // Log.d(TAG, "Blacklisted? [" + task.getBlacklisted() + "]");
                    if (task.getBlacklisted()) {
                        fadeTask(taskRL, taskName);
                    }
                }
            }
        });
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        SharedPreferences prefs;
        SharedPreferences.Editor editor;

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
            prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
            editor = prefs.edit();
        }

        @Override
        public Fragment getItem(final int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            final int prefLayout;
            switch (position) {
                case 0:
                    prefLayout = R.layout.behavior_settings;
                    break;
                case 1:
                    prefLayout = R.layout.appearance_settings;
                    break;
                default:
                    Fragment fragment = new Fragment() {
                        @Override
                        public void onResume() {
                            super.onResume();
                            drawTasks(appsView);
                        }
                        @Override
                        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                                 Bundle savedInstanceState) {
                            appsView = inflater.inflate(R.layout.apps_settings, container, false);
                            drawTasks(appsView);
                            return appsView;
                        }
                    };
                    fragment.setRetainInstance(true);
                    return fragment;
            }

            Fragment fragment = new PreferenceFragment() {
                CheckBoxPreference divider_preference;
                CheckBoxPreference weighted_recents_preference;
                CheckBoxPreference colorize_preference;
                ColorPickerPreference icon_color_preference;
                SwitchPreference toggle_preference;
                SwitchPreference boot_preference;
                UpdatingListPreference appnos_preference;
                UpdatingListPreference priority_preference;
                UpdatingListPreference weight_priority_preference;

                @Override
                public void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    setRetainInstance(true);
                    addPreferencesFromResource(prefLayout);

                    try {
                        // *** Appearance ***
                        divider_preference = (CheckBoxPreference)findPreference(DIVIDER_PREFERENCE);
                        divider_preference.setChecked(prefs.getBoolean(DIVIDER_PREFERENCE, DIVIDER_DEFAULT));
                        divider_preference.setOnPreferenceChangeListener(changeListener);

                        colorize_preference = (CheckBoxPreference)findPreference(COLORIZE_PREFERENCE);
                        colorize_preference.setChecked(prefs.getBoolean(COLORIZE_PREFERENCE, COLORIZE_DEFAULT));
                        colorize_preference.setOnPreferenceChangeListener(changeListener);

                        icon_color_preference = (ColorPickerPreference) findPreference(ICON_COLOR_PREFERENCE);
                        int intColor = prefs.getInt(ICON_COLOR_PREFERENCE, ICON_COLOR_DEFAULT);
                        String hexColor = String.format("#%08x", (intColor));
                        icon_color_preference.setSummary(hexColor);
                        icon_color_preference.setNewPreviewColor(intColor);
                        icon_color_preference.setOnPreferenceChangeListener(changeListener);

                        appnos_preference = (UpdatingListPreference)findPreference(APPSNO_PREFERENCE);
                        appnos_preference.setValue(prefs.getString(APPSNO_PREFERENCE, Integer.toString(APPSNO_DEFAULT)));
                        appnos_preference.setOnPreferenceChangeListener(changeListener);

                    } catch (NullPointerException e) {
                    }
                    try {
                        // *** Behavior ***
                        toggle_preference = (SwitchPreference)findPreference(TOGGLE_PREFERENCE);
                        toggle_preference.setChecked(prefs.getBoolean(TOGGLE_PREFERENCE, TOGGLE_DEFAULT));
                        toggle_preference.setOnPreferenceChangeListener(changeListener);

                        boot_preference = (SwitchPreference)findPreference(BOOT_PREFERENCE);
                        boot_preference.setChecked(prefs.getBoolean(BOOT_PREFERENCE, BOOT_DEFAULT));
                        boot_preference.setOnPreferenceChangeListener(changeListener);

                        weighted_recents_preference = (CheckBoxPreference)findPreference(WEIGHTED_RECENTS_PREFERENCE);
                        weighted_recents_preference.setChecked(prefs.getBoolean(WEIGHTED_RECENTS_PREFERENCE, WEIGHTED_RECENTS_DEFAULT));
                        weighted_recents_preference.setOnPreferenceChangeListener(changeListener);

                        weight_priority_preference = (UpdatingListPreference)findPreference(WEIGHT_PRIORITY_PREFERENCE);
                        weight_priority_preference.setValue(prefs.getString(WEIGHT_PRIORITY_PREFERENCE, Integer.toString(WEIGHT_PRIORITY_DEFAULT)));
                        weight_priority_preference.setOnPreferenceChangeListener(changeListener);

                        priority_preference = (UpdatingListPreference)findPreference(PRIORITY_PREFERENCE);
                        priority_preference.setValue(prefs.getString(PRIORITY_PREFERENCE, Integer.toString(PRIORITY_DEFAULT)));
                        priority_preference.setOnPreferenceChangeListener(changeListener);
                    } catch (NullPointerException e) {
                    }
                }
                Preference.OnPreferenceChangeListener changeListener = new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        Log.d(TAG, "onPreferenceChange pref.getKey=[" + preference.getKey() + "] newValue=[" + newValue + "]");

                        if (preference.getKey().equals(DIVIDER_PREFERENCE)) {
                            editor.putBoolean(DIVIDER_PREFERENCE, (Boolean) newValue);
                            s.destroyNotification();
                            s.runScan();
                        } else if (preference.getKey().equals(COLORIZE_PREFERENCE)) {
                            editor.putBoolean(COLORIZE_PREFERENCE, (Boolean) newValue);
                            s.destroyNotification();
                            s.runScan();
                        } else if (preference.getKey().equals(ICON_COLOR_PREFERENCE)) {
                            String hex = ColorPickerPreference.convertToARGB(Integer.valueOf(String.valueOf(newValue)));
                            preference.setSummary(hex);
                            int intHex = ColorPickerPreference.convertToColorInt(hex);
                            editor.putInt(ICON_COLOR_PREFERENCE, intHex);
                            s.destroyNotification();
                            s.runScan();
                        } else if (preference.getKey().equals(TOGGLE_PREFERENCE)) {
                            editor.putBoolean(TOGGLE_PREFERENCE, (Boolean) newValue);
                            editor.commit();

                            if (!(Boolean) newValue) {
                                s.destroyNotification();
                                // if (isBound) {
                                //     isBound = false;
                                //     unbindService(mConnection);
                                // }
                                // stopService(new Intent(SettingsActivity.this, WatchfulService.class));
                            } else {
                                new RebuildTasks().execute();
                                // Intent iStart = new Intent(SettingsActivity.this, WatchfulService.class);
                                // if (!isBound) {
                                //     startService(iStart);
                                //     bindService(iStart, mConnection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
                                // }
                            }

                            return true;
                        } else if (preference.getKey().equals(BOOT_PREFERENCE)) {
                            editor.putBoolean(BOOT_PREFERENCE, (Boolean) newValue);
                            editor.commit();
                            return true;
                        } else if (preference.getKey().equals(WEIGHT_PRIORITY_PREFERENCE)) {
                            editor.putString(WEIGHT_PRIORITY_PREFERENCE, (String) newValue);
                            editor.commit();
                            s.topPackage = null;
                            s.taskList.clear();
                            s.runScan();
                        } else if (preference.getKey().equals(WEIGHTED_RECENTS_PREFERENCE)) {
                            editor.putBoolean(WEIGHTED_RECENTS_PREFERENCE, (Boolean) newValue);
                            editor.commit();
                            s.topPackage = null;
                            s.taskList.clear();
                            s.runScan();
                        } else if (preference.getKey().equals(APPSNO_PREFERENCE)) {
                            editor.putString(APPSNO_PREFERENCE, (String) newValue);
                            editor.commit();
                            s.createNotification();
                            return true;
                        } else if (preference.getKey().equals(PRIORITY_PREFERENCE)) {
                            editor.putString(PRIORITY_PREFERENCE, (String) newValue);
                            s.destroyNotification();
                            s.runScan();
                        }
                        editor.commit();
                        return true;
                    }
                };
            };
            fragment.setRetainInstance(true);
            return fragment;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return getString(R.string.title_behavior).toUpperCase(l);
                case 1:
                    return getString(R.string.title_appearance).toUpperCase(l);
                case 2:
                    return getString(R.string.title_apps).toUpperCase(l);
            }
            return null;
        }
    }
}