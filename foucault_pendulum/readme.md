# Foucault Pendulum 3D

A JavaFX 3D simulation of a Foucault Pendulum.  
Visualizes pendulum precession at different latitudes with interactive 3D controls.

---

## Overview

This project simulates the motion of a Foucault Pendulum in 3D using JavaFX.  
It allows users to view precession and pendulum motion from multiple perspectives.

---

## Prerequisites

- **Java Development Kit (JDK) 21** 
- **Apache Maven 3.9.10** or later  
- Internet access to download dependencies on first build  

Check your environment:

```console
java -version
mvn -v
```

## Running

Running the application from the sources can be done with the mvn command.

From the project root directory (where this readme.md file resides in such as `<checkout base>/foucault_pendulum/`, not the Git checkout root):

```console
mvn javafx:run
```

## Build a JAR binary

Building a JAR binary in this case will not produce a JAR file that can be run on its own. It uses JavaFX which requires binary components not provided directly with the Java runtime.

```console
mvn clean package
```

The output JAR will be located at:

`target/foucault-pendulum-<version>-jar-with-dependencies.jar`

Note: the version is defined in the pom.xml file and will change based on the release. 

Run it with:

```console
`java -jar target/foucault-pendulum-<version>-jar-with-dependencies.jar`
```

Note: JavaFX modules must be on the module path. If you see runtime errors, like this:

```text
Error: JavaFX runtime components are missing, and are required to run this application
```

try:

```console
`java --module-path /path/to/javafx-sdk-21/lib --add-modules javafx.controls,javafx.graphics -jar target/foucault-pendulum-<version>-jar-with-dependencies.jar`
```

Replace /path/to/javafx-sdk-21/lib with your JavaFX SDK location.

## Build Platform-Specific Binaries

Run the command

```console
mvn -Pmodular clean javafx:jlink
```

Note: You will need to run this command on each target operating system to make the appropriate binaries for that platform.


## Attributions

```text
Image attribution:

“Brosen windrose”
© Brosen~commonswiki, March 2006.
Licensed under Creative Commons Attribution 2.5 Generic (CC BY 2.5).
https://creativecommons.org/licenses/by/2.5/

Source:
https://commons.wikimedia.org/wiki/File:Brosen_windrose.svg

PNG downloaded from Wikimedia Commons at 2048 × 2048 pixels.
No modifications were made.
```

