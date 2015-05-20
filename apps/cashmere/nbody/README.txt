The NBody algorithm simulates the evolution of a large set of bodies
under the influence of forces. It can be applied to various domains of
scientific computing, including but not limited to astrophysics, fluid
dynamics, electrostatics and even computer graphics. In our implementation,
the bodies represent planets and stars in the universe. The forces are
calculated using Newton's gravitational theory.

The evolution of the bodies is simulated in discrete time steps. Each of
these time steps corresponds to an iteration of the algorithm.
An iteration consists of, among others, a force calculation phase, in which
the force exerted on each body by all other bodies is computed, and
an update phase, in which the new position and velocity is computed
for each body. If
all pairwise interactions between two bodies are computed, the complexity
is O(N^2).

Recognized options are:
-it <num>
    specifies the number of iterations
-v
    make the program verbose
-no-v
    make the program unverbose
-viz
    turn on the vizualizer
-no-viz
    turn off the vizualizer
-min <num>
    specifies the number of bodies below which no jobs are spawned anymore,
    t.i., if a BodyTreeNode has more than <num> bodies beneath it, it is split
    up in separate jobs one more level.
-gpufactor <num>
    specifies the number of gpu jobs into which a leaf job is split.

In addition, a single integer parameter must be given: the number of bodies.
