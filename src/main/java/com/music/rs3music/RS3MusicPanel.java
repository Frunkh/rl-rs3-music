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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public
class RS3MusicPanel extends PluginPanel implements ChangeListener, ActionListener
{
    RS3MusicPlugin RS3MusicPlugin;



    public RS3MusicPanel(RS3MusicPlugin RS3MusicPlugin)
    {
        super(false);

        this.RS3MusicPlugin = RS3MusicPlugin;

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Title
        JPanel titlePanel = new JPanel();
        titlePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        titlePanel.setLayout(new BorderLayout());

        JLabel title = new JLabel();
        title.setText("RS3 Music Controls");
        title.setForeground(Color.WHITE);
        titlePanel.add(title, BorderLayout.WEST);

        add(titlePanel, BorderLayout.NORTH);

        // End Title Panel

        JPanel volumePanel = new JPanel();
        volumePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        volumePanel.setLayout(new FlowLayout());

        // Volume
        JLabel volumeLabel = new JLabel();
        volumeLabel.setText("Volume");
        volumeLabel.setForeground(Color.WHITE);
        JSlider volumeSlider = new JSlider(JSlider.HORIZONTAL, 0, 100,
                RS3MusicPlugin.getMusicConfig().volume());
        volumeSlider.setBackground(Color.LIGHT_GRAY);
        volumeSlider.setName("volume");
        volumeSlider.addChangeListener((ChangeListener) this);
        volumePanel.add(volumeLabel);
        volumePanel.add(volumeSlider);

        JPanel togglePanel = new JPanel();
        togglePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        togglePanel.setLayout(new GridLayout(0, 1));

        // Mute
        JLabel muteLabel = new JLabel();
        muteLabel.setText("Mute:");
        muteLabel.setForeground(Color.WHITE);
        JCheckBox muteCheckBox = new JCheckBox();
        muteCheckBox.setSelected(RS3MusicPlugin.getMusicConfig().mute());
        muteCheckBox.setForeground(Color.WHITE);
        muteCheckBox.setName("mute");
        muteCheckBox.addActionListener((ActionListener) this);
        togglePanel.add(new JSeparator());
        togglePanel.add(muteLabel);
        togglePanel.add(muteCheckBox);

        // Mute
        JLabel logMusicChange = new JLabel();
        logMusicChange.setText("Log music change:");
        logMusicChange.setForeground(Color.WHITE);
        JCheckBox logCheckBox = new JCheckBox();
        logCheckBox.setSelected(RS3MusicPlugin.getMusicConfig().log());
        logCheckBox.setForeground(Color.WHITE);
        logCheckBox.setName("log");
        logCheckBox.addActionListener((ActionListener) this);
        togglePanel.add(new JSeparator());
        togglePanel.add(logMusicChange);
        togglePanel.add(logCheckBox);

        volumePanel.add(togglePanel);

        add(volumePanel, BorderLayout.CENTER);
    }

    public void stateChanged(ChangeEvent e)
    {
        JSlider source = (JSlider)e.getSource();
        if (!source.getValueIsAdjusting()) {
            if (source.getName() == "volume")
            {
                    RS3MusicPlugin.getMusicConfig().setVolume(source.getValue());
            }
        }
    }

    public void actionPerformed(ActionEvent e)
    {
        JCheckBox source = (JCheckBox)e.getSource();
        if (source.getName() == "mute")
        {
            // log.info("Value of mute is " + source.isSelected());
            RS3MusicPlugin.getMusicConfig().setMute(source.isSelected());
        }
        if (source.getName() == "log")
        {
            // log.info("Value of mute is " + source.isSelected());
            RS3MusicPlugin.getMusicConfig().setLog(source.isSelected());
        }
    }
}
