package xcala.play.utils

import scala.xml.Elem
import scala.xml.Node

object SoapHelper {

  def wrap(xml: String): String = {
    s"""<?xml version="1.0" encoding="UTF-8" standalone="no"?>
    <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
      <SOAP-ENV:Body>
        $xml
      </SOAP-ENV:Body>
    </SOAP-ENV:Envelope>"""
  }

  def unwrap(elem: Elem): Node = {
    (elem \\ "Envelope" \\ "Body").flatMap(_.child).head
  }

}
