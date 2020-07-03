package com.testdroid

import com.android.build.gradle.AppPlugin
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class TestdroidPluginTest {
    @Test
    public void testTestdroidPluginInit() {
        Project project = ProjectBuilder.builder().build()
        project.plugins.apply(AppPlugin.class)
        project.apply plugin: 'testdroid'
    }
}
