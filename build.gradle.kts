plugins {
    id("net.nemerosa.versioning") version "3.0.0"
}

version = versioning.info.full
println("Version: $version")

allprojects {
    version = rootProject.version
}