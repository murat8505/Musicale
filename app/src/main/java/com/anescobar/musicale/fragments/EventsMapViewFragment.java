package com.anescobar.musicale.fragments;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.anescobar.musicale.R;
import com.anescobar.musicale.activities.HomeActivity;
import com.anescobar.musicale.interfaces.OnEventsFetcherTaskCompleted;
import com.anescobar.musicale.utilsHelpers.EventsFinder;
import com.anescobar.musicale.utilsHelpers.NetworkUtil;
import com.anescobar.musicale.utilsHelpers.SessionManager;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;

import de.umass.lastfm.Event;
import de.umass.lastfm.ImageSize;
import de.umass.lastfm.PaginatedResult;
import de.umass.lastfm.Session;

/**
* A simple {@link Fragment} subclass.
* Activities that contain this fragment must implement the
* {@link EventsMapViewFragment.OnEventsMapViewFragmentInteractionListener} interface
* to handle interaction home.
*
*/
public class EventsMapViewFragment extends Fragment implements OnEventsFetcherTaskCompleted,
        GoogleMap.OnInfoWindowClickListener {

    private MapFragment mMapFragment;
    private NetworkUtil mNetworkUtil;
    private GoogleMap mMap;
    private ProgressBar mEventsLoadingProgressbar;
    private OnEventsMapViewFragmentInteractionListener mListener;

    private int mTotalNumberOfPages = 0; // stores how many total pages of events there are
    private int mNumberOfPagesLoaded = 0; //keeps track of how many pages are loaded
    private Session mSession;
    private LatLng mUserLatLng;
    private HashMap<String, Event> mMarkers = new HashMap<String, Event>();
    private ArrayList<LatLng> mMarkerPositions = new ArrayList<LatLng>();
    private ArrayList<Event> mEvents = new ArrayList<Event>();


    public EventsMapViewFragment() {
        // Required empty public constructor
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     */
    public interface OnEventsMapViewFragmentInteractionListener {
        public void cacheEvents(int numberOfPagesLoaded, int totalNumberOfPages,ArrayList<Event> events);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //initializes networkUtil class
        mNetworkUtil = new NetworkUtil();

        //gets all sharedPreferences and stores them locally
        getCachedEvents();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_events_map_view, container, false);
        mMapFragment = new MapFragment();
        mEventsLoadingProgressbar = (ProgressBar) view.findViewById(R.id.fragment_events_map_view_events_loading);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.add(R.id.eventsMapView_framelayout_container, mMapFragment)
                .commit();

        return view;
    }

    // Called at the start of the visible lifetime.
    @Override
    public void onStart(){
        super.onStart();
        setUpMapIfNeeded(mUserLatLng, mSession);
        // Apply any required UI change now that the Fragment is visible.
    }

    // Called at the start of the active lifetime.
    @Override
    public void onResume(){
        super.onResume();
        setUpMapIfNeeded(mUserLatLng, mSession);
        // Resume any paused UI updates, threads, or processes required
        // by the Fragment but suspended when it became inactive.
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnEventsMapViewFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnEventMapViewFragmentInteractionListener");
        }
    }

    @Override
    public void onPause() {
        //caches all events data to sharedPreferences
        mListener.cacheEvents(mNumberOfPagesLoaded, mTotalNumberOfPages, mEvents);
        super.onPause();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    //gets events from backend
    private void getEventsFromServer(Integer pageNumber, Session session, LatLng userLocation) {

        if (mNetworkUtil.isNetworkAvailable(getActivity())) {
            new EventsFinder(session, this, userLocation).getEvents(pageNumber);
        } else {
            Toast.makeText(getActivity(),getString(R.string.error_no_network_connectivity),Toast.LENGTH_SHORT).show();
        }
    }

    //sets up map if it hasnt already been setup,
    private void setUpMapIfNeeded(LatLng userLocation, Session session) {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            mMap = mMapFragment.getMap();

            //set custom marker info window adapter
            mMap.setInfoWindowAdapter(new EventMarkerInfoWindowAdapter(getActivity()));
        }
        setUpMap(userLocation, session);
    }

    /**
     * This is where map is setup with home and with current or searched location as center
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap(LatLng userLocation, Session session) {
        //sets map's initial state
        mMap.setBuildingsEnabled(true);
        mMap.setMyLocationEnabled(true);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 10));

        //sets maps onInfoWindow click listener

        mMap.setOnInfoWindowClickListener(this);

        //if there are no events from previous saved session then fetch events from backend
        //else use events from previous saved session to populate cards
        if (mEvents.isEmpty()) {
            getEventsFromServer(1, session, userLocation);
        } else {
            displayEventsInMap();
        }
    }

    //iterates through arrayList of Events and displays them on map
    private void displayEventsInMap() {
        //clears old events from map
        mMap.clear();

        //iterate through events list
        for (Event event : mEvents) {
            float lat = event.getVenue().getLatitude();
            float lng = event.getVenue().getLongitude();
            LatLng venueLatLng = new LatLng(lat, lng);

            //adds marker that represents Event venue to map
            createMapMarker(venueLatLng, event);
        }
    }

    private void getCachedEvents() {
        Gson gson = new Gson();

        // Gets session from sharedPreferences
        SharedPreferences sessionPreferences = getActivity().getSharedPreferences(SessionManager.SESSION_SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        String serializedSession = sessionPreferences.getString("userSession", null);
        if (serializedSession != null) {
            mSession = gson.fromJson(serializedSession, Session.class);
        } //TODO here is where we check for and act on errors

        //Gets user's location(LatLng serialized into string) from sharedPreferences
        SharedPreferences userLocationPreferences = getActivity().getSharedPreferences(HomeActivity.LOCATION_SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        String serializedLatLng = userLocationPreferences.getString("userCurrentLatLng", null);
        if (serializedLatLng != null) {
            //deserializes userLatLng string into LatLng object
            mUserLatLng = gson.fromJson(serializedLatLng, LatLng.class);
        } //TODO here is where we check for and act on errors

        //Gets Events data from sharedPreferences
        SharedPreferences eventsPreferences = getActivity().getSharedPreferences(HomeActivity.EVENTS_SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        mNumberOfPagesLoaded = eventsPreferences.getInt("numberOfPagesLoaded", 0);
        mTotalNumberOfPages = eventsPreferences.getInt("totalNumberOfPages", 0);
        String serializedEvents = eventsPreferences.getString("events", null);

        //deserializes events if there are any
        if (serializedEvents != null) {
            Type listOfEvents = new TypeToken<ArrayList<Event>>(){}.getType();
            mEvents = gson.fromJson(serializedEvents, listOfEvents);

        }
    }

    @Override
    public void onTaskAboutToStart() {
        //displays progress bar before getting events
        mEventsLoadingProgressbar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onTaskCompleted(PaginatedResult<Event> eventsNearby) {
        ArrayList<Event> events= new ArrayList<Event>(eventsNearby.getPageResults());

        //add events to mEvents
        mEvents.addAll(events);

        mTotalNumberOfPages = eventsNearby.getTotalPages();

        //set events adapter with new events
        displayEventsInMap();

        //hide loading progressbar in middle of screen
        mEventsLoadingProgressbar.setVisibility(View.GONE);
    }

    private void createMapMarker(LatLng latLng, Event event) {
        Marker eventMarker;
        //if there is already a marker for same spot, creates new marker in a very slightly different spot than original
        //that way user can see ALL markers for same location
        if (mMarkerPositions.contains(latLng)) {
            eventMarker = mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(latLng.latitude * (Math.random() * (1.000001 - .999999) + .999999),
                                    latLng.longitude * (Math.random() * (1.000001 - .999999) + .999999)))
            );
        } else {
            eventMarker = mMap.addMarker(new MarkerOptions()
                            .position(latLng)
            );
        }
        //mMarkers keeps track of all Event markers that have been put down on map
        mMarkers.put(eventMarker.getId(), event);

        //mMarkerPositions keeps track of the location of all markers that have been put on map
        mMarkerPositions.add(latLng);
    }

    //gets latest events from cache and then displays them on map
    public void refreshEvents() {
        //gets cached events and event related data
        getCachedEvents();

        //displays events in map
        displayEventsInMap();
    }

    public class EventMarkerInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

        private Marker mMarkerShowingInfoWindow;
        private Context mContext;

        public EventMarkerInfoWindowAdapter(Context context) {
            mContext = context;
        }

        @Override
        public View getInfoContents(Marker marker) {

            mMarkerShowingInfoWindow = marker;

            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService( Context.LAYOUT_INFLATER_SERVICE );

            // Getting view from the layout file info_window_layout
            View infoWindow = inflater.inflate(R.layout.event_info_window, null);

            TextView eventTitleTextfield = (TextView) infoWindow.findViewById(R.id.event_info_window_event_title_textfield);
            TextView venueNameTextfield = (TextView) infoWindow.findViewById(R.id.event_info_window_venue_name_textfield);
            TextView eventDateTextfield = (TextView) infoWindow.findViewById(R.id.event_info_window_event_date_textfield);
            ImageView eventImage = (ImageView) infoWindow.findViewById(R.id.event_info_window_event_image);

            eventTitleTextfield.setText(mMarkers.get(marker.getId()).getTitle());
            //gets event date as Date object but only needs MMDDYYYY, not the timestamp
            eventDateTextfield.setText(mMarkers.get(marker.getId()).getStartDate().toLocaleString().substring(0, 12));
            venueNameTextfield.setText(mMarkers.get(marker.getId()).getVenue().getName());

            //load event image into eventImage imageView
            Picasso.with(mContext)
                    .load(mMarkers.get(marker.getId())
                    .getImageURL(ImageSize.EXTRALARGE))
                    .placeholder(R.drawable.placeholder)
                    .into(eventImage, onImageLoaded);

            // Returning the view containing InfoWindow contents
            return infoWindow;
        }

        @Override
        public View getInfoWindow(Marker marker) {
            return null;
        }

    /**
    * This method is called after the image has been loaded. It checks if the currently displayed
    * info window is the same info window which has been saved. If it is, then refresh the window
    * to display the newly loaded image.
    */
    private Callback onImageLoaded = new Callback() {

            @Override
            public void onSuccess() {
                if (mMarkerShowingInfoWindow != null && mMarkerShowingInfoWindow.isInfoWindowShown()) {
                    mMarkerShowingInfoWindow.hideInfoWindow();
                    mMarkerShowingInfoWindow.showInfoWindow();
                }
            }

            @Override
            public void onError() {

            }
        };

    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        String eventUrl = mMarkers.get(marker.getId()).getUrl();
        //opens event url in browser for now
        //TODO send intent to eventsDetails activity when that screen is completed
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(eventUrl));
        getActivity().startActivity(browserIntent);
    }

}
