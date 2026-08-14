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
package com.xeon.plugins.shortestpath;

import javax.swing.*;

final class ShortestPathMenu extends JPopupMenu
{
	private Runnable onCenterStart;
	private Runnable onCenterTarget;
	private Runnable onRecalculate;
	private Runnable onImportRoute;
	private Runnable onExportRoute;
	private Runnable onClear;

	ShortestPathMenu()
	{
		JMenuItem centerStart = new JMenuItem("Set start to center");
		JMenuItem centerTarget = new JMenuItem("Set target to center");
		JMenuItem recalculate = new JMenuItem("Recalculate");
		JMenuItem importRoute = new JMenuItem("Import route...");
		JMenuItem exportRoute = new JMenuItem("Export route...");
		JMenuItem clear = new JMenuItem("Clear");

		centerStart.addActionListener(e -> {
			if (onCenterStart != null)
			{
				onCenterStart.run();
			}
		});
		centerTarget.addActionListener(e -> {
			if (onCenterTarget != null)
			{
				onCenterTarget.run();
			}
		});
		recalculate.addActionListener(e -> {
			if (onRecalculate != null)
			{
				onRecalculate.run();
			}
		});
		importRoute.addActionListener(e -> {
			if (onImportRoute != null)
			{
				onImportRoute.run();
			}
		});
		exportRoute.addActionListener(e -> {
			if (onExportRoute != null)
			{
				onExportRoute.run();
			}
		});
		clear.addActionListener(e -> {
			if (onClear != null)
			{
				onClear.run();
			}
		});

		add(centerStart);
		add(centerTarget);
		addSeparator();
		add(recalculate);
		add(importRoute);
		add(exportRoute);
		add(clear);
	}

	void setOnCenterStart(Runnable onCenterStart)
	{
		this.onCenterStart = onCenterStart;
	}

	void setOnCenterTarget(Runnable onCenterTarget)
	{
		this.onCenterTarget = onCenterTarget;
	}

	void setOnRecalculate(Runnable onRecalculate)
	{
		this.onRecalculate = onRecalculate;
	}

	void setOnImportRoute(Runnable onImportRoute)
	{
		this.onImportRoute = onImportRoute;
	}

	void setOnExportRoute(Runnable onExportRoute)
	{
		this.onExportRoute = onExportRoute;
	}

	void setOnClear(Runnable onClear)
	{
		this.onClear = onClear;
	}
}
