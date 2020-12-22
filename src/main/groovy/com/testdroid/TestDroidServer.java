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
import com.testdroid.api.filter.FilterEntry;
import com.testdroid.api.model.*;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.testdroid.TestDroidExtension.Authorization.APIKEY;
import static com.testdroid.TestDroidExtension.Authorization.OAUTH2;
import static com.testdroid.api.dto.MappingKey.*;
import static com.testdroid.api.dto.Operand.EQ;
import static com.testdroid.api.model.APIFileConfig.Action.INSTALL;
import static com.testdroid.api.model.APIFileConfig.Action.RUN_TEST;
import static com.testdroid.api.model.APIDevice.OsType.ANDROID;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.MAX_VALUE;
import static org.apache.commons.lang3.StringUtils.*;

public class TestDroidServer extends TestServer {

    private final TestDroidExtension extension;

    private final Logger logger;

    private static final String CLOUD_URL = "https://cloud.bitbar.com";

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
        try {
            final Context<APIDeviceGroup> context = new Context<>(APIDeviceGroup.class, 0, MAX_VALUE, EMPTY, EMPTY);
            HashSetValuedHashMap<String, Object> extraParams = new HashSetValuedHashMap<>();
            extraParams.put(WITH_PUBLIC, TRUE);
            context.setExtraParams(extraParams);
            context.addFilter(new FilterEntry(DISPLAY_NAME, EQ, extension.getDeviceGroup()));

            APIDeviceGroup deviceGroup = user.getDeviceGroupsResource(context).getEntity().getData().stream()
                    .filter(apiDeviceGroup -> apiDeviceGroup.getDisplayName().equals(extension.getDeviceGroup()))
                    .findFirst()
                    .orElseThrow(() -> new InvalidUserDataException("TESTDROID: Can't find device group " + extension
                            .getDeviceGroup()));

            if (deviceGroup.getDeviceCount() == 0) {
                throw new InvalidUserDataException("TESTDROID: There is no devices in group:" + extension
                        .getDeviceGroup());
            }

            APITestRunConfig testRunConfig = new APITestRunConfig();
            if (isNotBlank(extension.getProjectName())) {
                APIProject project = findProject(user, extension.getProjectName());
                testRunConfig.setProjectId(project.getId());
                testRunConfig = user.validateTestRunConfig(testRunConfig);
            }

            updateAPITestRunConfigValues(user, testRunConfig, extension, deviceGroup.getId());

            logger.info("TESTDROID: Uploading apks");
            File instrumentationAPK = testApk;

            if (extension.getFullRunConfig().getInstrumentationAPKPath() != null
                    && new File(extension.getFullRunConfig().getInstrumentationAPKPath()).exists()) {
                instrumentationAPK = new File(extension.getFullRunConfig().getInstrumentationAPKPath());
                logger.info("TESTDROID: Using custom path for instrumentation APK: {}", extension.getFullRunConfig()
                        .getInstrumentationAPKPath());
            }
            List<APIFileConfig> apiFileConfigs = uploadBinaries(user, instrumentationAPK, testedApk,
                    extension.getVirusScanTimeout());
            testRunConfig.setFiles(apiFileConfigs);
            testRunConfig.setTestRunName(extension.getTestRunName() == null ? variantName : extension.getTestRunName());
            APITestRun testRun = user.startTestRun(testRunConfig);
            extension.setTestRunId(String.valueOf(testRun.getId()));
            extension.setProjectId(String.valueOf(testRun.getProjectId()));

        } catch (APIException | InterruptedException exc) {
            throw new InvalidUserDataException("TESTDROID: Uploading failed", exc);
        }
    }

    private APIProject findProject(APIUser user, String projectName) throws APIException{
        final Context<APIProject> context = new Context<>(APIProject.class, 0, MAX_VALUE, EMPTY, EMPTY);
        context.addFilter(new FilterEntry(NAME, EQ, projectName));
        return user.getProjectsResource(context).getEntity().getData().stream().findFirst()
                .orElseThrow(() -> new InvalidUserDataException("TESTDROID: Can't find project " + projectName));
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

    private List<APIFileConfig> uploadBinaries(APIUser user, File testApk, File testedApk, Long virusScanTimeout)
            throws APIException, InvalidUserDataException, InterruptedException {
        List<APIFileConfig> fileConfigs = new ArrayList<>();
        List<APIUserFile> files = new ArrayList<>();
        if (testedApk != null && testedApk.exists()) {
            fileConfigs.add(uploadFile(user, testedApk, files, INSTALL));
            logger.info("TESTDROID: Android application uploaded");
        }
        if (extension.getMode() == TestDroidExtension.Mode.FULL_RUN && testApk != null && testApk.exists()) {
            fileConfigs.add(uploadFile(user, testApk, files, RUN_TEST));
            logger.info("TESTDROID: Android test uploaded");
        }
        if (virusScanTimeout == null) {
            APIUserFile.waitForVirusScans(files.toArray(new APIUserFile[0]));
        } else {
            APIUserFile.waitForVirusScans(virusScanTimeout, files.toArray(new APIUserFile[0]));
        }
        return fileConfigs;
    }

    private APIFileConfig uploadFile(
            APIUser user, File file, List<APIUserFile> uploadedFiles, APIFileConfig.Action action)
            throws APIException {
        APIUserFile apiFile = user.uploadFile(file);
        uploadedFiles.add(apiFile);
        return new APIFileConfig(apiFile.getId(), action);
    }

    private void updateAPITestRunConfigValues(
            APIUser user, APITestRunConfig config, TestDroidExtension extension, Long deviceGroupId)
            throws APIException {

        config.setHookURL(extension.getHookUrl());
        config.setDeviceLanguageCode(isBlank(extension.getDeviceLanguageCode()) ? "en_US" : extension.getDeviceLanguageCode());
        if (extension.getScheduler() != null) {
            config.setScheduler(APITestRunConfig.Scheduler.valueOf(extension.getScheduler()));
        }

        if (extension.getFrameworkId() != null) {
            config.setFrameworkId(extension.getFrameworkId());
        } else {
            config.setFrameworkId(resolveFrameworkId(user));
        }
        config.setOsType(ANDROID);

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
        Optional.ofNullable(extension.getTimeout()).ifPresent(config::setTimeout);
    }

    private Long resolveFrameworkId(APIUser user) throws APIException {
        final Context<APIFramework> context = new Context<>(APIFramework.class, 0, MAX_VALUE, EMPTY, EMPTY);
        context.addFilter(new FilterEntry(OS_TYPE, EQ, ANDROID.name()));
        context.addFilter(new FilterEntry(FOR_PROJECTS, EQ, TRUE));
        context.addFilter(new FilterEntry(CAN_RUN_FROM_UI, EQ, TRUE));
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
