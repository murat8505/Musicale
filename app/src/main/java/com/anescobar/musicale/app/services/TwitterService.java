package com.anescobar.musicale.app.services;

import android.content.Context;

import com.anescobar.musicale.app.exceptions.NetworkNotAvailableException;
import com.anescobar.musicale.app.interfaces.TwitterGuestSessionFetcherListener;
import com.anescobar.musicale.app.interfaces.TwitterSearchTaskListener;
import com.anescobar.musicale.app.utils.NetworkUtil;
import com.crashlytics.android.Crashlytics;
import com.twitter.sdk.android.Twitter;
import com.twitter.sdk.android.core.AppSession;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.Session;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.models.Search;

/**
 * Created by Andres Escobar on 2/9/15.
 * Provides methods for using Twitter API Client
 */
public class TwitterService {
    private static final int SEARCH_COUNT = 20;
    private static final String SEARCH_RESULT_TYPE = "mixed";

    private NetworkUtil mNetworkUtil = new NetworkUtil();

    public TwitterService() {
    }

    //publicly accessible method that gets guest session from Twitter API client
    public void loginAsGuest(final TwitterGuestSessionFetcherListener twitterGuestSessionFetcherListener, Context context) throws NetworkNotAvailableException, TwitterException {
        if (mNetworkUtil.isNetworkAvailable(context)) {
            twitterGuestSessionFetcherListener.onTwitterGuestSessionFetcherTaskAboutToStart();
            TwitterCore.getInstance().logInGuest(new Callback<AppSession>() {

                @Override
                public void success(com.twitter.sdk.android.core.Result<AppSession> result) {
                    twitterGuestSessionFetcherListener.onTwitterGuestSessionFetcherTaskSuccessful(result.data);
                }

                @Override
                public void failure(TwitterException exception) {
                    Crashlytics.logException(exception);
                    Crashlytics.log("Twitter guest session getter failed");
                    throw exception;
                }
            });
        } else {
            throw new NetworkNotAvailableException("Not connected to network...");
        }
    }

    public void searchForTweets(final TwitterSearchTaskListener twitterSearchTaskListener, Context context,
                                Session session, String searchQuery, Long maxId) throws NetworkNotAvailableException, TwitterException {

        if (mNetworkUtil.isNetworkAvailable(context)) {
            twitterSearchTaskListener.onTwitterSearchTaskAboutToStart();

            Twitter.getApiClient(session)
                    .getSearchService()
                    .tweets(searchQuery, null, null, null, SEARCH_RESULT_TYPE, SEARCH_COUNT, null, null,
                    maxId, true, new Callback<Search>() {

                        @Override
                        public void success(Result<Search> searchResult) {
                            twitterSearchTaskListener.onTwitterSearchTaskSuccessful(searchResult.data.tweets);
                        }

                        @Override
                        public void failure(TwitterException exception) {
                            Crashlytics.logException(exception);
                            Crashlytics.log("Twitter search for Tweets failed");
                            throw exception;
                        }
                    });
        } else {
            throw new NetworkNotAvailableException("Not connected to network...");
        }
    }

}