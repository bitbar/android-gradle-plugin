Gradle plugin to deploys apks to TestDroid's online devices

A typical project build.gradle will look like this:

    buildscript {
        repositories {
            mavenCentral()
        }
        dependencies {
            classpath 'com.android.tools.build:gradle:0.4.2'
            classpath 'com.testdroid:gradle:1.0'
        }
    }
    
    apply plugin: 'android'
    apply plugin: 'testdroid'
    
    android {
        //...
    }
    
    testdroid {
    }


