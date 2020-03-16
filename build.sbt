val scalaVer = "2.13.1"

val zioVersion = "1.0.0-RC18-2"

lazy val compileDependencies = Seq(
  "dev.zio" %% "zio" % zioVersion
) map (_ % Compile)

lazy val testDependencies = Seq(
  "dev.zio" %% "zio-test"     % zioVersion,
  "dev.zio" %% "zio-test-sbt" % zioVersion
) map (_ % Test)

lazy val settings = Seq(
  name := "zio-lru-cache",
  version := "1.0.0",
  scalaVersion := scalaVer,
  libraryDependencies ++= compileDependencies ++ testDependencies,
  testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
)

lazy val root = (project in file("."))
  .settings(settings)
