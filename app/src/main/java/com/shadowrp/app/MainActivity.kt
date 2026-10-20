package com.shadowrp.app

import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
 private val bg=Color.rgb(4,9,18)
 private val panel=Color.rgb(15,22,34)
 private val blue=Color.rgb(53,117,255)
 private val muted=Color.rgb(160,183,211)
 private val violet=Color.rgb(109,93,242)
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private val prefs by lazy { getSharedPreferences("shadowrp", MODE_PRIVATE) }
 private val characters=mutableListOf<String>()
 private val chats=mutableMapOf<String,MutableList<Pair<String,String>>>()
 private var active="Алиса"
 private lateinit var root:LinearLayout
 private fun shape(color:Int,r:Float=18f):GradientDrawable=GradientDrawable().apply { setColor(color);cornerRadius=r*resources.displayMetrics.density }
 private fun text(s:String,size:Float=16f,color:Int=Color.WHITE,bold:Boolean=false)=TextView(this).apply {
  text=s;textSize=size;setTextColor(color);setPadding(14,12,14,12);if(bold)setTypeface(null,Typeface.BOLD)
 }
 private fun button(s:String,click:()->Unit)=TextView(this).apply {
  text=s;textSize=15f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(12,16,12,16)
  background=shape(panel);setOnClickListener { click() }
 }
 private fun section(title:String)=text(title,22f,Color.WHITE,true)
 private fun space(h:Int)=android.view.View(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
 private fun init(title:String) {
  root=LinearLayout(this).apply { orientation=1;setBackgroundColor(bg);setPadding(8,0,8,0); fitsSystemWindows=true }
  val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
  header.addView(section(title),LinearLayout.LayoutParams(0,-2,1f))
  header.addView(button("⚙") { settings() })
  root.addView(header)
  val accent=android.view.View(this).apply { setBackgroundColor(blue) }
  root.addView(accent,LinearLayout.LayoutParams(-1,dp(2)))
  setContentView(root)
 }
 private fun body():LinearLayout {
  val scroll=ScrollView(this).apply { isFillViewport=true }
  val content=LinearLayout(this).apply { orientation=1 }
  scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  return content
 }
 private fun item(parent:LinearLayout,title:String,subtitle:String="",onClick:()->Unit) {
  val card=LinearLayout(this).apply { orientation=1; background=shape(panel,22f);setPadding(10,10,10,10);elevation=dp(2).toFloat() }
  card.addView(text(title,17f,Color.WHITE,true))
  if(subtitle.isNotBlank())card.addView(text(subtitle,13f,muted))
  val lp=LinearLayout.LayoutParams(-1,-2).apply { setMargins(4,5,4,5) }
  parent.addView(card,lp);card.setOnClickListener { onClick() }
 }
 private fun nav(selected:Int) {
  val row=LinearLayout(this).apply { setPadding(0,8,0,10) }
  val names=listOf("⌂\nГлавная","◈\nКаталог","＋\nСоздать","☏\nЧаты","♙\nПрофиль")
  names.forEachIndexed { i,name ->
   val view=button(name) { when(i){0->home();1->catalog();2->studio();3->chatList();else->profile()} }
   view.textSize=12f;view.setTextColor(if(i==selected)blue else muted);view.setPadding(2,10,2,10);view.gravity=Gravity.CENTER
   row.addView(view,LinearLayout.LayoutParams(0,-2,1f))
  }
  root.addView(row)
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=bg;window.navigationBarColor=bg
  load();home()
 }
 private fun load() {
  val a=JSONArray(prefs.getString("characters", JSONArray(listOf("Алиса","Кай","Лира")).toString()))
  for(i in 0 until a.length())characters.add(a.getString(i))
  active=prefs.getString("active",characters.firstOrNull()?:"Алиса")!!
  characters.forEach { name ->
   val messages=mutableListOf<Pair<String,String>>()
   val arr=JSONArray(prefs.getString("chat_$name","[]"))
   for(i in 0 until arr.length()){val o=arr.getJSONObject(i);messages.add(o.getString("role") to o.getString("content"))}
   chats[name]=messages
  }
 }
 private fun save() {
  prefs.edit().putString("characters",JSONArray(characters).toString()).putString("active",active).apply()
  chats.forEach { (name,messages)->
   val a=JSONArray();messages.forEach { a.put(JSONObject().put("role",it.first).put("content",it.second)) }
   prefs.edit().putString("chat_$name",a.toString()).apply()
  }
 }
 private fun hero(parent:LinearLayout) {
  val h=LinearLayout(this).apply { orientation=1;background=shape(Color.rgb(19,39,77),24f);setPadding(16,18,16,18) }
  h.addView(text("SHADOW RP  •  ТВОЯ ИСТОРИЯ",12f,Color.rgb(134,185,255),true))
  h.addView(text("Создавай миры.\nИграй свои роли.",26f,Color.WHITE,true))
  h.addView(text("Персонажи, диалоги и собственные сценарии",14f,muted))
  h.addView(button("▶  Продолжить с $active") { chat(active) })
  parent.addView(h,LinearLayout.LayoutParams(-1,-2).apply { setMargins(dp(4),dp(8),dp(4),dp(10)) })
 }
 private fun home() {
  init("Shadow RP");val b=body()
  hero(b)
  b.addView(section("Твои истории"))
  item(b,"◉ Последняя история: $active","Открыть переписку и продолжить сюжет") { chat(active) }
  b.addView(section("✦ Персонажи"))
  characters.forEach { name->item(b,"✦ $name","Открыть ролевой чат") { chat(name) } }
  item(b,"＋ Создать персонажа","Добавить собственную героиню или героя") { createCharacter() }
  nav(0)
 }
 private fun catalog() {
  init("Каталог");val b=body()
  b.addView(text("✦ Открой свою историю",20f,Color.WHITE,true))
  b.addView(text("Личные персонажи • хранятся на устройстве",14f,muted))
  item(b,"＋ Новый персонаж","Создай персонажа с собственным характером") { createCharacter() }
  characters.forEach { name->item(b,"✦ $name","Начать или продолжить RP") { chat(name) } }
  nav(1)
 }
 private fun studio() {
  init("Творческий центр");val b=body()
  item(b,"＋ Создать персонажа","Имя и характер персонажа") { createCharacter() }
  item(b,"▣ Создать изображение","Пока доступен просмотр промпта по истории") { imagePrompt() }
  item(b,"▤ Лорбук","Планируется в следующих обновлениях") { AlertDialog.Builder(this).setMessage("Редактор лорбука ещё не реализован.").setPositiveButton("OK",null).show() }
  nav(2)
 }
 private fun createCharacter() {
  val layout=LinearLayout(this).apply { orientation=1;setPadding(20,8,20,8) }
  val name=EditText(this).apply { hint="Имя персонажа";layout.addView(this) }
  val persona=EditText(this).apply { hint="Характер, роль, предыстория";minLines=3;layout.addView(this) }
  val appearance=EditText(this).apply { hint="Внешность и стиль";minLines=2;layout.addView(this) }
  val scenario=EditText(this).apply { hint="Сценарий и начало истории";minLines=2;layout.addView(this) }
  AlertDialog.Builder(this).setTitle("Новый персонаж").setView(layout).setPositiveButton("Создать") { _,_->
   val n=name.text.toString().trim()
   if(n.isNotBlank()&&!characters.contains(n)) {
    characters.add(n);chats[n]=mutableListOf()
    prefs.edit().putString("persona_$n",listOf(persona.text.toString(), "Внешность: "+appearance.text.toString(), "Сценарий: "+scenario.text.toString()).joinToString("\n")).apply();save();chat(n)
   }
  }.setNegativeButton("Отмена",null).show()
 }
 private fun chatList() {
  init("Чаты");val b=body()
  characters.forEach { name->
   val last=chats[name]?.lastOrNull()?.second?:"Нет сообщений"
   item(b,name,last.take(90)) { chat(name) }
  }
  nav(3)
 }
 private fun chat(name:String) {
  active=name;save();init("✦ $name")
  val b=body();val messages=chats.getOrPut(name){mutableListOf()}
  if(messages.isEmpty())b.addView(text("✦ Начни историю с $name\n\nСоздай собственный сюжет и отправь первое сообщение.",16f,muted))
  messages.forEach { (role,value)->
   val bubble=text(value,16f)
   bubble.background=shape(if(role=="user")Color.rgb(13,75,148) else panel)
   val lp=LinearLayout.LayoutParams(-2,-2).apply {
    gravity=if(role=="user")Gravity.RIGHT else Gravity.LEFT
    setMargins(if(role=="user")55 else 6,7,if(role=="user")6 else 55,7)
   }
   b.addView(bubble,lp)
  }
  val input=EditText(this).apply { hint="Сообщение…";setTextColor(Color.WHITE);setHintTextColor(muted);minLines=1;maxLines=4 }
  val bar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
  bar.addView(button("▧") { imagePrompt() })
  bar.addView(input,LinearLayout.LayoutParams(0,-2,1f))
  bar.addView(button("➤") {
   val msg=input.text.toString().trim()
   if(msg.isNotBlank()){messages.add("user" to msg);save();chat(name);request(name)}
  })
  root.addView(bar)
 }
 private fun request(name:String) {
  val base=prefs.getString("url","")!!.trimEnd('/')
  val model=prefs.getString("model","")!!
  val key=prefs.getString("key","")!!
  if(base.isBlank()||model.isBlank()){alert("Настрой ИИ-модель через ⚙");return}
  val snapshot=chats[name]?.toList()?:return
  thread {
   try {
    val arr=JSONArray()
    val persona=prefs.getString("persona_$name","")!!
    arr.put(JSONObject().put("role","system").put("content","Ты играешь роль персонажа $name. $persona Отвечай по-русски, поддерживай сюжет, сохраняй характер."))
    snapshot.forEach { arr.put(JSONObject().put("role",it.first).put("content",it.second)) }
    val data=JSONObject().put("model",model).put("messages",arr).put("stream",false).toString()
    val c=(URL("$base/chat/completions").openConnection() as HttpURLConnection).apply {
     requestMethod="POST";connectTimeout=20000;readTimeout=120000;doOutput=true
     setRequestProperty("Content-Type","application/json")
     if(key.isNotBlank())setRequestProperty("Authorization","Bearer $key")
    }
    c.outputStream.use { it.write(data.toByteArray()) }
    val response=(if(c.responseCode in 200..299)c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
    if(c.responseCode !in 200..299)error("HTTP ${c.responseCode}: ${response.take(250)}")
    val reply=JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    c.disconnect()
    runOnUiThread { chats[name]?.add("assistant" to reply);save();if(active==name)chat(name) }
   }catch(e:Exception){runOnUiThread { alert("Ошибка модели: ${e.message}") }}
  }
 }
 private fun imagePrompt() {
  val scene=chats[active]?.takeLast(6)?.joinToString("\n"){ "${it.first}: ${it.second}" }?:""
  AlertDialog.Builder(this).setTitle("Контекст для изображения").setMessage(scene.ifBlank { "Сначала начни переписку." }+"\n\nГенератор изображений пока не подключён.").setPositiveButton("OK",null).show()
 }
 override fun onBackPressed() { home() }
 private fun profile() {
  init("Профиль");val b=body()
  item(b,"Мои персонажи","Всего: ${characters.size}") { catalog() }
  item(b,"Мои чаты","Сохранены локально") { chatList() }
  item(b,"Настройки ИИ","Подключить API") { settings() }
  nav(4)
 }
 private fun alert(s:String){AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show()}
 private fun settings() {
  val l=LinearLayout(this).apply { orientation=1;setPadding(20,8,20,8) }
  fun field(hint:String,value:String)=EditText(this).apply { this.hint=hint;setText(value);l.addView(this) }
  val url=field("HTTPS API URL до /v1",prefs.getString("url","")!!)
  val model=field("ID модели",prefs.getString("model","")!!)
  val key=field("API ключ (при необходимости)",prefs.getString("key","")!!);key.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
  AlertDialog.Builder(this).setTitle("Настройки ИИ").setView(l).setPositiveButton("Сохранить"){_,_->
   prefs.edit().putString("url",url.text.toString().trim()).putString("model",model.text.toString().trim()).putString("key",key.text.toString().trim()).apply()
  }.setNegativeButton("Отмена",null).show()
 }
}
