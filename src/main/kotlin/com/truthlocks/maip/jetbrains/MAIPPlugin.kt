/*
 * Copyright 2026 Truthlocks Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.truthlocks.maip.jetbrains

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.truthlocks.maip.jetbrains.services.AutoReceiptService
import com.truthlocks.maip.jetbrains.services.MAIPProjectService

/**
 * Entry point for the MAIP plugin. Executes on project open to initialize
 * project-level services and register event listeners.
 *
 * This [ProjectActivity] runs on a background thread, so it is safe to
 * perform network calls and other long-running initialization here.
 */
class MAIPPlugin : ProjectActivity {

    private val log = Logger.getInstance(MAIPPlugin::class.java)

    /**
     * Called when a project is opened. Initializes the MAIP project service
     * and the auto-receipt service so they begin caching data and listening
     * for events.
     *
     * @param project The opened project.
     */
    override suspend fun execute(project: Project) {
        log.info("MAIP plugin initializing for project: ${project.name}")

        val projectService = project.getService(MAIPProjectService::class.java)
        projectService.initialize()

        val autoReceiptService = project.getService(AutoReceiptService::class.java)
        autoReceiptService.initialize()

        log.info("MAIP plugin initialized for project: ${project.name}")
    }
}
