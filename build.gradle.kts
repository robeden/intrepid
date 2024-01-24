plugins {
    id("net.nemerosa.versioning") version "3.0.0"
}

version = versioning.info.display
println("Version: $version")

allprojects {
    version = rootProject.version
}