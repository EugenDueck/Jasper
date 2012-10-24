/* --- Copyright (c) Chris Rathman 1999. All rights reserved. -----------------
 > File:        jasper/ClassFile.java
 > Purpose:     Java class file format
 > Author:      Chris Rathman, 12 June 1999
 > Version:     1.00
 */
package jasper;
import java.io.*;
import java.util.zip.*;

/*=======================================================================
 = Class:         ClassFile                                             =
 =                                                                      =
 = Desc:          java class file format                                =
 =======================================================================*/
public class ClassFile implements Serializable {
   static final int SPACER = 25;                  // Column spacer constant for jasmine output

   private int magic;                             // magic field of class file 0xcafebabe
   private int minorVersion;                      // compiler minor version number
   private int majorVersion;                      // compiler major version number
   private Pool_Collection pool;                  // constant pool collection (symbol table)
   private int accessFlags;                       // access flags for class
   private int thisClass;                         // class name (via constant pool index)
   private int superClass;                        // super class name (via constant pool index)
   private Interface_Collection interfaces;       // interfaces implemented by the class
   private Field_Collection fields;               // fields defined by the class
   private Method_Collection methods;             // methods defined by the class
   private Attribute_Collection attributes;       // class attributes:
                                                  //    (SourceFile, InnerClasses, Synthetic, Deprecated)

   /*-----------------------------------------------------------------------
    - Method:        Class Constructor                                     -
    -                                                                      -
    - Desc:          construct object by reading in java class file        -
    -----------------------------------------------------------------------*/
   public ClassFile(String name) {
      DataInputStream ios = null;
      ZipInputStream zip = null;
      ZipEntry zin = null;

      // normalize the file name to expected format
      String fileName = parseFileDir(name) + parseFileName(name) + "." + parseFileExt(name);
      String className = fileName.replace(File.separatorChar, '/');
      System.out.println("Reading:   " + fileName);

      try {
         FIND: {
            // first check if the file is in the current directory
            File f = new File(fileName);
            if (f.exists()) {
               ios = new DataInputStream(new FileInputStream(fileName));
               break FIND;
            }

            // now check if the file is anywhere in the class path (or in a jar file)
            String[] classPath = getClassPath();
            for (int i = 0; i < classPath.length; i++) {
               f = new File(classPath[i]);
               if (f.exists()) {
                  if (f.isFile()) {
                     // I'm assuming that any files specified in the class path must be jars
                     zip = new ZipInputStream(new FileInputStream(classPath[i]));
                     for (zin = zip.getNextEntry(); zin != null; zin = zip.getNextEntry()) {
                        if (zin.getName().equals(className)) {
                           ios = new DataInputStream(zip);
                           break FIND;
                        }
                     }
                  } else if (f.isDirectory()) {
                     // if class path is directory check if file found along that path
                     f = new File(classPath[i] + File.separatorChar + fileName);
                     if (f.isFile()) {
                        ios = new DataInputStream(new FileInputStream(f));
                        break FIND;
                     }
                  }
               }
            }
            // if we get to this pount then throw file not found exception
            if (ios == null) throw new FileNotFoundException(fileName);
         }

         // read the magic bytes - abort if not a class file
         magic = ios.readInt();
         if (magic != 0xcafebabe) throw new IOException("File is not a java class file.");

         // read the compiler version
         minorVersion = ios.readShort();
         majorVersion = ios.readShort();

         // read the constant pool (symbol table area)
         pool = new Pool_Collection(ios);

         // read the class access flags
         accessFlags = ios.readShort();

         // get the name of this class (index into constant pool)
         thisClass = ios.readShort();

         // get the name of the super class (index into constant pool)
         superClass = ios.readShort();

         // read the interfaces that are implemented
         interfaces = new Interface_Collection(ios, pool);

         // read the class fields
         fields = new Field_Collection(ios, pool);

         // read the class methods
         methods = new Method_Collection(ios, pool);

         // read the attributes (SourceFile)
         attributes = new Attribute_Collection(ios, pool);

      } catch (FileNotFoundException e) {
         // report the error
         System.out.println(e);

      } catch (IOException e) {
         // report the error
         System.out.println(e);

         // dump the remaining bytes of the file as hex output (useful for debugging)
         dump(ios,  Integer.MAX_VALUE);
      }
   }

