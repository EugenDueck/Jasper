/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Method_Collection.java
 > Purpose:     Methods implemented by the class
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
package jasper;
import java.io.*;

/*=======================================================================
 = Class:         Method_Collection                                     =
 =                                                                      =
 = Desc:          Collection of Methods declared by the class           =
 =======================================================================*/
class Method_Collection {
   private int count;                  // number of methods declared
   private Method[] methods;           // methods declared by the class

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the method definitions from input stream      -
    -----------------------------------------------------------------------*/
   Method_Collection(DataInputStream ios, Pool_Collection pool) throws IOException {
      // get the number of methods
      count  = ios.readShort();

      // read in the methods
      methods = new Method[count];
      for (int i = 0; i < count; i++) methods[i] = new Method(ios, pool);
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the methods to the jasmin file                 -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) throws IOException {
      for (int i = 0; i < count; i++) methods[i].jasmin(out);
   }

   /*-----------------------------------------------------------------------
    - Method:        browseMethods                                         -
    -                                                                      -
    - Desc:          return array of strings representing the methods      -
    -----------------------------------------------------------------------*/
   String[] browseMethods(String thisClass){
      String[] retVal = new String[count];
      for (int i = 0; i < count; i++) retVal[i] = methods[i].browseMethod(thisClass);
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseFieldrefs                                       -
    -                                                                      -
    - Desc:          fields accessed - retrieve from Methods               -
    -----------------------------------------------------------------------*/
   String[][] browseFieldrefs() {
      String[][] retVal = new String[count][];
      for (int i = 0; i < count; i++) retVal[i] = methods[i].browseFieldrefs();
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseMethodrefs                                      -
    -                                                                      -
    - Desc:          methods accessed - retrieve from Methods              -
    -----------------------------------------------------------------------*/
   String[][] browseMethodrefs() {
      String[][] retVal = new String[count][];
      for (int i = 0; i < count; i++) retVal[i] = methods[i].browseMethodrefs();
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInterfaceMethodrefs                             -
    -                                                                      -
    - Desc:          interfaces accessed - retrieve from Methods           -
    -----------------------------------------------------------------------*/
   String[][] browseInterfaceMethodrefs() {
      String[][] retVal = new String[count][];
      for (int i = 0; i < count; i++) retVal[i] = methods[i].browseInterfaceMethodrefs();
      return retVal;
   }
}

/*=======================================================================
 = Class:         Method                                                =
 =                                                                      =
 = Desc:          Individual Method declared by the class               =
 =======================================================================*/
class Method {
   private Pool_Collection pool;             // constant pool table
   private int accessFlags;                  // method access flags
   private int nameIndex;                    // method name (index into constant pool table)
   private int descriptorIndex;              // return type (index into constant pool table)
   private Attribute_Collection attributes;  // method attributes: (Code, Exceptions, Synthetic, Deprecated)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the method definition from input stream       -
    -----------------------------------------------------------------------*/
   Method(DataInputStream ios, Pool_Collection pool) throws IOException {
      // save off the pool object for later reference
      this.pool = pool;

      // get the method access flags
      accessFlags = ios.readShort();

      // get the index for the method name
      nameIndex = ios.readShort();

      // get the index for the method return type descriptor
      descriptorIndex = ios.readShort();

      // read in the method attributes
      attributes = new Attribute_Collection(ios, pool);
   }

   /*-----------------------------------------------------------------------
    - Method:        accessString                                          -
    -                                                                      -
    - Desc:          build a string for the method access flags            -
    -----------------------------------------------------------------------*/
   private String accessString() {
      String s = "";
      if ((accessFlags & 0x0001) > 0) s += "public ";
      if ((accessFlags & 0x0002) > 0) s += "private ";
      if ((accessFlags & 0x0004) > 0) s += "protected ";
      if ((accessFlags & 0x0008) > 0) s += "static ";
      if ((accessFlags & 0x0010) > 0) s += "final ";
      if ((accessFlags & 0x0020) > 0) s += "synchronized ";
      if ((accessFlags & 0x0100) > 0) s += "native ";
      if ((accessFlags & 0x0400) > 0) s += "abstract ";
      return s;
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the method to the jasmin file                  -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) throws IOException {
      // output the .method directive
      out.println(ClassFile.pad(".method", ClassFile.SPACER) + accessString() +
         pool.toString(nameIndex) + pool.toString(descriptorIndex));

      // output the code,
      attributes.jasmin(out);

      // close out the .method directive
      out.println(ClassFile.pad(".end method", ClassFile.SPACER));
      out.println("");
   }

   /*-----------------------------------------------------------------------
    - Method:        browseMethod                                          -
    -                                                                      -
    - Desc:          return string representing the method                 -
    -----------------------------------------------------------------------*/
   String browseMethod(String thisClass) {
      String s = pool.browseDescriptor(descriptorIndex);
      String returnType = s.substring(0, s.indexOf('('));
      String functionParams = s.substring(s.indexOf('('));
      String functionName = pool.browseString(nameIndex);

      String retVal = attributes.browseDeprecated() + attributes.browseSynthetic() + accessString() +
         returnType + thisClass + "." + functionName + functionParams;

      String[] excVal = attributes.browseExceptions();
      if (excVal != null) {
         if (excVal.length > 0) {
            retVal += " throws " + excVal[0];
            for (int i = 1; i < excVal.length; i++) retVal += ", " + excVal[i];
         }
      }
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseFieldrefs                                       -
    -                                                                      -
    - Desc:          fields accessed - retrieve from Code Attribute        -
    -----------------------------------------------------------------------*/
   String[] browseFieldrefs() {
      return attributes.browseFieldrefs();
   }

   /*-----------------------------------------------------------------------
    - Method:        browseMethodrefs                                      -
    -                                                                      -
    - Desc:          methods accessed - retrieve from Code Attribute       -
    -----------------------------------------------------------------------*/
   String[] browseMethodrefs() {
      return attributes.browseMethodrefs();
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInterfaceMethodrefs                             -
    -                                                                      -
    - Desc:          interfaces accessed - retrieve from Code Attribute    -
    -----------------------------------------------------------------------*/
   String[] browseInterfaceMethodrefs() {
      return attributes.browseInterfaceMethodrefs();
   }
}
