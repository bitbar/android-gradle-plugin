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
import com.testdroid.api.APIClient;
import com.testdroid.api.APIException;
import com.testdroid.api.APIKeyClient;
import com.testdroid.api.DefaultAPIClient;
import com.testdroid.api.dto.Context;
import com.testdroid.api.filter.BooleanFilterEntry;
import com.testdroid.api.filter.StringFilterEntry;
import com.testdroid.api.model.*;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.testdroid.TestDroidExtension.Authorization.APIKEY;
import static com.testdroid.TestDroidExtension.Authorization.OAUTH2;
import static com.testdroid.api.dto.Operand.EQ;
import static com.testdroid.api.model.APIFileConfig.Action.INSTALL;
import static com.testdroid.api.model.APIFileConfig.Action.RUN_TEST;
import static com.testdroid.api.model.APIProject.Type.ANDROID;
import static com.testdroid.dao.repository.dto.MappingKey.*;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.MAX_VALUE;
import static org.apache.commons.lang3.StringUtils.EMPTY;

public class TestDroidServer extends TestServer {

    private final TestDroidExtension extension;

    private final Logger logger;

    private static final String CLOUD_URL = "https://cloud.testdroid.com";

    TestDroidServer(@NonNull TestDroidExtension extension, @NonNull Logger logger) {
        this.extension = extension;
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "testdroid";
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
                project = user.createProject(ANDROID);
                logger.info("TESTDROID: Created project:" + project.getName());
            } else {
                final Context<APIProject> context = new Context(APIProject.class, 0, MAX_VALUE, EMPTY, EMPTY);
                context.addFilter(new StringFilterEntry(NAME, EQ, extension.getProjectName()));
                project = user.getProjectsResource(context).getEntity().getData().stream().findFirst()
                        .orElseThrow(() -> new InvalidUserDataException("TESTDROID: Can't find project " + extension
                                .getProjectName()));
            }
            logger.info(project.getName());

            final Context<APIDeviceGroup> context = new Context(APIDeviceGroup.class, 0, MAX_VALUE, EMPTY, EMPTY);
            context.setExtraParams(Collections.singletonMap(WITH_PUBLIC, TRUE));
            context.addFilter(new StringFilterEntry(DISPLAY_NAME, EQ, extension.getDeviceGroup()));

            APIDeviceGroup deviceGroup = user.getDeviceGroupsResource(context).getEntity().getData().stream()
                    .findFirst()
                    .orElseThrow(() -> new InvalidUserDataException("TESTDROID: Can't find device group " + extension
                            .getDeviceGroup()));

            if (deviceGroup.getDeviceCount() == 0) {
                throw new InvalidUserDataException("TESTDROID: There is no devices in group:" + extension
                        .getDeviceGroup());
            }

            APITestRunConfig testRunConfig = project.getTestRunConfig();
            updateAPITestRunConfigValues(user, testRunConfig, extension, deviceGroup.getId());

            logger.info("TESTDROID: Uploading apks into project {} (id:{})", project.getName(), project.getId());
            File instrumentationAPK = testApk;

            if (extension.getFullRunConfig().getInstrumentationAPKPath() != null
                    && new File(extension.getFullRunConfig().getInstrumentationAPKPath()).exists()) {
                instrumentationAPK = new File(extension.getFullRunConfig().getInstrumentationAPKPath());
                logger.info("TESTDROID: Using custom path for instrumentation APK: {}", extension.getFullRunConfig()
                        .getInstrumentationAPKPath());
            }
            List<APIFileConfig> apiFileConfigs = uploadBinaries(user, instrumentationAPK, testedApk);
            testRunConfig.setFiles(apiFileConfigs);
            testRunConfig.setTestRunName(extension.getTestRunName() == null ? variantName : extension.getTestRunName());
            APITestRun testRun = user.startTestRun(testRunConfig);
            extension.setTestRunId(String.valueOf(testRun.getId()));
            extension.setProjectId(String.valueOf(testRun.getProjectId()));

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
                return new APIKeyClient(testdroidCloudURL, extension
                        .getApiKey(), buildProxyHost(), proxyUser, proxyPassword, false);
            case OAUTH:
                return new DefaultAPIClient(testdroidCloudURL, extension.getUsername(), extension.getPassword());
            case OAUTH_PROXY:
                return new DefaultAPIClient(testdroidCloudURL, extension.getUsername(), extension
                        .getPassword(), buildProxyHost(), false);
            case OAUTH_PROXY_CREDENTIALS:
                return new DefaultAPIClient(testdroidCloudURL, extension.getUsername(), extension
                        .getPassword(), buildProxyHost(), proxyUser, proxyPassword, false);
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

