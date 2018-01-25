/*
 * (C) Copyright 2018 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.aem.applysystemenvinstallhook;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstallHookLogger {

    private static final Logger LOG = LoggerFactory.getLogger(ApplySystemEnvInstallHook.class);

    private ImportOptions options;

    public void setOptions(ImportOptions options) {
        this.options = options;
    }

    public void logError(Logger logger, String message, Throwable throwable) {
        ProgressTrackerListener listener = options.getListener();
        if (listener != null) {
            listener.onMessage(ProgressTrackerListener.Mode.TEXT, "ERROR: " + message, "");
        }
        logger.error(message, throwable);
    }

    public void log(String message) {
        log(LOG, message);
    }

    public void log(Logger logger, String message) {
        ProgressTrackerListener listener = options.getListener();
        if (listener != null) {
            listener.onMessage(ProgressTrackerListener.Mode.TEXT, message, "");
            logger.debug(message);
        } else {
            logger.info(message);
        }
    }
}
