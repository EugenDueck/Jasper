/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/Jasper.java
 > Purpose:     Write Jasmin and browser files from input java class files
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.0.1
 */
package jasper;
import java.io.*;
import java.util.zip.*;

/*=======================================================================
 = Class:         Jasper                                                =
 =                                                                      =
 = Desc:          program to write Jasmin & Browse files                =
 =======================================================================*/
public class Jasper {
   public static final String version = "v1.0.1";  // jasper software version number

   /*-----------------------------------------------------------------------
    - Method:        main                                                  -
    -                                                                      -
    - Desc:          program entry point                                   -
    -----------------------------------------------------------------------*/
   public static void main(String[] args) {
      String accessPermission;
      boolean jasmin = true;
      boolean browse = false;
      boolean recurse = false;

      java.util.Vector classList = new java.util.Vector();

      for (int i = 0; i < args.length; i++) {
         if (args[i].charAt(0) != '-') {
            // put the class file name into the list of programs to read
            addClassFile(args[i], classList);

         } else {
            if (args[i].equals("--jasmin")) {
               // turn off the output to the jasmin assembly files
               jasmin = false;

            } else if(args[i].equals("-jasmin")) {
               // turn on the output to the jasmin assembly files
               jasmin = true;

            } else if(args[i].equals("-browse")) {
               // enable the output to the browse files
               browse = true;

            } else if(args[i].equals("-recurse")) {
               // recurse through the inheritance and composition for the class
               recurse = true;

            } else if(args[i].equals("-version")) {
               // print out the program name and version number
               printVersion();
               System.exit(0);

            } else if(args[i].equals("--version")) {
               // print out the program name, version number and BSD License
               printVersion();
               printLicense();
               System.exit(0);

            } else if((args[i].equals("-?")) || (args[i].equals("-help"))) {
               // explain the program usage and options which are available
               printVersion();
               printOptions();
               System.exit(0);

            } else {
               // option not recognized so give up
               System.out.println("Unrecognized option: " + args[i]);
               System.out.println("Could not run jasper.");
               printVersion();
               printOptions();
               System.exit(0);
            }
         }
      }

      // read in the files (and add new ones along the way for recurse option
      for (int i = 0; i < classList.size(); i++) {
         // get the next class to read in
         String fileName = (String)classList.elementAt(i);

         // pull in the class
         ClassFile cls = new ClassFile(fileName);

         // output the assembly files
         if (jasmin) cls.jasmin();

         // echo the browse output
         if (browse) browseDump(cls);

         // if recurse, then add classes to list that are referred to by inheritance and composition
         if (recurse) recurseClasses(cls, classList);
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        recurseClasses                                        -
    -                                                                      -
    - Desc:          trundle through browse strings to find class ref's    -
    -----------------------------------------------------------------------*/
   static void recurseClasses(ClassFile cls, java.util.Vector classList) {
      // bring in super class
      String newClass = cls.browseSuper();
      if (!newClass.equals("")) addClassFile(newClass + ".class", classList);

      // bring in interface classes
      String[] newClassArray = cls.browseInterfaces();
      for (int j = 0; j < newClassArray.length; j++) {
         newClass = newClassArray[j];
         addClassFile(newClass + ".class", classList);
      }

      // bring in field types
      newClassArray = cls.browseFields();
      for (int j = 0; j < newClassArray.length; j++) {
         // kill the field access permission attributes
         newClass = stripAccess(newClassArray[j]);

         // reduce the string to the field type
         if (newClass.indexOf(' ') > 0) newClass = newClass.substring(0, newClass.indexOf(' '));

         // strip off any array indexes on the field type
         if (newClass.indexOf('[') > 0) newClass = newClass.substring(0, newClass.indexOf('['));

         // add the class to the list of classes to read
         addClassFile(newClass + ".class", classList);
      }

      // bring in method types
      newClassArray = cls.browseMethods();
      for (int j = 0; j < newClassArray.length; j++) {
         // kill the field access permission attributes
         newClass = stripAccess(newClassArray[j]);

         // reduce the string to the field type
         if (newClass.indexOf(' ') > 0) newClass = newClass.substring(0, newClass.indexOf(' '));

         // strip off any array indexes on the field type
         if (newClass.indexOf('[') > 0) newClass = newClass.substring(0, newClass.indexOf('['));

         // add the class to the list of classes to read
         addClassFile(newClass + ".class", classList);

         // bring in method parameters
         String params = newClassArray[j];
         params = params.substring(params.indexOf('(')+1);
         params = params.substring(0, params.indexOf(')')+1);
         while (params.length() > 1) {
            int n = params.indexOf(',');
            if (n > 0) {
               newClass = params.substring(0, n);
               params = params.substring(n + 2);
            } else {
               newClass = params.substring(0, params.indexOf(')'));
               params = "";
            }
            if (newClass.indexOf('[') > 0) newClass = newClass.substring(0, newClass.indexOf('['));
            addClassFile(newClass + ".class", classList);
         }
      }

      // bring in the fields referenced by class methods
      String[][] newClassRef = cls.browseFieldrefs();
      for (int j = 0; j < newClassRef.length; j++) {
         for (int k = 0; k < newClassRef[j].length; k++) {
            // kill the field access permission attributes
            newClass = stripAccess(newClassRef[j][k]);

            // reduce the string to the field type
            if (newClass.indexOf(' ') > 0) newClass = newClass.substring(0, newClass.indexOf(' '));

            // strip off any array indexes on the field type
            if (newClass.indexOf('[') > 0) newClass = newClass.substring(0, newClass.indexOf('['));

            // add the class to the list of classes to read
            addClassFile(newClass + ".class", classList);

            // get the class name of the field being referred to
            newClass = stripAccess(newClassRef[j][k]);
            if (newClass.lastIndexOf(' ') > 0) newClass = newClass.substring(newClass.lastIndexOf(' ') + 1);

            // strip off the field name
            if (newClass.lastIndexOf('.') > 0) newClass = newClass.substring(0, newClass.lastIndexOf('.'));

            // add the class to the list of classes to read
            addClassFile(newClass + ".class", classList);
         }
      }

      // bring in the methods referenced by class methods
      newClassRef = cls.browseMethodrefs();
      for (int j = 0; j < newClassRef.length; j++) {
         for (int k = 0; k < newClassRef[j].length; k++) {
            // kill the field access permission attributes
            newClass = stripAccess(newClassRef[j][k]);

            // reduce the string to the method return type
            if (newClass.indexOf(' ') > 0) newClass = newClass.substring(0, newClass.indexOf(' '));

            // strip off any array indexes on the return type
            if (newClass.indexOf('[') > 0) newClass = newClass.substring(0, newClass.indexOf('['));

            if (!newClass.equals("new")) {
               // add the class to the list of classes to read
               addClassFile(newClass + ".class", classList);

               // get the class name of the method being referred to
               newClass = stripAccess(newClassRef[j][k]);
               if (newClass.indexOf('(') > 0) newClass = newClass.substring(0, newClass.indexOf('('));
               if (newClass.lastIndexOf(' ') > 0) newClass = newClass.substring(newClass.lastIndexOf(' ') + 1);

               // strip off the method name
               if (newClass.lastIndexOf('.') > 0) newClass = newClass.substring(0, newClass.lastIndexOf('.'));

               // add the class to the list of classes to read
               addClassFile(newClass + ".class", classList);
            }

            // bring in method parameters
            String params = newClassRef[j][k];
            params = params.substring(params.indexOf('(')+1);
            params = params.substring(0, params.indexOf(')')+1);
            while (params.length() > 1) {
               int n = params.indexOf(',');
               if (n > 0) {
                  newClass = params.substring(0, n);
                  params = params.substring(n + 2);
               } else {
                  newClass = params.substring(0, params.indexOf(')'));
                  params = "";
               }
               if (newClass.indexOf('[') > 0) newClass = newClass.substring(0, newClass.indexOf('['));
               addClassFile(newClass + ".class", classList);
            }
         }
      }

      // bring in the interface methods referenced by class methods
      newClassRef = cls.browseMethodrefs();
      for (int j = 0; j < newClassRef.length; j++) {
         for (int k = 0; k < newClassRef[j].length; k++) {
            // kill the field access permission attributes
            newClass = stripAccess(newClassRef[j][k]);

            // reduce the string to the method return type
            if (newClass.indexOf(' ') > 0) newClass = newClass.substring(0, newClass.indexOf(' '));

            // strip off any array indexes on the return type
            if (newClass.indexOf('[') > 0) newClass = newClass.substring(0, newClass.indexOf('['));

            if (!newClass.equals("new")) {
               // add the class to the list of classes to read
               addClassFile(newClass + ".class", classList);

               // get the class name of the method being referred to
               newClass = stripAccess(newClassRef[j][k]);
               if (newClass.indexOf('(') > 0) newClass = newClass.substring(0, newClass.indexOf('('));
               if (newClass.lastIndexOf(' ') > 0) newClass = newClass.substring(newClass.lastIndexOf(' ') + 1);

               // strip off the method name
               if (newClass.lastIndexOf('.') > 0) newClass = newClass.substring(0, newClass.lastIndexOf('.'));

               // add the class to the list of classes to read
               addClassFile(newClass + ".class", classList);
            }

            // bring in method parameters
            String params = newClassRef[j][k];
            params = params.substring(params.indexOf('(')+1);
            params = params.substring(0, params.indexOf(')')+1);
            while (params.length() > 1) {
               int n = params.indexOf(',');
               if (n > 0) {
                  newClass = params.substring(0, n);
                  params = params.substring(n + 2);
               } else {
                  newClass = params.substring(0, params.indexOf(')'));
                  params = "";
               }
               if (newClass.indexOf('[') > 0) newClass = newClass.substring(0, newClass.indexOf('['));
               addClassFile(newClass + ".class", classList);
            }
         }
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        addClassFile                                          -
    -                                                                      -
    - Desc:          if class not in process list, then add it             -
    -----------------------------------------------------------------------*/
   static void addClassFile(String name, java.util.Vector classList) {
      // normalize the file name
      String fileName = ClassFile.parseFileDir(name) + ClassFile.parseFileName(name) +
         "." + ClassFile.parseFileExt(name);

      // don't add the primitive types since there is no class file associated with them
      if (fileName.equals(".class")) return;
      if (fileName.equals("byte.class")) return;
      if (fileName.equals("short.class")) return;
      if (fileName.equals("int.class")) return;
      if (fileName.equals("long.class")) return;
      if (fileName.equals("boolean.class")) return;
      if (fileName.equals("float.class")) return;
      if (fileName.equals("double.class")) return;
      if (fileName.equals("char.class")) return;
      if (fileName.equals("void.class")) return;

      // check if file has already been read in
      for (int i = 0; i < classList.size(); i++) {
         if (((String)classList.elementAt(i)).equals(fileName)) return;
      }

      // add class to the list of those read
      classList.add(fileName);
   }

   /*-----------------------------------------------------------------------
    - Method:        stripAccess                                           -
    -                                                                      -
    - Desc:          strip access permission from fields and methods       -
    -                   element[i][0] = permission string to match         -
    -                   element[i][1] = field/method as synthetic          -
    -----------------------------------------------------------------------*/
   static String stripAccess(String newClass) {
      // loop until all the known access flags are stripped
      boolean flag = true;
      while (flag) {
         flag = false;
         for (int i = 0; i < accessStrings.length; i++) {
            String s = accessStrings[i][0];
            if (newClass.substring(0, s.length()).equals(s)) {
               // strip the attribute away from the string
               newClass = newClass.substring(s.length());
               flag = true;

               // check if this is a synthetic field/method
               boolean synthetic = accessStrings[i][1].equals("1");
               if (synthetic) return "";
            }
         }
      }
      return newClass;
   }

   /*-----------------------------------------------------------------------
    - Method:        accessStrings                                         -
    -                                                                      -
    - Desc:          array of access permission strings used for strip     -
    -                   element[i][0] = permission string to match         -
    -                   element[i][1] = synthetic field/method) ("1"=true) -
    -----------------------------------------------------------------------*/
   static String[][] accessStrings = {
      {"public ",       "0"},
      {"private ",      "0"},
      {"protected ",    "0"},
      {"static ",       "0"},
      {"final ",        "0"},
      {"synchronized ", "0"},
      {"volatile ",     "0"},
      {"transient ",    "0"},
      {"native ",       "0"},
      {"abstract ",     "0"},
      {"#deprecated# ", "0"},
      {"#synthetic# ",  "1"},
   };

   /*-----------------------------------------------------------------------
    - Method:        browseDump                                            -
    -                                                                      -
    - Desc:          echo the browse                                       -
    -----------------------------------------------------------------------*/
   static void browseDump(ClassFile cls) {
      String[] ix = cls.browseInterfaces();
      String[] fx = cls.browseFields();
      String[] mx = cls.browseMethods();
      String[][] mf = cls.browseFieldrefs();
      String[][] mm = cls.browseMethodrefs();
      String[][] mi = cls.browseInterfaceMethodrefs();
      String[][] ic = cls.browseInnerClasses();

      System.out.println("+++++++++++++++++++++++");
      System.out.println("   SourceFile = " + cls.browseSourceFile());
      System.out.println("   class      = " + cls.browseClass());
      System.out.println("   extends    = " + cls.browseSuper());
      for (int i = 0; i < ix.length; i++) System.out.println("   implements = " + ix[i]);
      for (int i = 0; i < fx.length; i++) System.out.println("   field      = " + fx[i]);
      for (int i = 0; i < mx.length; i++) {
         System.out.println("   method     = " + mx[i]);
         for (int j = 0; j < mf[i].length; j++) System.out.println("      fields     = " + mf[i][j]);
         for (int j = 0; j < mm[i].length; j++) System.out.println("      methods    = " + mm[i][j]);
         for (int j = 0; j < mi[i].length; j++) System.out.println("      interfaces = " + mi[i][j]);
      }
      for (int i = 0; i < ic.length; i++) {
         System.out.println("innerClass[" + i + "] = " + ic[i][0]);
         System.out.println("outerClass[" + i + "] = " + ic[i][1]);
         System.out.println("innerName[" + i + "]  = " + ic[i][2]);
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        printVersion                                          -
    -                                                                      -
    - Desc:          echo the program name and version number              -
    -----------------------------------------------------------------------*/
   private static void printVersion() {
      System.out.println("Jasper Version " + version +
         "  Copyright (c) Chris Rathman 1999. All rights reserved.");
      System.out.println("Syntax:  java [java-options] jasper/Jasper [jasper-options] files.class");
      System.out.println("");
   }

   /*-----------------------------------------------------------------------
    - Method:        printOptions                                          -
    -                                                                      -
    - Desc:          echo the program options                              -
    -----------------------------------------------------------------------*/
   private static void printOptions() {
      System.out.println("Jasper recognizes the following options:");
      System.out.println("   --jasmin   Disable jasmin file output");
      System.out.println("   -jasmin    Enable jasmin file output (default)");
      System.out.println("   -browse    Enable output to the browse files");
      System.out.println("   -recurse   Recurse through the inheritance and composition for the class");
      System.out.println("   -help      View Jasper help");
      System.out.println("   -version   View Jasper version number");
      System.out.println("   --version  View Jasper license");
      System.out.println("");
   }

   /*-----------------------------------------------------------------------
    - Method:        printLicense                                          -
    -                                                                      -
    - Desc:          Echo the software license                             -
    -----------------------------------------------------------------------*/
   static void printLicense() {
      System.out.println("");
      System.out.println("Redistribution and use in source and binary forms, with or without");
      System.out.println("modification, are permitted provided that the following conditions");
      System.out.println("are met:");
      System.out.println("");
      System.out.println("  1. Redistributions of source code must retain the above copyright");
      System.out.println("     notice, this list of conditions and the following disclaimer.");
      System.out.println("  2. Redistributions in binary form must reproduce the above copyright");
      System.out.println("     notice, this list of conditions and the following disclaimer in the");
      System.out.println("     documentation and/or other materials provided with the distribution.");
      System.out.println("  3. All advertising materials mentioning features or use of this software");
      System.out.println("     must display the following acknowledgement:");
      System.out.println("       This product includes software developed by Chris Rathman and");
      System.out.println("       its contributors.");
      System.out.println("  4. Neither the name of Chris Rathman nor the names of its contributors");
      System.out.println("     may be used to endorse or promote products derived from this software");
      System.out.println("     without specific prior written permission.");
      System.out.println("");
      System.out.println("THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS ``AS IS''");
      System.out.println("AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE");
      System.out.println("IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE");
      System.out.println("ARE DISCLAIMED. IN NO EVENT SHALL CHRIS RATHMAN OR CONTRIBUTORS BE LIABLE FOR");
      System.out.println("ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES");
      System.out.println("(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;");
      System.out.println("LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND");
      System.out.println("ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT");
      System.out.println("(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS");
      System.out.println("SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.");
      System.out.println("");
   }
}