   /*-----------------------------------------------------------------------
    - Method:        accessString                                          -
    -                                                                      -
    - Desc:          build a string for the class access flags             -
    -----------------------------------------------------------------------*/
   private String accessString() {
      String retVal = "";
      if ((accessFlags & 0x0001) > 0) retVal += "public ";
      if ((accessFlags & 0x0010) > 0) retVal += "final ";
      if ((accessFlags & 0x0400) > 0) retVal += "abstract ";
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        jasmin                                                -
    -                                                                      -
    - Desc:          output a jasmin assembly file                         -
    -----------------------------------------------------------------------*/
   public void jasmin() {
      try{
         // jasmine uses a ".j" extension by default
         String name = browseClass() + ".j";
         String fileName = parseFileDir(name) + parseFileName(name) + "." + parseFileExt(name);

         // make sure there is a place to put it
         if (!parseFileDir(name).equals("")) {
            // test if directory for the class exists
            File f = new File("jasper.out" + File.separatorChar + parseFileDir(name));
            fileName = "jasper.out" + File.separatorChar + fileName;

            // if the directory path does exist, then create it under the current directory
            if (!f.exists()) f.mkdirs();
         }

         // open up the output stream to write the file
         PrintStream out = new PrintStream(new FileOutputStream(fileName));

         // print the .source directive
         attributes.jasmin(out);

         // print the .class or .interface directive
         if ((accessFlags & 0x0200) > 0) {
            out.print(pad(".interface", SPACER));
         } else {
            out.print(pad(".class", SPACER));
         }
         out.println(accessString() + pool.toString(thisClass));

         // print the .super directive
         if (superClass > 0) out.println(pad(".super", SPACER) + pool.toString(superClass));

         // print the .implements directives
         interfaces.jasmin(out);
         out.println("");

         // print the .field directives
         fields.jasmin(out);
         out.println("");

         // print the .method directives
         methods.jasmin(out);

         // echo that the jasmine file has been completed
         System.out.println("Generated: " + fileName);

      } catch (IOException e) {
         // report the error
         System.out.println(e);
      }
   }


   /*-----------------------------------------------------------------------
    - Method:        browseSourceFile                                      -
    -                                                                      -
    - Desc:          class source file name                                -
    -----------------------------------------------------------------------*/
   public String browseSourceFile() {
      return attributes.browseDeprecated() + attributes.browseSynthetic() + accessString() +
         attributes.browseSourceFile();
   }

   /*-----------------------------------------------------------------------
    - Method:        browseClass                                           -
    -                                                                      -
    - Desc:          class name (definition).                              -
    -----------------------------------------------------------------------*/
   public String browseClass() {
      // note: jasper will add the attributes 'synthetic' and 'deprecated' if present for the class
      return pool.browseString(thisClass);
   }

   /*-----------------------------------------------------------------------
    - Method:        browseSuper                                           -
    -                                                                      -
    - Desc:          super class name (extends)                            -
    -----------------------------------------------------------------------*/
   public String browseSuper() {
      if (superClass > 0) return pool.browseString(superClass); else return "";
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInterfaces                                      -
    -                                                                      -
    - Desc:          interfaces which are implemented by the class         -
    -----------------------------------------------------------------------*/
   public String[] browseInterfaces() {
      return interfaces.browseInterfaces();
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInnerClasses                                    -
    -                                                                      -
    - Desc:          inner classes of the class                            -
    -----------------------------------------------------------------------*/
   public String[][] browseInnerClasses() {
      return attributes.browseInnerClasses();
   }

   /*-----------------------------------------------------------------------
    - Method:        browseFields                                          -
    -                                                                      -
    - Desc:          fields defined by the class.                          -
    -----------------------------------------------------------------------*/
   public String[] browseFields() {
      // note: jasper will add the attributes 'synthetic' and 'deprecated' if present for the field
      return fields.browseFields(browseClass());
   }

   /*-----------------------------------------------------------------------
    - Method:        browseMethods                                         -
    -                                                                      -
    - Desc:          methods defined by the class                          -
    -----------------------------------------------------------------------*/
   public String[] browseMethods() {
      // note: jasper will add the attributes 'synthetic' and 'deprecated' if present for the method
      return methods.browseMethods(browseClass());
   }

   /*-----------------------------------------------------------------------
    - Method:        browseFieldrefs                                       -
    -                                                                      -
    - Desc:          fields accessed (indexed by class methods)            -
    -----------------------------------------------------------------------*/
   public String[][] browseFieldrefs() {
      return methods.browseFieldrefs();
   }

   /*-----------------------------------------------------------------------
    - Method:        browseMethodrefs                                      -
    -                                                                      -
    - Desc:          methods accessed (indexed by class methods)           -
    -----------------------------------------------------------------------*/
   public String[][] browseMethodrefs() {
      return methods.browseMethodrefs();
   }

   /*-----------------------------------------------------------------------
    - Method:        browseInterfaceMethodrefs                             -
    -                                                                      -
    - Desc:          interface methods accessed (indexed by class methods) -
    -----------------------------------------------------------------------*/
   public String[][] browseInterfaceMethodrefs() {
      return methods.browseInterfaceMethodrefs();
   }

   /*-----------------------------------------------------------------------
    - Method:        pad                                                   -
    -                                                                      -
    - Desc:          pad a string with specified spaces.  sure wish the    -
    -                String class would give me this automatically.        -
    -----------------------------------------------------------------------*/
   public static String pad(String s, int pad) {
      StringBuffer a = new StringBuffer(pad);
      for (int i = 0; i < pad; i++) a = a.append(" ");
      return s + a.substring(s.length());
   }

   /*-----------------------------------------------------------------------
    - Method:        pad                                                   -
    -                                                                      -
    - Desc:          pad a string with specified spaces.  this overload of -
    -                the function prevents having to convert int to string -
    -----------------------------------------------------------------------*/
   public static String pad(int n, int pad) {
      return pad(n + "", pad);
   }

   /*-----------------------------------------------------------------------
    - Method:        getClassPath                                          -
    -                                                                      -
    - Desc:          get the java class path                               -
    -----------------------------------------------------------------------*/
   private String[] getClassPath() {
      java.util.Vector x = new java.util.Vector();
      String classPath = System.getProperty("java.class.path");
      String s = "";
      for (int i = 0; i < classPath.length(); i++) {
         if (classPath.charAt(i) == ';') {
            x.add(s);
            s = "";
         } else {
            s += classPath.charAt(i);
         }
      }
      if (!s.equals("")) x.add(s);
      String javaHome = System.getProperty("java.home");
      if (javaHome != null) x.add(javaHome + File.separatorChar + "lib" + File.separatorChar + "rt.jar");
      String[] retVal = new String[x.size()];
      x.copyInto(retVal);
      return retVal;
   }

   /*-----------------------------------------------------------------------
    - Method:        parseFileDir                                          -
    -                                                                      -
    - Desc:          parse the directory path from a file string           -
    -----------------------------------------------------------------------*/
   public static String parseFileDir(String s) {
      String fileDir = s;
      fileDir = fileDir.replace('/', File.separatorChar);
      fileDir = fileDir.replace('\\', File.separatorChar);
      int i = fileDir.lastIndexOf('.');
      if (i > 0) {
         fileDir = fileDir.substring(0, i).replace('.', File.separatorChar) + fileDir.substring(i);
      }
      if (fileDir.lastIndexOf(File.separatorChar) >= 0) {
         fileDir = fileDir.substring(0, fileDir.lastIndexOf(File.separatorChar) + 1);
      } else {
         fileDir = "";
      }
      fileDir = fileDir.replace('.', File.separatorChar);
      if (!fileDir.equals("")) {
         if (fileDir.charAt(fileDir.length()-1) != File.separatorChar) fileDir += File.separatorChar;
      }
      return fileDir;
   }

   /*-----------------------------------------------------------------------
    - Method:        parseFileName                                         -
    -                                                                      -
    - Desc:          parse the base file name from a file string- kill ext -
    -----------------------------------------------------------------------*/
   public static String parseFileName(String s) {
      String fileName = s;
      fileName = fileName.replace('/', File.separatorChar);
      fileName = fileName.replace('\\', File.separatorChar);
      int i = fileName.lastIndexOf('.');
      if (i > 0) {
         fileName = fileName.substring(0, i).replace('.', File.separatorChar) + fileName.substring(i);
      }
      if (fileName.lastIndexOf(File.separatorChar) >= 0) {
         fileName = fileName.substring(fileName.lastIndexOf(File.separatorChar) + 1);
      }
      if (fileName.lastIndexOf(".") >= 0) {
         fileName = fileName.substring(0, fileName.lastIndexOf("."));
      }
      return fileName;
   }

   /*-----------------------------------------------------------------------
    - Method:        parseFileExt                                          -
    -                                                                      -
    - Desc:          parse the file extension from a file string           -
    -----------------------------------------------------------------------*/
   public static String parseFileExt(String s) {
      String fileExt = s;
      if (fileExt.lastIndexOf(".") >= 0) {
         fileExt = fileExt.substring(fileExt.lastIndexOf(".") + 1);
      } else {
         fileExt = "class";
      }
      return fileExt;
   }

   /*-----------------------------------------------------------------------
    - Method:        dump                                                  -
    -                                                                      -
    - Desc:          hex dump of an input file stream                      -
    -----------------------------------------------------------------------*/
   public static void dump(DataInputStream ios, int length) {
      try {
         String s;
         String a = "";
         for (int i = 0, j = 0; i < length; i++) {
            int val = ios.read();
            if (val < 0) System.exit(0);
            if (j == 0) {
               System.out.println("  " + a);
               a = "";
               s = Integer.toHexString(i);
               s = s + "      ".substring(s.length());
               System.out.print(s + "  ");
            }
            if (j == 8) System.out.print("  ");
            s = Integer.toHexString(val);
            if (s.length() < 2) s = "0" + s;
            System.out.print(s + " ");
            if ((val > 32) && (val < 128)) a += (char)val; else a += ' ';
            if (++j == 16) j = 0;
         }
         System.out.println("");
      } catch(IOException e) {
         System.out.println(e);
      }
   }
}
