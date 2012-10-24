/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Interface_Collection.java
 > Purpose:     Interfaces implemented by the class
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
package jasper;
import java.io.*;

/*=======================================================================
 = Class:         Interface_Collection                                  =
 =                                                                      =
 = Desc:          interfaces implemented by the class                   =
 =======================================================================*/
class Interface_Collection {
   private Pool_Collection pool;       // constant pool table
   private int count;                  // number of interfaces
   private int[] interfaces;           // interfaces implemented (index into constant pool table)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the interfaces definitions from input stream  -
    -----------------------------------------------------------------------*/
   Interface_Collection(DataInputStream ios, Pool_Collection pool) throws IOException {
      // save off the pool object for later reference
      this.pool = pool;

      // get number of interfaces implemented;
      count  = ios.readShort();

      // grab the interface indexes from the input stream
      interfaces = new int[count];
      for (int i = 0; i < count; i++) interfaces[i] = ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the .implements directives to jasmin file      -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) throws IOException {
      for (int i = 0; i < count; i++) {
         out.println(ClassFile.pad(".implements", ClassFile.SPACER) + pool.toString(interfaces[i]));
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInterfaces                                      -
    -                                                                      -
    - Desc:          return array of strings representing the interfaces   -
    -----------------------------------------------------------------------*/
   String[] browseInterfaces() {
      String[] retVal = new String[count];
      for (int i = 0; i < count; i++) retVal[i] = pool.browseString(interfaces[i]);
      return retVal;
   }
}
