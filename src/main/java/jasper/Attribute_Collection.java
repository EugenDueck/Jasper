/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Attribute_Collection.java
 > Purpose:     Collection of attributes for class, fields, methods, and code
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
package jasper;
import java.io.*;
import java.lang.reflect.*;

/*
 * Note: You may want to break these classes into seperate source files.  The java compiler issues
 *       a warning:
 *             class jasper.Attribute is defined in Attribute_Collection.java.
 *             Because it is used outside of its source file, it should be defined
 *             in a file called "Attribute.java".
 *             class Code_Collection extends Attribute {
 *       This is due to the fact that the Code class extends the Attribute class of this file.
 *       Under JDK1.2, the compilation works (other than issuing a warning) as long as you compile
 *       the Attribute.java file prior to compiling the Code_Collection.java file.
 */

/*=======================================================================
 = Class:         Attribute_Collection                                  =
 =                                                                      =
 = Desc:          attributes for field, methods, code, or class         =
 =                                                                      =
 =                hierarchy of attribute collections                    =
 =                   .Field                                             =
 =                      ConstantValue                                   =
 =                      Synthetic                                       =
 =                      Deprecated                                      =
 =                   .Method                                            =
 =                      .Code_Collection (Code)                         =
 =                         LineNumberTable                              =
 =                         LocalVariableTable                           =
 =                      Exceptions                                      =
 =                      Synthetic                                       =
 =                      Deprecated                                      =
 =                   .ClassFile                                         =
 =                      SourceFile                                      =
 =                      InnerClasses                                    =
 =                      Synthetic                                       =
 =                      Deprecated                                      =
 =======================================================================*/
