pipeline {
    agent any

    // GitHub Actions is the primary CI quality gate. This Jenkinsfile is a reference
    // pipeline for teams that need Jenkins-hosted execution.

    parameters {
        choice(name: 'ENV', choices: ['dev', 'staging', 'prod'], description: 'Target test environment')
    }

    environment {
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
        GITHUB_TOKEN = credentials('github-token')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                runGradle('clean classes testClasses')
            }
        }

        stage('Test') {
            steps {
                runGradle("check securityScan -Denv=${params.ENV}")
            }
        }

        stage('Container Tests') {
            steps {
                runGradle("containerTest -Denv=${params.ENV}")
            }
        }

        stage('Allure Report') {
            steps {
                runGradle('allureReport')
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'build/reports/**, build/allure-results/**, build/reports/allure-report/**, build/logs/**, build/pacts/**', allowEmptyArchive: true
            }
        }
    }

    post {
        always {
            allure includeProperties: false, jdk: '', results: [[path: 'build/allure-results']]
            junit allowEmptyResults: true, testResults: 'build/test-results/test/*.xml'
        }
        failure {
            script {
                if (env.CHANGE_AUTHOR_EMAIL) {
                    mail to: env.CHANGE_AUTHOR_EMAIL,
                        subject: "ARIA API Framework Jenkins failure: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                        body: "Build failed: ${env.BUILD_URL}"
                }
            }
        }
    }
}

void runGradle(String arguments) {
    if (isUnix()) {
        sh "./gradlew ${arguments}"
    } else {
        bat ".\\gradlew.bat ${arguments}"
    }
}
