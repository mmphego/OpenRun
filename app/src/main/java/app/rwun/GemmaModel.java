package app.rwun;

import java.io.*;import java.security.*;
/** A pinned publisher artefact: arbitrary model files are not executed. */
final class GemmaModel {
 static final String ID="gemma3-1b-int4",NAME="Gemma 3 · 1B",REVISION="d448835d84411a08f577e25f3afebe528402d73b";
 static final String FILE="Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm";
 static final long BYTES=584417280L;
 static final String SHA256="1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be";
 static final String SOURCE="https://huggingface.co/litert-community/Gemma3-1B-IT";
 static final String DOWNLOAD=SOURCE+"/resolve/"+REVISION+"/"+FILE;
 interface Progress {void bytes(long count);}
 static String copyVerified(InputStream input,OutputStream output,Progress progress)throws Exception{
  MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] block=new byte[65536];long count=0;int n;
  while((n=input.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Import cancelled");count+=n;if(count>BYTES)throw new IOException("This file is larger than the supported Gemma model.");digest.update(block,0,n);output.write(block,0,n);progress.bytes(count);}
  StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
  if(count!=BYTES||!hash.toString().equals(SHA256))throw new IOException("Model verification failed. Choose the exact Gemma 3 1B INT4 .litertlm file linked here. The previous model is kept.");
  return hash.toString();
 }
 static String prompt(String summary){if(summary==null||summary.length()>5000)throw new IllegalArgumentException("Invalid coaching summary");return "You are OpenRun's offline running explainer. Explain this verified summary in at most 90 words. Do not invent activities, change the plan, give new mileage or pace targets, predict a finish time, diagnose injury or clear a training hold. Missing history means unknown fitness. If held, say to review the hold. Use supportive, concrete language. Summary: "+summary;}
}
