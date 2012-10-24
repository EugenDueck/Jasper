/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Code_Collection.java
 > Purpose:     Collection of code defined for a method
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
package jasper;
import java.io.*;
import java.lang.reflect.*;

/*=======================================================================
 = Class:         Code_Collection                                       =
 =                                                                      =
 = Desc:          java bytecode for the method                          =
 =======================================================================*/
class Code_Collection extends Attribute {
   private int pcReturnLabel = 0;                           // program counter if end method label needed
   private short maxStack;                                  // max stack space for method
   private short maxLocals;                                 // max local variable index
   private int codeLength;                                  // length of code block
   private java.util.Vector code = new java.util.Vector();  // collection of code operations
   private TryCatch_Collection trycatches;                  // try catch blocks
   private Attribute_Collection attributes;                 // code attributes
                                                            //    (LineNumberTable, LocalVariableTable)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the code for the method from the input stream -
    -----------------------------------------------------------------------*/
   Code_Collection(DataInputStream ios, Pool_Collection pool, int attribute_index) throws IOException {
      // set the common variables for attributes
      super(ios, pool, attribute_index);

      // get the max stack space
      maxStack = ios.readShort();

      // get the max number for local variable index
      maxLocals = ios.readShort();

      // get the length of the code block
      codeLength = ios.readInt();

      // read in the individual code operations
      for (int i = 0, pc = 0; pc < codeLength; i++) {
         // get the opcode for the instruction
         int opcode = ios.read();

         Code myCode;
         if (dispatch[opcode][3] == null) {
            // create instance of Code class
            myCode = new Code(ios, pool, opcode, pc);

         } else {
            // create instance of a Code subclass
            try {
               Constructor myConstructor;

               // get subclass that handles the opcode
               Class newClass = (Class)dispatch[opcode][3];
               try {
                  // for some reason this technique is not working in JDK1.2?
                  myConstructor = newClass.getConstructor(new Class[]
                     {ios.getClass(), pool.getClass(), int.class, int.class});
               } catch (NoSuchMethodException e) {
                  // since there is only one constructor, we can use this technique if above fails
                  myConstructor = newClass.getDeclaredConstructors()[0];
               }

            // read in the bytecode instruction
            myCode = (Code)myConstructor.newInstance(new Object[]
                  {ios, pool, new Integer(opcode), new Integer(pc)});

            } catch (InstantiationException e) {
               throw new IOException("InstantiationException");

            } catch (IllegalAccessException e) {
               throw new IOException("IllegalAccessException");

            } catch (InvocationTargetException e) {
               throw new IOException("InvocationTargetException");
            }
         }

         // increment the program counter by number of bytes occupied by the instruction
         pc += myCode.opbytes;

         // add code class to the collection
         code.addElement(myCode);
      }

      // read in the try catch definitions
      trycatches = new TryCatch_Collection(ios, pool);

      // get the attributes associated with this code (LineNumberTable, LocalVariableTable)
      attributes = new Attribute_Collection(ios, pool);

      // now iterate through the collection and find where in the code block labels are needed
      for (int i = 0; i < code.size(); i++) {
         Code myCode = ((Code)code.elementAt(i));
         myCode.getLabel(this);
      }
      trycatches.getLabel(this);
      attributes.getLabel(this);
   }

   /*-----------------------------------------------------------------------
    - Method:        setLabel                                              -
    -                                                                      -
    - Desc:          set the code to print label at given program counter  -
    -----------------------------------------------------------------------*/
   int setLabel(int pc) {
      int maxPC = 0;                               // initialize the maximum PC
      for (int i = 0; i < code.size(); i++) {
         Code myCode = (Code)code.elementAt(i);
         maxPC = myCode.setLabel(pc);              // get the PC of the next instruction
      }
      if (pc > maxPC) pcReturnLabel = pc;          // if pc exceeds code range then set end method pc
      return pc;
   }

   /*-----------------------------------------------------------------------
    - Method:        toLabel                                               -
    -                                                                      -
    - Desc:          return label string                                   -
    -----------------------------------------------------------------------*/
   static String toLabel(int pc) {
      return "LABEL0x" + Integer.toHexString(pc);
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the instructions of the method                 -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) throws IOException {
      // output the .limit directives
      out.println(ClassFile.pad("   .limit stack", ClassFile.SPACER) + maxStack);
      out.println(ClassFile.pad("   .limit locals", ClassFile.SPACER) + maxLocals);

      // output the .var and .throws directives
      attributes.jasmin(out);

      // output the opcode instructions
      for (int k = 0; k < code.size(); k++) {
         // get current code instruction
         Code myCode = (Code)code.elementAt(k);

         // get lines associated with current code pc
         int[] lines = attributes.getLineNumberTable(myCode.pc);

         // output the .line directives if associated source lines found
         if (lines != null) {
            for (int i = 0; i < lines.length; i++) {
               out.println(ClassFile.pad("   .line", ClassFile.SPACER) + lines[i]);
            }
         }

         // output the instruction
         myCode.jasmin(out);
         out.println("");
      }

      // if label need for end method, then output the label
      if (pcReturnLabel > 0) out.println(toLabel(pcReturnLabel) + ":");

      // output the .catch directives
      trycatches.jasmin(out);
   }

