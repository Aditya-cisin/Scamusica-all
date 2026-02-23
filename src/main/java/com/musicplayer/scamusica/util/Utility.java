package com.musicplayer.scamusica.util;

public enum Utility {

    BASE_URL("https://rtas5010.elb.cisinlive.com"),
    MUSIC_PATH("/public/music/"),
    API_SONGS_ENDPOINT("/api/songs"),

    VERIFY_LICENSE_CODE("/api/auth/verify-license-code");

    private final String value;

    Utility(String value) {
        this.value = value;
    }

    public String get() {
        return value;
    }

}
