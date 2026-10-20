package com.shadowrp.app

import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.widget.*
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
 private lateinit var messages: LinearLayout
 private lateinit var input: EditText
 private val history = mutableListOf<Pair<String,String>>()
 private val prefs by lazy { getSharedPreferences("shadow_settings", MODE_PRIVATE) }
 private fun label(s:String):TextView = TextView(this).apply { text=s; textSize=17f; setTextColor(Color.WHITE); setPadding(18,16,18,16) }
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); showChat() }
 private fun bubble(role:String, content:String) { messages.addView(label((if(role=="user") "Ты: " else "ИИ: ")+content)) }
 private fun showChat() {
  val root=LinearLayout(this).apply { orientation=1; setBackgroundColor(Color.rgb(9,19,44)) }
  val bar=LinearLayout(this)
  bar.addView(label("Shadow RP"),LinearLayout.LayoutParams(0,-2,1f))
  bar.addView(Button(this).apply { text="⚙"; setOnClickListener { settings() } })
  root.addView(bar)
  val scroll=ScrollView(this)
  messages=LinearLayout(this).apply { orientation=1 }
  scroll.addView(messages)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  history.forEach { bubble(it.first,it.second) }
  input=EditText(this).apply { hint="Напиши сообщение…"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); minLines=2 }
  root.addView(input)
  val actions=LinearLayout(this)
  actions.addView(Button(this).apply { text="Изображение"; setOnClickListener { imagePrompt() } },LinearLayout.LayoutParams(0,-2,1f))
  actions.addView(Button(this).apply { text="Отправить"; setOnClickListener { send() } },LinearLayout.LayoutParams(0,-2,1f))
  root.addView(actions)
  setContentView(root)
 }
 private fun send() {
  val msg=input.text.toString().trim()
  if(msg.isEmpty())return
  input.setText("")
  history.add("user" to msg)
  bubble("user",msg)
  val base=prefs.getString("url","")!!.trimEnd('/')
  val model=prefs.getString("model","")!!
  val key=prefs.getString("key","")!!
  if(base.isBlank()||model.isBlank()){bubble("assistant","Сначала настрой модель через ⚙");return}
  thread {
   try {
    val arr=JSONArray()
    arr.put(JSONObject().put("role","system").put("content","Ты ведущий ролевой истории. Отвечай по-русски, сохраняй характеры персонажей и последовательность событий."))
    history.forEach { arr.put(JSONObject().put("role",it.first).put("content",it.second)) }
    val body=JSONObject().put("model",model).put("messages",arr).put("stream",false).toString()
    val c=(URL("$base/chat/completions").openConnection() as HttpURLConnection).apply {
     requestMethod="POST"; connectTimeout=20000; readTimeout=120000; doOutput=true
     setRequestProperty("Content-Type","application/json")
     if(key.isNotBlank())setRequestProperty("Authorization","Bearer $key")
    }
    c.outputStream.use { it.write(body.toByteArray()) }
    val response=(if(c.responseCode in 200..299)c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
    if(c.responseCode !in 200..299)error("HTTP ${c.responseCode}: ${response.take(250)}")
    val reply=JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    c.disconnect()
    runOnUiThread { history.add("assistant" to reply); bubble("assistant",reply) }
   } catch(e:Exception) { runOnUiThread { bubble("assistant","Ошибка: ${e.message}") } }
  }
 }
 private fun imagePrompt() {
  val context=history.takeLast(6).joinToString("\n") { "${it.first}: ${it.second}" }
  AlertDialog.Builder(this).setTitle("Генерация изображения").setMessage("Контекст сцены:\n$context\n\nПодключение модели изображений будет добавлено в следующей версии.").setPositiveButton("OK",null).show()
 }
 private fun settings() {
  val layout=LinearLayout(this).apply { orientation=1; setPadding(20,8,20,8) }
  fun field(hint:String,value:String):EditText=EditText(this).apply { this.hint=hint; setText(value); layout.addView(this) }
  val url=field("HTTPS API URL до /v1",prefs.getString("url","")!!)
  val model=field("Имя модели",prefs.getString("model","")!!)
  val key=field("API ключ (если нужен)",prefs.getString("key","")!!)
  AlertDialog.Builder(this).setTitle("Настройки модели").setView(layout).setPositiveButton("Сохранить") { _,_->
   prefs.edit().putString("url",url.text.toString().trim()).putString("model",model.text.toString().trim()).putString("key",key.text.toString().trim()).apply()
  }.setNegativeButton("Отмена",null).show()
 }
}