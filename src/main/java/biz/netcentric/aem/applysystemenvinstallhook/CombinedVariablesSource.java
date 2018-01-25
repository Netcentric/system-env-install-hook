/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applysystemenvinstallhook;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.vault.packaging.InstallContext;

import biz.netcentric.aem.applysystemenvinstallhook.sources.JcrVarsSource;
import biz.netcentric.aem.applysystemenvinstallhook.sources.OsEnvVarsSource;
import biz.netcentric.aem.applysystemenvinstallhook.sources.SystemPropertiesVarsSource;
import biz.netcentric.aem.applysystemenvinstallhook.sources.ZooKeeperVarsSource;

/** Combines config variable sources. */
public class CombinedVariablesSource extends VariablesSource {

    public static CombinedVariablesSource forSources(List<String> sourceNames, InstallHookLogger logger, InstallContext context) {
        List<VariablesSource> sources = new ArrayList<VariablesSource>();
        for (String string : sourceNames) {
            if(string.equals(OsEnvVarsSource.NAME)) {
                sources.add(new OsEnvVarsSource());

            } else if (string.equals(SystemPropertiesVarsSource.NAME)) {
                sources.add(new SystemPropertiesVarsSource());

            } else if (string.equals(JcrVarsSource.NAME)) {
                sources.add(new JcrVarsSource(logger, context));

            } else if(string.equals(ZooKeeperVarsSource.NAME)) {
                sources.add(new ZooKeeperVarsSource(logger));

            } else {
                logger.log("Could not find source "+string+" ignoring");
            }
        }
        return new CombinedVariablesSource(StringUtils.join(sourceNames,", "), sources);
    }

    final List<VariablesSource> sources = new ArrayList<VariablesSource>();

    CombinedVariablesSource(String name, List<VariablesSource> sources) {
        super(name, null);
        this.sources.addAll(sources);
    }

    public NamedValue get(String varName) {
        for (VariablesSource source : sources) {
            NamedValue entry = source.get(varName);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }


}
