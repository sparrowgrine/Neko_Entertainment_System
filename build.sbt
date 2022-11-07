// See README.md for license details.

ThisBuild / scalaVersion     := "2.13.8"
ThisBuild / version          := "0.0.1"
ThisBuild / organization     := "ee.catgirl"
ThisBuild / transitiveClassifiers := Seq(Artifact.SourceClassifier)

resolvers ++= Resolver.sonatypeOssRepos("snapshots")
resolvers ++= Resolver.sonatypeOssRepos("releases")


lazy val root = (project in file("."))
  .settings(
    name := "Neko Entertainment System",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % "3.5.+",
      "edu.berkeley.cs" %% "chiseltest" % "0.5.+" % "test",
      "edu.berkeley.cs" %% "treadle" % "1.5.+",
      "org.scalatest" %% "scalatest" % "3.2.+" % "test",
      "org.scalacheck" %% "scalacheck" % "1.17.+"

    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-Ymacro-annotations",
      "-P:chiselplugin:genBundleElements",
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % "3.5.+" cross CrossVersion.full)
  )


