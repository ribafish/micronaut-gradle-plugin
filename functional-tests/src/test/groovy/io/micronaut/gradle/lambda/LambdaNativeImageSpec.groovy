package io.micronaut.gradle.lambda

import io.micronaut.gradle.AbstractGradleBuildSpec
import io.micronaut.gradle.fixtures.AbstractFunctionalTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

@Requires({ AbstractGradleBuildSpec.graalVmAvailable })
@IgnoreIf({ os.windows })
class LambdaNativeImageSpec extends AbstractFunctionalTest {

    void 'mainclass is set correctly for an application deployed as GraalVM and Lambda'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.docker"
                id "io.micronaut.graalvm"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }
            
            $repositoriesBlock
            
            application {
                mainClass.set("com.example.Application")
            }
            
            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find() { it.endsWith(' io.micronaut.function.aws.runtime.MicronautLambdaRuntime') }
        !dockerFileNative.find() { it.endsWith('com.example.Application') }
    }

    void 'native lambdas build in docker fetch the correct graalvm for #desc'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
                id "io.micronaut.docker"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            application {
                mainClass.set("com.example.Application")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }

            dockerfileNative {
                graalArch.set('$archset')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find() { it.contains("graalvm-jdk-17_linux-${archset}_bin.tar.gz ") }

        where:
        archset   | desc
        'aarch64' | 'ARM architecture'
        'x64'     | 'Intel architecture'
    }

    void 'it is possible to define the mainclass for a dockerfile native'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
                id "io.micronaut.docker"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "netty"
            }
            
            $repositoriesBlock
            
            application {
                mainClass.set("com.example.Application")
            }
            
            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }
            
            graalvmNative {
                binaries {
                    main {
                        mainClass.set("my.own.main.class")
                    }
                }
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find() { it.contains('my.own.main.class') }
        !dockerFileNative.find() { it.contains('com.example.Application') }
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/171")
    void 'mainclass is set correctly for an AWS Lambda function using custom-runtime and GraalVM'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
                id "io.micronaut.docker"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            dependencies {
                implementation("io.micronaut.validation:micronaut-validation")
                implementation("io.micronaut:micronaut-runtime")
                implementation("io.micronaut.aws:micronaut-function-aws")
                implementation("io.micronaut.aws:micronaut-function-aws-custom-runtime")
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }

            application {
                mainClass.set("com.example.BookLambdaRuntime")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        !dockerFileNative.find() { it.endsWith(' io.micronaut.function.aws.runtime.MicronautLambdaRuntime') }
        dockerFileNative.find() { it.endsWith('com.example.BookLambdaRuntime') }
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/279")
    void 'baseImage is overridden correctly for an AWS Lambda function using custom-runtime and GraalVM'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            dependencies {
                implementation("io.micronaut.validation:micronaut-validation")
                implementation("io.micronaut:micronaut-runtime")
                implementation("io.micronaut.aws:micronaut-function-aws")
                implementation("io.micronaut.aws:micronaut-function-aws-custom-runtime")
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }

            application {
                mainClass.set("com.example.BookLambdaRuntime")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }

            dockerfileNative {
                baseImage('internal.proxy.com/amazonlinux:2')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find() { it.contains('internal.proxy.com/amazonlinux:2') }
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/279")
    void 'baseImage is set correctly for an AWS Lambda function using custom-runtime and GraalVM'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.application"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            dependencies {
                implementation("io.micronaut.validation:micronaut-validation")
                implementation("io.micronaut:micronaut-runtime")
                implementation("io.micronaut.aws:micronaut-function-aws")
                implementation("io.micronaut.aws:micronaut-function-aws-custom-runtime")
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }

            application {
                mainClass.set("com.example.BookLambdaRuntime")
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find() { it.contains('amazonlinux:2') }
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/pull/537")
    void 'it is possible to define the GraalVM releases URL for a dockerfile native'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
                id "io.micronaut.docker"
            }
            
            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }
            
            $repositoriesBlock
            
            application {
                mainClass.set("com.example.Application")
            }
            
            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }
            
            graalvmNative {
                binaries {
                    main {
                        mainClass.set("my.own.main.class")
                    }
                }
            }

            tasks.named("dockerfileNative") {
                graalReleasesUrl = "https://releases.company.com/downloads"
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        !dockerFileNative.find() { it.contains('https://github.com/graalvm/graalvm-ce-builds/releases/download') }
        dockerFileNative.find() { it.contains('https://releases.company.com/downloads') }
    }

    @Issue("https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/753")
    void 'can pickup a different native lambda runtime'() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """import io.micronaut.gradle.graalvm.NativeLambdaRuntime
            plugins {
                id "io.micronaut.minimal.application"
                id "io.micronaut.graalvm"
                id "io.micronaut.docker"
            }

            micronaut {
                version "$micronautVersion"
                runtime "lambda_provided"
            }

            $repositoriesBlock

            dependencies {
                implementation("io.micronaut.validation:micronaut-validation")
                implementation("io.micronaut:micronaut-runtime")
                runtimeOnly("ch.qos.logback:logback-classic")
                testImplementation("io.micronaut:micronaut-http-client")
            }

            micronaut {
                nativeLambda {
                    lambdaRuntime = NativeLambdaRuntime.$runtime
                }
            }

            java {
                sourceCompatibility = JavaVersion.toVersion('17')
                targetCompatibility = JavaVersion.toVersion('17')
            }
        """

        when:
        def result = build('dockerfileNative')

        def dockerfileNativeTask = result.task(':dockerfileNative')
        def dockerFileNative = new File(testProjectDir.root, 'build/docker/native-main/DockerfileNative').readLines('UTF-8')

        then:
        dockerfileNativeTask.outcome == TaskOutcome.SUCCESS

        and:
        dockerFileNative.find() { it.endsWith(mainClass) }

        where:
        runtime          | mainClass
        'API_GATEWAY_V1' | 'io.micronaut.function.aws.runtime.MicronautLambdaRuntime'
        'API_GATEWAY_V2' | 'io.micronaut.function.aws.runtime.APIGatewayV2HTTPEventMicronautLambdaRuntime'
        'ALB'            | 'io.micronaut.function.aws.runtime.ApplicationLoadBalancerMicronautLambdaRuntime'
    }
}
