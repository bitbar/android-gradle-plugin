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
import com.android.utils.ILogger;
import com.testdroid.api.*;
import com.testdroid.api.model.*;
import com.testdroid.api.model.APIProject;
import org.gradle.api.Project;

import java.io.File;
import java.util.List;

public class TestDroidServer extends TestServer {

    private final TestDroidExtension extension;
    private final ILogger logger;
    private final String cloudURL = "https://cloud.testdroid.com";

    TestDroidServer(@NonNull TestDroidExtension extension,
                    @NonNull ILogger logger) {
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
                System.out.println("Device group name" + deviceGroup.getDisplayName());
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
        System.out.println(String.format(
                "TESTDROID: Variant(%s), Uploading APKs\n\t%s\n\t%s",
                variantName,
                testApk.getAbsolutePath(),
                testedApk != null ? testedApk.getAbsolutePath() : "<none>"));
        String testdroidCloudURL = extension.getCloudUrl() == null ? cloudURL : extension.getCloudUrl();
        logger.info("Testdroid URL %s", testdroidCloudURL);
        APIClient client = new DefaultAPIClient(testdroidCloudURL, extension.getUsername(), extension.getPassword());

        try {
            user = client.me();
        } catch (APIException e) {
            logger.error(e, "Client couldn't connect");
            return;
        }

        APIProject project;
        try {
            if (extension.getProjectName() == null) {
                System.out.println("Project name is not set - creating a new one");

                project = user.createProject(APIProject.Type.valueOf(extension.getMode()));

            } else {
                APIListResource<APIProject> projectList;
                projectList = user.getProjectsResource();

                project = searchProject(extension.getProjectName(), APIProject.Type.ANDROID, projectList);
                if (project == null) {
                    System.out.println("Can't find project " + extension.getProjectName());
                    return;
                }

            }
            //reload
            project = user.getProject(project.getId());
            System.out.println(project.getName());

            APIListResource<APIDeviceGroup> deviceGroupsResource = user.getDeviceGroupsResource();
            APIDeviceGroup deviceGroup = searchDeviceGroup(extension.getDeviceGroup(), deviceGroupsResource);

            if (deviceGroup == null) {
                System.out.println("Can't find device group " + extension.getDeviceGroup());
                return;
            }

            APITestRunConfig config;
            config = updateAPITestRunConfigValues(project, extension, deviceGroup.getId());


            System.out.println(String.format(
                    "TESTDROID: Uploading apks into project %s (id:%d)", project.getName(), project.getId()));

            logger.info("Uploading apks into project %s (id:%d)", project.getName(), project.getId());
            uploadBinaries(project, config, testApk, testedApk);

            project.run(variantName);

        } catch (APIException e) {
            logger.error(e, "Can't upload project");
            System.out.println(String.format(
                    "TESTDROID: Uploading failed:%s", e.getStatus()));

        }

    }

    private void uploadBinaries(APIProject project, APITestRunConfig config, File testApk, File testedApk) throws APIException {
        AndroidFiles androidFiles = project.getFiles(AndroidFiles.class);

        androidFiles.uploadApp(testedApk);
        logger.info("Android application uploaded");
        if (testedApk != null && config.getMode().equals(APITestRunConfig.Mode.APP_CRAWLER)) {
            androidFiles.uploadTest(testApk);
            logger.info("Android test uploaded");
        }
        if (APITestRunConfig.Mode.UIAUTOMATOR == config.getMode()) {
            if (project.getType().equals(APIProject.Type.UIAUTOMATOR)) {
                UIAutomatorFiles uiAutomatorFiles = project.getFiles(UIAutomatorFiles.class);
                File jarFile = new File(extension.getUiAutomatorTestConfig().getUiAutomatorJarPath());
                if (!jarFile.exists()) {
                    throw new APIException("Invalid uiAutomator jar file:" + jarFile.getAbsolutePath());
                }
                uiAutomatorFiles.uploadTest(new File(extension.getUiAutomatorTestConfig().getUiAutomatorJarPath()));
                logger.info("Android application uploaded");
            } else {
                throw new APIException("Invalid project mode - create a new UIAutomator project");
            }

        }

    }

    private APITestRunConfig updateAPITestRunConfigValues(APIProject project, TestDroidExtension extension, Long deviceGroupId) throws APIException {

        APITestRunConfig config = project.getTestRunConfig();

        config.setHookURL(extension.getHookUrl());
        config.setDeviceLanguageCode(extension.getDeviceLanguageCode());
        config.setScheduler(APITestRunConfig.Scheduler.valueOf(extension.getScheduler()));

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
        if (extension.getUsername() == null) {
            logger.warning("username has not been set");
            System.out.println("username has not been set");
            return false;
        }
        if (extension.getPassword() == null) {
            logger.warning("password has not been set");
            System.out.println("password has not been set");
            return false;
        }
        if (extension.getProjectName() == null) {
            logger.warning("project name has not been set, creating a new project");
            System.out.println("project name has not been set, creating a new project");
        }
        if (extension.getMode() == null || APITestRunConfig.Mode.valueOf(extension.getMode()) == null) {
            logger.warning("Test run mode has not been set(default: FULL_RUN");
            System.out.println("Test run mode has not been set(default: FULL_RUN");
            extension.setMode(APITestRunConfig.Mode.FULL_RUN.name());
        }
        if (extension.getDeviceGroup() == null) {
            logger.warning("Device group has not been set(default: free devices");
            System.out.println("Device group has not been set(default: free devices");
            extension.setDeviceGroup("Free devices");
        }
        return true;
    }
}
