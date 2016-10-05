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

package com.testdroid;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.testing.api.TestServer;
import com.testdroid.api.*;
import com.testdroid.api.model.*;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.List;

import static com.testdroid.TestDroidExtension.Authorization.*;

public class TestDroidServer extends TestServer {

    private final TestDroidExtension extension;
    private final Logger logger;
    private final String cloudURL = "https://cloud.testdroid.com";

    TestDroidServer(@NonNull TestDroidExtension extension,
                    @NonNull Logger logger) {
        this.extension = extension;
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "testdroid";
    }

    private APIProject searchProject(String projectName, APIProject.Type type, APIListResource<APIProject> projectList) throws APIException {
        if (projectList == null || projectList.getTotal() == 0 || projectList.getEntity() == null ||
                projectList.getEntity().getData() == null) {

            return null;
        }

        do {

            List<APIProject> projects = projectList.getEntity().getData();
            for (APIProject project : projects) {
                if (projectName.equals(project.getName())) {
                    return project;
                }
            }

            projectList = projectList.getNext();
        } while (projectList != null);

        return null;
    }

    private APIDeviceGroup searchDeviceGroup(String deviceGroupName, APIListResource<APIDeviceGroup> deviceGroupList) throws APIException {
        if (deviceGroupList == null || deviceGroupList.getTotal() == 0 || deviceGroupList.getEntity() == null ||
                deviceGroupList.getEntity().getData() == null) {

            return null;
        }

        do {
            List<APIDeviceGroup> deviceGroups = deviceGroupList.getEntity().getData();
            for (APIDeviceGroup deviceGroup : deviceGroups) {

                if (deviceGroupName.equals(deviceGroup.getDisplayName())) {
                    return deviceGroup;
                }
            }

            deviceGroupList = deviceGroupList.getNext();
        } while (deviceGroupList != null);

        return null;
    }

    @Override
    public void uploadApks(@NonNull String variantName, @NonNull File testApk, @Nullable File testedApk) {
        APIUser user;
        logger.info(String.format(
                "TESTDROID: Variant(%s), Uploading APKs\n\t%s\n\t%s",
                variantName,
                testApk.getAbsolutePath(),
                testedApk != null ? testedApk.getAbsolutePath() : "<none>"));
        String testdroidCloudURL = extension.getCloudUrl() == null ? cloudURL : extension.getCloudUrl();
        logger.info("Testdroid URL %s", testdroidCloudURL);

        APIClient client = createAPIClient(testdroidCloudURL, extension.getAuthorization());
        if (client == null) {
            logger.error("TESTDROID: Client couldn't be configured");
            return;
        }
        try {
            user = client.me();
        } catch (APIException e) {
            logger.error("TESTDROID: Client couldn't connect", e);
            return;
        }

        APIProject project;
        try {
            if (extension.getProjectName() == null) {
                logger.warn("TESTDROID: Project name is not set - creating a new one");
                APIProject.Type type = getProjectType(extension.getMode());
                project = user.createProject(type);
                logger.info("TESTDROID: Created project:" + project.getName());
            } else {
                APIListResource<APIProject> projectList;
                projectList = user.getProjectsResource();

                project = searchProject(extension.getProjectName(), getProjectType(extension.getMode()), projectList);
                if (project == null) {
                    logger.warn("TESTDROID: Can't find project " + extension.getProjectName());
                    return;
                }

            }
            //reload
            project = user.getProject(project.getId());
            logger.info(project.getName());

            APIListResource<APIDeviceGroup> deviceGroupsResource = user.getDeviceGroupsResource();
            APIDeviceGroup deviceGroup = searchDeviceGroup(extension.getDeviceGroup(), deviceGroupsResource);

            if (deviceGroup == null) {
                logger.warn("TESTDROID: Can't find device group " + extension.getDeviceGroup());
                return;
            } else if (deviceGroup.getDeviceCount() == 0) {
                logger.warn("TESTDROID: There is no devices in group:" + extension.getDeviceGroup());
                return;
            }

            APITestRunConfig config;
            config = updateAPITestRunConfigValues(project, extension, deviceGroup.getId());

            logger.info("TESTDROID: Uploading apks into project %s (id:%d)", project.getName(), project.getId());
            File instrumentationAPK = testApk;

            if (extension.getFullRunConfig() != null && extension.getFullRunConfig().getInstrumentationAPKPath() != null
                    && new File(extension.getFullRunConfig().getInstrumentationAPKPath()).exists()) {

                instrumentationAPK = new File(extension.getFullRunConfig().getInstrumentationAPKPath());
                logger.info("TESTDROID: Using custom path for instrumentation APK: %s", extension.getFullRunConfig().getInstrumentationAPKPath());
            }
            uploadBinaries(project, config, instrumentationAPK, testedApk);

            project.run(extension.getTestRunName() == null ? variantName : extension.getTestRunName());

        } catch (APIException e) {
            logger.error("Can't upload project", e);
            System.out.println(String.format(
                    "TESTDROID: Uploading failed:%s", e.getStatus()));

        }

    }

