import java.nio.charset.StandardCharsets

plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "com.logicastisan.intrepid"

repositories {
    mavenCentral()
}

java {
    withJavadocJar()
    withSourcesJar()
}

val testJavaVersion = System.getProperty("test.java.version", "19").toInt()
tasks.named<Test>("test") {
    useJUnitPlatform()

    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(testJavaVersion))
    })

    jvmArgs = jvmArgs + listOf(
        "-Dintrepid.lease.duration=2000",
        "-Dintrepid.lease.prune_interval=1000",
        "-Dintrepid.local_call_handler.initial_reservation=10000",
        "-Dintrepid.req_invoke_ack_rate_sec=1",
        "-Dorg.slf4j.simpleLogger.defaultLogLevel=INFO",
    )
}

tasks.withType<JavaCompile> {
    options.encoding = StandardCharsets.UTF_8.toString()
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
}


tasks.named<Javadoc>("javadoc") {
    // Disable warnings when methods aren't commented.
    // See https://github.com/gradle/gradle/issues/15209 for why this crazy cast is happening.
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:missing", "-quiet")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.name
            from(components["java"])
            pom {
                name = project.name
                description = "A Java RMI replacement with easier usage with better control and security"
                url = "https://github.com/robeden/intrepid/"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "robeden"
                        name = "Rob Eden"
                        email = "rob@robeden.com"
                    }
                }
                scm {
                    url = "https://github.com/robeden/intrepid/"
                    connection = "scm:git:git://github.com/robeden/intrepid.git"
                    developerConnection = "scm:git:ssh://git@github.com/robeden/intrepid.git"
                }
            }
        }
    }
    repositories {
        mavenCentral()
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}