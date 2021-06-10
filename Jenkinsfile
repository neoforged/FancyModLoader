library 'forge-shared-library'

pipeline {
    agent {
        docker {
            image 'gradle:7-jdk16'
            args '-v bootstraplaunchergc:/home/gradle/.gradle/'
        }
    }
    environment {
        GRADLE_ARGS = '--no-daemon'
    }

    stages {
        stage('buildandtest') {
            steps {
                withGradle {
                    sh './gradlew ${GRADLE_ARGS} --refresh-dependencies --continue build test'
                    gradleVersion(this, 'properties', 'MYVERSION')
                }
            }
            post {
                success {
                    writeChangelog(currentBuild, 'build/changelog.txt')
                    archiveArtifacts artifacts: 'build/changelog.txt', fingerprint: false
                }
            }
        }
        stage('publish') {
            when {
                not {
                    changeRequest()
                }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'maven-cpw-user', usernameVariable: 'MAVEN_USER', passwordVariable: 'MAVEN_PASSWORD')]) {
                    withGradle {
                        sh './gradlew ${GRADLE_ARGS} publish'
                    }
                }
            }
            post {
                success {
                    build job: 'filegenerator', parameters: [string(name: 'COMMAND', value: "promote cpw.mods:bootstraplauncher ${env.MYVERSION} latest")], propagate: false, wait: false
                }
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'build/libs/**/*.jar', fingerprint: true
        }
    }
}