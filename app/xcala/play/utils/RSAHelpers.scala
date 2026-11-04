package xcala.play.utils

import java.io.StringReader
import java.math.BigInteger
import java.security.{KeyFactory, PrivateKey, Signature}
import java.security.spec.RSAPrivateKeySpec
import javax.xml.parsers.DocumentBuilderFactory

import org.apache.commons.codec.binary.Base64
import org.w3c.dom.Document
import org.xml.sax.InputSource

object RSAHelpers {
  val b64: Base64 = new Base64()

  def sign(privateKey: PrivateKey, data: Array[Byte]): Array[Byte] = {
    val dsa = Signature.getInstance("SHA1withRSA")
    dsa.initSign(privateKey)
    dsa.update(data)
    dsa.sign
  }

  def getPrivateKeyFromXML(xml: String): PrivateKey = {
    val doc = loadXMLFromString(xml)

    val pkeyspec = new RSAPrivateKeySpec(
      new BigInteger(1, b64.decode(doc.getElementsByTagName("Modulus").item(0).getTextContent)),
      new BigInteger(1, b64.decode(doc.getElementsByTagName("D").item(0).getTextContent))
    )

    val fact = KeyFactory.getInstance("RSA")
    fact.generatePrivate(pkeyspec)
  }

  private def loadXMLFromString(xml: String): Document = {
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val is      = new InputSource(new StringReader(xml))
    builder.parse(is)
  }

}
