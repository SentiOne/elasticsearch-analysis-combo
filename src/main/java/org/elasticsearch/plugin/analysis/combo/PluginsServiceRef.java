package org.elasticsearch.plugin.analysis.combo;

import org.elasticsearch.env.Environment;
import org.elasticsearch.plugins.PluginsService;

import java.util.Collections;

public class PluginsServiceRef {

	private PluginsService pluginsService;

	public void init(Environment environment) {
		if (this.pluginsService == null) {
			this.pluginsService = new PluginsService(
					environment.settings(),
					environment.configFile(),
					environment.modulesFile(),
					environment.pluginsFile(),
					Collections.emptyList()
			);
		}
	}

	public PluginsService getPluginsService() {
		return this.pluginsService;
	}
}
