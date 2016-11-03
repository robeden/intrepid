Intrepid
========

Intrepid is a replacement for Java RMI (Remote Method Invocation) that allows for simpler
and more transparent usage with more flexible control over sessions and security.

Any object implementing an interface can be used as a remote object with no changes. This
allows even classes such as HashMap to be used remotely without any changes.

For more information, see [the website](http://intrepid.starlight-systems.com).


Download
--------

Download [the latest JAR][1] or grab via Maven:
```xml
<dependency>
  <groupId>com.logicartisan</groupId>
  <artifactId>intrepid</artifactId>
  <version>1.7.0</version>
</dependency>
```
or Gradle:
```groovy
compile 'com.logicartisan:intrepid:1.7.0'
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


 [1]: https://search.maven.org/remote_content?g=com.logicartisan&a=intrepid&v=LATEST
