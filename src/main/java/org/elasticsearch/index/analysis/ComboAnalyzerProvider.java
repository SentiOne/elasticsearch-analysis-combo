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

package org.elasticsearch.index.analysis;

import org.elasticsearch.analysis.common.ComboAnalyzerWrapper;
import org.elasticsearch.ElasticsearchException;
//import org.elasticsearch.analysis.common.CommonAnalysisPlugin;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugin.analysis.combo.PluginsServiceRef;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.PluginsService;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ComboAnalyzerProvider extends AbstractIndexAnalyzerProvider<ComboAnalyzerWrapper> {
    private static final String ANALYZER_SETTINGS_PREFIX = "index.analysis.analyzer";

    private final Environment environment;
    private final Settings analyzerSettings;
    private final String name;
    private final PluginsServiceRef pluginsServiceRef;

    public ComboAnalyzerProvider(IndexSettings indexSettings, Environment environment, String name, Settings settings, PluginsServiceRef pluginsServiceRef) {
        super(indexSettings, name, settings);
        // Store parameters for delegated usage inside the ComboAnalyzerWrapper itself
        // Sub-analyzer resolution must use the AnalysisService,
        // but as we're a dependency of it (and it's not a Proxy-able interface)
        // we cannot in turn rely on it. Therefore the dependency has to be used lazily.
        this.environment = environment;
        this.analyzerSettings = settings;
        this.name = name;

        // TODO: Way to get List<AnalysisPlugin> without loading all plugins by yourself?
        // Here because in get we get a plugin loading loop
        this.pluginsServiceRef = pluginsServiceRef;
        this.pluginsServiceRef.init(environment);
    }

    @Override
    public ComboAnalyzerWrapper get() {
        // This function is also called during the AnalysisService initialization,
        // hence the following constructor also needs to to perform lazy loading by itself.
        try {
            IndexAnalyzers indexAnalyzers = getIndexAnalyzers(environment);
            return new ComboAnalyzerWrapper(name, analyzerSettings, indexAnalyzers::get);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ElasticsearchException("Exception while getting ComboAnalyzerWrapper from ComboAnalyzerProvider for analyzer with name: [" + name + "]", e);
        }
    }

    private IndexAnalyzers getIndexAnalyzers(Environment environment) throws IOException {
        final AnalysisModule analysisModule = new AnalysisModule(
            environment,
            pluginsServiceRef.getPluginsService().filterPlugins(AnalysisPlugin.class)
        );

        final AnalysisRegistry analysisRegistry = analysisModule.getAnalysisRegistry();

        // we have to filter everything combo related or we will have infinite recursion
        final Settings newSettings = filterOutComboSettingsAndLeaveRestUntouched(indexSettings.getSettings());

        IndexMetaData newIndexMetaData = IndexMetaData.builder(indexSettings.getIndexMetaData())
            .settings(filterOutComboSettingsAndLeaveRestUntouched(indexSettings.getIndexMetaData().getSettings()))
            .build();

        final IndexScopedSettings newScopedSettings = indexSettings.getScopedSettings().copy(newSettings, newIndexMetaData);
        final IndexSettings newIndexSettings = new IndexSettings(
            newIndexMetaData,
            newSettings,
            newScopedSettings);

        return analysisRegistry.build(newIndexSettings);
    }

    private Settings filterOutComboSettingsAndLeaveRestUntouched(Settings settings) {
        // TODO: this is more complex that it should be
        Settings allSettingsButAnalyzers = settings.filter(s -> !s.startsWith(ANALYZER_SETTINGS_PREFIX));
        Map<String, Settings> groups = settings.getGroups(ANALYZER_SETTINGS_PREFIX);
        Map<String, Settings> filteredGroups = filterByValue(groups, s -> !s.get("type").equals("combo"));

        Settings.Builder newSettings = Settings.builder()
            .put(allSettingsButAnalyzers);

        filteredGroups.forEach((analyzerName, analyzerSettings) -> {
            analyzerSettings.keySet().forEach((analyzerSettingName) -> {
                String settingKey = ANALYZER_SETTINGS_PREFIX + "." + analyzerName + "." + analyzerSettingName;
                newSettings.copy(settingKey, analyzerSettingName, analyzerSettings);
            });
        });

        return newSettings.build();
    }

    private static <K, V> Map<K, V> filterByValue(Map<K, V> map, Predicate<V> predicate) {
        return map.entrySet()
            .stream()
            .filter(entry -> predicate.test(entry.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
