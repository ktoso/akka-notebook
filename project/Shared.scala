import Dependencies._
import sbt.Keys._
import sbt._

object Shared {
  lazy val sparkVersion = SettingKey[String]("x-spark-version")

  lazy val hadoopVersion = SettingKey[String]("x-hadoop-version")

  lazy val jets3tVersion = SettingKey[String]("x-jets3t-version")

  lazy val jlineDef = SettingKey[(String, String)]("x-jline-def")

  lazy val withHive = SettingKey[Boolean]("x-with-hive")
  
  lazy val withAkka = SettingKey[Boolean]("x-with-hive")

  lazy val sharedSettings: Seq[Def.Setting[_]] = Seq(
    publishArtifact in Test := false,
    publishMavenStyle := true,

    organization := MainProperties.organization,
    scalaVersion := defaultScalaVersion,
    sparkVersion := defaultSparkVersion,
    hadoopVersion := defaultHadoopVersion,
    jets3tVersion := defaultJets3tVersion,
    jlineDef := (if (defaultScalaVersion.startsWith("2.10")) {
      ("org.scala-lang", defaultScalaVersion)
    } else {
      ("jline", "2.12")
    }),
    withHive := false,
    withAkka := true,
    libraryDependencies += guava
  )

  val gisSettings: Seq[Def.Setting[_]] = Seq(
    libraryDependencies ++= geometryDeps
  )

  val repl: Seq[Def.Setting[_]] = Seq(
    libraryDependencies <+= (sparkVersion) { sv => sparkRepl(sv) }
  )

  val hive: Seq[Def.Setting[_]] = Seq(
    libraryDependencies <++= (withHive, sparkVersion) { (wh, sv) =>
      if (wh) List(sparkHive(sv)) else Nil
    }
  )

  val akka: Seq[Def.Setting[_]] = Seq(
    libraryDependencies ++= 
      (akkaDeps("2.5.4") ++ akkaHttpDeps("10.0.9"))
  )

  val yarnWebProxy: Seq[Def.Setting[_]] = Seq(
    libraryDependencies <++= (hadoopVersion) { (hv) =>
      if (!hv.startsWith("1")) List(yarnProxy(hv)) else Nil
    }
  )

  lazy val sparkSettings: Seq[Def.Setting[_]] = Seq(
    libraryDependencies <++= (scalaVersion, sparkVersion, hadoopVersion, jets3tVersion) { (v, sv, hv, jv) =>
      val jets3tVersion = sys.props.get("jets3t.version") match {
        case Some(jv) => jets3t(Some(jv), None)
        case _ => jets3t(None, Some(hv))
      }

      val jettyVersion = "8.1.14.v20131031"

      val libs = Seq(
        sparkCore(sv),
        sparkYarn(sv),
        sparkSQL(sv),
        hadoopClient(hv),
        jets3tVersion,
        commonsCodec
      ) ++ (
            if (!v.startsWith("2.10")) {
              // in 2.11
              //Boot.scala → HttpServer → eclipse
              // eclipse → provided boohooo :'-(
              Seq(
                "org.eclipse.jetty" % "jetty-http"         % jettyVersion,
                "org.eclipse.jetty" % "jetty-continuation" % jettyVersion,
                "org.eclipse.jetty" % "jetty-servlet"      % jettyVersion,
                "org.eclipse.jetty" % "jetty-util"         % jettyVersion,
                "org.eclipse.jetty" % "jetty-security"     % jettyVersion,
                "org.eclipse.jetty" % "jetty-plus"         % jettyVersion,
                "org.eclipse.jetty" % "jetty-server"       % jettyVersion
              )
            } else Nil
          ) ++ sparkMesos(sv)
      libs
    }
  ) ++ repl ++ hive ++ yarnWebProxy
}
