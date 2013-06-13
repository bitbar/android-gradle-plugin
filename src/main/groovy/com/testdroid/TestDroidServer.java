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
import com.testdroid.api.model.APIFiles;
import com.testdroid.api.model.APIProject;
import com.testdroid.api.model.APIUser;

import java.io.File;

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

    @Override
    public void uploadApks(@NonNull String variantName, @NonNull File testApk, @Nullable File testedApk) {
        APIUser user;
        System.out.println(String.format(
                "TESTDROID: Variant(%s), Uploading APKs\n\t%s\n\t%s",
                variantName,
                testApk.getAbsolutePath(),
                testedApk != null ? testedApk.getAbsolutePath() : "<none>"));
        String testdroidCloudURL = extension.getCloudUrl() == null ? cloudURL :extension.getCloudUrl();
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
            if(extension.getProjectName() == null) {
                project = user.createProject() ;

            } else {
                APIListResource<APIProject> projectList;
                projectList = user.getProjectsResource(0,2,extension.getProjectName(), null);

                if(projectList == null || projectList.getEntity() == null
                        || projectList.getEntity().getData() == null || projectList.getEntity().getData().get(0) == null)
                {
                    logger.warning("Project %s not found.. Skipping upload.", extension.getProjectName() );
                    return;
                }
                project = projectList.getEntity().getData().get(0);

                if(projectList.getEntity().getData().size() > 1) {
                    logger.warning("Found more than one project with name %s. Skipping upload.", extension.getProjectName() );
                    return;

                }
            }
            project = user.getProject(project.getId());
            APIFiles.AndroidFiles androidFiles = project.getFiles(APIFiles.AndroidFiles.class)  ;
            androidFiles.uploadApp(testedApk);
            androidFiles.uploadTest(testApk);
            System.out.println(String.format(
                    "TESTDROID: Uploading apks into project %s (id:%d)", project.getName(), project.getId()));

            logger.info("Uploading apks into project %s (id:%d)", project.getName(), project.getId() );


        } catch (APIException e) {
            logger.error(e, "Can't upload project");
            System.out.println(String.format(
                    "TESTDROID: Uploading failed:%s", e.getStatus()));

        }

    }

    @Override
    public boolean isConfigured() {
        if(extension.getUsername() == null) {
            logger.warning("username has not been set");
            System.out.println("username has not been set");
            return false;
        }
        if(extension.getPassword() == null) {
            logger.warning("password has not been set");
            System.out.println("password has not been set");
            return false;
        }
        if(extension.getProjectName() == null) {
            logger.warning("project name has not been set, creating a new project");
            System.out.println("project name has not been set, creating a new project");
        }
        return true;
    }
}
