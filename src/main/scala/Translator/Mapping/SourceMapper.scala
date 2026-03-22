package Translator.Mapping

import java.io.{File, PrintWriter}
import scala.collection.mutable

class SourceMapper {
  // Mapping: label name -> source file line number
  private val mapping = mutable.LinkedHashMap[String, Int]()

  def add(label: String, line: Int): Unit = {
    mapping(label) = line
  }

  // Saves to *.map.json file in the same directory as the source file
  def saveMapping(targetFilePath: String): Unit = {
    val mapPath = targetFilePath.stripSuffix(".tvl") + ".map.json"

    val jsonEntries = mapping.map { case (label, line) =>
      s"""  "$label": $line"""
    }.mkString(",\n")
    val jsonContent = s"{\n$jsonEntries\n}"

    val writer = new PrintWriter(new File(mapPath))
    try {
      writer.write(jsonContent)
    } finally {
      writer.close()
    }
    println(s"[SourceMapper] Saved mapping to $mapPath")
  }
}
