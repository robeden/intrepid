rootProject.name = "intrepid-parent"

includeBuild("build-support")

include(":intrepid")
//include(":intrepid-bom")
include(":intrepid-driver-mina")
include(":intrepid-driver-netty")