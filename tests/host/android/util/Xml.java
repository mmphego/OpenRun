package android.util;

/** Test-only adapter; production uses Android's XML pull parser. */
public final class Xml {
  public static org.xmlpull.v1.XmlPullParser newPullParser() {
    return new org.kxml2.io.KXmlParser();
  }
}
