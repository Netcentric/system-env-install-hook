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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.netcentric.aem.applysystemenvinstallhook.VariablesSource.NamedValue;

public class VariablesMerger {

    private int countVarsReplaced = 0;
    private int countVarsDefaultUsed = 0;
    private int countVarsNotFound = 0;

    private final InstallHookLogger logger;

    public VariablesMerger(InstallHookLogger logger) {
        this.logger = logger;
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
                countVarsReplaced++;
                action = "replaced from " + entry.getSourceName();
            } else {
                String effectiveDefaultVal;
                if (envVar.defaultVal != null) {
                    effectiveDefaultVal = envVar.defaultVal;
                    countVarsDefaultUsed++;
                    action = "default in package";
                } else {
                    // leave exactly what we matched as default if no default is given
                    effectiveDefaultVal = matcher.group(0);
                    countVarsNotFound++;
                    action = "var not found, no default provided!";
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

    public int getCountVarsReplaced() {
        return countVarsReplaced;
    }

    public int getCountVarsDefaultUsed() {
        return countVarsDefaultUsed;
    }

    public int getCountVarsNotFound() {
        return countVarsNotFound;
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
