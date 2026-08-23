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
import java.util.Set;

public final class Pathfinder implements Runnable
{
	private final PathfinderStats stats;
	private final int start;
	private final Set<Integer> targets;
	private final PathfinderConfig config;
	private final CollisionMap map;
	private final boolean targetInWilderness;
	private final Runnable completionCallback;
	private final NodeGraph graph = new NodeGraph(1 << 14);
	private final IntDeque boundary = new IntDeque(4096);
	private final IntMinHeap pending = new IntMinHeap(graph, 256);
	private final VisitedTiles visited;

	private volatile boolean done = false;
	private volatile boolean cancelled = false;
	private volatile int bestLastNode = NodeGraph.NO_NODE;
	private volatile List<PathStep> finalPath;
	private volatile int closestReachedPoint = WorldPointUtil.UNDEFINED;
	private List<PathStep> pathSteps = List.of();
	private boolean pathNeedsUpdate = false;
	private int bestRemainingDistance = Integer.MAX_VALUE;
	private int bestTravelledDistance = Integer.MAX_VALUE;
	private int bestX = Integer.MAX_VALUE;
	private int bestY = Integer.MAX_VALUE;
	private int reachedTarget = WorldPointUtil.UNDEFINED;
	private PathTerminationReason terminationReason;
	private int wildernessLevel = 31;

	public Pathfinder(PathfinderConfig config, int start, Set<Integer> targets, Runnable completionCallback)
	{
		stats = new PathfinderStats();
		this.config = config;
		this.map = config.getMap();
		this.start = start;
		this.targets = targets;
		this.completionCallback = completionCallback;
		visited = new VisitedTiles(map);
		targetInWilderness = WildernessChecker.isInWilderness(targets);
	}

	public int getStart()
	{
		return start;
	}

	public Set<Integer> getTargets()
	{
		return targets;
	}

	public boolean isDone()
	{
		return done;
	}

	public void cancel()
	{
		cancelled = true;
	}

	public PathfinderStats getStats()
	{
		return stats.started && stats.ended ? stats : null;
	}

	public List<PathStep> getPath()
	{
		int lastNode = bestLastNode;
		if (lastNode == NodeGraph.NO_NODE)
		{
			List<PathStep> finalized = finalPath;
			return finalized != null ? finalized : pathSteps;
		}

		if (done)
		{
			List<PathStep> finalized = finalPath;
			if (finalized != null)
			{
				return finalized;
			}
		}

		if (pathNeedsUpdate)
		{
			List<PathStep> walked = graph.getPathSteps(lastNode);
			if (!walked.isEmpty())
			{
				pathSteps = walked;
				pathNeedsUpdate = false;
			}
		}
		return pathSteps;
	}

	public PathfinderResult getResult()
	{
		PathfinderStats currentStats = getStats();
		if (currentStats == null)
		{
			return null;
		}
		List<PathStep> currentPath = getPath();
		boolean reached = reachedTarget != WorldPointUtil.UNDEFINED;
		int target = reached ? reachedTarget : (targets.isEmpty() ? WorldPointUtil.UNDEFINED : targets.iterator().next());
		return new PathfinderResult(
			start,
			target,
			reached,
			currentPath,
			closestReachedPoint,
			currentStats.getNodesChecked(),
			currentStats.getTransportsChecked(),
			currentStats.getElapsedTimeNanos(),
			terminationReason
		);
	}

	@Override
	public void run()
	{
		stats.start();
		boundary.addFirst(graph.createStart(start));

		long cutoffDurationMillis = config.getCalculationCutoffMillis();
		long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;

		while (!cancelled && (!boundary.isEmpty() || !pending.isEmpty()))
		{
			int boundaryHead = boundary.peekFirst();
			int pendingHead = pending.peek();

			int node;
			if (pendingHead != NodeGraph.NO_NODE
				&& (boundaryHead == NodeGraph.NO_NODE || graph.compareCost(pendingHead) < graph.cost(boundaryHead)))
			{
				node = pending.poll();
				if (graph.isDelayedVisit(node))
				{
					int packed = graph.packedPosition(node);
					boolean bank = graph.bankVisited(node);
					if (visited.get(packed, bank))
					{
						continue;
					}
					visited.set(node, graph);
				}
			}
			else
			{
				node = boundary.pollFirst();
			}
			if (node == NodeGraph.NO_NODE)
			{
				continue;
			}

			boolean nodeIsTile = graph.isTile(node);
			int nodePacked = nodeIsTile ? graph.packedPosition(node) : WorldPointUtil.UNDEFINED;
			if (nodeIsTile)
			{
				updateWildernessLevel(nodePacked);

				if (targets.contains(nodePacked))
				{
					bestLastNode = node;
					pathNeedsUpdate = true;
					reachedTarget = nodePacked;
					terminationReason = PathTerminationReason.TARGET_REACHED;
					break;
				}

				if (updateBestPathWhenUnreachable(node, nodePacked))
				{
					cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
				}
			}

			if (System.currentTimeMillis() > cutoffTimeMillis)
			{
				terminationReason = PathTerminationReason.CUTOFF_REACHED;
				break;
			}

			addNeighbors(node, nodeIsTile, nodePacked);
		}

		if (cancelled)
		{
			terminationReason = PathTerminationReason.CANCELLED;
		}
		else if (terminationReason == null)
		{
			terminationReason = PathTerminationReason.SEARCH_EXHAUSTED;
		}

		int lastNode = bestLastNode;
		if (lastNode != NodeGraph.NO_NODE)
		{
			finalPath = graph.getPathSteps(lastNode);
			closestReachedPoint = graph.getClosestTilePosition(lastNode);
		}
		else
		{
			finalPath = pathSteps;
			closestReachedPoint = start;
		}

		done = !cancelled;

		boundary.clear();
		visited.clear();
		pending.clear();
		graph.release();

		stats.end();
		if (completionCallback != null)
		{
			completionCallback.run();
		}
	}