    private APIClient createAPIClient(String testdroidCloudURL, TestDroidExtension.Authorization authorization) {
        String proxyHost = System.getProperty("http.proxyHost");
        Boolean useProxy = extension.getUseSystemProxySettings() && StringUtils.isNotBlank(proxyHost);
        String proxyUser = System.getProperty("http.proxyUser");
        String proxyPassword = System.getProperty("http.proxyPassword");
        Boolean useProxyCredentials = proxyUser != null && proxyPassword != null && useProxy;
        APIClientType apiClientType = APIClientType.resolveAPIClientType(authorization, useProxy, useProxyCredentials);
        logger.warn("TESTDROID: Using APIClientType {}", apiClientType);
        switch (apiClientType) {
            case APIKEY:
                return new APIKeyClient(testdroidCloudURL, extension.getApiKey());
            case APIKEY_PROXY:
                return new APIKeyClient(testdroidCloudURL, extension.getApiKey(), buildProxyHost(), false);
            case APIKEY_PROXY_CREDENTIALS:
                return new APIKeyClient(testdroidCloudURL, extension.getApiKey(), buildProxyHost(), proxyUser, proxyPassword, false);
            case OAUTH:
                return new DefaultAPIClient(testdroidCloudURL, extension.getUsername(), extension.getPassword());
            case OAUTH_PROXY:
                return new DefaultAPIClient(testdroidCloudURL, extension.getUsername(), extension.getPassword(), buildProxyHost(), false);
            case OAUTH_PROXY_CREDENTIALS:
                return new DefaultAPIClient(testdroidCloudURL, extension.getUsername(), extension.getPassword(), buildProxyHost(), proxyUser, proxyPassword, false);
            case UNSUPPORTED:
            default:
                return null;
        }
    }

    private HttpHost buildProxyHost() {
        String proxyHost = System.getProperty("http.proxyHost");
        String proxyPort = System.getProperty("http.proxyPort");
        int port = -1;
        try {
            port = Integer.valueOf(proxyPort);
        } catch (NumberFormatException nfe) {
            //ignore and use default
        }
        return new HttpHost(proxyHost, port);
    }

    private void uploadBinaries(APIProject project, APITestRunConfig config, File testApk, File testedApk) throws APIException {

        if (project.getType().equals(APIProject.Type.UIAUTOMATOR)) {

            UIAutomatorFiles uiAutomatorFiles = project.getFiles(UIAutomatorFiles.class);
            if (extension.getUiAutomatorTestConfig() == null || extension.getUiAutomatorTestConfig().getUiAutomatorJarPath() == null) {
                throw new APIException("TESTDROID: Configure uiautomator settings");
            }
            File jarFile = new File(extension.getUiAutomatorTestConfig().getUiAutomatorJarPath());
            if (!jarFile.exists()) {
                throw new APIException("TESTDROID: Invalid uiAutomator jar file:" + jarFile.getAbsolutePath());
            }
            uiAutomatorFiles.uploadTest(new File(extension.getUiAutomatorTestConfig().getUiAutomatorJarPath()));
            logger.info("TESTDROID: uiautomator file uploaded");
            uiAutomatorFiles.uploadApp(testedApk);
            logger.info("TESTDROID: Android application uploaded");
        } else {
            AndroidFiles androidFiles = project.getFiles(AndroidFiles.class);

            if (testedApk != null && testedApk.exists()) {
                androidFiles.uploadApp(testedApk);
                logger.info("TESTDROID: Android application uploaded");
            } else {
                logger.warn("TESTDROID: Target application has not been added - uploading only test apk ");
            }

            if (testApk != null && config.getMode().equals(APITestRunConfig.Mode.FULL_RUN)) {
                androidFiles.uploadTest(testApk);
                logger.info("TESTDROID: Android test uploaded");
                return;
            }
        }


        if (APITestRunConfig.Mode.UIAUTOMATOR == config.getMode()) {
            if (project.getType().equals(APIProject.Type.UIAUTOMATOR)) {

            } else {
                throw new APIException("TESTDROID: Invalid project mode - create a new UIAutomator project");
            }

        }

    }

