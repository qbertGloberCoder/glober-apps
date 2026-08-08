# skywatcher/app Module Catalog

The `skywatcher/app` module is the executable entry point of the Skywatcher
application suite. Its Maven artifact is `me.qbert.skywatch:sw-app` (version
1.0.0, packaging `jar`), a child of the `me.qbert.skywatch:skywatcher` parent
POM. The module's package root is `me.qbert.skywatch`.

Per its `pom.xml`, the module declares dependencies on two sibling modules
(`me.qbert.skywatch:ga-base` and `me.qbert.skywatch:sw-base`, both 1.3.0) plus
several third-party libraries (Gson, AppleJavaExtensions, opencsv, a
TwelveMonkeys-derived TIFF ImageIO plugin) and JUnit 3.8.1 for tests. The
`maven-jar-plugin` and `maven-assembly-plugin` configurations both designate
`me.qbert.skywatch.Main` as the runnable main class, confirming this module's
role as the application's launcher/bootstrap jar.

As of this writing, the module's source tree contains a single Java file and
no test sources at all — the declared JUnit dependency is currently unused.

## Package `me.qbert.skywatch`

### `me.qbert.skywatch.Main` (class)

**Responsibility:** Application entry point. Its `main(String[] args)` method
currently does nothing beyond printing `"Hello"` to standard output — it is a
placeholder/stub and does not yet wire up any real application logic,
argument parsing, or UI startup.

**Dependencies:**
- No dependencies on other classes, the sibling `ga-base` or `sw-base`
  modules, or any third-party library declared in the pom (Gson, opencsv,
  AppleJavaExtensions, imageio-tiff are all unused by this class).
- JDK: only implicit use of `System.out` (`java.lang.System`), no explicit
  imports at all.

**Platform-specific imports:** None. The file has zero `import` statements.

## Platform-Specific Imports

None found. The module's only source file, `me.qbert.skywatch.Main`, contains
no `import` statements whatsoever (it uses only `System.out.println`), so
there are no references to `java.awt.*`, `javax.swing.*`,
`java.awt.image.*`, `java.awt.geom.*`, `javafx.*`, or any other GUI toolkit
package anywhere in this module's source tree.
