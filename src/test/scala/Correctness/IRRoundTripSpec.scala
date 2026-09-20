package Correctness

import Translator.Frontend.TVIRReader
import org.scalatest.funsuite.AnyFunSuite

import java.io.File
import java.nio.file.{Files, Paths}

/**
 * Round-trip suite: for every golden .tvir file, reading it back through TVIRReader
 * and re-dumping must be byte-identical. Acts as a format-drift canary: any change
 * to FrontendResult.toTVIRString or TVIRReader that breaks their inverse relation
 * (or desynchronizes them from the goldens) fails here.
 */
class IRRoundTripSpec extends AnyFunSuite {

  def listTVIRFiles(dir: File): Array[File] = {
    val these = dir.listFiles
    if (these == null) Array.empty
    else these.filter(_.isFile).filter(_.getName.endsWith(".tvir")) ++ these.filter(_.isDirectory).flatMap(listTVIRFiles)
  }

  val goldensDir = Paths.get("src", "test", "tvir", "examples").toFile
  val tvirFiles = listTVIRFiles(goldensDir)

  if (tvirFiles.isEmpty) fail(s"No golden .tvir files found in $goldensDir")

  tvirFiles.foreach { tvirFile =>
    test(s"Round-trip: ${tvirFile.getName}") {
      val content = Files.readString(tvirFile.toPath)
      val redumped = TVIRReader.fromTVIRString(content).toTVIRString
      assert(redumped == content,
        s"Round-trip mismatch for ${tvirFile.getPath}: reading and re-dumping the golden .tvir changed it")
    }
  }
}
