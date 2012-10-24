/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/TryCatch_Collection.java
 > Purpose:     Try-Catch entries for the Code Collection
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
package jasper;
import java.io.*;

/*=======================================================================
 = Class:         TryCatch_Collection                                   =
 =                                                                      =
 = Desc:          collection of try catch regions for the code          =
 =======================================================================*/
class TryCatch_Collection {
   private int count;                  // number of entries
   private TryCatch[] trycatches;      // try catch definitions for the code

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the try catch collection from input stream    -
    -----------------------------------------------------------------------*/
   TryCatch_Collection(DataInputStream ios, Pool_Collection pool) throws IOException {
      // get the number of entries
      count = ios.readShort();

      // get the try catch definitions
      trycatches = new TryCatch[count];
      for (int i = 0; i < count; i++) trycatches[i] = new TryCatch(ios, pool);
   }

   /*-----------------------------------------------------------------------
    - Method:        getLabel                                              -
    -                                                                      -
    - Desc:          ensure that the region is labelled at begin and end   -
    -----------------------------------------------------------------------*/
   void getLabel(Code_Collection code) {
      for (int i = 0; i < count; i++) trycatches[i].getLabel(code);
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the .catch directives to the jasmin file       -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) throws IOException {
      for (int i = 0; i < count; i++) trycatches[i].jasmin(out);
   }
}

/*=======================================================================
 = Class:         TryCatch                                              =
 =                                                                      =
 = Desc:          try catch entry for the code                          =
 =======================================================================*/
class TryCatch {
   private Pool_Collection pool;
   private int startPC;                // start program counter for the try block
   private int endPC;                  // end program counter for the try block
   private int handlerPC;              // program counter address of the catch handler
   private int catchType;              // class name of the exception caught (index into constant pool table)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the try catch definitions from input stream   -
    -----------------------------------------------------------------------*/
   TryCatch(DataInputStream ios, Pool_Collection pool) throws IOException {
      // save off the constant pool table for later use
      this.pool = pool;

      // get start pc for the try block
      startPC = ios.readShort();

      // get end pc for the try block
      endPC = ios.readShort();

      // get catch handler address
      handlerPC = ios.readShort();

      // get name of the exception being caught (index into constant pool table)
      catchType = ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        getLabel                                              -
    -                                                                      -
    - Desc:          ensure that the region is labelled at begin and end   -
    -----------------------------------------------------------------------*/
   void getLabel(Code_Collection code) {
      // ensure label at start of try block
      code.setLabel(startPC);

      // ensure label at end of try block
      code.setLabel(endPC);

      // ensure label at the catch handler address
      code.setLabel(handlerPC);
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the .catch directive to the jasmin file        -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) throws IOException {
      String catchClass = "all";
      if (catchType > 0) catchClass = pool.toString(catchType);
      out.println(ClassFile.pad("   .catch", ClassFile.SPACER) + catchClass +
         " from LABEL0x" + Integer.toHexString(startPC) +
         " to LABEL0x" + Integer.toHexString(endPC) +
         " using LABEL0x" + Integer.toHexString(handlerPC));
   }
}
