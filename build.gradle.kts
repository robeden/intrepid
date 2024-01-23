plugins {
    id("net.nemerosa.versioning") version "2.8.2"
}

version = versioning.info.full

allprojects {
    version = rootProject.version
}