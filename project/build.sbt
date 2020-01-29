
lazy val jibPlugin = RootProject(uri("git://github.com/schmitch/sbt-jib#cac5354"))

lazy val root = (project in file("."))
  .settings(
    libraryDependencies ++= Seq(
      "io.skuber"  %% "skuber"     % "2.4.0",
    )
  )
  .dependsOn(jibPlugin)

