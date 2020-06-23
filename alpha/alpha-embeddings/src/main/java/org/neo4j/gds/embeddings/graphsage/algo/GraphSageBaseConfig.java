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
package org.neo4j.gds.embeddings.graphsage.algo;

import org.immutables.value.Value;
import org.neo4j.gds.embeddings.graphsage.LayerConfig;
import org.neo4j.graphalgo.annotation.Configuration;
import org.neo4j.graphalgo.config.AlgoBaseConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public interface GraphSageBaseConfig extends AlgoBaseConfig {

    int embeddingSize();

    String aggregator();
    String activationFunction();

    @Configuration.ConvertWith("toIntList")
    List<Integer> sampleSizes();

    // TODO: add validation that at least one of `nodePropertyNames` or `degreeAsProperty` is specified
    @Value.Default
    default List<String> nodePropertyNames() {
        return List.of();
    }

    @Value.Default
    default int batchSize() {
        return 100;
    }

    @Value.Default
    default double tolerance() {
        return 1e-4;
    }

    @Value.Default
    default double learningRate() {
        return 0.1;
    }

    @Value.Default
    default int epochs() {
        return 1;
    }

    @Value.Default
    default int maxOptimizationIterations() {
        return 100;
    }

    @Value.Default
    default int searchDepth() {
        return 5;
    }

    @Value.Default
    default int negativeSamples() {
        return 20;
    }

    @Value.Default
    default boolean degreeAsProperty() {
        return false;
    }

    // TODO: may be move this out
    @Configuration.Ignore
    default Collection<LayerConfig> layerConfigs() {
        Collection<LayerConfig> result = new ArrayList<>(sampleSizes().size());
        for (int i = 0; i < sampleSizes().size(); i++) {
            LayerConfig layerConfig = LayerConfig.builder()
                .aggregatorType(aggregator())
                .activationFunction(activationFunction())
                .rows(embeddingSize())
                .cols(i == 0 ? featuresSize() : embeddingSize())
                .sampleSize(sampleSizes().get(i))
                .build();

            result.add(layerConfig);
        }

        return result;
    }

    @Configuration.Ignore
    default int featuresSize() {
        return nodePropertyNames().size() + (degreeAsProperty() ? 1 : 0);
    }

    static List<Integer> toIntList(List<Long> input) {
        return input.stream()
            .mapToInt(Long::intValue)
            .boxed()
            .collect(Collectors.toList());
    }

}