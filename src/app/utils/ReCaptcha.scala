package xcala.play.utils

import java.util.Properties
import play.api.Play.current
import play.api.Logger
import play.twirl.api.Html
import net.tanesha.recaptcha.ReCaptchaFactory
import net.tanesha.recaptcha.ReCaptchaImpl
import net.tanesha.recaptcha.ReCaptchaResponse
 
object ReCaptcha {
 
  def publicKey: String = {
    current.configuration.getString("recaptcha.publickey").get
  }
  def privateKey: String = {
    current.configuration.getString("recaptcha.privatekey").get
  }
  def render = Html {
    ReCaptchaFactory.newReCaptcha(publicKey, privateKey, false).createRecaptchaHtml(null, new Properties)
  }

  def check(addr: String, challenge: String, response: String): Boolean = {
    val reCaptcha = new ReCaptchaImpl()
    reCaptcha.setPrivateKey(privateKey)
    val reCaptchaResponse = reCaptcha.checkAnswer(addr, challenge, response)
    reCaptchaResponse.isValid()
  }
}