Hey all,

this is my code-sink for mainly java files.

###Have fun to browse through many things for:

- Datastructures & Algorithms
- Apache Hadoop
- Apache Hama
- Machine learning (Clustering, Classification, Optimization algorithms)
- [Graph algorithms (they are now in the other repository called "tjungblut-graph")](https://github.com/thomasjungblut/tjungblut-graph "Thomas' nifty graph lib")
- [Matrix and Vector stuff (they are now in the other repository called "tjungblut-math")](https://github.com/thomasjungblut/tjungblut-math "Thomas' nifty math lib")
- JCuda experiments

License
-------

Since I am Apache committer, I consider everything inside of this repository 
licensed by Apache 2.0 license, although I haven't put the usual header into the source files.

If something is not licensed via Apache 2.0, there is a reference or an additional licence header included in the specific source file.

Package Layout
--------------

- /src -> raw source code.
- /test -> testcases for the code in /src.
- /files -> reserved directory for files in examples or ignored paths for output of applications.
- /jcuda -> the cuda 3rd party libs for the de.jungblut.math.cuda package

Build
-----

To build this library, you will need to install [my math lib on your local computer](https://github.com/thomasjungblut/tjungblut-math "Thomas' nifty math lib"), build instructions can be found there as well.
Everything else shall be found on the central maven repository, or in the case of JCUDA in the jcuda path which is automagically added.

You can simply build with:

> mvn clean package install

The created jar contains debuggable code + sources.

Note that there may be an issue to retrieve MRUnit-0.9.0-incubating, therefore you can simply download it and install it manually via Maven.

E.G. like this: 

> mvn install:install-file -DgroupId=org.apache.mrunit -DartifactId=mrunit -Dversion=0.9.0-incubating -Dpackaging=jar -Dfile=<Path to MRUnit>/mrunit-0.9.0-incubating-hadoop1.jar
