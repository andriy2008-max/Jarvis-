package com.andriy.jarvis;

public final class SpotifyPlaylistRowRulesTest {
    private static void ok(boolean condition) {
        if(!condition)throw new AssertionError("Spotify playlist row safety");
    }
    public static void main(String[] args) {
        ok(SpotifyPlaylistRowRules.row(20,510,660,610,0,0,684,1536,2.0f));
        ok(SpotifyPlaylistRowRules.row(30,580,650,665,0,0,684,1536,2.0f));
        ok(!SpotifyPlaylistRowRules.row(500,300,650,385,0,0,684,1536,2.0f));
        ok(!SpotifyPlaylistRowRules.row(25,1425,650,1500,0,0,684,1536,2.0f));
        ok(!SpotifyPlaylistRowRules.row(20,510,660,900,0,0,684,1536,2.0f));
        ok(!SpotifyPlaylistRowRules.row(20,200,660,295,0,0,684,1536,2.0f));
        ok(SpotifyPlaylistRowRules.possibleTitle("Квіти під дощем"));
        ok(SpotifyPlaylistRowRules.possibleTitle("Miyagi — Captain"));
        ok(!SpotifyPlaylistRowRules.possibleTitle("Play"));
        ok(!SpotifyPlaylistRowRules.possibleTitle("Моя бібліотека"));
        ok(!SpotifyPlaylistRowRules.possibleTitle("Фільтр"));
        ok(!SpotifyPlaylistRowRules.possibleTitle("Shuffle"));
        ok(!SpotifyPlaylistRowRules.possibleTitle("Реп"));
        System.out.println("PASS SpotifyPlaylistRowRulesTest");
    }
}
