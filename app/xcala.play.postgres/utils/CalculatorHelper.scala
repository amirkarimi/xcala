package xcala.play.postgres.utils

object CalculatorHelper {
  def roundUp(d: Double): Int = math.ceil(d).toInt

  def roundUp(bigDecimal: BigDecimal, scale: Int = 0): BigDecimal =
    bigDecimal.setScale(scale, BigDecimal.RoundingMode.UP)

}
