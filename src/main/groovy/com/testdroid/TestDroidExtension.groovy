/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.testdroid

import org.gradle.api.Project
import org.gradle.util.ConfigureUtil



class TestDroidExtension {
    Project project
    String username
    String password
    String projectName
    String cloudUrl
    String mode
    String deviceGroup
    String deviceLanguageCode
    String hookUrl
    String scheduler // PARALLEL or SERIAL
    String testScreenshotDir
    String testRunName

    FullRunConfig fullRunConfig = new FullRunConfig()
    AppCrawlerConfig appCrawlerConfig =  new AppCrawlerConfig()
    UiAutomatorTestConfig uiAutomatorTestConfig = new UiAutomatorTestConfig()

    TestDroidExtension(Project project) {
        this.project = project
    }

    def appCrawlerConfig(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, this.appCrawlerConfig)
    }
    def fullRunConfig(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, this.fullRunConfig)
    }
    def uiAutomatorTestConfig(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, this.uiAutomatorTestConfig)
    }
    class AppCrawlerConfig {
        String applicationUserName
        String applicationPassword
    }

    class FullRunConfig {
        String instrumentationRunner
        String withAnnotation
        String withOutAnnotation
        String limitationType
        String limitationValue
        String instrumentationAPKPath

    }
    class UiAutomatorTestConfig {
    	String uiAutomatorTestClasses
    	String uiAutomatorJarPath
    }
}

