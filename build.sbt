name := "jishop"
enablePlugins(PlayScala, DebianPlugin, JDebPackaging, SystemdPlugin, DockerPlugin)

version := "1.0"

maintainer in Linux := "Louis Vialar <louis.vialar@japan-impact.ch>"

packageSummary in Linux := "Scala Backend for Japan Impact Shop"
packageDescription := "Scala Backend for Japan Impact Shop"
debianPackageDependencies := Seq("java8-runtime-headless")

javaOptions in Universal ++= Seq(
  // Provide the PID file
  s"-Dpidfile.path=/dev/null",
  // s"-Dpidfile.path=/run/${packageName.value}/play.pid",

  // Set the configuration to the production file
  s"-Dconfig.file=/usr/share/${packageName.value}/conf/production.conf",

  // Apply DB evolutions automatically
  "-DapplyEvolutions.default=true"
)

dockerExposedPorts in Docker := Seq(80)

lazy val `jishop` = (project in file(".")).enablePlugins(PlayScala)

//resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
resolvers += Resolver.jcenterRepo
resolvers += Resolver.mavenLocal

scalaVersion := "2.13.1"

libraryDependencies ++= Seq( ehcache , ws , specs2 % Test , guice , jdbc , evolutions )
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "5.0.0",
  "mysql" % "mysql-connector-java" % "5.1.34",
  "org.mindrot" % "jbcrypt" % "0.3m",
  "com.pauldijou" %% "jwt-play" % "4.2.0",
  "com.hhandoko" %% "play27-scala-pdf" % "4.2.0",
  "org.playframework.anorm" %% "anorm" % "2.6.10"
)

libraryDependencies += "com.typesafe.play" %% "play-mailer" % "8.0.0"
libraryDependencies += "com.typesafe.play" %% "play-mailer-guice" % "8.0.0"
libraryDependencies += "net.sf.barcode4j" % "barcode4j" % "2.1"
libraryDependencies += "ch.japanimpact" %% "jiauthframework" % "2.0-SNAPSHOT"
libraryDependencies += "ch.japanimpact" %% "ji-uploads-api" % "1.0"

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

dockerUsername := Some("polyjapan")