	private void addNeighbors(int node, boolean nodeIsTile, int nodePacked)
	{
		PrimitiveIntList nodes = map.getNeighbors(node, visited, config, wildernessLevel, targetInWilderness, graph);
		int count = nodes.size();
		for (int i = 0; i < count; i++)
		{
			int neighbor = nodes.get(i);
			boolean neighborIsTile = graph.isTile(neighbor);
			if (nodeIsTile && neighborIsTile)
			{
				int neighborPacked = graph.packedPosition(neighbor);
				if (config.avoidWilderness(nodePacked, neighborPacked, targetInWilderness))
				{
					continue;
				}
				if (config.avoidBlockedRegion(nodePacked, neighborPacked, false))
				{
					continue;
				}
			}

			boolean neighborIsTransport = graph.isTransport(neighbor);
			if (!(neighborIsTransport && graph.isDelayedVisit(neighbor)))
			{
				visited.set(neighbor, graph);
			}
			if (neighborIsTransport)
			{
				pending.add(neighbor);
				stats.transportsChecked++;
			}
			else
			{
				boundary.addLast(neighbor);
				stats.nodesChecked++;
			}
		}
	}

	private boolean updateBestPathWhenUnreachable(int node, int packedPosition)
	{
		boolean update = false;
		int travelledDistance = graph.cost(node);
		for (int target : targets)
		{
			int remainingDistance = WorldPointUtil.distanceBetween(target, packedPosition,
				WorldPointUtil.EUCLIDEAN_SQUARED_DISTANCE_METRIC);
			int x = WorldPointUtil.unpackWorldX(packedPosition);
			int y = WorldPointUtil.unpackWorldY(packedPosition);
			if (remainingDistance < bestRemainingDistance
				|| remainingDistance == bestRemainingDistance && travelledDistance < bestTravelledDistance
				|| remainingDistance == bestRemainingDistance && travelledDistance == bestTravelledDistance && x < bestX
				|| remainingDistance == bestRemainingDistance && travelledDistance == bestTravelledDistance && x == bestX && y < bestY)
			{
				bestRemainingDistance = remainingDistance;
				bestTravelledDistance = travelledDistance;
				bestX = x;
				bestY = y;
				bestLastNode = node;
				pathNeedsUpdate = true;
				update = true;
			}
		}
		return update;
	}

	private void updateWildernessLevel(int packedPosition)
	{
		if (wildernessLevel <= 0)
		{
			return;
		}
		if (wildernessLevel > 30 && !WildernessChecker.isInLevel30Wilderness(packedPosition))
		{
			wildernessLevel = 30;
		}
		if (wildernessLevel > 20 && !WildernessChecker.isInLevel20Wilderness(packedPosition))
		{
			wildernessLevel = 20;
		}
		if (!WildernessChecker.isInWilderness(packedPosition))
		{
			wildernessLevel = 0;
		}
	}

	public static final class PathfinderStats
	{
		private int nodesChecked;
		private int transportsChecked;
		private long startNanos;
		private long endNanos;
		private volatile boolean started;
		private volatile boolean ended;

		public int getNodesChecked()
		{
			return nodesChecked;
		}

		public int getTransportsChecked()
		{
			return transportsChecked;
		}

		public int getTotalNodesChecked()
		{
			return nodesChecked + transportsChecked;
		}

		public long getElapsedTimeNanos()
		{
			return endNanos - startNanos;
		}

		private void start()
		{
			started = true;
			nodesChecked = 0;
			transportsChecked = 0;
			startNanos = System.nanoTime();
		}

		private void end()
		{
			endNanos = System.nanoTime();
			ended = true;
		}
	}
}
