/*
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
package com.xeon.plugins.shortestpath.core;

import java.util.List;

public final class PathfinderResult
{
	private final int start;
	private final int target;
	private final boolean reached;
	private final List<PathStep> pathSteps;
	private final int closestReachedPoint;
	private final int nodesChecked;
	private final int transportsChecked;
	private final long elapsedNanos;
	private final PathTerminationReason terminationReason;

	PathfinderResult(
		int start,
		int target,
		boolean reached,
		List<PathStep> pathSteps,
		int closestReachedPoint,
		int nodesChecked,
		int transportsChecked,
		long elapsedNanos,
		PathTerminationReason terminationReason
	)
	{
		this.start = start;
		this.target = target;
		this.reached = reached;
		this.pathSteps = pathSteps;
		this.closestReachedPoint = closestReachedPoint;
		this.nodesChecked = nodesChecked;
		this.transportsChecked = transportsChecked;
		this.elapsedNanos = elapsedNanos;
		this.terminationReason = terminationReason;
	}

	public int getStart()
	{
		return start;
	}

	public int getTarget()
	{
		return target;
	}

	public boolean isReached()
	{
		return reached;
	}

	public List<PathStep> getPathSteps()
	{
		return pathSteps;
	}

	public int getClosestReachedPoint()
	{
		return closestReachedPoint;
	}

	public int getNodesChecked()
	{
		return nodesChecked;
	}

	public int getTransportsChecked()
	{
		return transportsChecked;
	}

	public long getElapsedNanos()
	{
		return elapsedNanos;
	}

	public PathTerminationReason getTerminationReason()
	{
		return terminationReason;
	}
}
