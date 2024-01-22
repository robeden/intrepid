plugins {
  id("com.vanniktech.maven.publish.base")
  `java-library`
}


dependencies {
    api(libs.logicartisan.common)
    implementation(libs.trove)

    implementation(libs.slf4j.api)
    implementation(libs.findbugs.jsr305)


    testImplementation(libs.test.mockito)
	testImplementation(libs.okio)

    testRuntimeOnly(libs.slf4j.simple)

    testImplementation(libs.test.junit.jupiter)
    testRuntimeOnly(libs.test.junit.platform)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.named<Javadoc>("javadoc") {
    // Disable warnings when methods aren't commented.
    // See https://github.com/gradle/gradle/issues/15209 for why this crazy cast is happening.
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:missing", "-quiet")
}