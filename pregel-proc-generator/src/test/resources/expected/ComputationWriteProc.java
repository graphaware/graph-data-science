package org.neo4j.graphalgo.beta.pregel.cc;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.processing.Generated;
import org.neo4j.graphalgo.AlgoBaseProc;
import org.neo4j.graphalgo.AlgorithmFactory;
import org.neo4j.graphalgo.WriteProc;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.beta.pregel.PregelConfig;
import org.neo4j.graphalgo.beta.pregel.PregelWriteResult;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimation;
import org.neo4j.graphalgo.core.utils.mem.MemoryEstimations;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeDoubleArray;
import org.neo4j.graphalgo.core.write.PropertyTranslator;
import org.neo4j.graphalgo.result.AbstractResultBuilder;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

@Generated("org.neo4j.graphalgo.beta.pregel.PregelProcessor")
public final class ComputationWriteProc extends WriteProc<ComputationAlgorithm, HugeDoubleArray, PregelWriteResult, PregelConfig> {
    @Procedure(
            name = "gds.pregel.test.write",
            mode = Mode.WRITE
    )
    @Description("Test computation description")
    public Stream<PregelWriteResult> write(@Name("graphName") Object graphNameOrConfig,
            @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration) {
        return write(compute(graphNameOrConfig, configuration));
    }

    @Override
    protected AbstractResultBuilder<PregelWriteResult> resultBuilder(
            AlgoBaseProc.ComputationResult<ComputationAlgorithm, HugeDoubleArray, PregelConfig> computeResult) {
        return new PregelWriteResult.Builder();
    }

    @Override
    protected PregelConfig newConfig(String username, Optional<String> graphName,
            Optional<GraphCreateConfig> maybeImplicitCreate, CypherMapWrapper config) {
        return PregelConfig.of(username, graphName, maybeImplicitCreate, config);
    }

    @Override
    protected AlgorithmFactory<ComputationAlgorithm, PregelConfig> algorithmFactory() {
        return new AlgorithmFactory<ComputationAlgorithm, PregelConfig>() {
            @Override
            public ComputationAlgorithm build(Graph graph, PregelConfig configuration,
                    AllocationTracker tracker, Log log) {
                return new ComputationAlgorithm(graph, configuration, tracker, log);
            }

            @Override
            public MemoryEstimation memoryEstimation(PregelConfig configuration) {
                return MemoryEstimations.empty();
            }
        };
    }

    @Override
    protected PropertyTranslator<HugeDoubleArray> nodePropertyTranslator(
            AlgoBaseProc.ComputationResult<ComputationAlgorithm, HugeDoubleArray, PregelConfig> computationResult) {
        return HugeDoubleArray.Translator.INSTANCE;
    }
}