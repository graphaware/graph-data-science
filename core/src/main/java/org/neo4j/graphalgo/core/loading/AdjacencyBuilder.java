/*
 * Copyright (c) 2017-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphalgo.core.loading;

import org.apache.lucene.util.LongsRef;
import org.jetbrains.annotations.NotNull;
import org.neo4j.graphalgo.core.Aggregation;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.LongAdder;

import static org.neo4j.graphalgo.core.utils.mem.MemoryUsage.sizeOfLongArray;
import static org.neo4j.graphalgo.core.utils.mem.MemoryUsage.sizeOfObjectArray;
import static org.neo4j.graphalgo.utils.ExceptionUtil.unchecked;
import static org.neo4j.kernel.api.StatementConstants.NO_SUCH_PROPERTY_KEY;

final class AdjacencyBuilder {

    static AdjacencyBuilder compressing(
        @NotNull RelationshipsBuilder globalBuilder,
        int numPages,
        int pageSize,
        AllocationTracker tracker,
        LongAdder relationshipCounter,
        int[] propertyKeyIds,
        double[] defaultValues,
        Aggregation[] aggregations
    ) {
        tracker.add(sizeOfObjectArray(numPages) << 2);
        ThreadLocalRelationshipsBuilder[] localBuilders = new ThreadLocalRelationshipsBuilder[numPages];
        final CompressedLongArray[][] compressedAdjacencyLists = new CompressedLongArray[numPages][];
        LongsRef[] buffers = new LongsRef[numPages];
        long[][] globalAdjacencyOffsets = new long[numPages][];

        long[][][] globalPropertiesOffsets = new long[propertyKeyIds.length][numPages][];

        boolean atLeastOnePropertyToLoad = Arrays
            .stream(propertyKeyIds)
            .anyMatch(keyId -> keyId != NO_SUCH_PROPERTY_KEY);

        var compressingPagedAdjacency = new AdjacencyBuilder(
            globalBuilder,
            localBuilders,
            compressedAdjacencyLists,
            buffers,
            globalAdjacencyOffsets,
            globalPropertiesOffsets,
            pageSize,
            relationshipCounter,
            propertyKeyIds,
            defaultValues,
            aggregations,
            atLeastOnePropertyToLoad
        );
        for (int idx = 0; idx < numPages; idx++) {
            compressingPagedAdjacency.addAdjacencyImporter(tracker, idx);
        }
        compressingPagedAdjacency.finishPreparation();
        return compressingPagedAdjacency;
    }

    private final RelationshipsBuilder globalBuilder;
    private final ThreadLocalRelationshipsBuilder[] localBuilders;
    private final CompressedLongArray[][] compressedAdjacencyLists;
    private final LongsRef[] buffers;
    private final long[][] globalAdjacencyOffsets;
    private final long[][][] globalPropertiesOffsets;
    private final int pageSize;
    private final int pageShift;
    private final long pageMask;
    private final long sizeOfLongPage;
    private final long sizeOfObjectPage;
    private final LongAdder relationshipCounter;
    private final int[] propertyKeyIds;
    private final double[] defaultValues;
    private final Aggregation[] aggregations;
    private final boolean atLeastOnePropertyToLoad;

    private AdjacencyBuilder(
        RelationshipsBuilder globalBuilder,
        ThreadLocalRelationshipsBuilder[] localBuilders,
        CompressedLongArray[][] compressedAdjacencyLists,
        LongsRef[] buffers,
        long[][] globalAdjacencyOffsets,
        long[][][] globalPropertiesOffsets,
        int pageSize,
        LongAdder relationshipCounter,
        int[] propertyKeyIds,
        double[] defaultValues,
        Aggregation[] aggregations,
        boolean atLeastOnePropertyToLoad
    ) {
        this.globalBuilder = globalBuilder;
        this.localBuilders = localBuilders;
        this.compressedAdjacencyLists = compressedAdjacencyLists;
        this.buffers = buffers;
        this.globalAdjacencyOffsets = globalAdjacencyOffsets;
        this.globalPropertiesOffsets = globalPropertiesOffsets;
        this.pageSize = pageSize;
        this.pageShift = Integer.numberOfTrailingZeros(pageSize);
        this.pageMask = pageSize - 1;
        this.sizeOfLongPage = sizeOfLongArray(pageSize);
        this.sizeOfObjectPage = sizeOfObjectArray(pageSize);
        this.relationshipCounter = relationshipCounter;
        this.propertyKeyIds = propertyKeyIds;
        this.defaultValues = defaultValues;
        this.aggregations = aggregations;
        this.atLeastOnePropertyToLoad = atLeastOnePropertyToLoad;
    }

    void addAll(
        long[] batch,
        long[] targets,
        long[][] propertyValues,
        int[] offsets,
        int length,
        AllocationTracker tracker
    ) {
        int pageShift = this.pageShift;
        long pageMask = this.pageMask;

        ThreadLocalRelationshipsBuilder builder = null;
        int lastPageIndex = -1;
        int endOffset, startOffset = 0;
        try {
            for (int i = 0; i < length; ++i) {
                endOffset = offsets[i];

                // if there are no rels for this node, just go to next
                if (endOffset <= startOffset) {
                    continue;
                }

                long source = batch[startOffset << 2];
                int pageIndex = (int) (source >>> pageShift);

                if (pageIndex > lastPageIndex) {
                    // switch to the builder for this page
                    if (builder != null) {
                        builder.unlock();
                    }
                    builder = localBuilders[pageIndex];
                    builder.lock();
                    lastPageIndex = pageIndex;
                }

                int localId = (int) (source & pageMask);

                CompressedLongArray compressedTargets = this.compressedAdjacencyLists[pageIndex][localId];
                if (compressedTargets == null) {
                    compressedTargets = new CompressedLongArray(
                        tracker,
                        propertyValues == null ? 0 : propertyValues.length
                    );
                    this.compressedAdjacencyLists[pageIndex][localId] = compressedTargets;
                }

                if (propertyValues == null) {
                    compressedTargets.add(targets, startOffset, endOffset);
                } else {
                    compressedTargets.add(targets, propertyValues, startOffset, endOffset);
                }

                startOffset = endOffset;
            }
        } finally {
            if (builder != null && builder.isLockedByCurrentThread()) {
                builder.unlock();
            }
        }
    }

    Collection<Runnable> flushTasks() {
        Runnable[] runnables = new Runnable[localBuilders.length];
        Arrays.setAll(runnables, index -> () -> {
            ThreadLocalRelationshipsBuilder builder = localBuilders[index];
            CompressedLongArray[] allTargets = compressedAdjacencyLists[index];
            LongsRef buffer = buffers[index];
            long importedRelationships = 0L;
            for (int localId = 0; localId < allTargets.length; ++localId) {
                CompressedLongArray compressedAdjacencyList = allTargets[localId];
                if (compressedAdjacencyList != null) {
                    importedRelationships += builder.applyVariableDeltaEncoding(
                        compressedAdjacencyList,
                        buffer,
                        localId
                    );

                    allTargets[localId] = null;
                }
            }
            builder.release();
            relationshipCounter.add(importedRelationships);
        });
        var tasks = new ArrayList<>(Arrays.asList(runnables));
        // Final task to make sure that all property builders are flushed as well.
        tasks.add(unchecked(this.globalBuilder::flush));
        return tasks;
    }

    int[] getPropertyKeyIds() {
        return propertyKeyIds;
    }

    double[] getDefaultValues() {
        return defaultValues;
    }

    Aggregation[] getAggregations() {
        return aggregations;
    }

    boolean atLeastOnePropertyToLoad() {
        return atLeastOnePropertyToLoad;
    }

    boolean supportsProperties() {
        return this.globalBuilder.supportsProperties();
    }

    private void addAdjacencyImporter(AllocationTracker tracker, int pageIndex) {
        tracker.add(sizeOfObjectPage);
        tracker.add(sizeOfObjectPage);
        tracker.add(sizeOfLongPage);
        compressedAdjacencyLists[pageIndex] = new CompressedLongArray[pageSize];
        buffers[pageIndex] = new LongsRef();
        long[] localAdjacencyOffsets = globalAdjacencyOffsets[pageIndex] = new long[pageSize];

        long[][] localPropertiesOffsets = new long[globalPropertiesOffsets.length][];
        for (int i = 0; i < globalPropertiesOffsets.length; i++) {
            localPropertiesOffsets[i] = globalPropertiesOffsets[i][pageIndex] = new long[pageSize];
        }

        localBuilders[pageIndex] = globalBuilder.threadLocalRelationshipsBuilder(
            localAdjacencyOffsets,
            localPropertiesOffsets,
            aggregations
        );
        localBuilders[pageIndex].prepare();
    }

    private void finishPreparation() {
        globalBuilder.setGlobalAdjacencyOffsets(globalAdjacencyOffsets);
        globalBuilder.setGlobalPropertyOffsets(globalPropertiesOffsets);
    }
}
