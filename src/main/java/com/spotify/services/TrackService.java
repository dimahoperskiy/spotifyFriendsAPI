package com.spotify.services;

import com.spotify.models.Track;
import com.spotify.models.User;
import com.spotify.repositories.TrackRepository;
import com.spotify.repositories.UserRepository;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.SavedTrack;
import com.wrapper.spotify.requests.data.library.GetUsersSavedTracksRequest;
import org.apache.hc.core5.http.ParseException;
import org.apache.tomcat.jni.Time;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

@Service
public class TrackService {
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private UserRepository userRepository;


    //    int cntSaved = 0;
//    int cntNot = 0;
    Set<String> busyList = new HashSet<>();

    public Track saveTrack(Track track) {
        return trackRepository.save(track);
    }

    @Async("threadPoolTaskExecutor")
    public void asyncSaveTracks(SpotifyApi spotifyApi, int portions, int limitValue, User user) throws ParseException, SpotifyWebApiException, IOException, InterruptedException, ExecutionException {

        while (true) {
            if (busyList.contains(user.getId())) {
                System.out.println("Waiting on " + Thread.currentThread().getName() + " for " + user.getDisplayName());
                Thread.sleep(2000);
            } else {
                System.out.println(Thread.currentThread().getName() + " for " + user.getDisplayName() + " is free");
                break;
            }

        }

        busyList.add(user.getId());
        final int[] cntSaved = {0};
        final int[] cntNot = {0};

        System.out.println("_____________");
        System.out.println("Current thread: " + Thread.currentThread().getName());
        System.out.println("Started saving tracks for " + user.getDisplayName());
        long start = System.currentTimeMillis();

        Set<String> trackSet = new HashSet<>();
        userRepository.findById(user.getId()).orElseThrow().getTrackList().forEach(el -> {
            trackSet.add(el.getId());
        });


        for (int i = 0; i < portions; i++) {
            GetUsersSavedTracksRequest request = spotifyApi.getUsersSavedTracks()
                    .limit(limitValue)
                    .offset(i * limitValue)
                    .build();
            Paging<SavedTrack> tracks = request.execute();
            Arrays.stream(tracks.getItems()).forEach(savedTrack -> {
                if (!trackSet.contains(savedTrack.getTrack().getId())) {
                    try {
                        Track track = new Track(
                                savedTrack.getTrack().getId(),
                                savedTrack.getTrack().getName(),
                                savedTrack.getAddedAt()
                        );
                        track = saveTrack(track);
                        user.addTrack(track);
                        cntSaved[0]++;
                    } catch (DataIntegrityViolationException e) {
                        System.out.println("ERRRROROROROROR");
                        cntNot[0]++;
                    }
                } else cntNot[0]++;
            });
        }

        long end = System.currentTimeMillis();
        System.out.println(cntSaved[0] + " tracks saved");
        System.out.println(cntNot[0] + " tracks were already saved");
        System.out.println((end - start) / 1000.0 + " seconds spent");
        System.out.println("_____________");
        cntSaved[0] = 0;
        cntNot[0] = 0;
        userRepository.save(user);
        busyList.remove(user.getId());
    }
}
