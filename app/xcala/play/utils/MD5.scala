package xcala.play.utils

import java.security.MessageDigest

object MD5 {

  def hash(input: String): String = {
    val md = MessageDigest.getInstance("MD5")
    md.update(input.getBytes)
    val hash   = BigInt(1, md.digest())
    val result = hash.toString(16)
    val prefix = if (result.length < 32) "0" else ""
    prefix + result
  }

}