    private List<APIFileConfig> uploadBinaries(APIUser user, File testApk, File testedApk)
            throws APIException, InvalidUserDataException {
        List<APIFileConfig> files = new ArrayList<>();
        APIProjectJobConfig.Type type = resolveFrameworkType(extension);
        if (testedApk != null && testedApk.exists()) {
            files.add(new APIFileConfig(user.uploadFile(testedApk).getId(), INSTALL));
            logger.info("TESTDROID: Android application uploaded");
        }
        switch (type) {
            case INSTATEST:
                break;
            case DEFAULT:
                if (testApk != null && testApk.exists()) {
                    files.add(new APIFileConfig(user.uploadFile(testApk).getId(), RUN_TEST));
                    logger.info("TESTDROID: Android test uploaded");
                }
                break;
            case UIAUTOMATOR:
                if (extension.getUiAutomatorTestConfig().getUiAutomatorJarPath() == null) {
                    throw new APIException("TESTDROID: Configure uiautomator settings");
                }
                File jarFile = new File(extension.getUiAutomatorTestConfig().getUiAutomatorJarPath());
                if (jarFile.exists()) {
                    files.add(new APIFileConfig(user.uploadFile(jarFile).getId(), RUN_TEST));
                    logger.info("TESTDROID: uiautomator file uploaded");
                } else {
                    throw new InvalidUserDataException("TESTDROID: Invalid uiAutomator jar file:" + jarFile
                            .getAbsolutePath());
                }
                break;
            default:
        }
        return files;
    }

    private APITestRunConfig updateAPITestRunConfigValues(
            APIUser user, APITestRunConfig config, TestDroidExtension extension, Long deviceGroupId)
            throws APIException {

        config.setHookURL(extension.getHookUrl());
        config.setDeviceLanguageCode(extension.getDeviceLanguageCode());
        if (extension.getScheduler() != null) {
            config.setScheduler(APITestRunConfig.Scheduler.valueOf(extension.getScheduler()));
        }

        APIProjectJobConfig.Type type = resolveFrameworkType(extension);

        if(extension.getFrameworkId() != null) {
            config.setFrameworkId(extension.getFrameworkId());
        }
        else {
            config.setFrameworkId(resolveFrameworkId(user, type));
        }
        config.setOsType(APIDevice.OsType.ANDROID);

        //App crawler settings
        config.setApplicationUsername(extension.getAppCrawlerConfig().getApplicationUserName());
        config.setApplicationPassword(extension.getAppCrawlerConfig().getApplicationPassword());

        //Full run settings
        if (extension.getFullRunConfig().getLimitationType() != null) {
            config.setLimitationType(APITestRunConfig.LimitationType
                    .valueOf(extension.getFullRunConfig().getLimitationType()));
            config.setLimitationValue(extension.getFullRunConfig().getLimitationValue());
        }

        config.setWithAnnotation(extension.getFullRunConfig().getWithAnnotation());
        config.setWithoutAnnotation(extension.getFullRunConfig().getWithOutAnnotation());
        config.setScreenshotDir(extension.getTestScreenshotDir());
        config.setInstrumentationRunner(extension.getFullRunConfig().getInstrumentationRunner());
        config.setUsedDeviceGroupId(deviceGroupId);
        //Reset as in Gradle Plugin we use only deviceGroups
        config.setDeviceIds(null);
        //Ui automator settings
        config.setUiAutomatorTestClasses(extension.getUiAutomatorTestConfig().getUiAutomatorTestClasses());
        Optional.ofNullable(extension.getTimeout()).ifPresent(config::setTimeout);
        return config;

    }

    private APIProjectJobConfig.Type resolveFrameworkType(TestDroidExtension extension) {
        switch (extension.getMode()) {
            case FULL_RUN:
                return APIProjectJobConfig.Type.DEFAULT;
            case UI_AUTOMATOR:
                return APIProjectJobConfig.Type.UIAUTOMATOR;
            case APP_CRAWLER:
            default:
                return APIProjectJobConfig.Type.INSTATEST;
        }
    }

    private Long resolveFrameworkId(APIUser user, APIProjectJobConfig.Type type) throws APIException {
        final Context<APIFramework> context = new Context(APIFramework.class, 0, MAX_VALUE, EMPTY, EMPTY);
        context.addFilter(new StringFilterEntry(OS_TYPE, EQ, ANDROID.name()));
        context.addFilter(new BooleanFilterEntry(FOR_PROJECTS, EQ, TRUE));
        context.addFilter(new BooleanFilterEntry(CAN_RUN_FROM_UI, EQ, TRUE));
        context.addFilter(new StringFilterEntry(TYPE, EQ, type.name()));
        return user.getAvailableFrameworksResource(context).getEntity().getData().stream().findFirst()
                .map(APIFramework::getId)
                .orElseThrow(() -> new InvalidUserDataException("TESTDROID: Can't determinate framework for " +
                        extension.getProjectName()));
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
        if (extension.getDeviceGroup() == null) {
            logger.warn("TESTDROID: Device group has not been set");
            return false;
        }
        return true;
    }
}
