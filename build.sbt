organization := "edu.berkeley.cs"
version := "1.0"
name := "tcam"

scalaVersion := "2.13.16"

// Add Chisel dependencies
libraryDependencies ++= Seq(
  "org.chipsalliance" %% "chisel" % "6.7.0",
  "org.chipsalliance" %% "chiseltest" % "6.0.0" % "test"
)

// Add RocketChip dependencies
libraryDependencies ++= Seq(
  "org.chipsalliance" %% "rocket-chip" % "1.9.0"
)

// Add Chipyard dependencies
libraryDependencies ++= Seq(
  "edu.berkeley.cs" %% "chipyard" % "1.6"
)

// Chisel compiler plugin
addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % "6.7.0" cross CrossVersion.full)

// Scalac options
scalacOptions ++= Seq(
  "-Xsource:2.13",
  "-language:reflectiveCalls",
  "-deprecation",
  "-feature",
  "-Xcheckinit",
  "-P:chiselplugin:genBundleElements"
) 