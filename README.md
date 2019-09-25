Gradle plugin to deploys apks to Testdroid's online devices

A typical project build.gradle will look like this:

    buildscript {
        repositories {
            mavenCentral()
        }
        dependencies {
            classpath 'com.android.tools.build:gradle:2.3.0'
            classpath 'com.testdroid:gradle:2.81.0'
        }
    }
    
    apply plugin: 'android'
    apply plugin: 'testdroid'
    
    android {
        //...
    }
    
    testdroid {
        username 'testdroid@testdroid.com'
        password 'testdroid'
        deviceGroup 'My Devices'
        mode "FULL_RUN"
    }


With above configuration your application and instrumentation package 
are uploaded into Testdroid and test run is launched using device from group 'My Devices'

To launch test run from command line use testdroidUpload task:
>./gradlew testdroidUpload


You can fully control your testrun using the same configurations options which are available via Testdroid web UI.

Below is listed all the supported configurations parameters:

    testdroid {
        username  "demo@localhost" //required by default or authorization "OAUTH2"
        password "password" //required by default or authorization "OAUTH2"

        authorization "OAUTH2" //optional "APIKEY"|"OAUTH2"(default)

        apiKey "d409cad93079b6c6cd55b79e927b17i5" //required if authorization "APIKEY"

        deviceGroup "test group"

        cloudUrl = 'https://cloud.bitbar.com'  //optional - default live
        projectName "Project 1"  //optional - default: create a new project
        mode "FULL_RUN" //FULL_RUN / APP_CRAWLER / UI_AUTOMATOR
        testRunName "Custom test run name" //optional - default: build variant name

        deviceLanguageCode "en_US"    //optional - locale <ISO 63>_<ISO 3166> default: en_US
    
        hookUrl "http://localhost:9080"   //optional - call back URL after test run has finished default: empty
    
        scheduler "PARALLEL" // optional - PARALLEL or SERIAL default: PARALLEL
    
        testScreenshotDir = "/sdcard/abc"  //optional - custom screenshot folder  default: /sdcard/test-screenshots
    
        useSystemProxySettings true //optional - Use system proxy settings  default: true
        
        timeout 3600 //optional - test timeout, respected only for Customer with Plan
        
        frameworkId // optional - customer test framework id
        
        // AppCrawler configuration - set application credentials
        appCrawlerConfig{
            applicationPassword = "appPassword2"
            applicationUserName = "appUsername2"
        }
    
        // optional - Custom settings for test execution
        fullRunConfig {
            instrumentationRunner =  "com.android.testRunner" //use android.support.test.runner.AndroidJUnitRunner for Espresso2 tests
            withAnnotation = "com.my.annotation"
            withOutAnnotation = "com.my.not.annotation"
            limitationType = "CLASS"
            limitationValue = "foo.bar"
            instrumentationAPKPath = "/tmp/mytesti.apk" //optional - custom instrumentation apk path
        }
        
    }



