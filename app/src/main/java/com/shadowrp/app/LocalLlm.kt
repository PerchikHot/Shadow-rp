package com.shadowrp.app

class LocalLlm {
 companion object {
  val available:Boolean by lazy {
   try { System.loadLibrary("shadowrp_llm"); true } catch (_:Throwable) { false }
  }
 }
 external fun generate(path:String,prompt:String,maxTokens:Int):String
}
