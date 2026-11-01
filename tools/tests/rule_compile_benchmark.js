'use strict';
// Optional paired JVM microbenchmark against a frozen pre-change engine. Not an Android/WebView load measurement.
const fs=require('fs'),path=require('path'),os=require('os'),cp=require('child_process');
const args=process.argv.slice(2),option=(n,d)=>args.includes(n)?args[args.indexOf(n)+1]:d;
const repo=path.resolve(__dirname,'../..'),src=path.join(repo,'app/src/main/java/com/xinyv/median');
const before=option('--before');
if(!before)throw new Error('Supply a frozen pre-change AdBlockEngine.java with --before');
const out=path.resolve(args.includes('--out')?option('--out'):fs.mkdtempSync(path.join(os.tmpdir(),'median-rule-compile-benchmark-')));
const jdk=option('--jdk',process.env.JAVA_HOME||'');
const executable=name=>jdk?path.join(jdk,'bin',name+(process.platform==='win32'?'.exe':'')):name;
fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(path.join(out,'BeforeAdBlockEngine.java'),fs.readFileSync(before,'utf8').replaceAll('AdBlockEngine','BeforeAdBlockEngine'));
fs.writeFileSync(path.join(out,'RuleCompileBenchmark.java'),`package com.xinyv.median;
import java.util.*;
public final class RuleCompileBenchmark {
 static final BeforeAdBlockEngine before = new BeforeAdBlockEngine();
 static final AdBlockEngine after = new AdBlockEngine();
 static String input; static int sink;
 static long run(boolean candidate){
  long start=System.nanoTime();
  int count=candidate?after.compileRules("",Collections.singletonList(input),()->false).count:
   before.compileRules("",Collections.singletonList(input)).count;
  long elapsed=System.nanoTime()-start;
  if(count!=300000)throw new AssertionError("rule count changed");sink+=count;return elapsed;
 }
 public static void main(String[] args){
  StringBuilder rules=new StringBuilder(8000000);
  for(int i=0;i<300000;i++)rules.append("||tracker").append(i).append(".example^\\n");
  input=rules.toString();
  for(int i=0;i<4;i++){run(false);run(true);}
  long[] b=new long[11],a=new long[11];
  for(int i=0;i<11;i++){if((i&1)==0){b[i]=run(false);a[i]=run(true);}else{a[i]=run(true);b[i]=run(false);}}
  System.out.println("RuleCompileBenchmark rules=300000 warmup=4 rounds=11 beforeMs="+Arrays.toString(b)+" afterMs="+Arrays.toString(a));
  Arrays.sort(b);Arrays.sort(a);
  System.out.println("RuleCompileBenchmark beforeMedianMs="+b[5]/1e6+" afterMedianMs="+a[5]/1e6+" ratio="+a[5]/(double)b[5]);
 }
}`);
function run(command,argv){
 const r=cp.spawnSync(executable(command),argv,{encoding:'utf8',windowsHide:true});
 if(r.error||r.status!==0)throw new Error(r.error||r.stdout+r.stderr);process.stdout.write(r.stdout);
}
let successful=false;
try {
run('javac',['-encoding','UTF-8','--release','17','-d',out,path.join(__dirname,'adblock_stubs/android/net/Uri.java'),
 path.join(src,'AdBlockEngine.java'),path.join(out,'BeforeAdBlockEngine.java'),path.join(out,'RuleCompileBenchmark.java')]);
run('java',['-Xms512m','-Xmx768m','-cp',out,'com.xinyv.median.RuleCompileBenchmark']);
successful=true;
}finally{
 if(successful && !args.includes('--out')){
  if(path.dirname(out)!==path.resolve(os.tmpdir()) || !path.basename(out).startsWith('median-rule-compile-benchmark-'))
   throw new Error('Refusing cleanup outside generated benchmark directory');
  fs.rmSync(out,{recursive:true,force:true});
 }
}
