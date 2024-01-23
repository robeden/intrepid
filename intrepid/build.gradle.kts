plugins {
    id("intrepid-common")
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
