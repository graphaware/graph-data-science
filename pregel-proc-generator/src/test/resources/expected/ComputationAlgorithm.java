package org.neo4j.graphalgo.beta.pregel.cc;

import javax.annotation.processing.Generated;
import org.neo4j.graphalgo.Algorithm;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.beta.pregel.Pregel;
import org.neo4j.graphalgo.beta.pregel.PregelConfig;
import org.neo4j.graphalgo.core.concurrency.ParallelUtil;
import org.neo4j.graphalgo.core.concurrency.Pools;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeDoubleArray;
import org.neo4j.logging.Log;

@Generated("org.neo4j.graphalgo.beta.pregel.PregelProcessor")
public final class ComputationAlgorithm extends Algorithm<ComputationAlgorithm, HugeDoubleArray> {
    private final Pregel pregelJob;

    ComputationAlgorithm(Graph graph, PregelConfig configuration, AllocationTracker tracker,
                         Log log) {
        this.pregelJob = Pregel.withDefaultNodeValues(graph, configuration, new Computation(),(int) ParallelUtil.adjustedBatchSize(graph.nodeCount(), configuration.concurrency()),Pools.DEFAULT,tracker);
    }

    @Override
    public HugeDoubleArray compute() {
        return pregelJob.run();
    }

    @Override
    public ComputationAlgorithm me() {
        return this;
    }

    @Override
    public void release() {
        pregelJob.release();
    }
}
