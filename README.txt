** Cashmere README **

Cashmere is an open source Java grid software project of
the Computer Systems group of the Computer Science department of the
Faculty of Sciences at the Vrije Universiteit, Amsterdam, The Netherlands.
Cashmere extends Java with Cilk like primitives, that make it very convenient
for the programmer to write divide and conquer style programs. Unlike
manager/worker programs, divide-and-conquer algorithms operate by
recursively dividing a problem into smaller subproblems.
This recursive subdivision goes on until the remaining subproblem becomes
easily solvable with the aid of a many-core processor. After solving
subproblems, their results are recursively
recombined until the final solution is assembled.
Due to it's hierarchical nature, the divide-and-conquer model maps
cleanly to grid systems, which also tend to have an hierarchical
structure. Cashmere contains a efficient and simple load-balancing algorithm,
Cluster-aware Random Stealing (CRS), which outperforms existing load-balancing
strategies on multi-cluster systems.
In addition, Cashmere also provides efficient fault-tolerance,
malleability (e.g. the ability to cope with dynamically changing number
of processors) and migration in a way that is transparent to the application.

Cashmere is built on the Ibis Portability Layer (IPL), which is included in this
release. Some example Cashmere applications are provided in the "apps"
directory.

Cashmere is free software. See the file "LICENSE.txt" for copying permissions.

The javadoc of Cashmere is available in "javadoc/index.html".

Ibis has its own web-site: http://www.cs.vu.nl/ibis/.  There, you can
find more Ibis documentation, papers, application sources.

The current Cashmere source repository tree is accessible through GIT at
https://github.com/pieterhijma/cashmere.git

** Third party libraries included with Cashmere **

This product includes software developed by the Apache Software
Foundation (http://www.apache.org/).

The BCEL copyright notice lives in "notices/LICENSE.bcel.txt".  The
Log4J copyright notice lives in "notices/LICENSE.log4j.txt".  The
Commons copyright notice lives in notices/LICENSE.apache-2.0.txt".
The ASM copyright notice lives in "notices/LICENSE.asm.txt".

This product includes jstun, which is distributed with a dual license,
one of which is version 2.0 of the Apache license. It lives in
"notices/LICENSE.apache-2.0.txt".

This product includes the UPNP library from SuperBonBon Industries. Its
license lives in "notices/LICENSE.apache-2.0.txt".

This product includes the trilead SSH-2 library. Its license
lives in "notices/LICENSE.trilead.txt".

This product includes the JOCL library. Its license
lives in "notices/LICENSE.jocl.txt".

This product includes some of the JavaGAT libraries. Its license
lives in "notices/LICENSE.javagat.txt".

This product includes software developed by TouchGraph LLC
(http://www.touchgraph.com/). Its license lives in
"notices/LICENSE.TG.txt".
