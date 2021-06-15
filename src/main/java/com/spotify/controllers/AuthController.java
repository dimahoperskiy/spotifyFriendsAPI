package com.spotify.controllers;

import com.spotify.services.TrackService;
import com.spotify.services.UserService;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.*;
import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import com.wrapper.spotify.requests.data.library.GetUsersSavedTracksRequest;
import com.wrapper.spotify.requests.data.personalization.simplified.GetUsersTopArtistsRequest;
import com.wrapper.spotify.requests.data.personalization.simplified.GetUsersTopTracksRequest;
import com.wrapper.spotify.requests.data.users_profile.GetCurrentUsersProfileRequest;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.hc.core5.http.ParseException;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;


@RestController
@CrossOrigin
@RequestMapping("/api")
public class AuthController {
    @Autowired
    private UserService userService;
    @Autowired
    private TrackService trackService;

    private static final URI redirectUri = SpotifyHttpManager.makeUri("http://localhost:8080/api/getcode");
    private String code = "";

    private final Dotenv dotenv = Dotenv.load();

    private final SpotifyApi spotifyApi = new SpotifyApi.Builder()
            .setClientId(dotenv.get("CLIENT_ID"))
            .setClientSecret(dotenv.get("CLIENT_SECRET"))
            .setRedirectUri(redirectUri)
            .build();


    @GetMapping("/login")
    @ResponseBody
    public String spotifyLogin() {

        System.out.println(System.getenv("ME"));
        AuthorizationCodeUriRequest authorizationCodeUriRequest = spotifyApi.authorizationCodeUri()
                .scope("user-read-playback-position, user-read-private, user-read-email, " +
                        "playlist-read-private, user-library-read, user-library-modify, " +
                        "user-top-read, playlist-read-collaborative, playlist-modify-public, " +
                        "playlist-modify-private, ugc-image-upload, user-follow-read, " +
                        "user-follow-modify, user-read-playback-state, user-modify-playback-state, " +
                        "user-read-currently-playing, user-read-recently-played")
                .show_dialog(true)
                .build();
        final URI uri = authorizationCodeUriRequest.execute();

        return uri.toString();
    }

    @GetMapping("/getcode")
    public ResponseEntity<String> getSpotifyUserCode(@RequestParam("code") String userCode, HttpServletResponse response) throws IOException, ParseException, SpotifyWebApiException {
        code = userCode;

        String json = " ";
        Request request = Request.Post("https://accounts.spotify.com/api/token");
        String body = "grant_type=authorization_code&code=" + code + "&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fgetcode";
        request.bodyString(body, ContentType.APPLICATION_FORM_URLENCODED);
        request.setHeader("Authorization", "Basic ZTIzNWYzOGQ3NTFmNDc4NzgxM2Y1N2QzZjY4ZDI2MzU6ZGM5NDkxNTk1OWQxNDUzM2FiNWMxNzA0YmIwYTNjNzA=");
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        HttpResponse httpResponse = request.execute().returnResponse();
        if (httpResponse.getEntity() != null) {
            json = EntityUtils.toString(httpResponse.getEntity());
        }

        JSONObject jsonObject = new JSONObject(json);
        String access_token = jsonObject.getString("access_token");
        String refresh_token = jsonObject.getString("refresh_token");

        spotifyApi.setAccessToken(access_token);
        spotifyApi.setRefreshToken(refresh_token);

        SpotifyApi spotifyApi1 = new SpotifyApi.Builder()
                .setAccessToken(access_token).build();


        GetCurrentUsersProfileRequest getCurrentUsersProfileRequest = spotifyApi1.getCurrentUsersProfile()
                .build();

        System.out.println(access_token);

        getCurrentUsersProfileRequest.execute();

        Cookie cookie = new Cookie("at", access_token);
        cookie.setHttpOnly(true);
        cookie.setMaxAge(Integer.MAX_VALUE);
        cookie.setPath("/");
        Cookie cookie1 = new Cookie("rt", refresh_token);
        cookie1.setHttpOnly(true);
        cookie1.setMaxAge(Integer.MAX_VALUE);
        cookie1.setPath("/");

        response.addCookie(cookie);
        response.addCookie(cookie1);

        response.sendRedirect("http://localhost:3000/home");

        return ResponseEntity.ok().body("Good");
    }


