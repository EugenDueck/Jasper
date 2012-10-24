/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Pool_Collection.java
 > Purpose:     Constant pool collection - symbol table
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
package jasper;
import java.io.*;
import java.lang.reflect.*;

/*=======================================================================
 = Class:         Pool_Collection                                       =
 =                                                                      =
 = Desc:          Constant Table Pool                                   =
 =======================================================================*/
class Pool_Collection {
   private short count;                // number of entries in the constant pool table (no zero entry)
   private int[] poolType;             // type of pool constant
   private Pool[] pool;                // constant pool table

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read constants from the input stream                  -
    -----------------------------------------------------------------------*/
   Pool_Collection(DataInputStream ios) throws IOException {
      // read in the number of entries in the table
      count = ios.readShort();

      // allocate the arrays to hold the pool constants
      poolType = new int[count];
      pool = new Pool[count];

      // read in the pool constants from the input stream
      for (int i = 1; i < count; i++) {
         // get the type of constant
         poolType[i] = ios.read();

         try {
            Constructor myConstructor;

            // get subclass that handles the constant
            Class newClass = (Class)dispatch[poolType[i]][2];
            try {
               // for some reason this technique is not working in JDK1.2?
               myConstructor = newClass.getConstructor(new Class[] {ios.getClass(), Pool_Collection.class});
            } catch (NoSuchMethodException e) {
               // since there is only one constructor, we can use this technique if above fails
               myConstructor = newClass.getDeclaredConstructors()[0];
            }

            // read in the constant
            pool[i] = (Pool)myConstructor.newInstance(new Object[] {ios, this});

            // skip over entries for types long and double (they occupy two indexes in constant pool)
            for (int n = 1; n < Integer.parseInt((String)dispatch[poolType[i]][1]); n++) {
               pool[i+1] = pool[i];
               i = i + 1;
            }

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
    - Method:        toString                                              -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   String toString(int cptIndex) {
      if ((cptIndex == 0) || (cptIndex >= count)) {
         System.out.println("Index error for constant pool table: " + cptIndex);
         return "";
      }
      return pool[cptIndex].toString();
   }

   /*-----------------------------------------------------------------------
    - Method:        isFieldref                                            -
    -                                                                      -
    - Desc:          test if a class field is being referenced             -
    -----------------------------------------------------------------------*/
   boolean isFieldref(int cptIndex) {
      return pool[cptIndex].isFieldref();
   }

   /*-----------------------------------------------------------------------
    - Method:        isMethodref                                           -
    -                                                                      -
    - Desc:          test if a class method is being referenced            -
    -----------------------------------------------------------------------*/
   boolean isMethodref(int cptIndex) {
      return pool[cptIndex].isMethodref();
   }

   /*-----------------------------------------------------------------------
    - Method:        isInterfaceMethodref                                  -
    -                                                                      -
    - Desc:          test if an interface method is being referenced       -
    -----------------------------------------------------------------------*/
   boolean isInterfaceMethodref(int cptIndex) {
      return pool[cptIndex].isInterfaceMethodref();
   }

   /*-----------------------------------------------------------------------
    - Method:        browseString                                          -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   String browseString(int cptIndex) {
      return pool[cptIndex].browseString();
   }

   /*-----------------------------------------------------------------------
    - Method:        browseDescriptor                                      -
    -                                                                      -
    - Desc:          return string representation of type descriptor       -
    -----------------------------------------------------------------------*/
   String browseDescriptor(int cptIndex) {
      return pool[cptIndex].browseDescriptor();
   }

   /*-----------------------------------------------------------------------
    - Field:         dispatch                                              -
    -                                                                      -
    - Desc:          array used to dispatch for object instantiation       -
    -                   element[i][0] = constant type                      -
    -                   element[i][1] = entries occupied constant          -
    -                   element[i][2] = class name that handles attribute  -
    -----------------------------------------------------------------------*/
   private static Object[][] dispatch = {
      {"0",  "1", null},
      {"1",  "1", Pool_Utf8.class},
      {"2",  "1", Pool_Unicode.class},
      {"3",  "1", Pool_Integer.class},
      {"4",  "1", Pool_Float.class},
      {"5",  "2", Pool_Long.class},
      {"6",  "2", Pool_Double.class},
      {"7",  "1", Pool_Class.class},
      {"8",  "1", Pool_String.class},
      {"9",  "1", Pool_Fieldref.class},
      {"10", "1", Pool_Methodref.class},
      {"11", "1", Pool_InterfaceMethodref.class},
      {"12", "1", Pool_NamedType.class}
   };
}

/*=======================================================================
 = Class:         Pool                                                  =
 =                                                                      =
 = Desc:          abstract class for constant entries                   =
 =======================================================================*/
abstract class Pool {
   protected Pool_Collection pool;     // store of constant pool table for use by cross-ref'd constants

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read constant from the input stream                   -
    -----------------------------------------------------------------------*/
   Pool(Pool_Collection pool) {
      this.pool = pool;
   }

   /*-----------------------------------------------------------------------
    - Method:        escapeString                                          -
    -                                                                      -
    - Desc:          replace unprintable chars with octal constant (\xxx)  -
    -----------------------------------------------------------------------*/
   static String escapeString(String raw) {
      String retVal = raw;
      for (int i = 0; i < retVal.length(); i++) {
         if ((retVal.charAt(i) < ' ') || (retVal.charAt(i) > '~') || (retVal.charAt(i) == '\"')) {
            String s = Integer.toOctalString(retVal.charAt(i));
            while (s.length() < 3) s = '0' + s;
            s = '\\' + s;
            if (i > 0) {
               retVal = retVal.substring(0, i) + s + retVal.substring(i+1);
            } else {
               retVal = s + retVal.substring(i+1);
            }
         }
      }
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseDescriptor                                      -
    -                                                                      -
    - Desc:          convert the descriptor to resemble java code          -
    -----------------------------------------------------------------------*/
   String browseDescriptor(String s) {
      String funstring = "";           // function or field name
      String typestring = "";          // type of the function or field
      String arraystring = "";         // array indexes of the type descriptor
      int i = 0;                       // index used to traverse the string

      // pull off the function parameters
      if (s.charAt(0) == '(') {
         funstring = " (" + browseDescriptor(s.substring(1)) + ")";
         s = s.substring(s.indexOf(')') + 1);
      }

      // pull off the array indexes
      while (s.charAt(i) == '[') {
         i++;
         arraystring = arraystring + "[]";
      }

      // get the types
      switch (s.charAt(i++)) {
         case 'Z': typestring = "boolean"; break;
         case 'B': typestring = "byte";    break;
         case 'C': typestring = "char";    break;
         case 'S': typestring = "short";   break;
         case 'I': typestring = "int";     break;
         case 'J': typestring = "long";    break;
         case 'F': typestring = "float";   break;
         case 'D': typestring = "double";  break;
         case 'V': typestring = "void";    break;
         case 'L':
            // object type
            int j = s.indexOf(';');
            typestring = s.substring(i, j).replace('/', '.');
            i = j;
            break;
         case ')':
            // end of the function parameters
            i = i - 1;
            break;
      }

      // go past semicolons
      while ((s.length() > i) && (s.charAt(i) == ';')) i++;

      // if end of string then return
      if (s.length() <= i) return typestring + arraystring + funstring;

      // if end of function parameters then return
      if (s.charAt(i) == ')') return typestring + arraystring + funstring;

      // return the results
      return typestring + arraystring + ", " + browseDescriptor(s.substring(i)) + funstring;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseDescriptor                                      -
    -                                                                      -
    - Desc:          overload the function to allow external calls         -
    -----------------------------------------------------------------------*/
   String browseDescriptor() {
      return browseDescriptor(this.browseString());
   }

   /*-----------------------------------------------------------------------
    - Method:        browseString                                          -
    -                                                                      -
    - Desc:          default browse string to same as toString method      -
    -----------------------------------------------------------------------*/
   String browseString() {
      return toString();
   }

   /*-----------------------------------------------------------------------
    - Method:        isFieldref                                            -
    -                                                                      -
    - Desc:          default to class field not being referenced           -
    -----------------------------------------------------------------------*/
   boolean isFieldref() {
      return false;
   }

   /*-----------------------------------------------------------------------
    - Method:        isMethodref                                           -
    -                                                                      -
    - Desc:          default to class method not being referenced          -
    -----------------------------------------------------------------------*/
   boolean isMethodref() {
      return false;
   }

   /*-----------------------------------------------------------------------
    - Method:        isInterfaceMethodref                                  -
    -                                                                      -
    - Desc:          default to interface method not being referenced      -
    -----------------------------------------------------------------------*/
   boolean isInterfaceMethodref() {
      return false;
   }
}

/*=======================================================================
 = Class:         Pool_Utf8                                             =
 =                                                                      =
 = Desc:          UTF-8 encoded string constant                         =
 =======================================================================*/
class Pool_Utf8 extends Pool {
   private String value = "";          // value of UTF-8 string constant

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read UTF-8 constant from the input stream             -
    -----------------------------------------------------------------------*/
   Pool_Utf8(DataInputStream ios, Pool_Collection pool) throws IOException {
      super(pool);
      short length = ios.readShort();
      for (int i = 0; i < length; i++) {
         int a = ios.read();
         if ((a & 0x80) == 0) {
            value = value + (char)a;
         } else if ((a & 0x20) == 0) {
            int b = ios.read();
            value = value + (char)(((a & 0x1f) << 6) + (b & 0x3f));
            i++;
         } else {
            int b = ios.read();
            int c = ios.read();
            value = value + (char)(((a & 0xf) << 12) + ((b & 0x3f) << 6) + (c & 0x3f));
            i += 2;
         }
      }
      value = escapeString(value);
   }

   /*-----------------------------------------------------------------------
    - Method:        toString                                              -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   public String toString() {
      return value;
   }
}

/*=======================================================================
 = Class:         Pool_Unicode                                          =
 =                                                                      =
 = Desc:          Unicode string constant                               =
 =======================================================================*/
class Pool_Unicode extends Pool {
   private String value = "";          // value of unicode string constant

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read unicode constant from the input stream           -
    -----------------------------------------------------------------------*/
   Pool_Unicode(DataInputStream ios, Pool_Collection pool) throws IOException {
      super(pool);
      short length = ios.readShort();
      for (int i = 0; i < length; i++) {
         value = value + ios.readChar();
      }
      value = escapeString(value);
   }

   /*-----------------------------------------------------------------------
    - Method:        toString                                              -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   public String toString() {
      return value;
   }
}

/*=======================================================================
 = Class:         Pool_Integer                                          =
 =                                                                      =
 = Desc:          Integer constant                                      =
 =======================================================================*/
class Pool_Integer extends Pool {
   private int value;                  // value of int constant

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read integer constant from the input stream           -
    -----------------------------------------------------------------------*/
   Pool_Integer(DataInputStream ios, Pool_Collection pool) throws IOException {
      super(pool);
      value = ios.readInt();
   }

   /*-----------------------------------------------------------------------
    - Method:        toString                                              -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   public String toString() {
      return Integer.toString(value);
   }
}

/*=======================================================================
 = Class:         Pool_Float                                            =
 =                                                                      =
 = Desc:          Float constant                                        =
 =======================================================================*/
class Pool_Float extends Pool {
   private float value;                // value of float constant

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read float constant from the input stream             -
    -----------------------------------------------------------------------*/
   Pool_Float(DataInputStream ios, Pool_Collection pool) throws IOException {
      super(pool);
      value = ios.readFloat();
   }

   /*-----------------------------------------------------------------------
    - Method:        toString                                              -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   public String toString() {
      return Float.toString(value);
   }
}

/*=======================================================================
 = Class:         Pool_Long                                             =
 =                                                                      =
 = Desc:          Long constant                                         =
 =======================================================================*/
class Pool_Long extends Pool {
   private long value;                 // value of long constant

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read long constant from the input stream              -
    -----------------------------------------------------------------------*/
   Pool_Long(DataInputStream ios, Pool_Collection pool) throws IOException {
      super(pool);
      value = ios.readLong();
   }

   /*-----------------------------------------------------------------------
    - Method:        toString                                              -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   public String toString() {
      return Long.toString(value);
   }
}

/*=======================================================================
 = Class:         Pool_Double                                           =
 =                                                                      =
 = Desc:          Double constant                                       =
 =======================================================================*/
class Pool_Double extends Pool {
   private double value;               // value of double constant

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read double constant from the input stream            -
    -----------------------------------------------------------------------*/
   Pool_Double(DataInputStream ios, Pool_Collection pool) throws IOException {
      super(pool);
      value = ios.readDouble();
   }

   /*-----------------------------------------------------------------------
    - Method:        toString                                              -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   public String toString() {
      return Double.toString(value);
   }
}

/*=======================================================================
 = Class:         Pool_Class                                            =
 =                                                                      =
 = Desc:          Class constant                                        =
 =======================================================================*/
class Pool_Class extends Pool {
   private short index;                // class constant (index into constant pool table)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read class constant from the input stream             -
    -----------------------------------------------------------------------*/
   Pool_Class(DataInputStream ios, Pool_Collection pool) throws IOException {
      super(pool);
      index = ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        toString                                              -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   public String toString() {
      return pool.toString(index);
   }

   /*-----------------------------------------------------------------------
    - Method:        browseString                                          -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   String browseString() {
      return toString().replace('/', '.');
   }
}

/*=======================================================================
 = Class:         Pool_String                                           =
 =                                                                      =
 = Desc:          String constant                                       =
 =======================================================================*/
class Pool_String extends Pool {
   private short index;                // constant string (index into constant pool table)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read string constant from the input stream            -
    -----------------------------------------------------------------------*/
   Pool_String(DataInputStream ios, Pool_Collection pool) throws IOException {
      super(pool);
      index = ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        toString                                              -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   public String toString() {
      return "\"" + pool.toString(index) + "\"";
   }
}

/*=======================================================================
 = Class:         Pool_Fieldref                                         =
 =                                                                      =
 = Desc:          Class field reference constant                        =
 =======================================================================*/
class Pool_Fieldref extends Pool {
   private short classIndex;           // class name (index into the constant pool table)
   private short namedtypeIndex;       // return type and parameters (index into the constant pool table)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read fieldref constant from the input stream          -
    -----------------------------------------------------------------------*/
   Pool_Fieldref(DataInputStream ios, Pool_Collection pool) throws IOException {
      super(pool);
      classIndex = ios.readShort();
      namedtypeIndex = ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        toString                                              -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   public String toString() {
      return pool.toString(classIndex) + "/" + pool.toString(namedtypeIndex);
   }

   /*-----------------------------------------------------------------------
    - Method:        browseString                                          -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   String browseString() {
      int i;
      String s;
      String fieldType;
      String fieldName;
      String className;

      s = pool.browseString(namedtypeIndex);
      i = s.indexOf(' ');

      fieldType = s.substring(0, i);
      fieldName = s.substring(i+1);
      className = pool.browseString(classIndex);

      return fieldType + " " + className + "." + fieldName;
   }

   /*-----------------------------------------------------------------------
    - Method:        isFieldref                                            -
    -                                                                      -
    - Desc:          flag that a class field is referenced                 -
    -----------------------------------------------------------------------*/
   boolean isFieldref() {
      return true;
   }
}

/*=======================================================================
 = Class:         Pool_Methodref                                        =
 =                                                                      =
 = Desc:          Class method reference constant                       =
 =======================================================================*/
class Pool_Methodref extends Pool {
   private short classIndex;           // class name (index into the constant pool table)
   private short namedtypeIndex;       // return type and parameters (index into the constant pool table)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read methodref constant from the input stream         -
    -----------------------------------------------------------------------*/
   Pool_Methodref(DataInputStream ios, Pool_Collection pool) throws IOException {
      super(pool);
      classIndex = ios.readShort();
      namedtypeIndex = ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        toString                                              -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   public String toString() {
      return pool.toString(classIndex) + "/" + pool.toString(namedtypeIndex);
   }

   /*-----------------------------------------------------------------------
    - Method:        browseString                                          -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   String browseString() {
      String s = pool.browseString(namedtypeIndex);
      int i = s.indexOf(' ');
      int j = s.indexOf('(');

      String returnType = s.substring(0, i);
      String functionName = s.substring(i+1, j);
      String functionParams = s.substring(j);
      String className = pool.browseString(classIndex);

      if (functionName.equals("<init>")) {
         return "new " + className + functionParams;
      } else {
         return returnType + " " + className + "." + functionName + functionParams;
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        isMethodref                                           -
    -                                                                      -
    - Desc:          flag that a class method is referenced                -
    -----------------------------------------------------------------------*/
   boolean isMethodref() {
      return true;
   }
}

/*=======================================================================
 = Class:         Pool_InterfaceMethodref                               =
 =                                                                      =
 = Desc:          Interface method reference constant                   =
 =======================================================================*/
class Pool_InterfaceMethodref extends Pool {
   private short classIndex;           // class name (index into the constant pool table)
   private short namedtypeIndex;       // return type and parameters (index into the constant pool table)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read interfacemethodref constant from input stream    -
    -----------------------------------------------------------------------*/
   Pool_InterfaceMethodref(DataInputStream ios, Pool_Collection pool) throws IOException {
      super(pool);
      classIndex = ios.readShort();
      namedtypeIndex = ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        toString                                              -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   public String toString() {
      return pool.toString(classIndex) + "/" + pool.toString(namedtypeIndex);
   }

   /*-----------------------------------------------------------------------
    - Method:        browseString                                          -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   String browseString() {
      String s = pool.browseString(namedtypeIndex);
      int i = s.indexOf(' ');
      int j = s.indexOf('(');

      String returnType = s.substring(0, i);
      String functionName = s.substring(i+1, j);
      String functionParams = s.substring(j);
      String className = pool.browseString(classIndex);

      if (functionName.equals("<init>")) {
         return "new " + className + functionParams;
      } else {
         return returnType + " " + className + "." + functionName + functionParams;
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        isFieldref                                            -
    -                                                                      -
    - Desc:          flag that an interface method is referenced           -
    -----------------------------------------------------------------------*/
   boolean isInterfaceMethodref() {
      return true;
   }
}

/*=======================================================================
 = Class:         Pool_NamedType                                        =
 =                                                                      =
 = Desc:          Class name and type constant                          =
 =======================================================================*/
class Pool_NamedType extends Pool {
   private short nameIndex;            // field or class name (index into the constant pool table)
   private short descriptorIndex;      // return type and parameters (index into the constant pool table)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read namedtype constant from the input stream         -
    -----------------------------------------------------------------------*/
   Pool_NamedType(DataInputStream ios, Pool_Collection pool) throws IOException {
      super(pool);
      nameIndex = ios.readShort();
      descriptorIndex = ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        toString                                              -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   public String toString() {
      String s = pool.toString(descriptorIndex);
      if (s.charAt(0) != '(') s = " " + s;
      return pool.toString(nameIndex) + s;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseString                                          -
    -                                                                      -
    - Desc:          return string representation of constant              -
    -----------------------------------------------------------------------*/
   String browseString() {
      String s = pool.browseDescriptor(descriptorIndex);
      int i = s.indexOf('(');
      if (i > 0) {
         // this is a method
         return s.substring(0, i) + pool.browseString(nameIndex) + s.substring(i);
      } else {
         // this is a field
         return s + " " + pool.browseString(nameIndex);
      }
   }
}
