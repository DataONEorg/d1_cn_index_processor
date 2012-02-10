package org.dataone.cn.indexer.convert;

/**
 * Basic value check for longitude, assumed to be decimal degrees.
 * 
 * @author vieglais
 * 
 */
public class SolrLongitudeConverter implements IConverter {

  /**
   * Given a string representation of a potential longitude value, ensure it is
   * a valid floating point number and check the range.
   * 
   * TODO: add converters for different representations if necessary, such as 
   *       d m s
   * 
   * @param data
   *          The value to convert
   * 
   * @return String that is a valid representation of longitude, in the range
   *         -180.0 - 180.0
   */
  public String convert(String data) {
    double v = 0.0d;
    try {
      v = Double.parseDouble(data.trim());
      if (v < -180.0d) {
        throw new NumberFormatException("Longitude < -180.0");
      }
      if (v > 360.0d) {
        throw new NumberFormatException("Latitude > 360.0");
      }
      if (v > 180.0d) {
        v = 360.0d - v;
      }
    } catch (NumberFormatException e) {
      return null;
    }
    return Double.toString(v);
  }
}
