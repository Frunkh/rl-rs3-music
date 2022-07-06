/*
 * Copyright (c) 2020, RKGman
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.music.rs3music;

import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.io.*;
import java.net.URL;
import java.util.stream.Collectors;

import jaco.mp3.player.MP3Player;
import net.runelite.client.util.ImageUtil;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

@Slf4j
@PluginDescriptor(
        name = "RS3 Music",
        description = "Plays the RS3 variant of the current track being played in game.",
        tags = {"music"}
)
public class RS3MusicPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private RS3MusicConfig config;

    @Inject
    private OverlayManager overlayManager;

    private final static String TRACK_LIST_REPO =
            "https://rs3.frunk.ovh/";

    private final int FADING_TRACK_STATE = 0;

    private final int PLAYING_TRACK_STATE = 1;

    private int currentPlayerState = PLAYING_TRACK_STATE;

    private NavigationButton navButton;

    private RS3MusicPanel panel;

    private Widget currentTrackBox;

    private String previousTrack = "";

    private String nextTrack = "";

    private final MP3Player trackPlayer = new MP3Player();

    private Thread handlePlayThread = null;

    private Map<String, Track> mp3Map = new HashMap<String, Track>();

    private ArrayList<String> availableTrackNameArray = new ArrayList<String>();

    private Random rng = new Random();

    private Collection<Widget> tracks = null;

    private boolean remixAvailable = false;

    private boolean changingTracks = false;

    private boolean initializeTrack = true;


    private void buildMp3TrackMap() {
        try {
            // Check if track listing CSV exists.

            // Copy default track list from resources.
            String updatedCsv = getUpdatedListFromRepo();

            String delimiter = ",";

            for (String line : updatedCsv.split("\n")) {
                String[] track = line.split(delimiter);    // use comma as separator
                if (track.length == 1) {
                    // System.out.println("Track: [Name=" + track[0] + "]");
                    Track newTrack = new Track();
                    newTrack.name = track[0];
                    mp3Map.put(track[0], newTrack);
                } else {
                    // System.out.println("Track: [Name=" + track[0] + ", Link=" + track[1] + "]");
                    Track newTrack = new Track();
                    newTrack.name = track[0];
                    newTrack.link = (TRACK_LIST_REPO + track[1]).replace(" ", "%20");
                    newTrack.credit = "Jagex";
                    mp3Map.put(track[0], newTrack);
                    availableTrackNameArray.add(track[0]);
                }
            }

            log.info("Tracks successfully added to map.");
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private String getTrackLink(String anonFilesLink) {
        String link = "";

        try {
            // Only if in whitelist
            Document doc = Jsoup.connect(anonFilesLink).get();

            Element downloadUrl = doc.getElementById("download-url");

            link = downloadUrl.attr("href");

            link = link.replace(" ", "%20");

            // log.info("Link: " + link);

        } catch (Exception e) {
            // log.error(e.getMessage()); TODO: Still log but prevent spamming the file
        }

        return link;
    }

    private void fadeCurrentTrack() {
        client.setMusicVolume(0);
        trackPlayer.setVolume(0);
        trackPlayer.stop();
        previousTrack = nextTrack;
        currentPlayerState = PLAYING_TRACK_STATE;
    }

    private String getUpdatedListFromRepo() {
        String rv = "";

        try {
            // Only if in whitelist

            URL url = new URL(TRACK_LIST_REPO);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));

            String inputLine;
            StringBuffer content = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine + "\n");
            }

            in.close();

            rv = content.toString();

            connection.disconnect();

        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return rv;
    }

    private boolean isOnMusicTab() {
        return client.getVar(VarClientInt.INVENTORY_TAB) == 13;
    }

    private void tagRemixedTracks() {
        final Widget musicList = client.getWidget(WidgetInfo.MUSIC_TRACK_LIST);

        if (tracks == null) {
            tracks = Arrays.stream(musicList.getDynamicChildren())
                    .sorted(Comparator.comparing(Widget::getRelativeY))
                    .collect(Collectors.toList());
        }

        for (Widget track : tracks) {
            Track mappedTrack = mp3Map.get(track.getText());
            if (mappedTrack != null && mappedTrack.link != null) {
                // The track can be played, mark cyan.
                track.setTextColor(Color.CYAN.getRGB());
                // TODO: Figure out how to mark tracks not unlocked.  getColor doesn't match with Color.red / green
            }
        }
    }

    private void playTrack(String trackName) {
        trackPlayer.getPlayList().clear();

        Track track = mp3Map.get(trackName);

        if (track != null && track.link != null) {
            remixAvailable = true;
            client.setMusicVolume(0);
            trackPlayer.setVolume(config.volume());
            Track finalTrack = track;
            handlePlayThread = new Thread(() -> {
                try {
                    // Get actual track link
                    String directLink = finalTrack.link;
                    trackPlayer.addToPlayList(new URI(directLink).toURL());
                    trackPlayer.play();
                } catch (Exception e) {
                    // log.error(e.getMessage()); TODO: Still log but prevent spamming the file
                }
            });

            handlePlayThread.start();

            client.addChatMessage(ChatMessageType.GAMEMESSAGE,
                    "",
                    "RS3 Music Notice: " + track.name,
                    null);

            initializeTrack = false;
        } else {
            remixAvailable = false;
        }
    }

    @Override
    protected void startUp() throws Exception {
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

        panel = new RS3MusicPanel(this);

        navButton = NavigationButton.builder()
                .tooltip("RS3 Music")
                .icon(icon)
                .priority(50)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);

        // Build map of mp3 track links
        buildMp3TrackMap();

        log.info("RS3 Music started!");
    }

    @Override
    protected void shutDown() throws Exception {
        clientToolbar.removeNavigation(navButton);

        trackPlayer.stop();

        log.info("RS3 Music stopped!");
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged varClientIntChanged) {
        if (isOnMusicTab() == true) {
            tagRemixedTracks();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            config.setMusicVolume(0);
        }
        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
            try {
                client.setMusicVolume(0); // Attempt to force mute.

                if (config.mute() == true) {
                    trackPlayer.setVolume(0);
                }

                // Stop current track
                // trackPlayer.stop();
                trackPlayer.getPlayList().clear();
                // Start playing new track
                Track track = mp3Map.get("Scape Main");
                if (track.link != null) {
                    remixAvailable = true;
                    trackPlayer.setVolume(config.volume());
                    handlePlayThread = new Thread(() -> {
                        try {
                            // Get actual track link
                            String directLink = getTrackLink(track.link);
                            trackPlayer.addToPlayList(new URL(directLink));
                            trackPlayer.play();
                        } catch (Exception e) {
                            // log.error(e.getMessage()); TODO: Still log but prevent spamming the file
                        }
                    });

                    handlePlayThread.start();
                } else {
                    remixAvailable = false;
                    //  TODO: Handle playing normal song, or not
                }
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded) {
        if (widgetLoaded.getGroupId() == WidgetID.RESIZABLE_VIEWPORT_OLD_SCHOOL_BOX_GROUP_ID) {
            Widget viewport = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_OLD_SCHOOL_BOX);
            currentTrackBox = viewport.createChild(-1, WidgetType.TEXT);
        }
    }


    @Subscribe
    public void onGameTick(GameTick gameTick) {
        final Widget currentTrack = client.getWidget(
                MusicWidgetInfo.MUSIC_CURRENT_TRACK.getGroupId(),
                MusicWidgetInfo.MUSIC_CURRENT_TRACK.getChildId());

        // If loop flag set, the player is loaded with music, and it is no longer playing, start again.
        if (!trackPlayer.isPlaying()) {
            playTrack(currentTrack.getText());
        }

        if (isOnMusicTab() == true) {
            tagRemixedTracks();
        }

        if (previousTrack != currentTrack.getText()) {
            changingTracks = true;
            nextTrack = currentTrack.getText();
            currentPlayerState = FADING_TRACK_STATE;
            initializeTrack = true;
        } else {
            changingTracks = false;
        }


        try {
            if (changingTracks == true && currentPlayerState == FADING_TRACK_STATE) {
                    fadeCurrentTrack();
            } else {
                if (initializeTrack == true) {
                    playTrack(currentTrack.getText());
                }
            }
        } catch (Exception e) {
            // log.error(e.getMessage()); TODO: Still log but prevent spamming the file
        }

        if (config.mute() == true) {
            trackPlayer.setVolume(0);
            client.setMusicVolume(0);
        } else {
            if (remixAvailable == true) {    // If not in a fading state...
                if (currentPlayerState == PLAYING_TRACK_STATE) {
                    // TODO: Make this not trash.
                    if (trackPlayer.getVolume() < config.volume() && trackPlayer.getVolume() >= 0) {
                        int newVol = trackPlayer.getVolume() + 14;
                        if(newVol > 100) {
                            newVol = 100;
                        }
                        trackPlayer.setVolume(newVol);
                    } else if (trackPlayer.getVolume() > config.volume()) {
                        trackPlayer.setVolume(config.volume());
                    } else if(trackPlayer.getVolume() < 0){
                        //It's below 0 lmao
                        trackPlayer.setVolume(0);
                    }

                    client.setMusicVolume(0);
                }
            } else {
                trackPlayer.setVolume(0);
                client.setMusicVolume(0);
            }
        }

        if (currentTrackBox != null) {
            currentTrackBox.setText(currentTrack.getText());

            if (mp3Map.get(currentTrack.getText()) != null && mp3Map.get(currentTrack.getText()).link != null) {
                currentTrack.setTextColor(Color.CYAN.getRGB());
            } else {
                currentTrack.setTextColor(Color.GREEN.getRGB());
            }
        }
    }

    @Provides
    RS3MusicConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(RS3MusicConfig.class);
    }


    public RS3MusicConfig getMusicConfig() {
        return config;
    }
}

class Track {
    public String name;
    public String link;
    public String credit;
}
