package com.anescobar.musicale.interfaces;

import de.umass.lastfm.Event;
import de.umass.lastfm.PaginatedResult;

/**
 * Created by Andres Escobar on 8/11/14.
 * Interface to be used as callback for EventsFetcher asynctask
 */
public interface OnEventsFetcherTaskCompleted{
    void onTaskAboutToStart();
    void onTaskCompleted(PaginatedResult<Event> events);
}