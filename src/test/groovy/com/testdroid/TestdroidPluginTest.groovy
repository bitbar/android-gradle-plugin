package com.testdroid

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import com.android.build.gradle.AppPlugin

class TestdroidPluginTest {
    @Test
    public void testTestdroidPluginInit() {
        Project project = ProjectBuilder.builder().build()
        project.plugins.apply(com.android.build.gradle.AppPlugin.class)
        project.apply plugin: 'testdroid'
    }
}
