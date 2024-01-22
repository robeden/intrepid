import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import java.nio.charset.StandardCharsets
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED

plugins {
  id("build-support").apply(false)
}

buildscript {
  dependencies {
//    classpath(libs.android.gradle.plugin)
//    classpath(libs.dokka)
//    classpath(libs.jmh.gradle.plugin)
//    classpath(libs.binaryCompatibilityValidator)
//    classpath(libs.spotless)
//    classpath(libs.bnd)
    classpath(libs.vanniktech.publish.plugin)
  }

  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

apply(plugin = "com.vanniktech.maven.publish.base")


allprojects {
  group = project.property("GROUP") as String
  version = project.property("VERSION_NAME") as String

  repositories {
    mavenCentral()
  }

  plugins.withId("com.vanniktech.maven.publish.base") {
    configure<PublishingExtension> {
      repositories {
        /**
         * Want to push to an internal repository for testing? Set the following properties in
         * `~/.gradle/gradle.properties`.
         *
         * internalMavenUrl=YOUR_INTERNAL_MAVEN_REPOSITORY_URL
         * internalMavenUsername=YOUR_USERNAME
         * internalMavenPassword=YOUR_PASSWORD
         */
        val internalUrl = providers.gradleProperty("internalUrl")
        if (internalUrl.isPresent) {
          maven {
            name = "internal"
            setUrl(internalUrl)
            credentials(PasswordCredentials::class)
          }
        }
      }
    }
    val publishingExtension = extensions.getByType(PublishingExtension::class.java)
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(SonatypeHost.S01, automaticRelease = true)
      signAllPublications()
      pom {
        description.set("A Java RMI replacement with easier usage with better control and security")
        name.set(project.name)
        url.set("https://github.com/robeden/intrepid/")
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        scm {
          url.set("https://github.com/robeden/intrepid/")
          connection.set("scm:git:git://github.com/robeden/intrepid.git")
          developerConnection.set("scm:git:ssh://git@github.com/robeden/intrepid.git")
        }
        developers {
          developer {
            id.set("robeden")
            name.set("Rob Eden")
            email.set("rob@robeden.com")
          }
        }
      }

      // Configure the kotlinMultiplatform artifact to depend on the JVM artifact in pom.xml only.
      // This hack allows Maven users to continue using our original Okio artifact names (like
      // com.squareup.okio:okio:3.x.y) even though we changed that artifact from JVM-only to Kotlin
      // Multiplatform. Note that module.json doesn't need this hack.
      val mavenPublications = publishingExtension.publications.withType<MavenPublication>()
//      mavenPublications.configureEach {
//        if (name != "jvm") return@configureEach
//        val jvmPublication = this
//        val kmpPublication = mavenPublications.getByName("kotlinMultiplatform")
//        kmpPublication.pom.withXml {
//          val root = asNode()
//          val dependencies = (root["dependencies"] as NodeList).firstOrNull() as Node?
//            ?: root.appendNode("dependencies")
//          for (child in dependencies.children().toList()) {
//            dependencies.remove(child as Node)
//          }
//          dependencies.appendNode("dependency").apply {
//            appendNode("groupId", jvmPublication.groupId)
//            appendNode("artifactId", jvmPublication.artifactId)
//            appendNode("version", jvmPublication.version)
//            appendNode("scope", "compile")
//          }
//        }
//      }
    }
  }
}

subprojects {
  tasks.withType<JavaCompile> {
    options.encoding = StandardCharsets.UTF_8.toString()
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
  }

  val testJavaVersion = System.getProperty("test.java.version", "21").toInt()
  tasks.withType<Test> {
    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    javaLauncher.set(javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(testJavaVersion))
    })

    jvmArgs = jvmArgs!! + listOf(
      "-Dintrepid.lease.duration=2000",
      "-Dintrepid.lease.prune_interval=1000",
      "-Dintrepid.local_call_handler.initial_reservation=10000",
      "-Dintrepid.req_invoke_ack_rate_sec=1",
      "-Dorg.slf4j.simpleLogger.defaultLogLevel=INFO",
    )

    testLogging {
      events(STARTED, PASSED, SKIPPED, FAILED)
      exceptionFormat = TestExceptionFormat.FULL
      showStandardStreams = false
    }

//    if (loomEnabled) {
//      jvmArgs = jvmArgs!! + listOf(
//        "-Djdk.tracePinnedThread=full",
//        "--enable-preview",
//        "-DloomEnabled=true"
//      )
//    }
  }

  tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
  }

//  normalization {
//    runtimeClasspath {
//      metaInf {
//        ignoreAttribute("Bnd-LastModified")
//      }
//    }
//  }
}