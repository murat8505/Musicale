package com.anescobar.musicale.interfaces;

import de.umass.lastfm.Artist;

/**
 * Created by Andres Escobar on 9/16/14.
 * Callback listener for artist info fetcher async task
 */
public interface ArtistInfoFetcherTaskListener {
    void onArtistInfoFetcherTaskAboutToStart();
    void onArtistInfoFetcherTaskCompleted(Artist artist);
}