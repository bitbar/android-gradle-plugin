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

import java.io.File;

public class TestDroidServer extends TestServer {

    private final TestDroidExtension extension;
    private final ILogger logger;

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
        System.out.println(String.format(
                "TESTDROID: Variant(%s), Uploading APKs\n\t%s\n\t%s",
                variantName,
                testApk.getAbsolutePath(),
                testedApk != null ? testedApk.getAbsolutePath() : "<none>"));
    }

    @Override
    public boolean isConfigured() {
        // TODO: detect authentication is actually configured and return false if not.
        return true;
    }
}
