package xcala.play.utils

object ColorUtils {
  def lightenDarkenColor(color: String, amt: Int) = {
      
      val (usePound, rawColor) = color.startsWith("#") match {
        case true => (true, color.substring(1))
        case _ => (false, color)
      }
   
      val num = Integer.parseInt(rawColor, 16);
   
      var r = (num >> 16) + amt;
   
      if (r > 255) r = 255;
      else if  (r < 0) r = 0;
   
      var b = ((num >> 8) & 0x00FF) + amt;
   
      if (b > 255) b = 255;
      else if  (b < 0) b = 0;
   
      var g = (num & 0x0000FF) + amt;
   
      if (g > 255) g = 255;
      else if (g < 0) g = 0;
   
      (if(usePound)"#"else"") + "%06X".format(g | (b << 8) | (r << 16));
  }
}