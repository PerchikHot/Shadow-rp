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
 private var active="Цунаде"
 private lateinit var root:LinearLayout
 private var avatarTarget:String?=null
 private val groups=mutableListOf<String>()
 private val avatarPicker=7311
 private fun shape(color:Int,r:Float=18f):GradientDrawable=GradientDrawable().apply { setColor(color);cornerRadius=r*resources.displayMetrics.density }
 private fun text(s:String,size:Float=16f,color:Int=Color.WHITE,bold:Boolean=false)=TextView(this).apply {
  text=s;textSize=size;setTextColor(color);setPadding(14,12,14,12);if(bold)setTypeface(null,Typeface.BOLD)
 }
 private fun button(s:String,click:()->Unit)=TextView(this).apply {
  text=s;textSize=15f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(12,16,12,16)
  background=shape(panel);setOnClickListener { click() }
 }
 private fun styleDialog(dialog:AlertDialog) {
  dialog.window?.setBackgroundDrawable(shape(panel,22f))
  dialog.window?.setLayout((resources.displayMetrics.widthPixels*0.91).toInt(),WindowManager.LayoutParams.WRAP_CONTENT)
  dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(Color.WHITE)
  dialog.findViewById<TextView>(resources.getIdentifier("alertTitle","id","android"))?.setTextColor(Color.WHITE)
  listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL).forEach { id ->
   dialog.getButton(id)?.apply {setTextColor(Color.rgb(124,175,255));isAllCaps=false;textSize=15f}
  }
 }
 private fun section(title:String)=text(title,22f,Color.WHITE,true)
 private fun space(h:Int)=android.view.View(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
 private fun init(title:String) {
  root=LinearLayout(this).apply { orientation=1;setBackgroundColor(bg);setPadding(8,0,8,0); fitsSystemWindows=true }
  val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
  header.addView(section(title),LinearLayout.LayoutParams(0,-2,1f))
  header.addView(button("⚙") { settings() }.apply {textSize=25f;setPadding(dp(17),dp(12),dp(17),dp(12))})
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
  val row=LinearLayout(this).apply { setPadding(0,dp(8),0,dp(10)) }
  val names=listOf("⌂\nГлавная","◈\nКаталог","＋\nСоздать","☏\nЧаты","♙\nПрофиль")
  names.forEachIndexed { i,name ->
   val view=button(name) { when(i){0->home();1->catalog();2->studio();3->chatList();else->profile()} }
   view.textSize=14f;view.setTextColor(if(i==selected)blue else muted);view.setPadding(dp(2),dp(16),dp(2),dp(16));view.gravity=Gravity.CENTER
   row.addView(view,LinearLayout.LayoutParams(0,-2,1f))
  }
  root.addView(row)
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=bg;window.navigationBarColor=bg
  window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  load();seedCatalog();migrateDefaults();home()
 }
 private fun load() {
  val a=JSONArray(prefs.getString("characters", JSONArray(listOf("Цунаде","Йоруичи Сихоин","Нико Робин")).toString()))
  for(i in 0 until a.length())characters.add(a.getString(i))
  active=prefs.getString("active",characters.firstOrNull()?:"Цунаде")!!
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
 private val animeRoster=listOf(
  "Цунаде|Naruto",
  "Мэй Теруми|Naruto",
  "Конан|Naruto",
  "Йоруичи Сихоин|Bleach",
  "Рангику Мацумото|Bleach",
  "Рецу Унохана|Bleach",
  "Нико Робин|One Piece",
  "Боа Хэнкок|One Piece",
  "Нами|One Piece",
  "Эрза Скарлет|Fairy Tail",
  "Мираджейн Штраус|Fairy Tail",
  "Люси Хартфилия|Fairy Tail",
  "Аква|Konosuba",
  "Даркнесс|Konosuba",
  "Круш Карстен|Re:Zero",
  "Эльза Гранхирт|Re:Zero",
  "Эсдес|Akame ga Kill",
  "Оливье Армстронг|Fullmetal Alchemist",
  "Макима|Chainsaw Man",
  "Йор Форджер|Spy x Family"
 ).map { it.split("|") }
 private fun migrateDefaults() {
  if(prefs.getBoolean("removed_old_defaults",false))return
  listOf("Алиса","Кай","Лира").forEach { name ->
   if(chats[name].isNullOrEmpty()){characters.remove(name);chats.remove(name)}
  }
  if(!characters.contains(active))active=characters.firstOrNull()?:"Цунаде"
  prefs.edit().putBoolean("removed_old_defaults",true).apply();save()
 }
 private fun seedCatalog() {
  if(prefs.getBoolean("catalog_seed_05",false))return
  animeRoster.forEach { parts ->
   val name=parts[0]
   if(!characters.contains(name)){characters.add(name);chats[name]=mutableListOf()}
   if(!prefs.contains("persona_$name"))prefs.edit().putString("persona_$name","Вселенная: ${parts[1]}. Альтернативная взрослая версия персонажа.").apply()
  }
  prefs.edit().putBoolean("catalog_seed_05",true).apply()
  save()
 }
 private fun characterCard(parent:LinearLayout,name:String) {
  val tile=LinearLayout(this).apply { orientation=1;background=shape(panel,20f);setPadding(dp(6),dp(6),dp(6),dp(8)) }
  val palette=intArrayOf(Color.rgb(41,75,135),Color.rgb(90,58,130),Color.rgb(26,107,117),Color.rgb(112,65,96))
  val portrait=text(name.take(1),36f,Color.WHITE,true).apply {
   gravity=Gravity.CENTER
   background=shape(palette[(name.hashCode() and Int.MAX_VALUE)%palette.size],18f)
  }
  tile.addView(avatar(name,116),LinearLayout.LayoutParams(-1,dp(116)))
  tile.addView(text(name,14f,Color.WHITE,true))
  tile.addView(text(animeRoster.firstOrNull{it[0]==name}?.get(1)?: "Мой персонаж",12f,muted))
  tile.setOnClickListener { characterDetail(name) };tile.setOnLongClickListener { chooseAvatar(name);true }
  parent.addView(tile,LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(dp(4),dp(4),dp(4),dp(4))})
 }
 private fun chooseAvatar(name:String) {
  avatarTarget=name
  val intent=android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
   type="image/*";addCategory(android.content.Intent.CATEGORY_OPENABLE)
   addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
  }
  startActivityForResult(intent,avatarPicker)
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:android.content.Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==avatarPicker && resultCode==Activity.RESULT_OK && data?.data!=null){
   val uri=data.data!!
   try { contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }catch(_:Exception){}
   avatarTarget?.let { prefs.edit().putString("avatar_$it",uri.toString()).apply();catalog() }
  }
 }
 private fun avatar(name:String,height:Int):android.view.View {
  val saved=prefs.getString("avatar_$name",null)
  if(saved!=null){
   try {
    return ImageView(this).apply {
     setImageURI(android.net.Uri.parse(saved))
     scaleType=ImageView.ScaleType.CENTER_CROP
     background=shape(panel)
     clipToOutline=true
    }
   }catch(_:Exception){}
  }
  val palette=intArrayOf(Color.rgb(41,75,135),Color.rgb(90,58,130),Color.rgb(26,107,117),Color.rgb(112,65,96))
  return text(name.take(1),36f,Color.WHITE,true).apply {
   gravity=Gravity.CENTER
   background=shape(palette[(name.hashCode() and Int.MAX_VALUE)%palette.size],18f)
  }
 }
 private fun characterDetail(name:String) {
  val universe=animeRoster.firstOrNull {it[0]==name}?.get(1) ?: "Авторский персонаж"
  val intro=prefs.getString("persona_$name","Персонаж для ролевой истории.").orEmpty()
  val panelView=LinearLayout(this).apply {orientation=1;setPadding(dp(16),dp(6),dp(16),dp(8))}
  val face=avatar(name,110)
  panelView.addView(face,LinearLayout.LayoutParams(-1,dp(110)))
  panelView.addView(text(universe,14f,Color.rgb(124,175,255),true))
  panelView.addView(text(intro,15f,Color.WHITE))
  panelView.addView(button("🖼 Изменить изображение") {chooseAvatar(name)})
  panelView.addView(button("✎ Редактировать описание") {editPersona(name)})
  panelView.addView(text("Сценарий и история общения сохраняются отдельно для этого персонажа. Другие варианты истории можно будет добавить позже.",13f,muted))
  AlertDialog.Builder(this).setTitle(name).setView(panelView)
   .setPositiveButton("Начать RP"){_,_->chat(name)}
   .setNeutralButton("Отзывы"){_,_->comments(name)}
   .setNegativeButton("Закрыть",null).show().also {styleDialog(it)}
 }
 private fun comments(name:String) {
  val key="comments_$name"
  val list=JSONArray(prefs.getString(key,"[]"))
  val box=LinearLayout(this).apply {orientation=1;setPadding(dp(16),dp(8),dp(16),dp(8))}
  val scroll=ScrollView(this)
  val contents=LinearLayout(this).apply {orientation=1}
  for(i in 0 until list.length())contents.addView(text("✦ "+list.optString(i),14f,Color.WHITE))
  if(list.length()==0)contents.addView(text("Пока нет отзывов. Оставь первый!",14f,muted))
  scroll.addView(contents)
  box.addView(scroll,LinearLayout.LayoutParams(-1,dp(170)))
  val entry=EditText(this).apply {hint="Твой отзыв о персонаже";setHintTextColor(muted);setTextColor(Color.WHITE);background=shape(bg);setPadding(dp(12),dp(12),dp(12),dp(12))}
  box.addView(entry)
  AlertDialog.Builder(this).setTitle("Отзывы • $name").setView(box)
   .setPositiveButton("Добавить"){_,_->
    val value=entry.text.toString().trim()
    if(value.isNotEmpty()){list.put(value);prefs.edit().putString(key,list.toString()).apply()}
   }.setNegativeButton("Закрыть",null).show().also {styleDialog(it)}
 }
 private fun hero(parent:LinearLayout) {
  val h=LinearLayout(this).apply { orientation=1;background=shape(Color.rgb(19,39,77),24f);setPadding(16,18,16,18) }
  h.addView(text("SHADOW RP  •  ТВОЯ ИСТОРИЯ",12f,Color.rgb(134,185,255),true))
  h.addView(text("Создавай миры.\nИграй свои роли.",26f,Color.WHITE,true))
  h.addView(text("Персонажи, диалоги и собственные сценарии",14f,muted))
  h.addView(button("▶  Продолжить с $active") { chat(active) })
  parent.addView(h,LinearLayout.LayoutParams(-1,-2).apply { setMargins(dp(4),dp(8),dp(4),dp(10)) })
 }
 private fun shelf(parent:LinearLayout,title:String,names:List<String>) {
  if(names.isEmpty())return
  parent.addView(section(title))
  val scroller=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
  val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  names.forEach { name ->
   val card=LinearLayout(this).apply { orientation=1;background=shape(panel,18f);setPadding(dp(5),dp(5),dp(5),dp(7)) }
   val face=avatar(name,140)
   card.addView(face,LinearLayout.LayoutParams(-1,dp(140)))
   card.addView(text(name,14f,Color.WHITE,true))
   card.setOnClickListener { characterDetail(name) };card.setOnLongClickListener { chooseAvatar(name);true }
   row.addView(card,LinearLayout.LayoutParams(dp(152),-2).apply {setMargins(dp(4),dp(5),dp(4),dp(8))})
  }
  scroller.addView(row);parent.addView(scroller)
 }
 private fun home() {
  init("Shadow RP");val b=body()
  b.addView(text("Открой новую историю",24f,Color.WHITE,true))
  val recent=characters.filter { chats[it]?.isNotEmpty()==true }.sortedByDescending { chats[it]?.size?:0 }
  shelf(b,"Продолжить общение",recent.take(8))
  shelf(b,"✦ Популярные героини",animeRoster.take(10).map{it[0]})
  animeRoster.map{it[1]}.distinct().take(5).forEach { title ->
   shelf(b,title,animeRoster.filter{it[1]==title}.map{it[0]})
  }
  nav(0)
 }
 private fun catalog() {
  init("Каталог");val b=body()
  val search=EditText(this).apply { hint="Поиск по персонажам и аниме";setSingleLine(true);setTextColor(Color.WHITE);setHintTextColor(muted);background=shape(panel);setPadding(dp(12),dp(12),dp(12),dp(12)) }
  b.addView(search)
  val result=LinearLayout(this).apply { orientation=1 };b.addView(result)
  fun render(q:String) {
   result.removeAllViews()
   val names=characters.filter { it.contains(q,true) || animeRoster.firstOrNull{r->r[0]==it}?.get(1)?.contains(q,true)==true }
   if(q.isNotBlank()) {
    shelf(result,"Результаты поиска",names)
   } else {
    shelf(result,"Все персонажи",names.take(15))
    animeRoster.map{it[1]}.distinct().forEach { title ->
     shelf(result,title,animeRoster.filter{it[1]==title}.map{it[0]})
    }
   }
  }
  render("")
  search.addTextChangedListener(object:android.text.TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){render(s?.toString()?:"")}
   override fun afterTextChanged(s:android.text.Editable?) {}
  })
  nav(1)
 }
 private fun studio() {
  init("Творческий центр");val b=body()
  item(b,"＋ Создать персонажа","Аватар, приветствие, характер, сценарий, память") { createCharacter() }
  item(b,"♧ Создать комнату","Групповой RP с несколькими персонажами") { createRoom() }
  item(b,"♙ Моя личность","Имя, биография и роль игрока") { editIdentity() }
  item(b,"▣ Создать изображение","Пока доступен просмотр промпта по истории") { imagePrompt() }
  item(b,"▤ Лорбук","Планируется в следующих обновлениях") { AlertDialog.Builder(this).setMessage("Редактор лорбука ещё не реализован.").setPositiveButton("OK",null).show().also {styleDialog(it)} }
  nav(2)
 }
 private fun createCharacter() {
  val layout=LinearLayout(this).apply { orientation=1;setPadding(20,8,20,8) }
  val name=EditText(this).apply { hint="Имя персонажа";layout.addView(this) }
  val persona=EditText(this).apply { hint="Характер, роль, предыстория";minLines=3;layout.addView(this) }
  val appearance=EditText(this).apply { hint="Внешность и стиль";minLines=2;layout.addView(this) }
  val scenario=EditText(this).apply { hint="Сценарий и начало истории";minLines=2;layout.addView(this) }
  val greeting=EditText(this).apply {hint="Приветствие персонажа";minLines=2;layout.addView(this)}
  val protocol=EditText(this).apply {hint="Протокол: правила поведения ИИ";minLines=2;layout.addView(this)}
  val memory=EditText(this).apply {hint="Карточка памяти: важные факты";minLines=2;layout.addView(this)}
  AlertDialog.Builder(this).setTitle("Новый персонаж").setView(layout).setPositiveButton("Создать") { _,_->
   val n=name.text.toString().trim()
   if(n.isNotBlank()&&!characters.contains(n)) {
    characters.add(n);chats[n]=mutableListOf()
    prefs.edit().putString("persona_$n",listOf(persona.text.toString(), "Внешность: "+appearance.text.toString(), "Сценарий: "+scenario.text.toString(), "Протокол: "+protocol.text.toString(), "Память: "+memory.text.toString()).joinToString("\n")).putString("greeting_$n",greeting.text.toString()).apply();save();chooseAvatar(n);characterDetail(n)
   }
  }.setNegativeButton("Отмена",null).show().also {styleDialog(it)}
 }
 private fun chatList() {
  init("Чаты");val b=body()
  val started=characters.filter { chats[it]?.isNotEmpty()==true }
  if(started.isEmpty())b.addView(text("Здесь появятся персонажи, с которыми ты начнёшь общаться. Выбери героиню в каталоге.",16f,muted))
  started.forEach { name ->
   val last=chats[name]?.lastOrNull()?.second?:""
   item(b,name,last.take(90)) { chat(name) }
  }
  nav(3)
 }
 private fun chat(name:String) {
  active=name;save();init("✦ $name")
  val b=body();val messages=chats.getOrPut(name){mutableListOf()}
  if(messages.isEmpty())b.addView(text(prefs.getString("greeting_$name","").orEmpty().ifBlank { "✦ Начни историю с $name\n\nСоздай собственный сюжет и отправь первое сообщение." },16f,muted))
  messages.forEach { (role,value)->
   val bubble=text(value,15f).apply {setPadding(dp(10),dp(8),dp(10),dp(8))}
   bubble.background=shape(if(role=="user")Color.rgb(13,75,148) else panel)
   val lp=LinearLayout.LayoutParams(-2,-2).apply {
    gravity=if(role=="user")Gravity.RIGHT else Gravity.LEFT
    setMargins(if(role=="user")dp(55) else dp(6),dp(4),if(role=="user")dp(6) else dp(55),dp(4))
   }
   b.addView(bubble,lp)
  }
  val input=EditText(this).apply { hint="Напиши сообщение…";setTextColor(Color.WHITE);setHintTextColor(muted);minLines=2;maxLines=6;setTextSize(17f);setPadding(dp(12),dp(12),dp(12),dp(12));background=shape(panel,16f) }
  val bar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),dp(7),dp(4),dp(9)) }
  bar.addView(button("▧") { imagePrompt() }.apply {textSize=21f;setPadding(dp(12),dp(18),dp(12),dp(18))})
  bar.addView(input,LinearLayout.LayoutParams(0,-2,1f))
  bar.addView(button("➤") {
   val msg=input.text.toString().trim()
   if(msg.isNotBlank()){messages.add("user" to msg);save();chat(name);request(name)}
  }.apply {textSize=23f;setPadding(dp(14),dp(18),dp(14),dp(18))})
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
    val persona=prefs.getString("persona_$name","")!! + "\nИгрок: " + prefs.getString("identity_name","Игрок").orEmpty() + ". " + prefs.getString("identity_bio","").orEmpty()
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
  AlertDialog.Builder(this).setTitle("Контекст для изображения").setMessage(scene.ifBlank { "Сначала начни переписку." }+"\n\nГенератор изображений пока не подключён.").setPositiveButton("OK",null).show().also {styleDialog(it)}
 }
 override fun onBackPressed() { home() }
 private fun profile() {
  init("Профиль");val b=body()
  item(b,"Мои персонажи","Всего: ${characters.size}") { catalog() }
  item(b,"Моя личность",prefs.getString("identity_name","Не настроена").orEmpty()) { editIdentity() }
  item(b,"Групповые комнаты","Создавать и открывать групповые истории") { roomList() }
  item(b,"Мои чаты","Сохранены локально") { chatList() }
  item(b,"Настройки ИИ","Подключить API") { settings() }
  nav(4)
 }

 private fun editPersona(name:String) {
  val field=EditText(this).apply {setText(prefs.getString("persona_$name",""));minLines=6;setTextColor(Color.WHITE);setPadding(dp(16),dp(12),dp(16),dp(12))}
  AlertDialog.Builder(this).setTitle("Описание • $name").setView(field).setPositiveButton("Сохранить"){_,_->
   prefs.edit().putString("persona_$name",field.text.toString()).apply();characterDetail(name)
  }.setNegativeButton("Отмена",null).show().also {styleDialog(it)}
 }
 private fun editIdentity() {
  val l=LinearLayout(this).apply {orientation=1;setPadding(dp(16),dp(8),dp(16),dp(8))}
  val name=EditText(this).apply {hint="Имя игрока";setText(prefs.getString("identity_name",""));l.addView(this)}
  val bio=EditText(this).apply {hint="Биография и RP-личность";minLines=4;setText(prefs.getString("identity_bio",""));l.addView(this)}
  AlertDialog.Builder(this).setTitle("Моя личность").setView(l).setPositiveButton("Сохранить"){_,_->
   prefs.edit().putString("identity_name",name.text.toString()).putString("identity_bio",bio.text.toString()).apply();profile()
  }.setNegativeButton("Отмена",null).show().also {styleDialog(it)}
 }
 private fun roomList() {
  init("Групповые комнаты");val b=body()
  item(b,"＋ Создать комнату","Укажи участников и сюжет") {createRoom()}
  val a=JSONArray(prefs.getString("rooms","[]"))
  for(i in 0 until a.length()) {
   val room=a.optJSONObject(i)?:continue
   val title=room.optString("title")
   item(b,title,room.optString("members")){roomChat(room)}
  }
  nav(3)
 }
 private fun createRoom() {
  val l=LinearLayout(this).apply {orientation=1;setPadding(dp(16),dp(8),dp(16),dp(8))}
  fun field(h:String):EditText=EditText(this).apply {hint=h;setTextColor(Color.WHITE);setHintTextColor(muted);l.addView(this)}
  val title=field("Название комнаты")
  val members=field("Участники через запятую (имена из каталога)")
  val story=field("Тема и отношения между персонажами")
  AlertDialog.Builder(this).setTitle("Создать комнату").setView(l).setPositiveButton("Создать"){_,_->
   if(title.text.isNotBlank() && members.text.isNotBlank()) {
    val a=JSONArray(prefs.getString("rooms","[]"))
    val room=JSONObject().put("title",title.text.toString()).put("members",members.text.toString()).put("story",story.text.toString())
    a.put(room);prefs.edit().putString("rooms",a.toString()).apply();roomChat(room)
   }
  }.setNegativeButton("Отмена",null).show().also {styleDialog(it)}
 }
 private fun roomChat(room:JSONObject) {
  val title=room.optString("title")
  val members=room.optString("members").split(",").map{it.trim()}.filter{it.isNotBlank()}
  init(title);val b=body()
  b.addView(text("Участники: "+members.joinToString(", "),14f,muted))
  val key="room_chat_$title"
  val history=JSONArray(prefs.getString(key,"[]"))
  for(i in 0 until history.length()) {
   val o=history.optJSONObject(i)?:continue
   b.addView(text(o.optString("role")+": "+o.optString("text"),15f))
  }
  val input=EditText(this).apply {hint="Твоё действие или реплика";setTextColor(Color.WHITE);setHintTextColor(muted)}
  root.addView(input)
  root.addView(button("➤ Отправить") {
   val msg=input.text.toString().trim()
   if(msg.isNotBlank()) {
    history.put(JSONObject().put("role","Игрок").put("text",msg))
    prefs.edit().putString(key,history.toString()).apply()
    val base=prefs.getString("url","").orEmpty().trimEnd('/')
    val model=prefs.getString("model","").orEmpty()
    val apiKey=prefs.getString("key","").orEmpty()
    if(base.isBlank()||model.isBlank()){roomChat(room);alert("Для ответов участников настрой ИИ в параметрах ⚙")}
    else thread {
     try {
      val arr=JSONArray()
      val personas=members.joinToString("\n"){ it+": "+prefs.getString("persona_$it","").orEmpty() }
      arr.put(JSONObject().put("role","system").put("content","Ты ведущий групповой ролевой игры. Участники: "+members.joinToString(", ")+". Сюжет: "+room.optString("story")+"\n"+personas+"\nОтвечай по-русски, от лица нескольких персонажей, не управляй действиями игрока."))
      for(i in 0 until history.length()){val o=history.getJSONObject(i);arr.put(JSONObject().put("role",if(o.optString("role")=="Игрок")"user" else "assistant").put("content",o.optString("text")))}
      val conn=(URL("$base/chat/completions").openConnection() as HttpURLConnection).apply {requestMethod="POST";doOutput=true;connectTimeout=20000;readTimeout=120000;setRequestProperty("Content-Type","application/json");if(apiKey.isNotBlank())setRequestProperty("Authorization","Bearer $apiKey")}
      conn.outputStream.use{it.write(JSONObject().put("model",model).put("messages",arr).put("stream",false).toString().toByteArray())}
      val response=(if(conn.responseCode in 200..299)conn.inputStream else conn.errorStream).bufferedReader().use{it.readText()}
      if(conn.responseCode !in 200..299)error("HTTP "+conn.responseCode+": "+response.take(200))
      val reply=JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
      runOnUiThread {history.put(JSONObject().put("role","Персонажи").put("text",reply));prefs.edit().putString(key,history.toString()).apply();roomChat(room)}
     }catch(e:Exception){runOnUiThread{alert("Ошибка модели: "+e.message)}}
    }
   }
  })
 }
 private fun alert(s:String){AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show().also {styleDialog(it)}}
 private fun settings() {
  init("Настройки ИИ");val b=body()
  b.addView(section("Подключение модели"))
  b.addView(text("Выбери тип подключения. Для облачной модели нужен API, для локальной — запущенный совместимый сервер.",14f,muted))
  val providers=listOf("OpenAI-совместимый API","OpenRouter","Локальный сервер (LM Studio / Ollama)")
  providers.forEachIndexed { index,label ->
   item(b,label,when(index){0->"Свой URL, ID модели и ключ";1->"Выбор моделей через OpenRouter API";else->"Адрес сервера в локальной сети"}) {
    editModel(index)
   }
  }
  val model=prefs.getString("model","").orEmpty()
  item(b,"Текущая модель",if(model.isBlank())"Не настроена" else model){editModel(0)}
  nav(4)
 }
 private fun editModel(provider:Int) {
  val l=LinearLayout(this).apply {orientation=1;setPadding(dp(18),dp(8),dp(18),dp(8))}
  fun field(label:String,value:String):EditText {
   l.addView(text(label,14f,muted))
   return EditText(this).apply {setText(value);setTextColor(Color.WHITE);setSingleLine(true);background=shape(panel);setPadding(dp(12),dp(12),dp(12),dp(12));l.addView(this,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(12)})}
  }
  val defaultUrl=when(provider){1->"https://openrouter.ai/api/v1";2->"http://127.0.0.1:11434/v1";else->""}
  val url=field("API URL (включая /v1)",if(provider==0)prefs.getString("url","").orEmpty() else defaultUrl)
  val model=field("ID модели",prefs.getString("model","").orEmpty())
  val key=field("API ключ",prefs.getString("key","").orEmpty())
  key.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
  AlertDialog.Builder(this).setTitle("Подключить ИИ").setView(l)
   .setPositiveButton("Сохранить"){_,_->
    prefs.edit().putString("url",url.text.toString().trim()).putString("model",model.text.toString().trim()).putString("key",key.text.toString().trim()).apply()
    settings()
   }.setNegativeButton("Отмена",null).show().also {styleDialog(it)}
 }
}