    @GetMapping("me")
    public Object getMe(@CookieValue("at") String access_token, HttpServletResponse response) {
        SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setAccessToken(access_token)
                .build();

        GetCurrentUsersProfileRequest getCurrentUsersProfileRequest = spotifyApi.getCurrentUsersProfile()
                .build();

        try {
            User user = getCurrentUsersProfileRequest.execute();

            Image image;
            String url;

            Image[] images = user.getImages();
            if (images.length > 0) {
                image = (Image) Array.get(images, 0);
                url = image.getUrl();
            } else {
                url = "http://localhost:8080/images/profile.png";
            }

            com.spotify.models.User user1;

            if (userService.existsById(user.getId())) {
                com.spotify.models.User savedUser = userService.findById(user.getId());
                savedUser.setDisplayName(user.getDisplayName());
                savedUser.setCountry(user.getCountry().getName());
                savedUser.setImage(url);
                savedUser.setEmail(user.getEmail());
                user1 = userService.saveUser(savedUser);
                return ResponseEntity.ok().body(user1);
            } else {
                return ResponseEntity.ok().body("redirect");
            }
        } catch (ParseException | SpotifyWebApiException | IOException e) {
            return ResponseEntity.badRequest().body("Bad access token");
        }
    }

    @PostMapping("save-user")
    public ResponseEntity<?> saveUser(@CookieValue("at") String access_token, @RequestBody String username) {
        username = username.substring(0, username.length()-1);

        if (userService.existsByUsername(username)) {
            return ResponseEntity.badRequest().body("username exists");
        }

        SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setAccessToken(access_token)
                .build();

        GetCurrentUsersProfileRequest getCurrentUsersProfileRequest = spotifyApi.getCurrentUsersProfile()
                .build();

        try {
            User user = getCurrentUsersProfileRequest.execute();

            Image image;
            String url;

            Image[] images = user.getImages();
            if (images.length > 0) {
                image = (Image) Array.get(images, 0);
                url = image.getUrl();
            } else {
                url = "http://localhost:8080/images/profile.png";
            }

            com.spotify.models.User user1 = new com.spotify.models.User(
                    user.getId(),
                    username,
                    user.getDisplayName(),
                    user.getEmail(),
                    url,
                    user.getCountry().getName()
            );
            user1 = userService.saveUser(user1);

            System.out.println("GOOD");

            return ResponseEntity.ok().body(user1);
        } catch (ParseException | SpotifyWebApiException | IOException e) {
            return ResponseEntity.badRequest().body("Bad access token");
        }
    }

    @GetMapping("get-saved-tracks")
    public ResponseEntity<?> getSavedTracks(@CookieValue("at") String access_token) {
        int limitValue = 50;

        SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setAccessToken(access_token)
                .build();

        GetCurrentUsersProfileRequest getCurrentUsersProfileRequest = spotifyApi.getCurrentUsersProfile()
                .build();

        GetUsersSavedTracksRequest getUsersSavedTracksRequest = spotifyApi.getUsersSavedTracks()
                .limit(limitValue)
                .build();

        try {
            User user = getCurrentUsersProfileRequest.execute();
            com.spotify.models.User user1 = userService.findById(user.getId());

            Paging<SavedTrack> savedTrackPaging = getUsersSavedTracksRequest.execute();

            int total = savedTrackPaging.getTotal();
            int portions = (int) Math.ceil(total / (double) limitValue);

            trackService.asyncSaveTracks(spotifyApi, portions, limitValue, user1);

            return ResponseEntity.ok().body(savedTrackPaging.getItems());
        } catch (ParseException | SpotifyWebApiException | IOException | InterruptedException | ExecutionException e) {
            return ResponseEntity.badRequest().body("Bad access token");
        }
    }


    @GetMapping("get-top-tracks")
    public ResponseEntity<?> getTopTracks(@CookieValue("at") String access_token, @RequestParam String time_range) {
        SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setAccessToken(access_token)
                .build();

        GetUsersTopTracksRequest getUsersTopTracksRequest = spotifyApi.getUsersTopTracks()
                .limit(20)
                .time_range(time_range)
                .build();

        try {
            Paging<Track> topTracksPaging = getUsersTopTracksRequest.execute();
            return ResponseEntity.ok().body(topTracksPaging.getItems());
        } catch (ParseException | SpotifyWebApiException | IOException e) {
            return ResponseEntity.badRequest().body("Bad access token");
        }
    }

    @GetMapping("get-top-artists")
    public ResponseEntity<?> getTopArtists(@CookieValue("at") String access_token, @RequestParam String time_range) {
        SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setAccessToken(access_token)
                .build();

        GetUsersTopArtistsRequest getUsersTopArtistsRequest = spotifyApi.getUsersTopArtists()
                .limit(20)
                .time_range(time_range)
                .build();

        try {
            Paging<Artist> topArtistsPaging = getUsersTopArtistsRequest.execute();
            return ResponseEntity.ok().body(topArtistsPaging.getItems());
        } catch (ParseException | SpotifyWebApiException | IOException e) {
            return ResponseEntity.badRequest().body("Bad access token");
        }
    }
}
