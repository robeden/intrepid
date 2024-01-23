plugins {
    id("intrepid-common")
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