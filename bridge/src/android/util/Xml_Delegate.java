package android.util;

import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class Xml_Delegate {
  static XmlPullParser newPullParser() {
    try {
      KXmlParser parser = new KXmlParser();
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      return parser;
    } catch (XmlPullParserException e) {
      throw new AssertionError(e);
    }
  }
}
