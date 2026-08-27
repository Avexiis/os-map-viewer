/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
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
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.xeon.util.wikisync;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.net.URL;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public final class WikiSyncProfileControls extends JPanel
{
	private static final int DEFAULT_CONTENT_WIDTH = 252;
	private static final int LOOKUP_GAP = 6;
	private static final Color PROFILE_STATUS_LOADED_FOREGROUND = new Color(0x35D07F);
	private static final Color PROFILE_STATUS_MISSING_FOREGROUND = new Color(0xFF5C7A);

	private final boolean showTitle;
	private final JLabel title = new JLabel("WikiSync Profile");
	private final JTextField profileName = new JTextField(12);
	private final JButton lookUpProfile = new JButton("Look up");
	private final JLabel profileStatus = new JLabel("Profile: None");
	private final JPanel lookupRow = new JPanel(new GridBagLayout());
	private final JPanel statusRow = new JPanel(new GridBagLayout());
	private final WikiSyncManager.Listener listener = ignored -> syncFromManager();

	private WikiSyncManager manager;
	private Consumer<String> statusSink;
	private int contentWidth = DEFAULT_CONTENT_WIDTH;
	private long lastReportedStatusVersion = Long.MIN_VALUE;

	public WikiSyncProfileControls()
	{
		this(true);
	}

	public WikiSyncProfileControls(boolean showTitle)
	{
		this.showTitle = showTitle;
		setOpaque(false);
		setLayout(new GridBagLayout());
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));

		lookupRow.setOpaque(false);
		statusRow.setOpaque(false);
		lookUpProfile.setIcon(loadRuneLiteIcon());
		lookUpProfile.setHorizontalTextPosition(SwingConstants.RIGHT);
		lookUpProfile.setFocusable(false);
		lookUpProfile.setMargin(new Insets(2, 6, 2, 6));
		profileName.addActionListener(e -> emitProfileLookup());
		lookUpProfile.addActionListener(e -> emitProfileLookup());

		buildRows();
		setContentWidth(DEFAULT_CONTENT_WIDTH);
		setProfileLoaded(false);
	}

	public void bind(WikiSyncManager manager, Consumer<String> statusSink)
	{
		unbind();
		this.manager = manager;
		this.statusSink = statusSink;
		if (manager != null)
		{
			manager.addListener(listener);
		}
		syncFromManager();
	}

	public void unbind()
	{
		if (manager != null)
		{
			manager.removeListener(listener);
		}
		manager = null;
		statusSink = null;
	}

	public void setContentWidth(int width)
	{
		contentWidth = Math.max(180, width);
		int rowHeight = Math.max(26, lookUpProfile.getPreferredSize().height);
		int buttonWidth = Math.max(88, lookUpProfile.getPreferredSize().width);
		int fieldWidth = Math.max(80, contentWidth - buttonWidth - LOOKUP_GAP);
		setFixedSize(profileName, new Dimension(fieldWidth, rowHeight));
		setFixedSize(lookUpProfile, new Dimension(buttonWidth, rowHeight));
		setFixedSize(lookupRow, new Dimension(contentWidth, rowHeight));
		setFixedSize(statusRow, new Dimension(contentWidth, Math.max(20, profileStatus.getPreferredSize().height)));

		int height = rowHeight + statusRow.getPreferredSize().height + 4;
		if (showTitle)
		{
			height += Math.max(20, title.getPreferredSize().height) + 4;
		}
		setMinimumSize(new Dimension(contentWidth, height));
		setPreferredSize(new Dimension(contentWidth, height));
		setMaximumSize(new Dimension(contentWidth, height));
		revalidate();
	}

	private void buildRows()
	{
		GridBagConstraints rowConstraints = new GridBagConstraints();
		rowConstraints.gridx = 0;
		rowConstraints.gridy = 0;
		rowConstraints.weightx = 1.0;
		rowConstraints.fill = GridBagConstraints.HORIZONTAL;
		rowConstraints.anchor = GridBagConstraints.WEST;
		rowConstraints.insets = new Insets(0, 0, showTitle ? 4 : 0, 0);
		if (showTitle)
		{
			add(title, rowConstraints);
			rowConstraints.gridy++;
		}

		GridBagConstraints lookupConstraints = new GridBagConstraints();
		lookupConstraints.gridx = 0;
		lookupConstraints.gridy = 0;
		lookupConstraints.weightx = 1.0;
		lookupConstraints.fill = GridBagConstraints.HORIZONTAL;
		lookupConstraints.anchor = GridBagConstraints.WEST;
		lookupRow.add(profileName, lookupConstraints);
		lookupConstraints.gridx = 1;
		lookupConstraints.weightx = 0.0;
		lookupConstraints.fill = GridBagConstraints.NONE;
		lookupConstraints.insets = new Insets(0, LOOKUP_GAP, 0, 0);
		lookupRow.add(lookUpProfile, lookupConstraints);

		rowConstraints.insets = new Insets(0, 0, 3, 0);
		add(lookupRow, rowConstraints);
		rowConstraints.gridy++;

		GridBagConstraints statusConstraints = new GridBagConstraints();
		statusConstraints.gridx = 0;
		statusConstraints.gridy = 0;
		statusConstraints.weightx = 1.0;
		statusConstraints.anchor = GridBagConstraints.WEST;
		statusConstraints.fill = GridBagConstraints.HORIZONTAL;
		statusRow.add(profileStatus, statusConstraints);
		rowConstraints.insets = new Insets(0, 0, 0, 0);
		add(statusRow, rowConstraints);
	}

	private void emitProfileLookup()
	{
		WikiSyncManager current = manager;
		if (current == null)
		{
			reportStatus("WikiSync is unavailable.");
			return;
		}
		current.lookupAndStoreAsync(profileName.getText());
	}

	private void syncFromManager()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::syncFromManager);
			return;
		}
		WikiSyncManager current = manager;
		if (current == null)
		{
			setProfileLookupRunning(false);
			setProfileLoaded(false);
			return;
		}

		String currentUsername = current.username();
		if (currentUsername != null && !currentUsername.isBlank()
			&& (!profileName.hasFocus() || current.isLookupRunning()))
		{
			profileName.setText(currentUsername);
		}

		setProfileLookupRunning(current.isLookupRunning());
		if (current.isLookupRunning())
		{
			profileStatus.setText("Profile: Loading");
			profileStatus.setForeground(PROFILE_STATUS_MISSING_FOREGROUND);
		}
		else
		{
			setProfileLoaded(current.hasProfile());
		}

		long version = current.stateVersion();
		String message = current.message();
		if (version != lastReportedStatusVersion && message != null && !message.isBlank()
			&& !"WikiSync profile not configured".equals(message))
		{
			lastReportedStatusVersion = version;
			reportStatus(message);
		}
	}

	private void setProfileLoaded(boolean loaded)
	{
		profileStatus.setText(loaded ? "Profile Loaded" : "Profile: None");
		profileStatus.setForeground(loaded ? PROFILE_STATUS_LOADED_FOREGROUND : PROFILE_STATUS_MISSING_FOREGROUND);
	}

	private void setProfileLookupRunning(boolean running)
	{
		profileName.setEnabled(!running);
		lookUpProfile.setEnabled(!running);
	}

	private void reportStatus(String message)
	{
		if (statusSink != null)
		{
			statusSink.accept(message);
		}
	}

	private static void setFixedSize(JPanel panel, Dimension size)
	{
		panel.setMinimumSize(size);
		panel.setPreferredSize(size);
		panel.setMaximumSize(size);
	}

	private static void setFixedSize(AbstractButton button, Dimension size)
	{
		button.setMinimumSize(size);
		button.setPreferredSize(size);
		button.setMaximumSize(size);
	}

	private static void setFixedSize(JTextField field, Dimension size)
	{
		field.setMinimumSize(size);
		field.setPreferredSize(size);
		field.setMaximumSize(size);
	}

	private static Icon loadRuneLiteIcon()
	{
		URL resource = WikiSyncProfileControls.class.getResource("/com/xeon/application/data/runelite_icon.png");
		if (resource == null)
		{
			return null;
		}
		ImageIcon icon = new ImageIcon(resource);
		Image image = icon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
		return new ImageIcon(image);
	}
}
