/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.plugin.analysis.combo;

import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.analysis.common.ComboAnalyzerWrapper;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AnalyzerProvider;
import org.elasticsearch.index.analysis.ComboAnalyzerProvider;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.watcher.ResourceWatcherService;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static java.util.Collections.singletonMap;

public class AnalysisComboPlugin extends Plugin implements AnalysisPlugin {

    private static final PluginsServiceRef pluginsServiceRef = new PluginsServiceRef();

    @Override
    public Map<String, AnalysisModule.AnalysisProvider<AnalyzerProvider<? extends Analyzer>>> getAnalyzers() {
        AnalysisModule.AnalysisProvider<AnalyzerProvider<? extends Analyzer>> analysisProvider = new AnalysisModule.AnalysisProvider<AnalyzerProvider<? extends Analyzer>>() {
            @Override
            public AnalyzerProvider<? extends Analyzer> get(IndexSettings indexSettings, Environment environment, String name, Settings settings) throws IOException {
                return new ComboAnalyzerProvider(indexSettings, environment, name, settings, pluginsServiceRef);
            }

            @Override
            public boolean requiresAnalysisSettings() {
                return true;
            }
        };

        return singletonMap(ComboAnalyzerWrapper.NAME, analysisProvider);
    }

    @Override
    public Collection<Object> createComponents(Client client, ClusterService clusterService, ThreadPool threadPool, ResourceWatcherService resourceWatcherService, ScriptService scriptService, NamedXContentRegistry xContentRegistry, Environment environment, NodeEnvironment nodeEnvironment, NamedWriteableRegistry namedWriteableRegistry) {
        pluginsServiceRef.init(environment);
        return super.createComponents(client, clusterService, threadPool, resourceWatcherService, scriptService, xContentRegistry, environment, nodeEnvironment, namedWriteableRegistry);
    }
}
