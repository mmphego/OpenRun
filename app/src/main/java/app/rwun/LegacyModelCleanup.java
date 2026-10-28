package app.rwun;

import java.io.File;
import java.io.IOException;

/** Removes only retired model downloads/cache during upgrade; never touches training storage. */
final class LegacyModelCleanup {
  private LegacyModelCleanup() {}
  static void remove(File files,File cache){
    for(File retired:new File[]{new File(files,"offline-coach"),new File(cache,"gemma")}){
      try{File parent=retired.getParentFile().getCanonicalFile();delete(new File(parent,retired.getName()),0,new int[]{2000});}catch(IOException ignored){/* Retry at the next process start. */}
    }
  }
  private static void delete(File file,int depth,int[] remaining)throws IOException {
    if(depth>16||--remaining[0]<0||!file.getAbsoluteFile().equals(file.getCanonicalFile()))return;
    if(file.isDirectory()){File[] children=file.listFiles();if(children==null)return;for(File child:children)delete(child,depth+1,remaining);}
    if(file.exists()&&!file.delete())throw new IOException("Retired model file could not be removed");
  }
}
