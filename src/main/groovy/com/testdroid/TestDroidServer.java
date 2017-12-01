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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.List;

import static com.testdroid.TestDroidExtension.Authorization.APIKEY;
import static com.testdroid.TestDroidExtension.Authorization.OAUTH2;

public class TestDroidServer extends TestServer {

    private final TestDroidExtension extension;
    private final Logger logger;
    private static final String CLOUD_URL = "https://cloud.testdroid.com";

    TestDroidServer(@NonNull TestDroidExtension extension,
                    @NonNull Logger logger) {
        this.extension = extension;
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "testdroid";
    }

    private APIProject searchProject(String projectName, APIListResource<APIProject> projectList) throws APIException {
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
        logger.info("TESTDROID: Variant({}), Uploading APKs\n\t{}\n\t{}",
                variantName,
                testApk.getAbsolutePath(),
                testedApk != null ? testedApk.getAbsolutePath() : "<none>");
        String testdroidCloudURL = extension.getCloudUrl() == null ? CLOUD_URL : extension.getCloudUrl();
        logger.info("Testdroid URL {}", testdroidCloudURL);

        APIClient client = createAPIClient(testdroidCloudURL, extension.getAuthorization());
        if (client == null) {
            throw new InvalidUserDataException("TESTDROID: Client couldn't be configured");
        }
        try {
            user = client.me();
        } catch (APIException e) {
            throw new InvalidUserDataException("TESTDROID: Client couldn't connect", e);
        }

        APIProject project;
        try {
            if (extension.getProjectName() == null) {
                logger.warn("TESTDROID: Project name is not set - creating a new one");
                project = user.createProject(APIProject.Type.ANDROID);
                logger.info("TESTDROID: Created project:" + project.getName());
            } else {
                APIListResource<APIProject> projectList;
                projectList = user.getProjectsResource();

                project = searchProject(extension.getProjectName(), projectList);
                if (project == null) {
                    throw new InvalidUserDataException("TESTDROID: Can't find project " + extension.getProjectName());
                }

            }
            //reload
            project = user.getProject(project.getId());
            logger.info(project.getName());

            APIListResource<APIDeviceGroup> deviceGroupsResource = user.getDeviceGroupsResource();
            APIDeviceGroup deviceGroup = searchDeviceGroup(extension.getDeviceGroup(), deviceGroupsResource);

            if (deviceGroup == null) {
                throw new InvalidUserDataException("TESTDROID: Can't find device group " + extension.getDeviceGroup());
            } else if (deviceGroup.getDeviceCount() == 0) {
                throw new InvalidUserDataException("TESTDROID: There is no devices in group:" + extension.getDeviceGroup());
            }

            updateAPITestRunConfigValues(project, extension, deviceGroup.getId());

            logger.info("TESTDROID: Uploading apks into project {} (id:{})", project.getName(), project.getId());
            File instrumentationAPK = testApk;

            if (extension.getFullRunConfig() != null && extension.getFullRunConfig().getInstrumentationAPKPath() != null
                    && new File(extension.getFullRunConfig().getInstrumentationAPKPath()).exists()) {

                instrumentationAPK = new File(extension.getFullRunConfig().getInstrumentationAPKPath());
                logger.info("TESTDROID: Using custom path for instrumentation APK: {}", extension.getFullRunConfig().getInstrumentationAPKPath());
            }
            uploadBinaries(project, instrumentationAPK, testedApk);

            APITestRun apiTestRun = project.run(extension.getTestRunName() == null ? variantName : extension.getTestRunName());
            extension.setTestRunId(String.valueOf(apiTestRun.getId()));
            extension.setProjectId(String.valueOf(apiTestRun.getProjectId()));

        } catch (APIException exc) {
            throw new InvalidUserDataException("TESTDROID: Uploading failed", exc);
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

    private void uploadBinaries(APIProject project, File testApk, File testedApk) throws APIException {

        if (project.getType().equals(APIProject.Type.UIAUTOMATOR)) {

            if (extension.getUiAutomatorTestConfig() == null || extension.getUiAutomatorTestConfig().getUiAutomatorJarPath() == null) {
                throw new APIException("TESTDROID: Configure uiautomator settings");
            }
            File jarFile = new File(extension.getUiAutomatorTestConfig().getUiAutomatorJarPath());
            if (!jarFile.exists()) {
                throw new APIException("TESTDROID: Invalid uiAutomator jar file:" + jarFile.getAbsolutePath());
            }
            project.uploadTest(new File(extension.getUiAutomatorTestConfig().getUiAutomatorJarPath()), "application/octet-stream");
            logger.info("TESTDROID: uiautomator file uploaded");
            project.uploadApplication(testedApk, "application/octet-stream");
            logger.info("TESTDROID: Android application uploaded");
        } else {

            if (testedApk != null && testedApk.exists()) {
                project.uploadApplication(testedApk, "application/octet-stream");
                logger.info("TESTDROID: Android application uploaded");
            } else {
                logger.warn("TESTDROID: Target application has not been added - uploading only test apk ");
            }

            if (testApk != null && APIProject.Type.ANDROID == project.getType()) {
                project.uploadTest(testApk, "application/octet-stream");
                logger.info("TESTDROID: Android test uploaded");
            }
        }

    }



    private APITestRunConfig updateAPITestRunConfigValues(APIProject project, TestDroidExtension extension, Long deviceGroupId) throws APIException {

        APITestRunConfig config = project.getTestRunConfig();

        config.setHookURL(extension.getHookUrl());
        config.setDeviceLanguageCode(extension.getDeviceLanguageCode());
        config.setScheduler(extension.getScheduler() != null ? APITestRunConfig.Scheduler.valueOf(extension.getScheduler()) : null);
        if (extension.getMode() != null) {
            logger.warn("TESTDROID: mode variable is not used anymore");
        }

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
        if (extension.getMode() != null) {
            logger.warn("TESTDROID: mode variable is not used anymore");
        }
        if (extension.getDeviceGroup() == null) {
            logger.warn("TESTDROID: Device group has not been set");
            return false;
        }
        return true;
    }
}
