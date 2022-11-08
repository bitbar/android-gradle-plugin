Gradle plugin to deploys apks to Testdroid's online devices

A typical project build.gradle will look like this:

    plugins {
        id 'com.android.application'
        id 'testdroid' version '3.0' apply true
    }

    android {
        compileSdk 32

        defaultConfig {
            applicationId "com.example.myapplication"
            minSdk 21
            targetSdk 32
            versionCode 1
            versionName "1.0"

            testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        }

        buildTypes {
            release {
                minifyEnabled false
                proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            }
        }
        compileOptions {
            sourceCompatibility JavaVersion.VERSION_11
            targetCompatibility JavaVersion.VERSION_11
        }
    }
    
    dependencies {
        implementation 'androidx.appcompat:appcompat:1.3.0'
        implementation 'com.google.android.material:material:1.4.0'
        implementation 'androidx.constraintlayout:constraintlayout:2.0.4'
        testImplementation 'junit:junit:4.13.2'
        androidTestImplementation 'androidx.test.ext:junit:1.1.3'
        androidTestImplementation 'androidx.test.espresso:espresso-core:3.4.0'
    }
    
    testdroid {
        username  "demo@localhost"
        password "password"
        cloudUrl = 'https://cloud.bitbar.com'
        projectName "Project 1"
        mode "FULL_RUN"
        scheduler "SINGLE"
        frameworkId 252
        deviceGroup 'My devices'
    }



With above configuration your application and instrumentation package 
are uploaded into BitBar Cloud and test run is launched using device from group 'My Devices'

To launch test run from command line use testdroidUpload task:
>./gradlew testdroidUpload

You can fully control your testrun using the same configurations options which are available via BitBar Cloud web UI.

Below is listed all the supported configurations parameters:

    testdroid {
        username  "demo@localhost" //required by default or authorization "OAUTH2"
        password "password" //required by default or authorization "OAUTH2"

        authorization "OAUTH2" //optional "APIKEY"|"OAUTH2"(default)

        apiKey "d409cad93079b6c6cd55b79e927b17i5" //required if authorization "APIKEY"

        deviceGroup "test group"

        cloudUrl = 'https://cloud.bitbar.com'  //optional - default live
        projectName "Project 1"  //optional - default: create a new project
        mode "FULL_RUN" //FULL_RUN / APP_CRAWLER
        frameworkId // customer test framework id
        testRunName "Custom test run name" //optional - default: build variant name

        deviceLanguageCode "en_US"    //optional - locale <ISO 63>_<ISO 3166> default: en_US
    
        hookUrl "http://localhost:9080"   //optional - call back URL after test run has finished default: empty
    
        scheduler "PARALLEL" // optional - PARALLEL or SERIAL default: PARALLEL
    
        testScreenshotDir = "/sdcard/abc"  //optional - custom screenshot folder  default: /sdcard/test-screenshots
    
        useSystemProxySettings true //optional - Use system proxy settings  default: true
        
        timeout 3600 //optional - test timeout, respected only for Customer with Plan
        
        virusScanTimeout 300000 // optional - timeout for waiting on virus scan (in ms)
        
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