   /*-----------------------------------------------------------------------
    - Method:        browseFieldrefs                                       -
    -                                                                      -
    - Desc:          ask the code what fields are referenced               -
    -----------------------------------------------------------------------*/
   String[] browseFieldrefs() {
      java.util.Vector x = new java.util.Vector();
      for (int i = 0; i < code.size(); i++) {
         Code myCode = (Code)code.elementAt(i);
         int cptIndex = myCode.browseFieldref();
         if (cptIndex > 0) {
            boolean found = false;
            String s = pool.browseString(cptIndex);
            for (int j = 0; j < x.size(); j++) {
               if (((String)x.elementAt(j)).equals(s)) {
                  found = true;
                  break;
               }
            }
            if (!found) x.add(pool.browseString(cptIndex));
         }
      }
      String[] retVal = new String[x.size()];
      x.copyInto(retVal);
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseMethodrefs                                      -
    -                                                                      -
    - Desc:          ask the code what methods are referenced              -
    -----------------------------------------------------------------------*/
   String[] browseMethodrefs() {
      java.util.Vector x = new java.util.Vector();
      for (int i = 0; i < code.size(); i++) {
         Code myCode = (Code)code.elementAt(i);
         int cptIndex = myCode.browseMethodref();
         if (cptIndex > 0) {
            boolean found = false;
            String s = pool.browseString(cptIndex);
            for (int j = 0; j < x.size(); j++) {
               if (((String)x.elementAt(j)).equals(s)) {
                  found = true;
                  break;
               }
            }
            if (!found) x.add(pool.browseString(cptIndex));
         }
      }
      String[] retVal = new String[x.size()];
      x.copyInto(retVal);
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInterfaceMethodrefs                             -
    -                                                                      -
    - Desc:          ask the code what interface methods are referenced    -
    -----------------------------------------------------------------------*/
   String[] browseInterfaceMethodrefs() {
      java.util.Vector x = new java.util.Vector();
      for (int i = 0; i < code.size(); i++) {
         Code myCode = (Code)code.elementAt(i);
         int cptIndex = myCode.browseInterfaceMethodref();
         if (cptIndex > 0) {
            boolean found = false;
            String s = pool.browseString(cptIndex);
            for (int j = 0; j < x.size(); j++) {
               if (((String)x.elementAt(j)).equals(s)) {
                  found = true;
                  break;
               }
            }
            if (!found) x.add(pool.browseString(cptIndex));
         }
      }
      String[] retVal = new String[x.size()];
      x.copyInto(retVal);
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Field:         dispatch                                              -
    -                                                                      -
    - Desc:          array used to dispatch for object instantiation       -
    -                   element[i][0] = opcode (same as array index)       -
    -                   element[i][1] = number of bytes for operation      -
    -                   element[i][2] = bytecode operation                 -
    -                   element[i][3] = subclass for opcode (null = Code)  -
    -----------------------------------------------------------------------*/
   static Object[][] dispatch = {
      {"0x00", "1", "nop",             null},
      {"0x01", "1", "aconst_null",     null},
      {"0x02", "1", "iconst_m1",       null},
      {"0x03", "1", "iconst_0",        null},
      {"0x04", "1", "iconst_1",        null},
      {"0x05", "1", "iconst_2",        null},
      {"0x06", "1", "iconst_3",        null},
      {"0x07", "1", "iconst_4",        null},
      {"0x08", "1", "iconst_5",        null},
      {"0x09", "1", "lconst_0",        null},
      {"0x0a", "1", "lconst_1",        null},
      {"0x0b", "1", "fconst_0",        null},
      {"0x0c", "1", "fconst_1",        null},
      {"0x0d", "1", "fconst_2",        null},
      {"0x0e", "1", "dconst_0",        null},
      {"0x0f", "1", "dconst_1",        null},
      {"0x10", "2", "bipush",          Code_bipush.class},
      {"0x11", "3", "sipush",          Code_sipush.class},
      {"0x12", "2", "ldc",             Code_ldc.class},
      {"0x13", "3", "ldc_w",           Code_Pool.class},
      {"0x14", "3", "ldc2_w",          Code_Pool.class},
      {"0x15", "2", "iload",           Code_VarTable.class},
      {"0x16", "2", "lload",           Code_VarTable.class},
      {"0x17", "2", "fload",           Code_VarTable.class},
      {"0x18", "2", "dload",           Code_VarTable.class},
      {"0x19", "2", "aload",           Code_VarTable.class},
      {"0x1a", "1", "iload_0",         null},
      {"0x1b", "1", "iload_1",         null},
      {"0x1c", "1", "iload_2",         null},
      {"0x1d", "1", "iload_3",         null},
      {"0x1e", "1", "lload_0",         null},
      {"0x1f", "1", "lload_1",         null},
      {"0x20", "1", "lload_2",         null},
      {"0x21", "1", "lload_3",         null},
      {"0x22", "1", "fload_0",         null},
      {"0x23", "1", "fload_1",         null},
      {"0x24", "1", "fload_2",         null},
      {"0x25", "1", "fload_3",         null},
      {"0x26", "1", "dload_0",         null},
      {"0x27", "1", "dload_1",         null},
      {"0x28", "1", "dload_2",         null},
      {"0x29", "1", "dload_3",         null},
      {"0x2a", "1", "aload_0",         null},
      {"0x2b", "1", "aload_1",         null},
      {"0x2c", "1", "aload_2",         null},
      {"0x2d", "1", "aload_3",         null},
      {"0x2e", "1", "iaload",          null},
      {"0x2f", "1", "laload",          null},
      {"0x30", "1", "faload",          null},
      {"0x31", "1", "daload",          null},
      {"0x32", "1", "aaload",          null},
      {"0x33", "1", "baload",          null},
      {"0x34", "1", "caload",          null},
      {"0x35", "1", "saload",          null},
      {"0x36", "2", "istore",          Code_VarTable.class},
      {"0x37", "2", "lstore",          Code_VarTable.class},
      {"0x38", "2", "fstore",          Code_VarTable.class},
      {"0x39", "2", "dstore",          Code_VarTable.class},
      {"0x3a", "2", "astore",          Code_VarTable.class},
      {"0x3b", "1", "istore_0",        null},
      {"0x3c", "1", "istore_1",        null},
      {"0x3d", "1", "istore_2",        null},
      {"0x3e", "1", "istore_3",        null},
      {"0x3f", "1", "lstore_0",        null},
      {"0x40", "1", "lstore_1",        null},
      {"0x41", "1", "lstore_2",        null},
      {"0x42", "1", "lstore_3",        null},
      {"0x43", "1", "fstore_0",        null},
      {"0x44", "1", "fstore_1",        null},
      {"0x45", "1", "fstore_2",        null},
      {"0x46", "1", "fstore_3",        null},
      {"0x47", "1", "dstore_0",        null},
      {"0x48", "1", "dstore_1",        null},
      {"0x49", "1", "dstore_2",        null},
      {"0x4a", "1", "dstore_3",        null},
      {"0x4b", "1", "astore_0",        null},
      {"0x4c", "1", "astore_1",        null},
      {"0x4d", "1", "astore_2",        null},
      {"0x4e", "1", "astore_3",        null},
      {"0x4f", "1", "iastore",         null},
      {"0x50", "1", "lastore",         null},
      {"0x51", "1", "fastore",         null},
      {"0x52", "1", "dastore",         null},
      {"0x53", "1", "aastore",         null},
      {"0x54", "1", "bastore",         null},
      {"0x55", "1", "castore",         null},
      {"0x56", "1", "sastore",         null},
      {"0x57", "1", "pop",             null},
      {"0x58", "1", "pop2",            null},
      {"0x59", "1", "dup",             null},
      {"0x5a", "1", "dup_x1",          null},
      {"0x5b", "1", "dup_x2",          null},
      {"0x5c", "1", "dup2",            null},
      {"0x5d", "1", "dup2_x1",         null},
      {"0x5e", "1", "dup2_x2",         null},
      {"0x5f", "1", "swap",            null},
      {"0x60", "1", "iadd",            null},
      {"0x61", "1", "ladd",            null},
      {"0x62", "1", "fadd",            null},
      {"0x63", "1", "dadd",            null},
      {"0x64", "1", "isub",            null},
      {"0x65", "1", "lsub",            null},
      {"0x66", "1", "fsub",            null},
      {"0x67", "1", "dsub",            null},
      {"0x68", "1", "imul",            null},
      {"0x69", "1", "lmul",            null},
      {"0x6a", "1", "fmul",            null},
      {"0x6b", "1", "dmul",            null},
      {"0x6c", "1", "idiv",            null},
      {"0x6d", "1", "ldiv",            null},
      {"0x6e", "1", "fdiv",            null},
      {"0x6f", "1", "ddiv",            null},
      {"0x70", "1", "irem",            null},
      {"0x71", "1", "lrem",            null},
      {"0x72", "1", "frem",            null},
      {"0x73", "1", "drem",            null},
      {"0x74", "1", "ineg",            null},
      {"0x75", "1", "lneg",            null},
      {"0x76", "1", "fneg",            null},
      {"0x77", "1", "dneg",            null},
      {"0x78", "1", "ishl",            null},
      {"0x79", "1", "lshl",            null},
      {"0x7a", "1", "ishr",            null},
      {"0x7b", "1", "lshr",            null},
      {"0x7c", "1", "iushr",           null},
      {"0x7d", "1", "lushr",           null},
      {"0x7e", "1", "iand",            null},
      {"0x7f", "1", "land",            null},
      {"0x80", "1", "ior",             null},
      {"0x81", "1", "lor",             null},
      {"0x82", "1", "ixor",            null},
      {"0x83", "1", "lxor",            null},
      {"0x84", "3", "iinc",            Code_iinc.class},
      {"0x85", "1", "i2l",             null},
      {"0x86", "1", "i2f",             null},
      {"0x87", "1", "i2d",             null},
      {"0x88", "1", "l2i",             null},
      {"0x89", "1", "l2f",             null},
      {"0x8a", "1", "l2d",             null},
      {"0x8b", "1", "f2i",             null},
      {"0x8c", "1", "f2l",             null},
      {"0x8d", "1", "f2d",             null},
      {"0x8e", "1", "d2i",             null},
      {"0x8f", "1", "d2l",             null},
      {"0x90", "1", "d2f",             null},
      {"0x91", "1", "i2b",             null},
      {"0x92", "1", "i2c",             null},
      {"0x93", "1", "i2s",             null},
      {"0x94", "1", "lcmp",            null},
      {"0x95", "1", "fcmpl",           null},
      {"0x96", "1", "fcmpg",           null},
      {"0x97", "1", "dcmpl",           null},
      {"0x98", "1", "dcmpg",           null},
      {"0x99", "3", "ifeq",            Code_Branch.class},
      {"0x9a", "3", "ifne",            Code_Branch.class},
      {"0x9b", "3", "iflt",            Code_Branch.class},
      {"0x9c", "3", "ifge",            Code_Branch.class},
      {"0x9d", "3", "ifgt",            Code_Branch.class},
      {"0x9e", "3", "ifle",            Code_Branch.class},
      {"0x9f", "3", "if_icmpeq",       Code_Branch.class},
      {"0xa0", "3", "if_icmpne",       Code_Branch.class},
      {"0xa1", "3", "if_icmplt",       Code_Branch.class},
      {"0xa2", "3", "if_icmpge",       Code_Branch.class},
      {"0xa3", "3", "if_icmpgt",       Code_Branch.class},
      {"0xa4", "3", "if_icmple",       Code_Branch.class},
      {"0xa5", "3", "if_acmpeq",       Code_Branch.class},
      {"0xa6", "3", "if_acmpne",       Code_Branch.class},
      {"0xa7", "3", "goto",            Code_Branch.class},
      {"0xa8", "3", "jsr",             Code_Branch.class},
      {"0xa9", "2", "ret",             Code_VarTable.class},
      {"0xaa", "0", "tableswitch",     Code_tableswitch.class},
      {"0xab", "0", "lookupswitch",    Code_lookupswitch.class},
      {"0xac", "1", "ireturn",         null},
      {"0xad", "1", "lreturn",         null},
      {"0xae", "1", "freturn",         null},
      {"0xaf", "1", "dreturn",         null},
      {"0xb0", "1", "areturn",         null},
      {"0xb1", "1", "return",          null},
      {"0xb2", "3", "getstatic",       Code_Pool.class},
      {"0xb3", "3", "putstatic",       Code_Pool.class},
      {"0xb4", "3", "getfield",        Code_Pool.class},
      {"0xb5", "3", "putfield",        Code_Pool.class},
      {"0xb6", "3", "invokevirtual",   Code_Pool.class},
      {"0xb7", "3", "invokespecial",   Code_Pool.class},
      {"0xb8", "3", "invokestatic",    Code_Pool.class},
      {"0xb9", "5", "invokeinterface", Code_invokeinterface.class},
      {"0xba", "1", "xxxunusedxxx",    null},
      {"0xbb", "3", "new",             Code_Pool.class},
      {"0xbc", "2", "newarray",        Code_newarray.class},
      {"0xbd", "3", "anewarray",       Code_Pool.class},
      {"0xbe", "1", "arraylength",     null},
      {"0xbf", "1", "athrow",          null},
      {"0xc0", "3", "checkcast",       Code_Pool.class},
      {"0xc1", "3", "instanceof",      Code_Pool.class},
      {"0xc2", "1", "monitorenter",    null},
      {"0xc3", "1", "monitorexit",     null},
      {"0xc4", "4", "wide",            Code_wide.class},
      {"0xc5", "4", "multianewarray",  Code_multianewarray.class},
      {"0xc6", "3", "ifnull",          Code_Branch.class},
      {"0xc7", "3", "ifnonnull",       Code_Branch.class},
      {"0xc8", "5", "goto_w",          Code_BranchInt.class},
      {"0xc9", "5", "jsr_w",           Code_BranchInt.class},
      {"0xca", "1", "breakpoint",      null},
      {"0xcb", "1", "xxxunusedxxx",    null},
      {"0xcc", "1", "xxxunusedxxx",    null},
      {"0xcd", "1", "xxxunusedxxx",    null},
      {"0xce", "1", "xxxunusedxxx",    null},
      {"0xcf", "1", "xxxunusedxxx",    null},
      {"0xd0", "1", "xxxunusedxxx",    null},
      {"0xd1", "1", "xxxunusedxxx",    null},
      {"0xd2", "1", "xxxunusedxxx",    null},
      {"0xd3", "1", "xxxunusedxxx",    null},
      {"0xd4", "1", "xxxunusedxxx",    null},
      {"0xd5", "1", "xxxunusedxxx",    null},
      {"0xd6", "1", "xxxunusedxxx",    null},
      {"0xd7", "1", "xxxunusedxxx",    null},
      {"0xd8", "1", "xxxunusedxxx",    null},
      {"0xd9", "1", "xxxunusedxxx",    null},
      {"0xda", "1", "xxxunusedxxx",    null},
      {"0xdb", "1", "xxxunusedxxx",    null},
      {"0xdc", "1", "xxxunusedxxx",    null},
      {"0xdd", "1", "xxxunusedxxx",    null},
      {"0xde", "1", "xxxunusedxxx",    null},
      {"0xdf", "1", "xxxunusedxxx",    null},
      {"0xe0", "1", "xxxunusedxxx",    null},
      {"0xe1", "1", "xxxunusedxxx",    null},
      {"0xe2", "1", "xxxunusedxxx",    null},
      {"0xe3", "1", "xxxunusedxxx",    null},
      {"0xe4", "1", "xxxunusedxxx",    null},
      {"0xe5", "1", "xxxunusedxxx",    null},
      {"0xe6", "1", "xxxunusedxxx",    null},
      {"0xe7", "1", "xxxunusedxxx",    null},
      {"0xe8", "1", "xxxunusedxxx",    null},
      {"0xe9", "1", "xxxunusedxxx",    null},
      {"0xea", "1", "xxxunusedxxx",    null},
      {"0xeb", "1", "xxxunusedxxx",    null},
      {"0xec", "1", "xxxunusedxxx",    null},
      {"0xed", "1", "xxxunusedxxx",    null},
      {"0xee", "1", "xxxunusedxxx",    null},
      {"0xef", "1", "xxxunusedxxx",    null},
      {"0xf0", "1", "xxxunusedxxx",    null},
      {"0xf1", "1", "xxxunusedxxx",    null},
      {"0xf2", "1", "xxxunusedxxx",    null},
      {"0xf3", "1", "xxxunusedxxx",    null},
      {"0xf4", "1", "xxxunusedxxx",    null},
      {"0xf5", "1", "xxxunusedxxx",    null},
      {"0xf6", "1", "xxxunusedxxx",    null},
      {"0xf7", "1", "xxxunusedxxx",    null},
      {"0xf8", "1", "xxxunusedxxx",    null},
      {"0xf9", "1", "xxxunusedxxx",    null},
      {"0xfa", "1", "xxxunusedxxx",    null},
      {"0xfb", "1", "xxxunusedxxx",    null},
      {"0xfc", "1", "xxxunusedxxx",    null},
      {"0xfd", "1", "xxxunusedxxx",    null},
      {"0xfe", "1", "impdep1",         null},
      {"0xff", "1", "impdep2",         null}
   };
}

/*=======================================================================
 = Class:         Code                                                  =
 =                                                                      =
 = Desc:          java bytecode instruction                             =
 =======================================================================*/
class Code {
   protected Pool_Collection pool;               // constant pool table
   protected String opdesc;                      // operation description
   protected int opcode;                         // operation code
   protected int opbytes = 0;                    // bytes that the operation occupies
   protected int pc = 0;                         // program counter for the operation
   protected boolean label = false;              // flag whether a label should be printed at this address

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code(DataInputStream ios, Pool_Collection pool, int opcode, int pc) {
      // save off the constant pool table for later reference
      this.pool = pool;

      // save off the operation code
      this.opcode = opcode;

      // save off the program counter of the code
      this.pc = pc;

      // save off the description of the operation
      this.opdesc = (String)Code_Collection.dispatch[opcode][2];

      // save off the number of bytes for the operations (note: lookupswitch & tableswitch overwrite)
      opbytes = Integer.parseInt((String)Code_Collection.dispatch[opcode][1]);
   }

   /*-----------------------------------------------------------------------
    - Method:        getLabel                                              -
    -                                                                      -
    - Desc:          default to no labels needed by operation              -
    -                overridden by subclass: Code_Branch; Code_BranchInt;  -
    -                Code_lookupswitch; Code_tableswitch                   -
    -----------------------------------------------------------------------*/
   void getLabel(Code_Collection code) {
   }

   /*-----------------------------------------------------------------------
    - Method:        setLabel                                              -
    -                                                                      -
    - Desc:          if pc for label is pc for opcode then set label flag  -
    -----------------------------------------------------------------------*/
   int setLabel(int pc) {
      if (this.pc == pc) label = true;
      return this.pc;
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      if (label) out.println(Code_Collection.toLabel(pc) + ":");
      out.print(pad("   " + jasminDesc(), ClassFile.SPACER));
   }

   /*-----------------------------------------------------------------------
    - Method:        jasminDesc                                            -
    -                                                                      -
    - Desc:          return the operator description                       -
    -                may be overriden by wide instructions                 -
    -----------------------------------------------------------------------*/
   String jasminDesc() {
      return opdesc;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseFieldref                                        -
    -                                                                      -
    - Desc:          default to no fields referenced by operation code     -
    -                overridden by subclass: Code_Pool                     -
    -----------------------------------------------------------------------*/
   int browseFieldref() {
      return -1;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseMethodref                                       -
    -                                                                      -
    - Desc:          default to no methods referenced by operation code    -
    -                overridden by subclass: Code_Pool                     -
    -----------------------------------------------------------------------*/
   int browseMethodref() {
      return -1;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInterfaceMethodref                              -
    -                                                                      -
    - Desc:          default to no interfaces referenced by operation code -
    -                overridden by subclass: Code_Pool                     -
    -----------------------------------------------------------------------*/
   int browseInterfaceMethodref() {
      return -1;
   }

   /*-----------------------------------------------------------------------
    - Method:        pad                                                   -
    -                                                                      -
    - Desc:          pad a string with specified spaces.  Done here to     -
    -                keep the code length manageable.                      -
    -----------------------------------------------------------------------*/
   static String pad(String s, int pad) {
      return ClassFile.pad(s, pad);
   }

   /*-----------------------------------------------------------------------
    - Method:        pad                                                   -
    -                                                                      -
    - Desc:          pad a string with specified spaces.  this overload of -
    -                the function prevents having to convert int to string -
    -----------------------------------------------------------------------*/
   static String pad(int n, int pad) {
      return pad(n + "", pad);
   }
}

/*=======================================================================
 = Class:         Code_Branch                                           =
 =                                                                      =
 = Desc:          instructions which perform relative branches          =
 =                   goto           if_acmpeq      if_acmpne            =
 =                   if_icmpeq      if_icmpge      if_icmpgt            =
 =                   if_icmple      if_icmplt      if_icmpne            =
 =                   ifeq           ifge           ifgt                 =
 =                   ifle           iflt           ifne                 =
 =                   ifnonnull      ifnull                              =
 =======================================================================*/
class Code_Branch extends Code {
   private int branch;                 // branch offset for the operation (relative to current pc)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_Branch(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // get the branch offset for the operation
      branch = ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        getLabel                                              -
    -                                                                      -
    - Desc:          label needed for branch address                       -
    -----------------------------------------------------------------------*/
   void getLabel(Code_Collection code) {
      code.setLabel(branch + pc);
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.print(Code_Collection.toLabel(pc+branch));
   }
}

/*=======================================================================
 = Class:         Code_BranchInt                                        =
 =                                                                      =
 = Desc:          instructions which perform branches (integer offset)  =
 =                   goto_w         jsr_w                               =
 =======================================================================*/
class Code_BranchInt extends Code {
   private int branch;                 // branch offset for the operation (relative to current pc)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_BranchInt(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // get the branch offset for the operation (branch is relative to current pc)
      branch = ios.readInt();
   }

   /*-----------------------------------------------------------------------
    - Method:        getLabel                                              -
    -                                                                      -
    - Desc:          label needed for branch address                       -
    -----------------------------------------------------------------------*/
   void getLabel(Code_Collection code) {
      code.setLabel(pc+branch);
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.print(Code_Collection.toLabel(pc+branch));
   }
}

/*=======================================================================
 = Class:         Code_VarTable                                         =
 =                                                                      =
 = Desc:          instructions which access local variables             =
 =                   aload          astore         dload                =
 =                   dstore         fload          fstore               =
 =                   iload          istore         lload                =
 =                   lstore         ret                                 =
 =======================================================================*/
class Code_VarTable extends Code {
   private int lvtIndex;               // index into the local variable table

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_VarTable(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // get the local variable table index
      lvtIndex = ios.read();
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.print(lvtIndex);
   }
}

/*=======================================================================
 = Class:         Code_Pool                                             =
 =                                                                      =
 = Desc:          instructions which access the constant pool table     =
 =                   anewarray      checkcast      getfield             =
 =                   getstatic      instanceof     invokespecial        =
 =                   invokestatic   invokevirtual  ldc_w                =
 =                   ldc2_w         new            putfield             =
 =                   putstatic                                          =
 =======================================================================*/
class Code_Pool extends Code {
   private int cptIndex;               // index into the constant pool table

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_Pool(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // get the constant pool index for the referenced constant
      cptIndex = ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.print(pool.toString(cptIndex));
   }

   /*-----------------------------------------------------------------------
    - Method:        browseFieldref                                        -
    -                                                                      -
    - Desc:          check if this instruction is referencing a field      -
    -----------------------------------------------------------------------*/
   int browseFieldref() {
      if (pool.isFieldref(cptIndex)) return cptIndex; else return -1;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseMethodref                                       -
    -                                                                      -
    - Desc:          check if this instruction is referencing a method     -
    -----------------------------------------------------------------------*/
   int browseMethodref() {
      if (pool.isMethodref(cptIndex)) return cptIndex; else return -1;
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInterfaceMethodref                              -
    -                                                                      -
    - Desc:          check if this instruction is referencing an interface -
    -----------------------------------------------------------------------*/
   int browseInterfaceMethodref() {
      if (pool.isInterfaceMethodref(cptIndex)) return cptIndex; else return -1;
   }
}

/*=======================================================================
 = Class:         Code_bipush                                           =
 =                                                                      =
 = Desc:          push byte                                             =
 =======================================================================*/
class Code_bipush extends Code {
   private int value;                  // value to push onto the stack

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_bipush(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // get value being pushed
      value = (byte)ios.read();
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.print(value);
   }
}

/*=======================================================================
 = Class:         Code_iinc                                             =
 =                                                                      =
 = Desc:          increment local variable by constant                  =
 =======================================================================*/
class Code_iinc extends Code_VarTable {
   private int value;                  // increment value for the operation

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_iinc(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // get the signed byte value for the increment
      value = (byte)ios.read();
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.print(" " + value);
   }
}

/*=======================================================================
 = Class:         Code_invokeinterface                                  =
 =                                                                      =
 = Desc:          invoke interface method                               =
 =======================================================================*/
class Code_invokeinterface extends Code_Pool {
   private int count;                  // number of words to pop off the stack
   private int unused;

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_invokeinterface(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // get the number of words to pop off the operand stack
      count = ios.read();

      // get past the unused byte
      unused = ios.read();
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.print(" " + count);
   }
}

/*=======================================================================
 = Class:         Code_ldc                                              =
 =                                                                      =
 = Desc:          push item from runtime constant pool                  =
 =======================================================================*/
class Code_ldc extends Code {
   private int cptIndex;               // index into the constant pool table

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_ldc(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // get the index into the constant pool table for the constant (note that index is a byte)
      cptIndex = ios.read();
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.print(pool.toString(cptIndex));
   }
}

/*=======================================================================
 = Class:         Code_lookupswitch                                     =
 =                                                                      =
 = Desc:          switch instruction for non-sequential indexes         =
 =======================================================================*/
class Code_lookupswitch extends Code {
   private int lookupSkip;             // number of pad bytes to allign default integer
   private int lookupDefault;          // offset to the default handler
   private int count;                  // number of match-branch pairs
   private int[] match;                // case values to match int
   private int[] branch;               // offset to the handler for the match

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_lookupswitch(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // calculate the number of bytes to skip to make default offset be 4 byte alligned
      lookupSkip = 4 - (pc + 1) % 4;
      if (lookupSkip == 4) lookupSkip = 0;
      if (lookupSkip > 0) ios.skip(lookupSkip);

      // get the offset to the default handler
      lookupDefault = ios.readInt();

      // get the number of match-branch pairs
      count = ios.readInt();

      // allocate the arrays to hold the pairs
      match = new int[count];
      branch = new int[count];

      for (int i = 0; i < count; i++) {
         // get the value to match for the case
         match[i] = ios.readInt();

         // get the branch offset for this match
         branch[i] = ios.readInt();
      }

      // calculate the number of bytes that the lookupswitch occupies
      opbytes = 9 + lookupSkip + count*8;
   }

   /*-----------------------------------------------------------------------
    - Method:        getLabel                                              -
    -                                                                      -
    - Desc:          label needed for branch addresses                     -
    -----------------------------------------------------------------------*/
   void getLabel(Code_Collection code) {
      for (int i = 0; i < count; i++) code.setLabel(pc+branch[i]);
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.println("");
      for (int i = 0; i < count; i++) {
         out.println(pad("        " + match[i] + " :", ClassFile.SPACER) +
            Code_Collection.toLabel(pc+branch[i]));
      }
      out.println(pad("        default :", ClassFile.SPACER) + Code_Collection.toLabel(pc+lookupDefault));
   }
}

/*=======================================================================
 = Class:         Code_multianewarray                                   =
 =                                                                      =
 = Desc:          create a new multidimensional array                   =
 =======================================================================*/
class Code_multianewarray extends Code_Pool {
   private int dimensions;             // number of dimensions to allocate for the multidimensional array

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_multianewarray(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // get number dimensions to allocate
      dimensions = ios.read();
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.print(" " + dimensions);
   }
}

/*=======================================================================
 = Class:         Code_newarray                                         =
 =                                                                      =
 = Desc:          create new array                                      =
 =======================================================================*/
class Code_newarray extends Code {
   private int arrayType;              // type of array to allocate

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_newarray(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // get the array type
      arrayType = ios.read();
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.print(getArrayType(arrayType));
   }

   /*-----------------------------------------------------------------------
    - Method:        getArrayType                                          -
    -                                                                      -
    - Desc:          get type of array being allocated                     -
    -----------------------------------------------------------------------*/
   static String getArrayType(int arrayType) {
      String s = "unknown";
      if (arrayType < arrayTypeMap.length) s = arrayTypeMap[arrayType][1];
      return s;
   }

   /*-----------------------------------------------------------------------
    - Field:         arrayTypeMap                                          -
    -                                                                      -
    - Desc:          array used to determine array type from index         -
    -                   element[i][0] = array type index                   -
    -                   element[i][1] = array type description             -
    -----------------------------------------------------------------------*/
   private static String[][] arrayTypeMap = {
      {"0",  "unknown"},
      {"1",  "unknown"},
      {"2",  "unknown"},
      {"3",  "unknown"},
      {"4",  "boolean"},
      {"5",  "char"},
      {"6",  "float"},
      {"7",  "double"},
      {"8",  "byte"},
      {"9",  "short"},
      {"10", "int"},
      {"11", "long"},
   };
}

/*=======================================================================
 = Class:         Code_sipush                                           =
 =                                                                      =
 = Desc:          push short                                            =
 =======================================================================*/
class Code_sipush extends Code {
   private int value;                  // short value to be pushed on stack

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_sipush(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // get signed short value to push on stack
      value = (short)ios.readShort();
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.print(value);
   }
}

/*=======================================================================
 = Class:         Code_tableswitch                                      =
 =                                                                      =
 = Desc:          switch instruction for sequential indexes             =
 =======================================================================*/
class Code_tableswitch extends Code {
   private int tableSkip;              // number of bytes to skip to 4-byte allign the default offset
   private int tableDefault;           // branch offset for the default handler
   private int tableLow;               // low value for sequence of matches
   private int tableHigh;              // high value for sequence of matches
   private int count;                  // number of cases in switch (computed internally from low & high)
   private int[] branch;               // branch offset for match

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_tableswitch(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // calculate the number of bytes to skip to 4-byte allign the default branch offset
      tableSkip = 4 - (pc + 1) % 4;
      if (tableSkip == 4) tableSkip = 0;
      if (tableSkip > 0) ios.skip(tableSkip);

      // get the default branch offset for the default case in the switch
      tableDefault = ios.readInt();

      // get the low value for the matches
      tableLow = ios.readInt();

      // get the high value for the matches
      tableHigh = ios.readInt();

      // compute the number of entries
      count = tableHigh - tableLow + 1;

      // allocate the array to hold the branch offsets
      branch = new int[count];

      // read in the branch offsets
      for (int i = 0; i < count; i++) branch[i] = ios.readInt();

      // calculate the number of bytes that the tableswitch operation occupies
      opbytes = 13 + tableSkip + count*4;
   }

   /*-----------------------------------------------------------------------
    - Method:        getLabel                                              -
    -                                                                      -
    - Desc:          label needed for branch addresses                     -
    -----------------------------------------------------------------------*/
   void getLabel(Code_Collection code) {
      for (int i = 0; i < count; i++) code.setLabel(pc+branch[i]);
      code.setLabel(pc+tableDefault);
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.println(tableLow + " " + tableHigh);
      for (int i = 0; i < count; i++) {
         out.println(pad("", ClassFile.SPACER) + Code_Collection.toLabel(pc+branch[i]));
      }
      out.println(pad("       default :", ClassFile.SPACER) + Code_Collection.toLabel(pc+tableDefault));
   }
}

/*=======================================================================
 = Class:         Code_wide                                             =
 =                                                                      =
 = Desc:          extend access of local variable index to 32 bit range =
 =======================================================================*/
class Code_wide extends Code {
   private int widecode;               // opcode for the wide extended instruction
   private int lvtIndex;               // index into the local variable table
   private int value;                  // increment value for the wide iinc operation
   private String wideDesc;            // description of the wide extended operation

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          read in the instruction from the input stream         -
    -----------------------------------------------------------------------*/
   Code_wide(DataInputStream ios, Pool_Collection pool, int opcode, int pc) throws IOException {
      super(ios, pool, opcode, pc);

      // get the opcode being wide extended
      widecode = ios.read();

      // get the index into the local variable table
      lvtIndex = ios.readShort();

      // get the description of the wide extended opcode
      wideDesc = (String)Code_Collection.dispatch[lvtIndex][2];

      // if the iinc is being extended, then 2 additional bytes need to be read in
      if (wideDesc.equals("iinc")) {
         // set the number of bytes occupied by the wide extended iinc opcode
         opbytes = 6;

         // read the increment value for the iinc instruction
         value = ios.readShort();
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        jasminDesc                                            -
    -                                                                      -
    - Desc:          override the operation description (don't want 'wide')-
    -----------------------------------------------------------------------*/
   String jasminDesc() {
      return wideDesc;
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output the operation instruction to the jasmin file   -
    -----------------------------------------------------------------------*/
   void jasmin(PrintStream out) {
      super.jasmin(out);
      out.print(lvtIndex);
      if (wideDesc.equals("iinc")) out.print(" " + value);
   }
}
