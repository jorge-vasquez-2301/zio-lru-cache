val scalaVer = "2.13.10"

val zioVersion = "2.0.13"

lazy val compileDependencies = Seq(
  "dev.zio" %% "zio"        % zioVersion,
  "dev.zio" %% "zio-macros" % zioVersion
) map (_ % Compile)

lazy val testDependencies = Seq(
  "dev.zio" %% "zio-test"     % zioVersion,
  "dev.zio" %% "zio-test-sbt" % zioVersion
) map (_ % Test)

lazy val settings = Seq(
  name := "zio-lru-cache",
  version := "2.0.0",
  scalaVersion := scalaVer,
  scalacOptions += "-Ymacro-annotations",
  libraryDependencies ++= compileDependencies ++ testDependencies,
  testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
)

lazy val root = (project in file("."))
  .settings(settings)
