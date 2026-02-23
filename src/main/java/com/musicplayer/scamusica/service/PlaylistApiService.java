package com.musicplayer.scamusica.service;

import com.google.gson.*;
import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.model.PlaylistTrack;
import com.musicplayer.scamusica.storage.SongMetadataStore;
import com.musicplayer.scamusica.util.ApiClient;
import com.musicplayer.scamusica.util.Utility;

import java.io.File;
import java.util.*;

public class PlaylistApiService {

    private static final String SONGS_URL =
            Utility.BASE_URL.get() + Utility.API_SONGS_ENDPOINT.get();

    public List<String> fetchPlaylistTitles() {
        try {
            JsonObject root = fetchRootJson();
            InternetState.markOnline();

            List<String> titles = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray("data")) {
                titles.add(el.getAsJsonObject().get("title").getAsString());
            }
            return titles;

        } catch (Exception e) {
            System.out.println("[API] Playlist fetch failed → OFFLINE");
            return loadOfflineGenres();
        }
    }

    public List<PlaylistTrack> fetchTracksForGenre(String genre) {
        try {
            List<PlaylistTrack> tracks = fetchOnlineTracks(genre);
            return tracks;

        } catch (Exception e) {
            System.out.println("[API] Track fetch failed → OFFLINE");
            return loadOfflineTracks(genre);
        }
    }

    public List<Integer> fetchDownloadSequenceForGenre(String genre) {
        try {
            JsonObject root = fetchRootJson();
            InternetState.markOnline();

            List<Integer> seq = new ArrayList<>();

            for (JsonElement el : root.getAsJsonArray("data")) {
                JsonObject g = el.getAsJsonObject();
                if (!genre.equals(g.get("title").getAsString())) continue;

                for (JsonElement f : g.getAsJsonArray("folders")) {
                    JsonObject folder = f.getAsJsonObject();

                    for (JsonElement s : folder.getAsJsonArray("songs")) {
                        seq.add(s.getAsJsonObject().get("id").getAsInt());
                    }
                }
                break;
            }

            Collections.shuffle(seq);
            return seq;

        } catch (Exception e) {
            System.out.println("[API] Download sequence fetch failed → OFFLINE");
            return Collections.emptyList();
        }
    }

    private JsonObject fetchRootJson() throws Exception {
        String token = SessionManager.loadToken();

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Accept", "application/json");

        String response = ApiClient.get(SONGS_URL, headers);
        return JsonParser.parseString(response).getAsJsonObject();
    }

    private List<PlaylistTrack> fetchOnlineTracks(String genre) throws Exception {

        List<PlaylistTrack> result = new ArrayList<>();
        JsonObject root = fetchRootJson();

        String commonPath = root.has("filePath")
                ? root.get("filePath").getAsString()
                : "";

        for (JsonElement el : root.getAsJsonArray("data")) {

            JsonObject g = el.getAsJsonObject();
            if (!genre.equals(g.get("title").getAsString())) continue;

            for (JsonElement f : g.getAsJsonArray("folders")) {

                JsonObject folderObj = f.getAsJsonObject();
                String folderTitle = folderObj.get("title").getAsString();

                // ✅ album image from folder
                String albumImgUrl = null;
                if (folderObj.has("album_img")
                        && !folderObj.get("album_img").isJsonNull()
                        && !folderObj.get("album_img").getAsString().isEmpty()) {

                    albumImgUrl = Utility.BASE_URL.get() + "/"
                            + folderObj.get("album_img").getAsString();
                }

                for (JsonElement s : folderObj.getAsJsonArray("songs")) {

                    PlaylistTrack track = parseSong(
                            s.getAsJsonObject(),
                            commonPath,
                            folderTitle,
                            albumImgUrl   // 🔥 HERE
                    );

                    if (track != null) result.add(track);
                }
            }
            break;
        }
        return result;
    }


    private List<PlaylistTrack> loadOfflineTracks(String genre) {
        List<PlaylistTrack> tracks = new ArrayList<>();
        File dir = new File("./downloads/" + genre.replaceAll("\\s+", "_"));
        if (!dir.exists()) return tracks;

        File[] files = dir.listFiles((d, n) -> n.endsWith(".mp3"));
        if (files == null) return tracks;

        for (File f : files) {
            PlaylistTrack t = SongMetadataStore.load(f);
            if (t != null) tracks.add(t);
        }

        System.out.println("[Offline] Tracks loaded: " + tracks.size());
        return tracks;
    }

    private List<String> loadOfflineGenres() {
        List<String> list = new ArrayList<>();
        File root = new File("./downloads");
        if (!root.exists()) return list;

        for (File d : Objects.requireNonNull(root.listFiles(File::isDirectory))) {
            list.add(d.getName().replace("_", " "));
        }
        return list;
    }

    private PlaylistTrack parseSong(JsonObject s,
                                    String commonPath,
                                    String folder,
                                    String albumImgUrl) {

        int id = s.get("id").getAsInt();
        String title = s.get("title").getAsString();

        String path = s.has("filePath")
                ? s.get("filePath").getAsString()
                : commonPath + s.get("file").getAsString();

        String url = path.startsWith("http")
                ? path
                : Utility.BASE_URL.get() + path;

        return new PlaylistTrack(
                id,
                title,
                url,
                0,
                folder,
                albumImgUrl
        );
    }
}
