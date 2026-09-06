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
package com.xeon.view3d;

import com.xeon.model.Tile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Map3DPanelHoverPolicyTest
{
	private final Map3DLayer hideForObjects = new Map3DLayer()
	{
		@Override
		public boolean tileHoverSelectorVisible(boolean objectHovered)
		{
			return !objectHovered;
		}
	};

	@Test
	void npcOutlineSuppressesTileSelectorWithoutAPlugin()
	{
		assertFalse(Map3DPanel.shouldShowTileHoverSelector(null, true, false, false));
		assertTrue(Map3DPanel.shouldShowTileHoverSelector(null, false, false, false));
	}

	@Test
	void pluginControlsOrdinaryObjectHoverOnly()
	{
		assertFalse(Map3DPanel.shouldShowTileHoverSelector(hideForObjects, false, true, false));
		assertTrue(Map3DPanel.shouldShowTileHoverSelector(hideForObjects, false, false, false));
	}

	@Test
	void agilityObstacleKeepsTileSelectorVisible()
	{
		assertTrue(Map3DPanel.shouldShowTileHoverSelector(hideForObjects, false, true, true));
		Tile tile = new Tile(3200, 3201, 0);
		TerrainRenderer.HoveredObjectInfo hovered = new TerrainRenderer.HoveredObjectInfo(tile, 10, 11);
		AgilityObstacleInstance obstacle = new AgilityObstacleInstance(tile, 1, 1, 10, 11,
			AgilityObstacleData.Kind.COURSE, 1, "Obstacle");
		assertTrue(Map3DPanel.matchesAgilityObstacle(hovered, obstacle));
		assertFalse(Map3DPanel.matchesAgilityObstacle(hovered,
			new AgilityObstacleInstance(new Tile(3202, 3201, 0), 1, 1, 10, 11,
				AgilityObstacleData.Kind.COURSE, 1, "Other")));
		assertFalse(Map3DPanel.matchesAgilityObstacle(
			new TerrainRenderer.HoveredObjectInfo(tile, 20, -1),
			new AgilityObstacleInstance(tile, 1, 1, 21, -1,
				AgilityObstacleData.Kind.COURSE, 1, "Unknown transform")));
	}
}
