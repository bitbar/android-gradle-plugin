node("gpg") {
    ansiColor("xterm") {
        stage('Checkout') {
            checkout([
                    $class           : 'GitSCM',
                    userRemoteConfigs: [[credentialsId: 'f652697e-beb7-4724-b1b5-4913a2bf45f5',
                                         url : 'git@github.com:bitbar/testdroid-gradle-plugin.git']]
            ])
        }

        stage('Build') {
            sh "./gradlew clean build"
        }
        stage('Archive artifacts') {
            archiveArtifacts "build/libs/*.jar"
        }
        stage("deploy") {                 
            withCredentials([usernamePassword(credentialsId:'b06e9466-56dc-4bdd-8fa7-d5c3112c937a', 
            passwordVariable: 'osspasswd', usernameVariable: 'ossusername')]) {
                sh "./gradlew -PsonatypeUsername=$ossusername -PsonatypePassword=$osspasswd uploadArchives"
            }
        }
    }
}

