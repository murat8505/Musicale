package com.anescobar.musicale.view.fragments;

import android.accounts.NetworkErrorException;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.anescobar.musicale.R;
import com.anescobar.musicale.app.services.interfaces.TwitterGuestSessionFetcherListener;
import com.anescobar.musicale.app.services.interfaces.TwitterSearchTaskListener;
import com.anescobar.musicale.app.services.TwitterService;
import com.google.gson.Gson;
import com.twitter.sdk.android.core.Session;
import com.twitter.sdk.android.core.models.Tweet;
import com.twitter.sdk.android.tweetui.TweetViewAdapter;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import de.umass.lastfm.Event;

public class EventSocialMediaFragment extends BaseFragment implements TwitterGuestSessionFetcherListener, TwitterSearchTaskListener {

    private static final String ARG_EVENT = "eventArg";
    private TweetViewAdapter<? extends com.twitter.sdk.android.tweetui.BaseTweetView> mTweetsAdapter;
    private long mMaxId;
    private boolean mTweetsCurrentlyLoading = false;
    private boolean mEndOfSearchResults = false;
    private TwitterService mTwitterService;
    private boolean mTweetsDisplayed;

    private Event mEvent;

    @InjectView(R.id.event_tweets_list) ListView mTweetsList;
    @InjectView(R.id.tweets_loading_progressbar) ProgressBar mLoadingProgressBar;
    @InjectView(R.id.tweets_message_container) TextView mMessageContainer;

    public EventSocialMediaFragment() {}

    public static EventSocialMediaFragment newInstance(Event event) {
        EventSocialMediaFragment fragment = new EventSocialMediaFragment();

        Gson gson = new Gson();

        String serializedEvent = gson.toJson(event, Event.class);

        Bundle args = new Bundle();

        args.putString(ARG_EVENT, serializedEvent);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_event_social_media, container, false);
        mTwitterService = new TwitterService();

        ButterKnife.inject(this, view);

        Gson gson = new Gson();

        String serializedEvent = getArguments().getString(ARG_EVENT, null);

        mEvent = gson.fromJson(serializedEvent, Event.class);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        try {
            mTwitterService.loginAsGuest(this, getActivity().getApplicationContext());
        } catch (NetworkErrorException e) {
            mLoadingProgressBar.setVisibility(View.GONE);
            mMessageContainer.setText(R.string.error_no_network_connectivity);
            mMessageContainer.setVisibility(View.VISIBLE);
        }
    }

    public void setupView(final Session session) {
        mTweetsAdapter = new TweetViewAdapter<>(getActivity());
        mTweetsList.setAdapter(mTweetsAdapter);
        mTweetsList.setOnScrollListener(new ListView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {}

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                                 int totalItemCount) {
                if (firstVisibleItem + visibleItemCount == totalItemCount) {
                    if (!mTweetsCurrentlyLoading && !mEndOfSearchResults) {
                        mTweetsCurrentlyLoading = true;
                        loadTweets(session);
                    }
                }
            }
        });
    }

    private void loadTweets(Session session) {
        //if activity is null (b/c user navigated away from screen) then shouldnt load events to screen
        if (getActivity() != null) {
            try {
                mTwitterService.searchForTweets(this, getActivity().getApplicationContext(),
                        session, createSearchQuery(mEvent), mMaxId);
            } catch (NetworkErrorException e) {
                if (mTweetsDisplayed) {
                    Toast.makeText(getActivity(), R.string.error_no_network_connectivity, Toast.LENGTH_SHORT).show();
                } else {
                    mLoadingProgressBar.setVisibility(View.GONE);
                    mMessageContainer.setText(R.string.error_no_network_connectivity);
                    mMessageContainer.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    @Override
    public void onTwitterGuestSessionFetcherTaskAboutToStart() {}

    @Override
    public void onTwitterGuestSessionFetcherTaskSuccessful(Session session) {
        setupView(session);
    }

    @Override
    public void onTwitterSearchTaskAboutToStart() {}

    @Override
    public void onTwitterSearchTaskSuccessful(List<Tweet> tweets) {
        //if activity is null (b/c user navigated away from screen) then shouldnt load events to screen
        if (getActivity() != null) {
            mLoadingProgressBar.setVisibility(View.GONE);
            mTweetsCurrentlyLoading = false;

            if (!tweets.isEmpty()) {
                mTweetsDisplayed = true;
                mTweetsAdapter.getTweets().addAll(tweets);
                mTweetsAdapter.notifyDataSetChanged();

                if (tweets.size() > 0) {
                    mMaxId = tweets.get(tweets.size() - 1).id - 1;
                } else {
                    mEndOfSearchResults = true;
                }
            } else {
                if (mTweetsDisplayed) {
                    Toast.makeText(getActivity(), R.string.no_tweets_found, Toast.LENGTH_SHORT).show();
                } else {
                    mMessageContainer.setText(R.string.no_tweets_found);
                    mMessageContainer.setVisibility(View.VISIBLE);
                }
            }
        }
    }


    //creates search query that Twitter API will understand given an event
    // creates query to search twitter for tweets containing any of first 5 artists at event, event name, or venue name
    private String createSearchQuery(Event event) {
        String query = "";

        int counter = 0;
        for (String artist : event.getArtists()) {
            if (counter < 5) {
                counter ++;
                query += " OR " + "\"" + artist + "\" ";
            }
        }
        query += " OR " + "\"" + event.getTitle() + "\"" + " OR " + "\"" + event.getVenue().getName() + "\"";

        try {
            query = URLEncoder.encode(query.substring(4), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return query;
    }
}