package com.anescobar.musicale.view.fragments;

import android.os.Bundle;
import android.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.anescobar.musicale.R;
import com.anescobar.musicale.app.adapters.EventsAdapter;
import com.anescobar.musicale.app.interfaces.EventFetcherListener;
import com.anescobar.musicale.app.models.EventQueryDetails;
import com.anescobar.musicale.app.utils.NetworkNotAvailableException;
import com.anescobar.musicale.rest.services.EventsFinder;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;

import java.util.ArrayList;

import butterknife.ButterKnife;
import butterknife.InjectView;
import de.umass.lastfm.Caller;
import de.umass.lastfm.Event;
import de.umass.lastfm.PaginatedResult;

public class EventsListViewFragment extends Fragment implements EventFetcherListener {

    private LinearLayoutManager mLayoutManager;
    private EventsAdapter mAdapter;

    @InjectView(R.id.events_recyclerview) RecyclerView mRecyclerView;
    @InjectView(R.id.events_list_message_container) TextView mMessageContainer;
    @InjectView(R.id.events_list_progressbar) ProgressBar mEventsLoadingProgressBar;

    private boolean mCurrentlyGettingEvents = false;

    private LatLng mLatLng;

    private EventQueryDetails mEventQueryDetails = EventQueryDetails.getInstance();

    private boolean mAdapterSet = false;

    //always use this to create new instance of this fragment
    public static EventsListViewFragment newInstance(LatLng currentLocation) {
        EventsListViewFragment eventsListViewFragment = new EventsListViewFragment();

        final Gson gson = new Gson();
        String serializedCurrentLatLng = gson.toJson(currentLocation, LatLng.class);
        Bundle args = new Bundle();
        args.putString("currentLatLng", serializedCurrentLatLng);
        eventsListViewFragment.setArguments(args);

        return eventsListViewFragment;
    }

        public EventsListViewFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //lets it know that this fragment has its own menu implementation
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_events_list_view, container, false);

        ButterKnife.inject(this, view);

        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        menu.findItem(R.id.action_refresh_events).setVisible(true);
        menu.findItem(R.id.action_search_events).setVisible(true);
    }


    // Called at the start of the visible lifetime.
    @Override
    public void onStart(){
        super.onStart();

        //gets serialized latlng string from bundle and deserializes it
        final Gson gson = new Gson();
        String serializedLatLng = getArguments().getString("currentLatLng", null);

        if (serializedLatLng != null) {
            LatLng currentLatLng = gson.fromJson(serializedLatLng, LatLng.class);

            //adds events to view
            loadEventsToView(currentLatLng);

            //stores latLng
            mLatLng = currentLatLng;
        } else {
            setErrorMessage(getString(R.string.error_generic));
        }
    }

    private void setEventsAdapter() {
        // only sets up adapter if it hasnt been setup already
        if (!mAdapterSet) {
            // Create the adapter
            mAdapter = new EventsAdapter(getActivity(), mEventQueryDetails.events);

            //set recycler view with adapter
            mRecyclerView.setAdapter(mAdapter);

            //sets the onScrollListener that will inform us of when user has scrolled to bottom of recycleView
            mRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {

                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int state) {}

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    int loadNextPagePoint = mAdapter.getItemCount()/2;

                    //if user has scrolled to halfway point of list and there are still pages of events left then will fetch next page
                    if (mLayoutManager.findFirstVisibleItemPosition() > loadNextPagePoint && !mCurrentlyGettingEvents && mEventQueryDetails.totalNumberOfEventPages > mEventQueryDetails.numberOfEventPagesLoaded) {
                        getEventsFromServer(mEventQueryDetails.numberOfEventPagesLoaded + 1, mLatLng);
                    }
                }
            });

            mAdapterSet = true;
        } else {
            //notifies adapter of data set change so that it can update view
            mAdapter.notifyDataSetChanged();
        }
    }

    //gets events from backend, keeps track of how many pages are already loaded and cached
    public void getEventsFromServer(Integer pageNumber, LatLng userLocation) {
        try {
            mEventQueryDetails.numberOfEventPagesLoaded = pageNumber;
            new EventsFinder().getEvents(pageNumber, userLocation, this, getActivity());
        } catch (NetworkNotAvailableException e) {
            e.printStackTrace();
            if (pageNumber == 1) {
                setErrorMessage(getString(R.string.error_no_network_connectivity));
            } else {
                Toast.makeText(getActivity(),getString(R.string.error_no_network_connectivity),Toast.LENGTH_SHORT).show();
            }
        }
    }

    //called onPreExecute of eventsFetcherTask
    @Override
    public void onEventFetcherTaskAboutToStart() {
        mCurrentlyGettingEvents = true;
        mMessageContainer.setVisibility(View.GONE);

        //display loading progressbar in middle of screen if it is loading first page of events
        if (mEventQueryDetails.numberOfEventPagesLoaded == 1) {
            mRecyclerView.setVisibility(View.INVISIBLE);
            mEventsLoadingProgressBar.setVisibility(View.VISIBLE);
        }
    }

    //called onPostExecute of eventsFetcherTask
    @Override
    public void onEventFetcherTaskCompleted(PaginatedResult<Event> eventsNearby) {
        mCurrentlyGettingEvents = false;
        //if last call was successful then load events to screen
        if (Caller.getInstance().getLastResult().isSuccessful()) {
            ArrayList<Event> events= new ArrayList<>(eventsNearby.getPageResults());

            //set variable that stores total number of pages
            mEventQueryDetails.totalNumberOfEventPages = eventsNearby.getTotalPages();

            if (mEventQueryDetails.numberOfEventPagesLoaded == 1) {
                //clears events list before adding events to it
                mEventQueryDetails.events.clear();
            }
            //add events to mEvents
            mEventQueryDetails.events.addAll(events);

            //set events adapter with new events
            setEventsAdapter();
        } else {
            setErrorMessage(getString(R.string.error_generic));
        }

        if (mEventQueryDetails.numberOfEventPagesLoaded == 1) {
            //hide loading progressbar in middle of screen
            mEventsLoadingProgressBar.setVisibility(View.GONE);

            //scrolls to top of recycler view because brand new set of events was loaded
            mRecyclerView.scrollToPosition(0);

            mRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    //loads events and sets adapter that will display them in recycler view
    private void loadEventsToView(LatLng latLng) {
        //if there are no events from previous saved session then fetch events from backend
        //else use events from previous saved session to populate cards
        if (mEventQueryDetails.events.isEmpty()) {
            getEventsFromServer(1, latLng);
        } else {
            setEventsAdapter();
        }
    }

    private void setErrorMessage(String message) {
        mRecyclerView.setVisibility(View.GONE);
        mMessageContainer.setText(message);
        mMessageContainer.setVisibility(View.VISIBLE);
    }
}