class Attribute_Collection {
   private int count;                  // number of attributes
   private Attribute[] attributes;     // attributes declared in this section (field, class, or method)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the attribute definitions from input stream   -
    -----------------------------------------------------------------------*/
   Attribute_Collection(DataInputStream ios, Pool_Collection pool) throws IOException {
      // get the number of methods
      count  = ios.readShort();

      // read in the attributes - use reflection to dispatch to the appropriate class
      attributes = new Attribute[count];
      for (int i = 0; i < count; i++) {
         // get the attribute name (index into constant pool table)
         int attributeIndex = ios.readShort();
         try {
            Constructor myConstructor;

            // get subclass that handles the attribute
            Class newClass = getDispatch(pool.toString(attributeIndex));
            try {
               // for some reason this technique is not working in JDK1.2?
               myConstructor = newClass.getConstructor(new Class[]
                  {ios.getClass(), pool.getClass(), int.class});
            } catch (NoSuchMethodException e) {
               // since there is only one constructor, we can use this technique if above fails
               myConstructor = newClass.getDeclaredConstructors()[0];
            }

            // read in the attribute
            attributes[i] = (Attribute)myConstructor.newInstance(new Object[]
               {ios, pool, new Integer(attributeIndex)});

         } catch (InstantiationException e) {
            throw new IOException("InstantiationException");

         } catch (IllegalAccessException e) {
            throw new IOException("IllegalAccessException");

         } catch (InvocationTargetException e) {
            throw new IOException("InvocationTargetException");
         }
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        getDispatch                                           -
    -                                                                      -
    - Desc:          get the class name that handles the attribute         -
    -----------------------------------------------------------------------*/
   static Class getDispatch(String s) {
      // look for a match of the attribute name in the static array
      for (int i = 1; i < dispatch.length; i++) {
         if (s.equals((String)dispatch[i][0])) return (Class)dispatch[i][1];
      }

      // if dispatch class not found then use Attribute_Unknown class
      return (Class)dispatch[0][1];
   }

   /*-----------------------------------------------------------------------
    - Field:         dispatch                                              -
    -                                                                      -
    - Desc:          array used to dispatch for object instantiation       -
    -                   element[i][0] = attribute name in class file       -
    -                   element[i][1] = class name that handles attribute  -
    -----------------------------------------------------------------------*/
   private static Object[][] dispatch = {
      {"",                   Attribute_Unknown.class},
      {"Code",               Code_Collection.class},
      {"ConstantValue",      Attribute_ConstantValue.class},
      {"Deprecated",         Attribute_Deprecated.class},
      {"Exceptions",         Attribute_Exceptions.class},
      {"InnerClasses",       Attribute_InnerClasses.class},
      {"LineNumberTable",    Attribute_LineNumberTable.class},
      {"LocalVariableTable", Attribute_LocalVariableTable.class},
      {"SourceFile",         Attribute_SourceFile.class},
      {"Synthetic",          Attribute_Synthetic.class}
   };

   /*-----------------------------------------------------------------------
    - Method:        getLabel                                              -
    -                                                                      -
    - Desc:          ask attributes if they need any LABELs to be printed  -
    -----------------------------------------------------------------------*/
   void getLabel(Code_Collection code) {
      for (int i = 0; i < count; i++) attributes[i].getLabel(code);
   }

   /*-----------------------------------------------------------------------
    - Method:        getLineNumberTable                                    -
    -                                                                      -
    - Desc:          get line numbers from LineNumberTable attribute       -
    -----------------------------------------------------------------------*/
   int[] getLineNumberTable(int pc) {
      for (int i = 0; i < count; i++) {
         int[] retVal = attributes[i].getLineNumberTable(pc);
         if (retVal != null) return retVal;
      }
      return null;
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output to jasmin file (action specific to attribute)  -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) throws IOException {
      for (int i = 0; i < count; i++) attributes[i].jasmin(out);
   }

   /*-----------------------------------------------------------------------
    - Method:        jasminConstantValue                                   -
    -                                                                      -
    - Desc:          get constant value from ConstantValue attribute       -
    -----------------------------------------------------------------------*/
   String jasminConstantValue() {
      String retVal = "";
      for (int i = 0; i < count; i++) retVal += attributes[i].jasminConstantValue();
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        jasminSourceFile                                      -
    -                                                                      -
    - Desc:          get source file name from SourceFile attribute        -
    -----------------------------------------------------------------------*/
   String jasminSourceFile() {
      String retVal = "";
      for (int i = 0; i < count; i++) retVal += attributes[i].jasminSourceFile();
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseSourceFile                                      -
    -                                                                      -
    - Desc:          get source file name from SourceFile attribute        -
    -----------------------------------------------------------------------*/
   String browseSourceFile() {
      String retVal = "";
      for (int i = 0; i < count; i++) retVal += attributes[i].browseSourceFile();
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseDeprecated                                      -
    -                                                                      -
    - Desc:          get 'deprecated' string from Deprecated attribute     -
    -----------------------------------------------------------------------*/
   String browseDeprecated() {
      String retVal = "";
      for (int i = 0; i < count; i++) retVal += attributes[i].browseDeprecated();
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseSynthetic                                       -
    -                                                                      -
    - Desc:          get 'synthetic' string from Synthetic attribute       -
    -----------------------------------------------------------------------*/
   String browseSynthetic() {
      String retVal = "";
      for (int i = 0; i < count; i++) retVal += attributes[i].browseSynthetic();
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseConstantValue                                   -
    -                                                                      -
    - Desc:          get constant value from ConstantValue attribute       -
    -----------------------------------------------------------------------*/
   String browseConstantValue() {
      String retVal = "";
      for (int i = 0; i < count; i++) retVal += attributes[i].browseConstantValue();
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseExceptions                                      -
    -                                                                      -
    - Desc:          get exceptions thrown from Exceptions attribute       -
    -----------------------------------------------------------------------*/
   String[] browseExceptions() {
      for (int i = 0; i < count; i++) {
         String[] retVal = attributes[i].browseExceptions();
         if (retVal != null) return retVal;
      }
      return new String[0];
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInnerClasses                                    -
    -                                                                      -
    - Desc:          get inner classes from InnerClasses attribute         -
    -----------------------------------------------------------------------*/
   String[][] browseInnerClasses() {
      for (int i = 0; i < count; i++) {
         String[][] retVal = attributes[i].browseInnerClasses();
         if (retVal != null) return retVal;
      }
      return new String[0][0];
   }

   /*-----------------------------------------------------------------------
    - Method:        browseFieldrefs                                       -
    -                                                                      -
    - Desc:          get fields referenced from Code attribute             -
    -----------------------------------------------------------------------*/
   String[] browseFieldrefs() {
      for (int i = 0; i < count; i++) {
         String[] retVal = attributes[i].browseFieldrefs();
         if (retVal != null) return retVal;
      }
      return new String[0];
   }

   /*-----------------------------------------------------------------------
    - Method:        browseMethodrefs                                      -
    -                                                                      -
    - Desc:          get methods referenced from Code attribute            -
    -----------------------------------------------------------------------*/
   String[] browseMethodrefs() {
      for (int i = 0; i < count; i++) {
         String[] retVal = attributes[i].browseMethodrefs();
         if (retVal != null) return retVal;
      }
      return new String[0];
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInterfaceMethodrefs                             -
    -                                                                      -
    - Desc:          get interface methods referenced from Code attribute  -
    -----------------------------------------------------------------------*/
   String[] browseInterfaceMethodrefs() {
      for (int i = 0; i < count; i++) {
         String[] retVal = attributes[i].browseInterfaceMethodrefs();
         if (retVal != null) return retVal;
      }
      return new String[0];
   }
}

/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Attribute.java
 > Purpose:     Base abstract class for all attributes
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
//package jasper;
//import java.io.*;

/*=======================================================================
 = Class:         Attribute                                             =
 =                                                                      =
 = Desc:          abstract class for attributes (polymorphic behavior)  =
 =======================================================================*/
abstract class Attribute {
   protected Pool_Collection pool;             // constant pool table
   protected int attributeIndex;               // attribute name (index into constant pool table)
   protected int length;                       // length of the attribute in bytes

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          set the values for the common fields                  -
    -----------------------------------------------------------------------*/
   public Attribute(DataInputStream ios, Pool_Collection pool, int attributeIndex) throws IOException {
      // set common variables for the attribute
      this.pool = pool;
      this.attributeIndex = attributeIndex;

      // read the length of the attribute
      int length = ios.readInt();
   }

   /*-----------------------------------------------------------------------
    - Method:        getLabel                                              -
    -                                                                      -
    - Desc:          default to no labels needed by attribute              -
    -                overridden by subclass: Attribute_LocalVariableTable  -
    -----------------------------------------------------------------------*/
   void getLabel(Code_Collection code) {
   }

   /*-----------------------------------------------------------------------
    - Method:        getLineNumberTable                                    -
    -                                                                      -
    - Desc:          default to no line numbers for attribute              -
    -                overridden by subclass: Attribute_LineNumberTable     -
    -----------------------------------------------------------------------*/
   int[] getLineNumberTable(int pc) {
      return null;
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          default to no output to jasmin assembly file          -
    -                overridden by subclasses: Attribute_SourceFile;       -
    -                   Attribute_Exceptions; Attribute_LocalVariableTable;-
    -                   Code_Collection                                    -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) throws IOException {
   }

   /*-----------------------------------------------------------------------
    - Method:        jasminSourceFile                                      -
    -                                                                      -
    - Desc:          default to no source file name for attribute          -
    -                overridden by subclass: Attribute_SourceFile          -
    -----------------------------------------------------------------------*/
   String jasminSourceFile() {
      return "";
   }

   /*-----------------------------------------------------------------------
    - Method:        jasminConstantValue                                   -
    -                                                                      -
    - Desc:          default to no constant value string for attribute     -
    -                overridden by subclass: Attribute_ConstantValue       -
    -----------------------------------------------------------------------*/
   String jasminConstantValue() {
      return "";
   }

   /*-----------------------------------------------------------------------
    - Method:        browseSourceFile                                      -
    -                                                                      -
    - Desc:          default to no source file name for attribute          -
    -                overridden by subclass: Attribute_SourceFile          -
    -----------------------------------------------------------------------*/
   String browseSourceFile() {
      return "";
   }

   /*-----------------------------------------------------------------------
    - Method:        browseDeprecated                                      -
    -                                                                      -
    - Desc:          default to no deprecated string for attribute         -
    -                overridden by subclass: Attribute_Deprecated          -
    -----------------------------------------------------------------------*/
   String browseDeprecated() {
      return "";
   }

   /*-----------------------------------------------------------------------
    - Method:        browseSynthetic                                       -
    -                                                                      -
    - Desc:          default to no synthetic string for attribute          -
    -                overridden by subclass: Attribute_Synthetic           -
    -----------------------------------------------------------------------*/
   String browseSynthetic() {
      return "";
   }

   /*-----------------------------------------------------------------------
    - Method:        browseConstantValue                                   -
    -                                                                      -
    - Desc:          default to no constant value string for attribute     -
    -                overridden by subclass: Attribute_ConstantValue       -
    -----------------------------------------------------------------------*/
   String browseConstantValue() {
      return "";
   }

   /*-----------------------------------------------------------------------
    - Method:        browseExceptions                                      -
    -                                                                      -
    - Desc:          default to no exception throws for attribute          -
    -                overridden by subclass: Attribute_Exception           -
    -----------------------------------------------------------------------*/
   String[] browseExceptions() {
      return null;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInnerClasses                                    -
    -                                                                      -
    - Desc:          default to no inner classes for attribute             -
    -                overridden by subclass: Attribute_InnerClasses        -
    -----------------------------------------------------------------------*/
   String[][] browseInnerClasses() {
      return null;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseFieldrefs                                       -
    -                                                                      -
    - Desc:          default to no fields referenced for attribute         -
    -                overridden by subclass: Code_Collection               -
    -----------------------------------------------------------------------*/
   String[] browseFieldrefs() {
      return null;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseMethodrefs                                      -
    -                                                                      -
    - Desc:          default to no methods referenced for attribute        -
    -                overridden by subclass: Code_Collection               -
    -----------------------------------------------------------------------*/
   String[] browseMethodrefs() {
      return null;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInterfaceMethodrefs                             -
    -                                                                      -
    - Desc:          default to no interfaces referenced for attribute     -
    -                overridden by subclass: Code_Collection               -
    -----------------------------------------------------------------------*/
   String[] browseInterfaceMethodrefs() {
      return null;
   }
}

/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Attribute_ConstantValue.java
 > Purpose:     ConstantValue attribute for static final fields
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
//package jasper;
//import java.io.*;

/*=======================================================================
 = Class:         Attribute_ConstantValue                               =
 =                                                                      =
 = Desc:          constant value for static final field                 =
 =======================================================================*/
class Attribute_ConstantValue extends Attribute {
   private int constantIndex;          // constant value (index into constant pool table)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read the attribute info from the input stream         -
    -----------------------------------------------------------------------*/
   public Attribute_ConstantValue(DataInputStream ios, Pool_Collection pool, int attributeIndex) throws IOException {
      // set the common variables
      super(ios, pool, attributeIndex);

      // get the constant value (index into constant pool table)
      constantIndex = ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        jasminConstantValue                                   -
    -                                                                      -
    - Desc:          set constant value string to complete field declare   -
    -----------------------------------------------------------------------*/
   String jasminConstantValue() {
      return " = " + pool.toString(constantIndex);
   }

   /*-----------------------------------------------------------------------
    - Method:        browseConstantValue                                   -
    -                                                                      -
    - Desc:          set constant value string to complete field declare   -
    -----------------------------------------------------------------------*/
   String browseConstantValue() {
      return " = " + pool.browseString(constantIndex);
   }
}

/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Attribute_Deprecated.java
 > Purpose:     Deprecated attribute for class, field or method
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
//package jasper;
//import java.io.*;

/*=======================================================================
 = Class:         Attribute_Deprecated                                  =
 =                                                                      =
 = Desc:          deprecated class, field or method                     =
 =======================================================================*/
class Attribute_Deprecated extends Attribute {

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read the attribute info from the input stream         -
    -----------------------------------------------------------------------*/
   public Attribute_Deprecated(DataInputStream ios, Pool_Collection pool, int attributeIndex) throws IOException {
      // set the common variables
      super(ios, pool, attributeIndex);

      // deprecated is only a flag with no further bytes needed - skip bytes just in case
      ios.skip(length);
   }

   /*-----------------------------------------------------------------------
    - Method:        browseDeprecated                                      -
    -                                                                      -
    - Desc:          set string to indicate deprecate                      -
    -----------------------------------------------------------------------*/
   String browseDeprecated() {
      return "#deprecated# ";
   }
}

/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Attribute_Exceptions.java
 > Purpose:     Exceptions which are thrown by a method
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
//package jasper;
//import java.io.*;

/*=======================================================================
 = Class:         Attribute_Exceptions                                  =
 =                                                                      =
 = Desc:          Exceptions attribute (Method)                         =
 =======================================================================*/
class Attribute_Exceptions extends Attribute {
   private int count;                  // number of exceptions which the method throws
   private int[] exceptionIndex;       // name of the exception class (index into constant pool table)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read the attribute info from the input stream         -
    -----------------------------------------------------------------------*/
   public Attribute_Exceptions(DataInputStream ios, Pool_Collection pool, int attributeIndex) throws IOException {
      // set the common variables
      super(ios, pool, attributeIndex);

      // get the number of exceptions in the throws clause
      count = ios.readShort();

      // read in the class names of the exceptions (index into constant pool table)
      exceptionIndex = new int[count];
      for (int i = 0; i < count; i++) exceptionIndex[i] = ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the .throws directives to the jasmin file      -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) throws IOException {
      for (int i = 0; i < count; i++) {
         out.println(ClassFile.pad("   .throws", ClassFile.SPACER) + pool.toString(exceptionIndex[i]));
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        browseExceptions                                      -
    -                                                                      -
    - Desc:          return the exceptions which the method throws         -
    -----------------------------------------------------------------------*/
   String[] browseExceptions() {
      String[] retVal = new String[count];
      for (int i = 0; i < count; i++) retVal[i] = pool.browseString(exceptionIndex[i]);
      return retVal;
   }
}

/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Attribute_InnerClasses.java
 > Purpose:     InnerClassses attribute for the class
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
//package jasper;
//import java.io.*;

/*=======================================================================
 = Class:         Attribute_InnerClasses                                =
 =                                                                      =
 = Desc:          InnerClasses attribute (Class)                        =
 =======================================================================*/
class Attribute_InnerClasses extends Attribute {
   private int count;                  // number of inner class entries
   private int[] innerIndex;           // full class name of inner class
   private int[] outerIndex;           // the class of which the inner class is a member (current = 0)
   private int[] nameIndex;            // simple class name of the inner class (anynomous = 0)
   private int[] accessFlags;          // access permissions for the inner class

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read the attribute info from the input stream         -
    -----------------------------------------------------------------------*/
   public Attribute_InnerClasses(DataInputStream ios, Pool_Collection pool, int attributeIndex) throws IOException {
      // set the common variables
      super(ios, pool, attributeIndex);

      // get the number of InnerClasses
      count = ios.readShort();

      // allocate the arrays to hold the inner class information
      innerIndex = new int[count];
      outerIndex = new int[count];
      nameIndex = new int[count];
      accessFlags = new int[count];

      for (int i = 0; i < count; i++) {
         // get the class name of the inner class
         innerIndex[i] = ios.readShort();

         // get the class of which the inner class is a member
         outerIndex[i] = ios.readShort();

         // get the simple class name of the inner class
         nameIndex[i] = ios.readShort();

         // get the access permissions of the inner class
         accessFlags[i] = ios.readShort();
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInnerClasses                                    -
    -                                                                      -
    - Desc:          return the inner class information                    -
    -----------------------------------------------------------------------*/
   String[][] browseInnerClasses() {
      String[][] retVal = new String[count][3];
      for (int i = 0; i < count; i++) {
         for (int j = 0; j < retVal[i].length; j++) retVal[i][j] = "";
         if (innerIndex[i] > 0) retVal[i][0] = pool.browseString(innerIndex[i]);
         if (outerIndex[i] > 0) retVal[i][1] = pool.browseString(outerIndex[i]);
         if ((accessFlags[i] & 0x0001) > 0) retVal[i][2] += "public ";
         if ((accessFlags[i] & 0x0010) > 0) retVal[i][2] += "final ";
         if ((accessFlags[i] & 0x0400) > 0) retVal[i][2] += "abstract ";
         if (nameIndex[i] > 0) retVal[i][2] += pool.browseString(nameIndex[i]);
      }
      return retVal;
   }
}

/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/LineNumberTable.java
 > Purpose:     LineNumberTable attribute for the code collection
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
//package jasper;
//import java.io.*;

/*=======================================================================
 = Class:         Attribute_LineNumberTable                             =
 =                                                                      =
 = Desc:          LocalVariableTable attribute (Code)                   =
 =======================================================================*/
class Attribute_LineNumberTable extends Attribute {
   private int count;                  // number of entries in the table
   private int[] pc;                   // program counter associated with the source line number
   private int[] lineNum;              // line number in the java source file

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read the attribute info from the input stream         -
    -----------------------------------------------------------------------*/
   public Attribute_LineNumberTable(DataInputStream ios, Pool_Collection pool, int attributeIndex) throws IOException {
      // set the common variables
      super(ios, pool, attributeIndex);

      // get the number of entries
      count = ios.readShort();

      // allocate the arrays to hold the line number entries
      pc = new int[count];
      lineNum = new int[count];

      for (int i = 0; i < count; i++) {
         // get the program counter
         pc[i] = ios.readShort();

         // get the java source line number
         lineNum[i] = ios.readShort();
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        getLineNumberTable                                    -
    -                                                                      -
    - Desc:          source line numbers for given program counter         -
    -----------------------------------------------------------------------*/
   int[] getLineNumberTable(int pc) {
      // note that more than one source line can be associated with a single pc
      int lineCount = 0;
      for (int i = 0; i < count; i++) if (this.pc[i] == pc) ++lineCount;
      if (lineCount == 0) return null;
      int[] lines = new int[lineCount];
      for (int i = 0, n = 0; i < count; i++) if (this.pc[i] == pc) lines[n++] = lineNum[i];
      return lines;
   }
}

/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Attribute_LocalVariableTable.java
 > Purpose:     LocalVariableTable attribute for the code collection
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
//package jasper;
//import java.io.*;

/*=======================================================================
 = Class:         Attribute_LocalVariableTable                          =
 =                                                                      =
 = Desc:          LocalVariableTable attribute (Code)                   =
 =======================================================================*/
class Attribute_LocalVariableTable extends Attribute {
   private int count;                  // number of entries
   private int[] startPC;              // program counter start for local variable
   private int[] len;                  // length of local variable usage (relative to startPC)
   private int[] nameIndex;            // name of the local variable (index into constant pool table)
   private int[] descriptorIndex;      // type of the local variable (index into constant pool table)
   private int[] varIndex;             // local variable table index in the java bytecode

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read the attribute info from the input stream         -
    -----------------------------------------------------------------------*/
   public Attribute_LocalVariableTable(DataInputStream ios, Pool_Collection pool, int attributeIndex) throws IOException {
      // set the common variables
      super(ios, pool, attributeIndex);

      // get number of entries in this local variable table
      count = ios.readShort();

      // allocate arrays to hold entries
      startPC = new int[count];
      len = new int[count];
      nameIndex = new int[count];
      descriptorIndex = new int[count];
      varIndex = new int[count];

      for (int i = 0; i < count; i++) {
         // get program counter start for local variable
         startPC[i] = ios.readShort();

         // get length of local variable usage (relative to startPC)
         len[i] = ios.readShort();

         // get name of the local variable (index into constant pool table)
         nameIndex[i] = ios.readShort();

         // get type of the local variable (index into constant pool table)
         descriptorIndex[i] = ios.readShort();

         // get local variable table index in the java bytecode
         varIndex[i] = ios.readShort();
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        getLabel                                              -
    -                                                                      -
    - Desc:          make sure a LABEL is printed for .local directives    -
    -----------------------------------------------------------------------*/
   void getLabel(Code_Collection code) {
      for (int i = 0; i < count; i++) {
         // label the position in the code for the start pc for the local
         code.setLabel(startPC[i]);

         // label the position in the code for the end pc for the local
         code.setLabel(startPC[i]+len[i]);
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the .local directives to the jasmin file       -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) throws IOException {
      for (int i = 0; i < count; i++) {
         out.println(ClassFile.pad("   .var " + varIndex[i] + " is ", ClassFile.SPACER) +
             pool.toString(nameIndex[i]) + " " + pool.toString(descriptorIndex[i]) +
             " from " + Code_Collection.toLabel(startPC[i]) +
             " to " + Code_Collection.toLabel(startPC[i] + len[i]));
      }
   }
}

/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Attribute_SourceFile.java
 > Purpose:     SourceFile attribute for the class
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
//package jasper;
//import java.io.*;

/*=======================================================================
 = Class:         Attribute_SourceFile                                  =
 =                                                                      =
 = Desc:          SourceFile attribute (Class)                          =
 =======================================================================*/
class Attribute_SourceFile extends Attribute {
   private int sourceIndex;            // source file name (index into the constant pool table)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read the attribute info from the input stream         -
    -----------------------------------------------------------------------*/
   public Attribute_SourceFile(DataInputStream ios, Pool_Collection pool, int attributeIndex) throws IOException {
      // set the common variables
      super(ios, pool, attributeIndex);

      // get the source file name
      sourceIndex = ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the .source directive to the jasmin file       -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) throws IOException {
      out.println(ClassFile.pad(".source", ClassFile.SPACER) + jasminSourceFile());
   }

   /*-----------------------------------------------------------------------
    - Method:        jasminSourceFile                                      -
    -                                                                      -
    - Desc:          return the source file name                           -
    -----------------------------------------------------------------------*/
   String jasminSourceFile() {
      return pool.toString(sourceIndex);
   }

   /*-----------------------------------------------------------------------
    - Method:        browseSourceFile                                      -
    -                                                                      -
    - Desc:          return the source file name                           -
    -----------------------------------------------------------------------*/
   String browseSourceFile() {
      return pool.browseString(sourceIndex);
   }
}

/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Attribute_Synthetic.java
 > Purpose:     Synthetic attribute of class, field or method
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
//package jasper;
//import java.io.*;

/*=======================================================================
 = Class:         Attribute_Synthetic                                   =
 =                                                                      =
 = Desc:          Synthetic attribute (Field, Method, Class)            =
 =======================================================================*/
class Attribute_Synthetic extends Attribute {

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read the attribute info from the input stream         -
    -----------------------------------------------------------------------*/
   public Attribute_Synthetic(DataInputStream ios, Pool_Collection pool, int attributeIndex) throws IOException {
      // set the common variables
      super(ios, pool, attributeIndex);

      // synthetic is only a flag with no further bytes needed - skip bytes just in case
      ios.skip(length);
   }

   /*-----------------------------------------------------------------------
    - Method:        browseSynthetic                                       -
    -                                                                      -
    - Desc:          set string to indicate deprecate                      -
    -----------------------------------------------------------------------*/
   String browseSynthetic() {
      return "#synthetic# ";
   }
}

/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Attribute_Unknown.java
 > Purpose:     Unknown attribute - vendor specific or not yet implemented by jasper
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
//package jasper;
//import java.io.*;

/*=======================================================================
 = Class:         Attribute_Unknown                                     =
 =                                                                      =
 = Desc:          Unknown attribute (Field, Method, Code, Class)        =
 =======================================================================*/
class Attribute_Unknown extends Attribute {

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read the attribute info from the input stream         -
    -----------------------------------------------------------------------*/
   public Attribute_Unknown(DataInputStream ios, Pool_Collection pool, int attributeIndex) throws IOException {
      // set the common variables
      super(ios, pool, attributeIndex);

      // echo that jasper doesn't currently handle the attribute
      System.out.println("Undefined Attribute (length=" + length + ") (name=" + this.toString() + ")");

      // do a hex dump of the attributes (mostly for debugging)
      ClassFile.dump(ios, length);
   }
}
