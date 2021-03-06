package com.anescobar.musicale.view.activities;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBarActivity;

import com.anescobar.musicale.app.MusicaleApp;
import com.anescobar.musicale.app.utils.AnalyticsUtil;

/**
 * Created by andres on 9/5/14.
 * Abstract class to be superclass for all activities
 * Includes common methods and functionality
 */

public abstract class BaseActivity extends ActionBarActivity {
    private FragmentManager mFragmentManager;
    protected AnalyticsUtil mAnalyticsUtil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        mAnalyticsUtil = ((MusicaleApp) getApplication()).mAnalyticsUtil;

        mAnalyticsUtil.sendAnalyticsScreenHit(getClass().getSimpleName());

        mFragmentManager = getSupportFragmentManager();
    }

    protected void addFragmentToActivity(int container, Fragment fragment, String fragmentTag) {
        mFragmentManager.beginTransaction()
                .replace(container, fragment, fragmentTag)
                .addToBackStack(null)
                .commit();
    }

    protected Fragment getFragmentByTag(String tag) {
        return mFragmentManager.findFragmentByTag(tag);
    }

}