    private APIProject.Type getProjectType(String testrunMode) throws APIException {
        if (APITestRunConfig.Mode.FULL_RUN.name().equals(testrunMode) || APITestRunConfig.Mode.APP_CRAWLER.name().equals(testrunMode)) {
            return APIProject.Type.ANDROID;
        } else if (APITestRunConfig.Mode.UIAUTOMATOR.name().equals(testrunMode)) {
            return APIProject.Type.UIAUTOMATOR;
        } else {
            throw new APIException("TESTDROID: Not supported test run mode:" + testrunMode + " Enum" + APITestRunConfig.Mode.FULL_RUN.name());
        }

    }

    private APITestRunConfig updateAPITestRunConfigValues(APIProject project, TestDroidExtension extension, Long deviceGroupId) throws APIException {

        APITestRunConfig config = project.getTestRunConfig();

        config.setHookURL(extension.getHookUrl());
        config.setDeviceLanguageCode(extension.getDeviceLanguageCode());
        config.setScheduler(extension.getScheduler() != null ? APITestRunConfig.Scheduler.valueOf(extension.getScheduler()) : null);

        config.setMode(APITestRunConfig.Mode.valueOf(extension.getMode()));
        //App crawler settings
        config.setApplicationUsername(extension.getAppCrawlerConfig().getApplicationUserName());
        config.setApplicationPassword(extension.getAppCrawlerConfig().getApplicationPassword());

        //Full run settings
        if (extension.getFullRunConfig().getLimitationType() != null) {
            config.setLimitationType(APITestRunConfig.LimitationType.valueOf(extension.getFullRunConfig().getLimitationType()));
            config.setLimitationValue(extension.getFullRunConfig().getLimitationValue());
        }

        config.setWithAnnotation(extension.getFullRunConfig().getWithAnnotation());
        config.setWithoutAnnotation(extension.getFullRunConfig().getWithOutAnnotation());
        config.setScreenshotDir(extension.getTestScreenshotDir());
        config.setInstrumentationRunner(extension.getFullRunConfig().getInstrumentationRunner());
        config.setUsedDeviceGroupId(deviceGroupId);
        //Ui automator settings
        config.setUiAutomatorTestClasses(extension.getUiAutomatorTestConfig().getUiAutomatorTestClasses());

        config.update();
        return config;


    }

    @Override
    public boolean isConfigured() {
        if (extension.getAuthorization() == OAUTH2 && extension.getUsername() == null) {
            logger.warn("TESTDROID: username has not been set");
            return false;
        }
        if (extension.getAuthorization() == OAUTH2 && extension.getPassword() == null) {
            logger.warn("TESTDROID: password has not been set");
            return false;
        }
        if (extension.getAuthorization() == APIKEY && extension.getApiKey() == null) {
            logger.warn("TESTDROID: apiKey has not been set");
            return false;
        }
        if (extension.getProjectName() == null) {
            logger.warn("TESTDROID: project name has not been set, creating a new project");
        }
        if (extension.getMode() == null || APITestRunConfig.Mode.valueOf(extension.getMode()) == null) {
            logger.warn("TESTDROID: Test run mode has not been set(default: FULL_RUN)");
            extension.setMode(APITestRunConfig.Mode.FULL_RUN.name());
        }
        if (extension.getDeviceGroup() == null) {
            logger.warn("TESTDROID: Device group has not been set");
            return false;
        }
        return true;
    }
}
