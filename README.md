Intrepid
========

Intrepid is a replacement for Java RMI (Remote Method Invocation) that allows for simpler
and more transparent usage with more flexible control over sessions and security.

Any object implementing an interface can be used as a remote object with no changes. This
allows even classes such as HashMap to be used remotely without any changes.

[![Javadocs](http://javadoc.io/badge/com.logicartisan.intrepid/intrepid-core.svg)](http://javadoc.io/doc/com.logicartisan.intrepid/intrepid-core)



Download
--------

Download the [latest core JAR](https://search.maven.org/remote_content?g=com.logicartisan.intrepid&a=intrepid&v=LATEST)
and [driver JAR](https://search.maven.org/remote_content?g=com.logicartisan.intrepid&a=intrepid-driver-netty&v=LATEST) or grab via Maven:
```
<dependency>
  <groupId>com.logicartisan.intrepid</groupId>
  <artifactId>intrepid-core</artifactId>
  <version>2.0.0</version>
</dependency>
<dependency>
  <groupId>com.logicartisan.intrepid</groupId>
  <artifactId>intrepid-driver-netty</artifactId>
  <version>2.0.0</version>
  <scope>runtime</scope>  <!-- runtime only -->
</dependency>
```
or Gradle:
```kotlin

implementation("com.logicartisan.intrepid:intrepid-core:2.0.0")
runtimeOnly("com.logicartisan.intrepid:intrepid-driver-netty:2.0.0")
```



License
=======

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	   http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
