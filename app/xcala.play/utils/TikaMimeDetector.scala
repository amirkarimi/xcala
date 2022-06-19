package xcala.play.utils

import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.apache.tika.config.TikaConfig
import org.apache.tika.io.TikaInputStream
import java.io.File
import java.io.FileInputStream


object TikaMimeDetector{

    private val tika = new TikaConfig()

    def guessMimeBasedOnFileContentAndName(file: File, fileName: String):String  = {
            // It is important to mix both fileName and content to guess the mime type
            // otherwise Tika might mixup some types like xml based files with each other
            val helpingMeta = new Metadata()
            helpingMeta.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName)

            tika
              .getDetector()
              .detect(TikaInputStream.get(new FileInputStream(file)), helpingMeta)
              .toString

        }



}