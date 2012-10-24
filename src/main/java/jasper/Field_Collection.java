/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Field_Collection.java
 > Purpose:     Fields declared by the class - Class properties
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
package jasper;
import java.io.*;

/*=======================================================================
 = Class:         Field_Collection                                      =
 =                                                                      =
 = Desc:          Collection of Fields declared by the class            =
 =======================================================================*/
class Field_Collection {
   private int count;                  // number of fields declared
   private Field[] fields;             // fields declared by the class - class properties

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the field definitions from input stream       -
    -----------------------------------------------------------------------*/
   Field_Collection(DataInputStream ios, Pool_Collection pool) throws IOException {
      // get the number of fields
      count  = ios.readShort();

      // read in the fields
      fields = new Field[count];
      for (int i = 0; i < count; i++) fields[i] = new Field(ios, pool);
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the .field directives to the jasmin file       -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) throws IOException {
      for (int i = 0; i < count; i++) fields[i].jasmin(out);
   }

   /*-----------------------------------------------------------------------
    - Method:        browseFields                                          -
    -                                                                      -
    - Desc:          return array of strings representing the fields       -
    -----------------------------------------------------------------------*/
   String[] browseFields(String thisClass){
      String[] retVal = new String[count];
      for (int i = 0; i < count; i++) {
         retVal[i] = fields[i].browseField(thisClass);
      }
      return retVal;
   }
}

/*=======================================================================
 = Class:         Field                                                 =
 =                                                                      =
 = Desc:          Individual Field declared by the class                =
 =======================================================================*/
class Field {
   private Pool_Collection pool;             // constant pool table
   private int accessFlags;                  // field access flags
   private int nameIndex;                    // field name (index into constant pool table)
   private int descriptorIndex;              // field type (index into constant pool table)
   private Attribute_Collection attributes;  // field attributes: (ConstantValue, Synthetic, Deprecated)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the field definition from input stream        -
    -----------------------------------------------------------------------*/
   Field(DataInputStream ios, Pool_Collection pool) throws IOException {
      // save off the pool object for later reference
      this.pool = pool;

      // get the field access flags
      accessFlags = ios.readShort();

      // get the index for the field name
      nameIndex = ios.readShort();

      // get the index for the field type descriptor
      descriptorIndex = ios.readShort();

      // read in the field attributes
      attributes = new Attribute_Collection(ios, pool);
   }

   /*-----------------------------------------------------------------------
    - Method:        accessString                                          -
    -                                                                      -
    - Desc:          build a string for the field access flags             -
    -----------------------------------------------------------------------*/
   private String accessString() {
      String s = "";
      if ((accessFlags & 0x0001) > 0) s += "public ";
      if ((accessFlags & 0x0002) > 0) s += "private ";
      if ((accessFlags & 0x0004) > 0) s += "protected ";
      if ((accessFlags & 0x0008) > 0) s += "static ";
      if ((accessFlags & 0x0010) > 0) s += "final ";
      if ((accessFlags & 0x0040) > 0) s += "volatile ";
      if ((accessFlags & 0x0080) > 0) s += "transient ";
      return s;
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the .field directive to the jasmin file        -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) throws IOException {
      out.println(ClassFile.pad(".field", ClassFile.SPACER) + accessString() +
         pool.toString(nameIndex) + " " + pool.toString(descriptorIndex) +
         attributes.jasminConstantValue());
   }

   /*-----------------------------------------------------------------------
    - Method:        browseField                                           -
    -                                                                      -
    - Desc:          return string representing the field                  -
    -----------------------------------------------------------------------*/
   String browseField(String thisClass) {
      return attributes.browseDeprecated() + attributes.browseSynthetic() + accessString() +
         pool.browseDescriptor(descriptorIndex) + " "  + thisClass + "." +
         pool.browseString(nameIndex) + attributes.browseConstantValue();
   }
}
