plugins {
  id("com.vanniktech.maven.publish.base")
  `java-library`
}


dependencies {
    implementation(project(":intrepid"))

    implementation(libs.mina.core)
    implementation(libs.mina.compression)



    testImplementation(libs.trove)
    testImplementation(libs.test.mockito)
    testImplementation(libs.test.easymock)
	testImplementation(libs.byteunits)
//    testImplementation(project(":intrepid").sourceSets.test.output)

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