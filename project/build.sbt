
lazy val jibPlugin = RootProject(uri("git://github.com/schmitch/sbt-jib#cac5354"))

lazy val root = (project in file("."))
  .settings(
    libraryDependencies ++= Seq(
      "io.skuber"  %% "skuber"     % "2.4.0",
      "com.typesafe.akka" %% "akka-stream" % "2.5.23",
      "com.typesafe.akka" %% "akka-actor" % "2.5.23"
    )
  )
  .dependsOn(jibPlugin)

