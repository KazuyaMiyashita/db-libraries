import Dependencies._

ThisBuild / scalaVersion     := "2.13.3"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val commonSettings = Seq(
  scalacOptions ++= "-deprecation" :: "-feature" :: "-Xlint" :: Nil,
  scalacOptions in (Compile, console) ~= {_.filterNot(_ == "-Xlint")},
  scalafmtOnCompile := true
)

lazy val root = (project in file("."))
  .settings(
    name := "db-libraries",
    libraryDependencies += "org.playframework.anorm" %% "anorm" % "2.6.7",
    libraryDependencies += "com.typesafe.slick" %% "slick" % "3.3.3",
    libraryDependencies += "org.scalikejdbc" %% "scalikejdbc" % "3.5.0",
    libraryDependencies += "org.tpolecat" %% "doobie-core" % "0.9.2",
    libraryDependencies += "io.getquill" %% "quill-core" % "3.5.3",

    libraryDependencies += "mysql" % "mysql-connector-java" % "8.0.21",

    libraryDependencies += scalaTest % Test
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
