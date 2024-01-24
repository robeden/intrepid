version = project.property("VERSION") as String
println("Version: $version")

allprojects {
    version = rootProject.version
}