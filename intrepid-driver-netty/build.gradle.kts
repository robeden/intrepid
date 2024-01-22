plugins {
  id("com.vanniktech.maven.publish.base")
  `java-library`
}


dependencies {
    implementation(project(":intrepid"))

    implementation(libs.netty.handler)
    implementation(libs.netty.transport)


    testImplementation(libs.trove)
    testImplementation(libs.test.jzlib)
    testImplementation(libs.test.mockito)
    testImplementation(libs.test.easymock)
	testImplementation(libs.byteunits)
//    testCompile(projects(":intrepid").sourceSets.test.output)


    testImplementation(libs.netty.transport.epoll.classes)
    testImplementation(libs.netty.transport.kqueue.classes)

    testRuntimeOnly(variantOf(libs.netty.transport.epoll.native) { classifier("linux-x86_64") } )
    testRuntimeOnly(variantOf(libs.netty.transport.kqueue.native) { classifier("osx-x86_64") } )
    testRuntimeOnly(variantOf(libs.netty.transport.kqueue.native) { classifier("osx-aarch_64") } )

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