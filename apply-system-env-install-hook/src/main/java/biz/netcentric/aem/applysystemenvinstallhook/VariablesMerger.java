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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.netcentric.aem.applysystemenvinstallhook.VariablesSource.NamedValue;

public class VariablesMerger {

    private static final String DEFAULT_KEY = "default";
    private static final String NOTFOUND_KEY = "not found";

    private Map<String, Integer> counts = new LinkedHashMap<String, Integer>();

    private final InstallHookLogger logger;

    public VariablesMerger(InstallHookLogger logger) {
        this.logger = logger;

        // ensure special keys are set
        counts.put(DEFAULT_KEY, 0);
        counts.put(NOTFOUND_KEY, 0);
    }

    private void incrementCount(String key) {
        Integer currentCountIntObj = counts.get(key);
        int count = currentCountIntObj!=null? currentCountIntObj : 0;
        counts.put(key, ++count);
    }

    List<EnvVarDeclaration> getEnvVarDeclarations(String text) {

        List<EnvVarDeclaration> varDeclarations = new ArrayList<EnvVarDeclaration>();
        Matcher matcher = EnvVarDeclaration.VAR_PATTERN.matcher(text);
        while (matcher.find()) {
            EnvVarDeclaration envVarDeclaration = new EnvVarDeclaration(matcher.group(1));
            varDeclarations.add(envVarDeclaration);
        }
        return varDeclarations;
    }

    String applyEnvVars(String text, VariablesSource env, String path) {

        Matcher matcher = EnvVarDeclaration.VAR_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            EnvVarDeclaration envVar = new EnvVarDeclaration(matcher.group(1));

            String valueToBeUsed;
            String action;
            NamedValue entry = env.get(envVar.name);
            
            if (entry != null) {
                valueToBeUsed = String.valueOf(entry.getValue());
                incrementCount(entry.getSourceName());
                action = "replaced from " + entry.getSourceName();
            } else {
                String effectiveDefaultVal;
                if (envVar.defaultVal != null) {
                    effectiveDefaultVal = envVar.defaultVal;
                    incrementCount(DEFAULT_KEY);
                    action = "default in package";
                } else {
                    // leave exactly what we matched as default if no default is given
                    effectiveDefaultVal = matcher.group(0);
                    incrementCount(NOTFOUND_KEY);
                    action = "var not found (no default provided)";
                }
                valueToBeUsed = effectiveDefaultVal;
            }
            logger.log(path.replace(ApplySystemEnvInstallHook.TEMPLATE_SUFFIX, "") + ": " + envVar.name + "=\"" + valueToBeUsed + "\" ("
                    + action + ")");
            matcher.appendReplacement(result, Matcher.quoteReplacement(valueToBeUsed));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public String getReplacementSummary() {
        int total = 0;
        StringBuilder sb = new StringBuilder();
        for(String key: counts.keySet()) {
            if (DEFAULT_KEY.equals(key) || NOTFOUND_KEY.equals(key)) {
                continue;
            }
            int count = counts.get(key);
            total += count;
            sb.append("Replacement count for '" + key + "': " + count + "\n");
        }
        int defaultUsedCount = counts.get(DEFAULT_KEY);
        total += defaultUsedCount;
        sb.append("Count default value used: " + defaultUsedCount + "\n");
        sb.append("Total variables replaced: " + total);
        int notFoundCount = counts.get(NOTFOUND_KEY);
        if (notFoundCount > 0) {
            sb.append("\nWARN: No value found for variable and no default given: " + notFoundCount);
        }

        return sb.toString();
    }


    public static class EnvVarDeclaration {
        private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^\\}]+)\\}");

        public final String name;
        public final String defaultVal;

        EnvVarDeclaration(String groupOneOfMatcher) {
            String givenVarAndDefault = groupOneOfMatcher;
            String[] bits = givenVarAndDefault.split(":", 2);

            name = bits[0];
            if (bits.length > 1) {
                defaultVal = bits[1];
            } else {
                defaultVal = null;
            }
        }

    }

}
