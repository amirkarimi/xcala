package xcala.play.utils

import java.io.{File, FileInputStream}

import org.apache.tika.config.TikaConfig
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.{Metadata, TikaCoreProperties}

object TikaMimeDetector {

  private val tika: TikaConfig = new TikaConfig()

  def guessMimeBasedOnFileContentAndName(file: File, fileName: String): String = {
    // It is important to mix both fileName and content to guess the mime type
    // otherwise Tika might mixup some types like xml based files with each other
    val helpingMeta = new Metadata()
    helpingMeta.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName)
    val fis = new FileInputStream(file)
    val result =
      tika
        .getDetector
        .detect(TikaInputStream.get(fis), helpingMeta)
        .toString

    fis.close()
    result

  }

  def guessMimeBasedOnFileContent(byteArray: Array[Byte]): String = {

    val helpingMeta = new Metadata()

    tika
      .getDetector
      .detect(TikaInputStream.get(byteArray), helpingMeta)
      .toString

  }

}
