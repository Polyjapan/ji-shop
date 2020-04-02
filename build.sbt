name := "jishop"
enablePlugins(PlayScala, DebianPlugin, JDebPackaging, SystemdPlugin)

version := "1.0"

maintainer in Linux := "Louis Vialar <louis.vialar@japan-impact.ch>"

packageSummary in Linux := "Scala Backend for Japan Impact Shop"
packageDescription := "Scala Backend for Japan Impact Shop"
debianPackageDependencies := Seq("java8-runtime-headless")


lazy val `jishop` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
resolvers += Resolver.jcenterRepo
resolvers += Resolver.mavenLocal

scalaVersion := "2.13.1"

libraryDependencies ++= Seq( ehcache , ws , specs2 % Test , guice )
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "5.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0",
  "mysql" % "mysql-connector-java" % "5.1.34",
  "org.mindrot" % "jbcrypt" % "0.3m",
  "com.pauldijou" %% "jwt-play" % "4.2.0",
  "com.hhandoko" %% "play27-scala-pdf" % "4.2.0"
)

libraryDependencies += "com.typesafe.play" %% "play-mailer" % "8.0.0"
libraryDependencies += "com.typesafe.play" %% "play-mailer-guice" % "8.0.0"
libraryDependencies += "net.sf.barcode4j" % "barcode4j" % "2.1"
libraryDependencies += "ch.japanimpact" %% "jiauthframework" % "0.3-SNAPSHOT"

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
