package spark

import spark.util.{CommandLineOptions, Timestamp}
import spark.util.CommandLineOptions.Opt
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._

/** 
 * Joins7 - Perform joins of datasets.
 */
object Joins7 {
  def main(args: Array[String]) = {

    /** The "dictionary" of book abbreviations to full names */
    val abbrevsFile = "data/abbrevs-to-names.tsv"
    val abbrevs = Opt(
      name   = "abbreviations",
      value  = abbrevsFile,
      help   = s"-a | --abbreviations  path The dictionary of book abbreviations to full names (default: $abbrevsFile)",
      parser = {
        case ("-a" | "--abbreviations") +: path +: tail => (("abbreviations", path), tail)
      })

    val options = CommandLineOptions(
      this.getClass.getSimpleName,
      CommandLineOptions.inputPath("data/kjvdat.txt"),
      abbrevs,
      CommandLineOptions.outputPath("output/kjv-joins"),
      CommandLineOptions.master("local"))

    val argz = options(args.toList)

    val sc = new SparkContext(argz("master").toString, "Joins (7)")
    try {
      // Load one of the religious texts, don't convert each line to lower case
      // this time, then extract the fields in the "book|chapter|verse|text" format 
      // used for each line, creating an RDD. However, note that the logic used
      // to split the line will work reliably even if the delimiters aren't present!
      // Note also the output nested tuple. Joins only work for RDDs of 
      // (key,value) tuples
      val input = sc.textFile(argz("input-path").toString)
        .map { line => 
          val ary = line.split("\\s*\\|\\s*")
          (ary(0), (ary(1), ary(2), ary(3)))
        }

      // The abbreviations file is tab separated, but we only want to split
      // on the first space (in the unlikely case there are embedded tabs 
      // in the names!)
      val abbrevs = sc.textFile(argz("abbreviations").toString)
        .map{ line => 
          val ary = line.split("\\s+", 2)
          (ary(0), ary(1).trim)  // I've noticed trailing whitespace...
        }

      // Cache both RDDs in memory for fast, repeated access.
      input.cache
      abbrevs.cache

      // Join on the key, the first field in the tuples; the book abbreviation.

      val verses = input.join(abbrevs)
      
      if (input.count != verses.count) {
        println(s"input count, ${input.count}, doesn't match output count, ${verses.count}")
      }

      // Project out the flattened data we want:
      //   fullBookName|chapter|verse|text

      val verses2 = verses.map {
        // Drop the key - the abbreviated book name
        case (_, ((chapter, verse, text), fullBookName)) => 
          (fullBookName, chapter, verse, text)
      }

      val now = Timestamp.now()
      val outpath = s"${argz("output-path")}-$now"
      println(s"Writing output to: $outpath")
      verses2.saveAsTextFile(outpath)
    } finally {
      sc.stop()
    }

    // Exercise: Try different sacred text files (English language).
  }
}