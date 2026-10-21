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
import java.net.URLEncoder
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {
 private val bg=Color.rgb(4,9,18)
 private val panel=Color.rgb(22,31,49)
 private val blue=Color.rgb(53,117,255)
 private val muted=Color.rgb(186,204,228)
 private val violet=Color.rgb(109,93,242)
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private val prefs by lazy { getSharedPreferences("shadowrp", MODE_PRIVATE) }
 private val characters=mutableListOf<String>()
 private val chats=mutableMapOf<String,MutableList<Pair<String,String>>>()
 private var active="Цунаде"
 private val localPending=mutableSetOf<String>()
 private val localStarted=mutableMapOf<String,Long>()
 private val timerHandler=Handler(Looper.getMainLooper())
 private lateinit var root:LinearLayout
 private var avatarTarget:String?=null
 private var avatarReturn:String?=null
 private val groups=mutableListOf<String>()
 private val avatarPicker=7311
 private val ggufPicker=7312
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
 private fun section(title:String)=text(title,24f,Color.WHITE,true)
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
   view.textSize=16f;view.setTextColor(if(i==selected)blue else muted);view.setPadding(dp(2),dp(20),dp(2),dp(20));view.gravity=Gravity.CENTER
   row.addView(view,LinearLayout.LayoutParams(0,-2,1f))
  }
  root.addView(row)
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=bg;window.navigationBarColor=bg
  window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  load();seedCatalog();migrateDefaults();showSplash()
 }
 private fun showSplash() {
  val backdrop=android.widget.FrameLayout(this).apply {setBackgroundColor(Color.rgb(5,9,19))}
  val center=LinearLayout(this).apply {orientation=1;gravity=Gravity.CENTER}
  val logo=ImageView(this).apply{setImageResource(R.drawable.shadow_icon);scaleType=ImageView.ScaleType.FIT_CENTER}
  center.addView(logo,LinearLayout.LayoutParams(dp(156),dp(156)))
  val label=text("Shadow RP",26f,Color.WHITE,true).apply{gravity=Gravity.CENTER}
  center.addView(label)
  backdrop.addView(center,android.widget.FrameLayout.LayoutParams(-1,-1))
  setContentView(backdrop)
  val handler=Handler(Looper.getMainLooper())
  handler.postDelayed({logo.setImageResource(R.drawable.shadow_icon_blink)},570)
  handler.postDelayed({logo.setImageResource(R.drawable.shadow_icon)},720)
  handler.postDelayed({logo.setImageResource(R.drawable.shadow_icon_blink)},1170)
  handler.postDelayed({logo.setImageResource(R.drawable.shadow_icon)},1310)
  handler.postDelayed({if(!isFinishing)home()},1800)
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
  "Оливье|The Eminence in Shadow",
  "Макима|Chainsaw Man",
  "Йор Форджер|Spy x Family",
  "Асуна Юки|Sword Art Online",
  "Алиса Цуберг|Sword Art Online",
  "Синон|Sword Art Online",
  "Лифа|Sword Art Online",
  "Куруми Токисаки|Date A Live",
  "Тока Ятогами|Date A Live",
  "Оригами Тобиичи|Date A Live",
  "Котори Ицука|Date A Live",
  "Лилит Асами|Trinity Seven",
  "Арин Каннадзуки|Trinity Seven",
  "Леви Кадзама|Trinity Seven",
  "Рем|Re:Zero",
  "Рам|Re:Zero",
  "Эмилия|Re:Zero",
  "Эхидна|Re:Zero",
  "Мегумин|Konosuba",
  "Виз|Konosuba",
  "Ультиар Милкович|Fairy Tail",
  "Кана Альберона|Fairy Tail",
  "Биска Коннелл|Fairy Tail",
  "Тиа Харрибел|Bleach",
  "Неллиэль Ту Одельшванк|Bleach",
  "Орихимэ Иноуэ|Bleach",
  "Сакура Харуно|Naruto",
  "Хината Хьюга|Naruto",
  "Темари|Naruto",
  "Кушина Узумаки|Naruto",
  "Виви Нефертари|One Piece",
  "Перона|One Piece",
  "Ребекка|One Piece",
  "Элизабет|The Eminence in Shadow",
  "Альфа|The Eminence in Shadow",
  "Бета|The Eminence in Shadow",
  "Гамма|The Eminence in Shadow",
  "Дельта|The Eminence in Shadow",
  "Эпсилон|The Eminence in Shadow",
  "Дзета|The Eminence in Shadow",
  "Эта|The Eminence in Shadow",
  "Тентен|Naruto",
  "Айра Сиратори|Dandadan",
  "Шуна|That Time I Got Reincarnated as a Slime",
  "Шион|That Time I Got Reincarnated as a Slime",
  "Юно Гасай|Future Diary",
  "Касуми Мива|Jujutsu Kaisen",
  "Рукия Кучики|Bleach",
  "Ребекка (Cyberpunk)|Cyberpunk: Edgerunners",
  "Ноэль Сильва|Black Clover",
  "Аска Лэнгли|Neon Genesis Evangelion",
  "Нэдзуко Камадо|Demon Slayer",
  "Ноль Два|Darling in the Franxx",
  "Нобара Кугисаки|Jujutsu Kaisen",
  "Мицури Канродзи|Demon Slayer",
  "Ямато|One Piece",
  "Эсиль Радиру|Solo Leveling",
  "Шинобу Кочо|Demon Slayer",
  "Мимоза Вермиллион|Black Clover",
  "Рафталия|The Rising of the Shield Hero",
  "Момо Аясэ|Dandadan",
  "Альбедо|Overlord",
  "Алиса Михайловна Кудзё|Alya Sometimes Hides Her Feelings in Russian",
  "Риас Гремори|High School DxD",
  "Чха Хэин|Solo Leveling",
  "Сэйко Аясэ|Dandadan",
  "Ферн|Frieren: Beyond Journey's End",
  "Марин Китагава|My Dress-Up Darling",
  "Очако Урарака|My Hero Academia",
  "Тору Хагакуре|My Hero Academia",
  "Фрирен|Frieren: Beyond Journey's End",
  "Акено Химэдзима|High School DxD",
  "Сёко Коми|Komi Can't Communicate",
  "Сюко Коми|Komi Can't Communicate",
  "Момо Яойорозу|My Hero Academia",
  "Цую Асуи|My Hero Academia",
  "Мина Ашидо|My Hero Academia",
  "Леди Гора|My Hero Academia",
  "Полночь|My Hero Academia",
  "Сарада Учиха|Naruto",
  "Ханаби Хьюга|Naruto",
  "Ху Тао|Genshin Impact",
  "Конэко Тодзё|High School DxD",
  "Хана Удзаки|Uzaki-chan Wants to Hang Out!",
  "Цуки Удзаки|Uzaki-chan Wants to Hang Out!",
  "Мирко|My Hero Academia",
  "Даки|Demon Slayer",
  "Томоэ|Tsukimichi: Moonlit Fantasy",
  "Ёсино Химэкава|Date A Live",
  "Ёсинон|Date A Live",
  "Мику Идзаёй|Date A Live",
  "Нацуми Кёно|Date A Live",
  "Ния Хондзё|Date A Live",
  "Мукуро Хосимия|Date A Live",
  "Маюри|Date A Live",
  "Ринне Соноґами|Date A Live",
  "Юи Курата|Trinity Seven",
  "Мира Яманa|Trinity Seven",
  "Акио Фудо|Trinity Seven",
  "Лизелотта Шерлок|Trinity Seven",
 ).map { it.split("|") }
 private val characterReference=mapOf(
  "Тентен" to "Tenten",
  "Айра Сиратори" to "Aira Shiratori",
  "Шуна" to "Shuna",
  "Шион" to "Shion",
  "Юно Гасай" to "Yuno Gasai",
  "Касуми Мива" to "Kasumi Miwa",
  "Рукия Кучики" to "Rukia Kuchiki",
  "Ребекка (Cyberpunk)" to "Rebecca Cyberpunk Edgerunners",
  "Ноэль Сильва" to "Noelle Silva",
  "Аска Лэнгли" to "Asuka Langley Soryu",
  "Нэдзуко Камадо" to "Nezuko Kamado",
  "Ноль Два" to "Zero Two",
  "Нобара Кугисаки" to "Nobara Kugisaki",
  "Мицури Канродзи" to "Mitsuri Kanroji",
  "Ямато" to "Yamato One Piece",
  "Эсиль Радиру" to "Esil Radiru",
  "Шинобу Кочо" to "Shinobu Kocho",
  "Мимоза Вермиллион" to "Mimosa Vermillion",
  "Рафталия" to "Raphtalia",
  "Момо Аясэ" to "Momo Ayase",
  "Альбедо" to "Albedo Overlord",
  "Алиса Михайловна Кудзё" to "Alisa Mikhailovna Kujou",
  "Риас Гремори" to "Rias Gremory",
  "Чха Хэин" to "Cha Hae In",
  "Сэйко Аясэ" to "Seiko Ayase",
  "Ферн" to "Fern Frieren",
  "Марин Китагава" to "Marin Kitagawa",
  "Очако Урарака" to "Ochaco Uraraka",
  "Тору Хагакуре" to "Toru Hagakure",
  "Фрирен" to "Frieren",
  "Акено Химэдзима" to "Akeno Himejima",
  "Сёко Коми" to "Shouko Komi",
  "Сюко Коми" to "Shuuko Komi",
  "Момо Яойорозу" to "Momo Yaoyorozu",
  "Цую Асуи" to "Tsuyu Asui",
  "Мина Ашидо" to "Mina Ashido",
  "Леди Гора" to "Yu Takeyama",
  "Полночь" to "Nemuri Kayama",
  "Сарада Учиха" to "Sarada Uchiha",
  "Ханаби Хьюга" to "Hanabi Hyuuga",
  "Ху Тао" to "Hu Tao Genshin",
  "Конэко Тодзё" to "Koneko Toujou",
  "Хана Удзаки" to "Hana Uzaki",
  "Цуки Удзаки" to "Tsuki Uzaki",
  "Мирко" to "Rumi Usagiyama",
  "Даки" to "Daki Demon Slayer",
  "Томоэ" to "Tomoe Tsukimichi",
  "Ёсино Химэкава" to "Yoshino Himekawa",
  "Ёсинон" to "Yoshinon Date A Live",
  "Мику Идзаёй" to "Miku Izayoi",
  "Нацуми Кёно" to "Natsumi Kyouno",
  "Ния Хондзё" to "Nia Honjou",
  "Мукуро Хосимия" to "Mukuro Hoshimiya",
  "Маюри" to "Mayuri Date A Live",
  "Ринне Соноґами" to "Rinne Sonogami",
  "Юи Курата" to "Yui Kurata",
  "Мира Яманa" to "Mira Yamana",
  "Акио Фудо" to "Akio Fudou",
  "Лизелотта Шерлок" to "Lieselotte Sherlock",
  "Цунаде" to "Tsunade",
  "Мэй Теруми" to "Mei Terumi",
  "Конан" to "Konan",
  "Йоруичи Сихоин" to "Yoruichi Shihouin",
  "Рангику Мацумото" to "Rangiku Matsumoto",
  "Рецу Унохана" to "Retsu Unohana",
  "Нико Робин" to "Nico Robin",
  "Боа Хэнкок" to "Boa Hancock",
  "Нами" to "Nami",
  "Эрза Скарлет" to "Erza Scarlet",
  "Мираджейн Штраус" to "Mirajane Strauss",
  "Люси Хартфилия" to "Lucy Heartfilia",
  "Аква" to "Aqua",
  "Даркнесс" to "Darkness",
  "Круш Карстен" to "Crusch Karsten",
  "Эльза Гранхирт" to "Elsa Granhiert",
  "Эсдес" to "Esdeath",
  "Оливье" to "Olivier",
  "Макима" to "Makima",
  "Йор Форджер" to "Yor Forger",
  "Асуна Юки" to "Asuna Yuuki",
  "Алиса Цуберг" to "Alice Zuberg",
  "Синон" to "Shinon",
  "Лифа" to "Leafa",
  "Куруми Токисаки" to "Kurumi Tokisaki",
  "Тока Ятогами" to "Tohka Yatogami",
  "Оригами Тобиичи" to "Origami Tobiichi",
  "Котори Ицука" to "Kotori Itsuka",
  "Лилит Асами" to "Lilith Asami",
  "Арин Каннадзуки" to "Arin Kannazuki",
  "Леви Кадзама" to "Levi Kazama",
  "Рем" to "Rem",
  "Рам" to "Ram",
  "Эмилия" to "Emilia",
  "Эхидна" to "Echidna",
  "Мегумин" to "Megumin",
  "Виз" to "Wiz",
  "Ультиар Милкович" to "Ultear Milkovich",
  "Кана Альберона" to "Cana Alberona",
  "Биска Коннелл" to "Bisca Connell",
  "Тиа Харрибел" to "Tier Harribel",
  "Неллиэль Ту Одельшванк" to "Nelliel Tu Odelschwanck",
  "Орихимэ Иноуэ" to "Orihime Inoue",
  "Сакура Харуно" to "Sakura Haruno",
  "Хината Хьюга" to "Hinata Hyuuga",
  "Темари" to "Temari",
  "Кушина Узумаки" to "Kushina Uzumaki",
  "Виви Нефертари" to "Nefertari Vivi",
  "Перона" to "Perona",
  "Ребекка" to "Rebecca",
  "Элизабет" to "Elisabeth",
  "Альфа" to "Alpha",
  "Бета" to "Beta",
  "Гамма" to "Gamma",
  "Дельта" to "Delta",
  "Эпсилон" to "Epsilon",
  "Дзета" to "Zeta",
  "Эта" to "Eta"
 )
 private val curatedAppearance=mapOf(
  "Тентен" to "Девушка с каштановыми волосами, собранными в два пучка, и карими глазами; носит одежду шиноби и использует свитки с оружием.",
  "Айра Сиратори" to "Школьница с длинными розовыми волосами и выразительными глазами; обычно носит школьную форму.",
  "Шуна" to "Они с длинными розовыми волосами, розовыми глазами и небольшими рогами; носит традиционную одежду.",
  "Шион" to "Высокая они с длинными фиолетовыми волосами, фиолетовыми глазами и рогом; часто носит деловой костюм.",
  "Юно Гасай" to "Девушка с длинными розовыми волосами, розовыми глазами и школьной формой.",
  "Касуми Мива" to "Девушка с голубыми волосами, обычно собранными спереди, и тёмной формой школы магии; владеет мечом.",
  "Рукия Кучики" to "Невысокая девушка с короткими чёрными волосами и фиолетовыми глазами; носит чёрную форму синигами.",
  "Ребекка (Cyberpunk)" to "Невысокая девушка с белыми волосами с зеленоватыми прядями, красными глазами и яркими киберпанк-нарядами.",
  "Ноэль Сильва" to "Девушка с длинными серебристыми волосами, розовато-лиловыми глазами и одеждой отряда Чёрных Быков.",
  "Аска Лэнгли" to "Девушка с длинными рыжими волосами, голубыми глазами и красным пилотским костюмом.",
  "Нэдзуко Камадо" to "Девушка с длинными тёмными волосами с оранжевыми кончиками, розовыми глазами и розовым кимоно; часто носит бамбуковый намордник.",
  "Ноль Два" to "Девушка с длинными розовыми волосами, бирюзовыми глазами и небольшими красными рожками; носит красную форму.",
  "Нобара Кугисаки" to "Девушка с короткими каштановыми волосами, карими глазами и тёмной школьной формой.",
  "Мицури Канродзи" to "Девушка с длинными розовыми волосами с зелёными кончиками, зелёными глазами и формой истребителей демонов.",
  "Ямато" to "Высокая девушка с длинными белыми волосами с голубыми кончиками, рогами и красными глазами.",
  "Эсиль Радиру" to "Демонесса с длинными фиолетовыми волосами, красными глазами и характерными рогами.",
  "Шинобу Кочо" to "Девушка с тёмными волосами с фиолетовыми кончиками, фиолетовыми глазами и хаори с узором крыльев бабочки.",
  "Мимоза Вермиллион" to "Девушка с длинными рыжеватыми волосами, зелёными глазами и одеждой Золотого Рассвета.",
  "Рафталия" to "Девушка-зверолюдка с длинными каштановыми волосами, ушами и хвостом енотовидного зверя; носит приключенческую одежду.",
  "Момо Аясэ" to "Школьница с каштановыми волосами, обычно собранными в пучки или хвост, и карими глазами.",
  "Альбедо" to "Женщина с длинными чёрными волосами, золотистыми глазами, белым платьем, чёрными крыльями и рогами.",
  "Алиса Михайловна Кудзё" to "Школьница с длинными серебристыми волосами, голубыми глазами и аккуратной школьной формой.",
  "Риас Гремори" to "Девушка с очень длинными алыми волосами, голубыми глазами и школьной формой.",
  "Чха Хэин" to "Девушка со светлыми волосами, собранными в хвост, и карими глазами; носит боевую экипировку охотника.",
  "Сэйко Аясэ" to "Женщина с длинными серебристо-белыми волосами, молодым обликом и выразительным взглядом; носит повседневную одежду.",
  "Ферн" to "Девушка с длинными фиолетовыми волосами, фиолетовыми глазами и строгой одеждой мага.",
  "Марин Китагава" to "Девушка с длинными светлыми волосами с розовыми кончиками, розовыми глазами и модной одеждой.",
  "Очако Урарака" to "Девушка с короткими каштановыми волосами, карими глазами и розово-чёрным геройским костюмом.",
  "Тору Хагакуре" to "Невидимая героиня; обычно видны её перчатки, обувь и элементы костюма, а внешность без невидимости показана отдельно.",
  "Фрирен" to "Эльфийка с длинными серебристо-белыми волосами, зелёными глазами, острыми ушами и бело-золотой одеждой мага.",
  "Акено Химэдзима" to "Девушка с очень длинными чёрными волосами, фиолетовыми глазами и школьной формой.",
  "Сёко Коми" to "Высокая школьница с длинными чёрными волосами, фиолетовыми глазами и тёмной школьной формой.",
  "Сюко Коми" to "Женщина с длинными тёмными волосами и молодым обликом; мать Сёко Коми.",
  "Момо Яойорозу" to "Высокая девушка с длинными чёрными волосами, собранными в высокий хвост, и красным геройским костюмом.",
  "Цую Асуи" to "Девушка с длинными тёмно-зелёными волосами, большими глазами и зелёным костюмом героини-лягушки.",
  "Мина Ашидо" to "Девушка с розовой кожей и волосами, чёрными глазами с жёлтой радужкой и маленькими рожками.",
  "Леди Гора" to "Высокая блондинка с фиолетовыми глазами и фиолетово-бежевым геройским костюмом.",
  "Полночь" to "Женщина с длинными тёмно-фиолетовыми волосами, голубыми глазами и чёрно-белым геройским костюмом.",
  "Сарада Учиха" to "Девушка с короткими чёрными волосами, чёрными глазами, красными очками и одеждой шиноби.",
  "Ханаби Хьюга" to "Девушка с тёмными волосами и светлыми глазами без выраженных зрачков, как у клана Хьюга.",
  "Ху Тао" to "Девушка с длинными тёмно-каштановыми волосами, красными глазами и чёрной шляпой с цветком.",
  "Конэко Тодзё" to "Невысокая девушка с короткими белыми волосами, золотистыми глазами и школьной формой.",
  "Хана Удзаки" to "Девушка с короткими серебристо-серыми волосами, голубыми глазами и повседневной одеждой.",
  "Цуки Удзаки" to "Женщина с короткими тёмно-синими волосами, голубыми глазами и домашней одеждой; мать Ханы.",
  "Мирко" to "Высокая спортивная женщина с белыми волосами, красными глазами, длинными кроличьими ушами и бело-фиолетовым геройским костюмом.",
  "Даки" to "Демоница с длинными светлыми волосами с зелёными кончиками, яркими глазами и украшенным поясами кимоно.",
  "Томоэ" to "Женщина с длинными сине-зелёными волосами, голубыми глазами и традиционной японской одеждой.",
  "Ёсино Химэкава" to "Девушка с длинными голубыми волосами, голубыми глазами, зелёным плащом и куклой-кроликом.",
  "Ёсинон" to "Говорящая кукла-кролик на руке Ёсино: белая мордочка, зелёный капюшон и повязка на глазу.",
  "Мику Идзаёй" to "Девушка с длинными фиолетовыми волосами, фиолетовыми глазами и сценическими нарядами.",
  "Нацуми Кёно" to "Дух с зелёными волосами и глазами, часто носит ведьминскую шляпу и платье.",
  "Ния Хондзё" to "Женщина с серебристо-белыми волосами, очками и одеждой с монашескими мотивами.",
  "Мукуро Хосимия" to "Девушка с очень длинными золотистыми волосами, янтарными глазами и необычной одеждой духа.",
  "Маюри" to "Девушка со светлыми волосами и голубыми глазами, носит светлое платье.",
  "Ринне Соноґами" to "Девушка с длинными светло-каштановыми волосами и школьной формой.",
  "Юи Курата" to "Девушка со светлыми волосами и голубыми глазами, часто носит школьную форму.",
  "Мира Яманa" to "Девушка с длинными светлыми волосами и школьной формой.",
  "Акио Фудо" to "Девушка с короткими светлыми волосами и боевой школьной формой.",
  "Лизелотта Шерлок" to "Девушка с длинными светлыми волосами, голубыми глазами и школьной формой.",
  "Цунаде" to "Высокая женщина со светлыми волосами, собранными в два длинных хвоста; карие глаза, ромбовидная метка на лбу, зелёная накидка поверх серой одежды.",
  "Мэй Теруми" to "Стройная женщина с длинными каштановыми волосами, закрывающими один глаз; зелёные глаза, синее платье и пояс.",
  "Конан" to "Женщина с короткими сине-фиолетовыми волосами, янтарными глазами и бумажным цветком в причёске; чёрный плащ Акацуки.",
  "Йоруичи Сихоин" to "Темнокожая спортивная женщина с золотистыми глазами и длинными фиолетовыми волосами, собранными в высокий хвост.",
  "Рангику Мацумото" to "Высокая женщина с пышными волнистыми светло-рыжими волосами, голубыми глазами и формой синигами.",
  "Рецу Унохана" to "Женщина с длинными чёрными волосами, заплетёнными спереди, тёмными глазами и белым капитанским хаори.",
  "Нико Робин" to "Высокая стройная женщина с длинными чёрными волосами, голубыми глазами и утончёнными чертами лица; одежда меняется по сюжетным аркам.",
  "Боа Хэнкок" to "Высокая женщина с очень длинными прямыми чёрными волосами, тёмно-синими глазами и красно-фиолетовым нарядом с золотыми украшениями.",
  "Нами" to "Стройная девушка с оранжевыми волосами, карими глазами и татуировкой на левом плече; носит разнообразную яркую одежду.",
  "Эрза Скарлет" to "Высокая девушка с длинными алыми волосами, карими глазами и боевыми доспехами; часто меняет магические комплекты брони.",
  "Мираджейн Штраус" to "Девушка с длинными белыми волосами, голубыми глазами и доброжелательным выражением лица; часто носит платье.",
  "Люси Хартфилия" to "Светловолосая девушка с большими карими глазами, длинными волосами и синим знаком гильдии на правой руке.",
  "Аква" to "Девушка с длинными голубыми волосами, собранными в высокий хвост, голубыми глазами и сине-белым платьем.",
  "Даркнесс" to "Высокая блондинка с длинными волосами, голубыми глазами и рыцарскими доспехами.",
  "Круш Карстен" to "Девушка с зелёными волосами до плеч, янтарными глазами и строгой военной формой с белыми элементами.",
  "Эльза Гранхирт" to "Высокая женщина с длинными тёмными волосами, фиолетовыми глазами и открытым чёрным нарядом с меховыми деталями.",
  "Эсдес" to "Высокая женщина с длинными голубыми волосами, синими глазами, белой военной формой и фуражкой.",
  "Оливье" to "Светловолосая воительница с длинными волосами, светлыми глазами и бело-золотыми доспехами; образ героини из «Восхождения в тени».",
  "Макима" to "Женщина с рыжевато-красными волосами, собранными в косу, золотистыми глазами с концентрическими кольцами; белая рубашка и чёрный галстук.",
  "Йор Форджер" to "Женщина с длинными чёрными волосами, красными глазами и золотыми украшениями для волос; обычно носит красно-чёрное платье или повседневную одежду.",
  "Асуна Юки" to "Стройная девушка с длинными каштановыми волосами и янтарными глазами; известна бело-красным костюмом фехтовальщицы.",
  "Алиса Цуберг" to "Девушка с длинными золотистыми волосами, голубыми глазами и золотыми рыцарскими доспехами.",
  "Синон" to "Девушка с короткими бирюзовыми волосами, голубыми глазами и сине-белой экипировкой стрелка в GGO.",
  "Лифа" to "Девушка с длинными светлыми волосами, зелёными глазами и зелёно-белым костюмом сильфа.",
  "Куруми Токисаки" to "Девушка с длинными чёрными волосами, красным правым глазом и золотым левым глазом с часовым циферблатом; красно-чёрное готическое платье.",
  "Тока Ятогами" to "Девушка с очень длинными тёмно-фиолетовыми волосами, фиолетовыми глазами и фиолетовыми доспехами духа.",
  "Оригами Тобиичи" to "Девушка с короткими серебристо-белыми волосами, голубыми глазами и сдержанным выражением лица.",
  "Котори Ицука" to "Девушка с длинными красными волосами, обычно собранными в два хвоста с чёрными или белыми лентами; красные глаза.",
  "Лилит Асами" to "Девушка с длинными светлыми волосами, голубыми глазами, школьной формой и чёрной шляпой ведьмы.",
  "Арин Каннадзуки" to "Девушка с длинными серебристо-белыми волосами, красными глазами и школьной формой.",
  "Леви Кадзама" to "Девушка с короткими светлыми волосами и зелёными глазами; носит школьную форму и характерные аксессуары.",
  "Рем" to "Девушка с короткими голубыми волосами, закрывающими один глаз, голубыми глазами и чёрно-белой формой горничной.",
  "Рам" to "Девушка с короткими розовыми волосами, закрывающими один глаз, красными глазами и чёрно-белой формой горничной.",
  "Эмилия" to "Девушка с длинными серебристыми волосами, фиолетово-синими глазами и бело-лиловой одеждой.",
  "Эхидна" to "Женщина с длинными белыми волосами, чёрными глазами и бело-чёрным платьем с украшениями в виде бабочек.",
  "Мегумин" to "Невысокая девушка с короткими тёмно-каштановыми волосами, красными глазами, красной мантией и большой остроконечной шляпой.",
  "Виз" to "Женщина с длинными каштановыми волосами, карими глазами и фиолетовым платьем с чёрным плащом.",
  "Ультиар Милкович" to "Женщина с длинными тёмно-фиолетовыми волосами, красноватыми глазами и нарядом с открытыми плечами.",
  "Кана Альберона" to "Девушка с длинными каштановыми волосами, фиолетовыми глазами и повседневной одеждой; часто держит карты.",
  "Биска Коннелл" to "Девушка с зелёными волосами и одеждой в стиле вестерн, часто с ковбойской шляпой.",
  "Тиа Харрибел" to "Высокая темнокожая женщина со светлыми волосами, зелёными глазами и белым костюмом арранкара.",
  "Неллиэль Ту Одельшванк" to "Женщина с длинными зелёными волосами, зелёными глазами и остатком маски пустого на голове.",
  "Орихимэ Иноуэ" to "Девушка с длинными рыжими волосами, серо-карими глазами и цветочными заколками.",
  "Сакура Харуно" to "Девушка с короткими розовыми волосами, зелёными глазами и красной одеждой шиноби.",
  "Хината Хьюга" to "Девушка с тёмно-синими волосами, светлыми глазами без видимых зрачков и одеждой клана Хьюга.",
  "Темари" to "Девушка с песочно-светлыми волосами, собранными в четыре хвоста, зелёными глазами и одеждой шиноби Песка.",
  "Кушина Узумаки" to "Женщина с очень длинными ярко-красными волосами, фиолетовыми глазами и светлой кожей.",
  "Виви Нефертари" to "Девушка с длинными светло-голубыми волосами, карими глазами и королевской одеждой.",
  "Перона" to "Девушка с длинными розовыми волосами, большими глазами и готическим нарядом с зонтиком.",
  "Ребекка" to "Девушка с длинными розовыми волосами, светлыми глазами и гладиаторскими доспехами.",
  "Элизабет" to "Женщина с длинными серебристо-белыми волосами, красными глазами и роскошным тёмно-красным платьем; известна как Кровавая Королева.",
  "Альфа" to "Эльфийка с длинными светлыми волосами, голубыми глазами и острыми ушами; носит тёмный боевой костюм Сада Теней.",
  "Бета" to "Эльфийка с длинными серебристыми волосами, голубыми глазами и острыми ушами; носит тёмный костюм Сада Теней.",
  "Гамма" to "Эльфийка с длинными тёмно-синими волосами, голубыми глазами и острыми ушами; носит деловой наряд или форму Сада Теней.",
  "Дельта" to "Зверолюдка с длинными чёрными волосами, звериными ушами и хвостом; носит чёрный костюм Сада Теней.",
  "Эпсилон" to "Эльфийка с длинными голубыми волосами и голубыми глазами; носит тёмный костюм Сада Теней.",
  "Дзета" to "Зверолюдка со светлыми волосами, кошачьими ушами и хвостом; носит тёмный костюм Сада Теней.",
  "Эта" to "Эльфийка с тёмными волосами, острыми ушами и сонным взглядом; носит тёмный костюм Сада Теней."
 )
 private fun migrateDefaults() {
  if(prefs.getBoolean("removed_old_defaults",false))return
  listOf("Алиса","Кай","Лира").forEach { name ->
   if(chats[name].isNullOrEmpty()){characters.remove(name);chats.remove(name)}
  }
  if(!characters.contains(active))active=characters.firstOrNull()?:"Цунаде"
  prefs.edit().putBoolean("removed_old_defaults",true).apply();save()
 }
 private fun seedCatalog() {
  // Synchronize new characters without overwriting existing user profiles.
  animeRoster.forEach { parts ->
   val name=parts[0]
   if(!characters.contains(name)){characters.add(name);chats[name]=mutableListOf()}
   fillStoryDefaults(name,parts[1])
   if(prefs.getString("memory_$name","").isNullOrBlank())prefs.edit().putString("memory_$name","Постоянные сведения: $name — персонаж мира ${parts[1]}. "+(curatedAppearance[name]?: "Внешность уточняется.")+"\n\nИстория отношений с игроком: пока нет подтверждённых событий. Не выдумывай прошлые встречи, обещания или воспоминания. Сохраняй важные факты из текущей переписки.").apply()
   if(prefs.getString("appearance_$name","").isNullOrBlank())prefs.edit().putString("appearance_$name",curatedAppearance[name].orEmpty()).apply()
  }
  prefs.edit().putBoolean("catalog_seed_05",true).putBoolean("story_defaults_094",true).apply()
  save()
 }
 private fun fillStoryDefaults(name:String,universe:String) {
  val editor=prefs.edit()
  val role=prefs.getString("persona_$name","").orEmpty()
  if(role.isBlank() || role.startsWith("Вселенная:"))editor.putString("persona_$name",
   "$name — персонаж вселенной $universe. Учитывай канонические особенности личности, привычки, цели и отношения. Не придумывай факты о прошлом игрока.")
  if(prefs.getString("scenario_$name","").isNullOrBlank())editor.putString("scenario_$name",
   "Мир: $universe. Интерактивная ролевая история с последовательными событиями, выборами и последствиями. Персонаж $name действует самостоятельно, но не управляет решениями игрока.")
  if(prefs.getString("opening_$name","").isNullOrBlank())editor.putString("opening_$name",
   "Игрок впервые встречает $name. Место, обстоятельства и причина встречи раскрываются в первых сообщениях. История начинается с живой сцены, а не пересказа.")
  if(prefs.getString("greeting_$name","").isNullOrBlank())editor.putString("greeting_$name",
   "*Встреча начинается неожиданно. $name замечает тебя и внимательно смотрит.*\\n— Кто ты и что привело тебя сюда?")
  if(prefs.getString("protocol_$name","").isNullOrBlank())editor.putString("protocol_$name",
   "Пиши по-русски. Сохраняй характер, логику мира и хронологию. Отвечай живыми сценами и диалогами средней длины. Не пиши реплики, мысли или действия за игрока. Не повторяй дословно сообщения игрока.")
  editor.apply()
 }
 private fun characterCard(parent:LinearLayout,name:String) {
  val tile=LinearLayout(this).apply { orientation=1;background=shape(panel,20f);setPadding(dp(6),dp(6),dp(6),dp(8)) }
  val palette=intArrayOf(Color.rgb(41,75,135),Color.rgb(90,58,130),Color.rgb(26,107,117),Color.rgb(112,65,96))
  tile.addView(avatar(name,116),LinearLayout.LayoutParams(-1,dp(116)))
  tile.addView(text(name,14f,Color.WHITE,true))
  tile.addView(text(animeRoster.firstOrNull{it[0]==name}?.get(1)?: "Мой персонаж",12f,muted))
  tile.setOnClickListener { characterDetail(name) };tile.setOnLongClickListener { chooseAvatar(name);true }
  parent.addView(tile,LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(dp(4),dp(4),dp(4),dp(4))})
 }
 private fun chooseAvatar(name:String) {
  avatarTarget=name
  avatarReturn=name
  val intent=android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
   type="image/*";addCategory(android.content.Intent.CATEGORY_OPENABLE)
   addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
  }
  startActivityForResult(intent,avatarPicker)
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:android.content.Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==ggufPicker && resultCode==Activity.RESULT_OK && data?.data!=null){
   val uri=data.data!!
   val name=try{
    contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->
     if(c.moveToFirst())c.getString(0) else null
    }
   }catch(_:Exception){null}
   val display=name?:"selected.gguf"
   if(!display.lowercase(java.util.Locale.ROOT).endsWith(".gguf")){alert("Выбери файл модели с расширением .gguf");return}
   val target=File(File(getExternalFilesDir(null),"models/text").apply{mkdirs()},"imported-"+System.currentTimeMillis()+".gguf")
   val progress=AlertDialog.Builder(this).setTitle("Импорт GGUF").setMessage("Копирование модели в память приложения… Не закрывай Shadow RP до завершения.").setCancelable(false).create()
   progress.show();styleDialog(progress)
   thread{
    try{
     val available=android.os.StatFs(target.parentFile!!.absolutePath).availableBytes
     val size=contentResolver.openAssetFileDescriptor(uri,"r")?.use{it.length}?:-1L
     if(size>0 && available<size+64L*1024*1024)error("Недостаточно места: требуется ещё около "+((size+64L*1024*1024-available)/1048576)+" МиБ")
     contentResolver.openInputStream(uri)?.use{input->
      target.outputStream().buffered().use{output->input.copyTo(output,1024*1024)}
     }?:error("Не удалось открыть выбранный файл")
     if(target.length()<10000000L)error("Файл слишком мал для модели GGUF")
     target.inputStream().use{stream->
      val magic=ByteArray(4)
      if(stream.read(magic)!=4||!magic.contentEquals(byteArrayOf(71,71,85,70)))error("Неверный формат файла GGUF")
     }
     prefs.edit().putString("local_model",target.name).apply()
     runOnUiThread{progress.dismiss();alert("Модель импортирована: "+display+" ("+target.length()/1048576+" МиБ). Она выбрана для RP. Перезапусти приложение перед запуском.") }
    }catch(e:Exception){target.delete();runOnUiThread{progress.dismiss();alert("Не удалось импортировать GGUF: "+e.message)}}
   }
   return
  }
  if(requestCode==avatarPicker && resultCode==Activity.RESULT_OK && data?.data!=null){
   val uri=data.data!!
   try { contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }catch(_:Exception){}
   avatarTarget?.let { prefs.edit().putString("avatar_$it",uri.toString()).apply();characterDetail(it) }
  }
 }
 private val portraitPending=mutableMapOf<String,MutableList<ImageView>>()
 private val portraitExecutor=java.util.concurrent.Executors.newSingleThreadExecutor()
 private val portraitRetry=mutableMapOf<String,Long>()
 private fun requestVerifiedPortrait(name:String,view:ImageView) {
  val query=characterReference[name]?:return
  val cache=File(filesDir,"verified_portraits").apply{mkdirs()}
  val file=File(cache,java.lang.Integer.toHexString(name.hashCode())+".jpg")
  if(file.exists() && file.length()>1024) {
   val bitmap=BitmapFactory.decodeFile(file.absolutePath)
   if(bitmap!=null){view.setImageBitmap(bitmap);view.visibility=View.VISIBLE;return}
   file.delete()
  }
  synchronized(portraitPending){
   if(portraitRetry[name]?.let{System.currentTimeMillis()-it<3600000L}==true)return
   val listeners=portraitPending[name]
   if(listeners!=null){listeners.add(view);return}
   portraitPending[name]=mutableListOf(view)
  }
  portraitExecutor.execute {
   var bitmap:android.graphics.Bitmap?=null
   try {
    val endpoint="https://api.jikan.moe/v4/characters?q="+URLEncoder.encode(query.substringBefore(" "),"UTF-8")+"&limit=25"
    val conn=(URL(endpoint).openConnection() as HttpURLConnection).apply{
     connectTimeout=12000;readTimeout=12000;setRequestProperty("User-Agent","ShadowRP/0.11.1")
    }
    val response=JSONObject(conn.inputStream.bufferedReader().use{it.readText()});conn.disconnect()
    val matches=response.optJSONArray("data")?:JSONArray()
    val parts=query.lowercase().split(Regex("[^a-z]+")).filter{it.isNotEmpty()}
    var url=""
    for(i in 0 until matches.length()){
     val item=matches.optJSONObject(i)?:continue
     val nameParts=item.optString("name").lowercase().split(Regex("[^a-z]+")).filter{it.isNotEmpty()}
     val exact=parts.size==nameParts.size && parts.toSet()==nameParts.toSet()
     if(!exact)continue
     url=item.optJSONObject("images")?.optJSONObject("jpg")?.optString("image_url","").orEmpty()
     if(url.startsWith("https://"))break
    }
    if(url.startsWith("https://")){
     val download=(URL(url).openConnection() as HttpURLConnection).apply{connectTimeout=12000;readTimeout=12000}
     val bytes=download.inputStream.use{it.readBytes()};download.disconnect()
     if(bytes.size in 1024..3000000){
      bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
      if(bitmap!=null)file.writeBytes(bytes)
     }
    }
   }catch(_:Exception){}
   val result=bitmap
   val listeners=synchronized(portraitPending){
    if(result==null)portraitRetry[name]=System.currentTimeMillis()
    portraitPending.remove(name)?:mutableListOf()
   }
   if(result!=null)runOnUiThread {
    if(prefs.getString("avatar_$name",null)==null)listeners.forEach{it.setImageBitmap(result);it.visibility=View.VISIBLE}
   }
   Thread.sleep(900)
  }
 }
 private fun avatar(name:String,height:Int):android.view.View {
  val saved=prefs.getString("avatar_$name",null)
  if(saved!=null){
   val image=ImageView(this).apply {
    scaleType=ImageView.ScaleType.CENTER_CROP
    background=shape(panel)
    clipToOutline=true
   }
   try {
    val uri=android.net.Uri.parse(saved)
    contentResolver.openInputStream(uri)?.use { stream ->
     val bmp=BitmapFactory.decodeStream(stream)
     if(bmp!=null){image.setImageBitmap(bmp);return image}
    }
   }catch(_:Exception){}
  }
  val holder=android.widget.FrameLayout(this)
  val placeholder=text("✦\n＋ Портрет\n"+name,17f,Color.WHITE,true).apply {
   gravity=Gravity.CENTER
   background=shape(Color.rgb(37,54,86),18f)
   setOnClickListener {chooseAvatar(name)}
  }
  holder.addView(placeholder,android.widget.FrameLayout.LayoutParams(-1,-1))
  val remote=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;visibility=android.view.View.GONE;clipToOutline=true;background=shape(panel)}
  holder.addView(remote,android.widget.FrameLayout.LayoutParams(-1,-1))
  requestVerifiedPortrait(name,remote)
  return holder
 }
 private fun characterDetail(name:String) {
  active=name
  init("✦ $name")
  val b=body()
  b.addView(avatar(name,300),LinearLayout.LayoutParams(-1,dp(300)).apply {setMargins(dp(8),dp(12),dp(8),dp(8))})
  b.addView(text(name,30f,Color.WHITE,true))
  if(prefs.getString("avatar_$name",null)==null)b.addView(button("＋ Добавить портрет персонажа") {chooseAvatar(name)}.apply {setTextColor(Color.rgb(149,190,255))})
  val universe=animeRoster.firstOrNull{it[0]==name}?.get(1)?:"Авторский персонаж"
  b.addView(text("✦ $universe",16f,Color.rgb(133,179,255),true))
  b.addView(button("▶  Начать RP с $name"){chat(name)}.apply{background=shape(Color.rgb(78,62,189),16f);textSize=19f})
  val tabs=LinearLayout(this)
  listOf("О персонаже","История","Отзывы").forEachIndexed {i,label->
   tabs.addView(button(label){if(i==2)comments(name) else if(i==1)chat(name) else characterDetail(name)}.apply{textSize=13f;setTextColor(if(i==0)Color.rgb(178,149,255) else muted)},LinearLayout.LayoutParams(0,-2,1f))
  }
  b.addView(tabs)
  val fields=listOf(
   "Характер и предыстория" to "persona",
   "Внешность" to "appearance",
   "Сценарий ролевой игры" to "scenario",
   "Начальная ситуация" to "opening",
   "Первое сообщение персонажа" to "greeting",
   "Правила поведения ИИ" to "protocol",
   "Память персонажа" to "memory"
  )
  fields.forEach { (label,key) ->
   val value=prefs.getString(key+"_"+name,"").orEmpty().ifBlank {
    if(key=="persona")prefs.getString("persona_$name","").orEmpty() else ""
   }
   val card=LinearLayout(this).apply {orientation=1;background=shape(panel,20f);setPadding(dp(12),dp(12),dp(12),dp(12))}
   card.addView(text(label,20f,Color.WHITE,true))
   card.addView(text(value.ifBlank { "Пока не заполнено" },16f,if(value.isBlank())muted else Color.WHITE))
   val edit=button("✎  Изменить") {editCharacterField(name,label,key)}.apply {
    textSize=15f;setTextColor(Color.rgb(153,193,255));gravity=Gravity.RIGHT
   }
   card.addView(edit)
   b.addView(card,LinearLayout.LayoutParams(-1,-2).apply {setMargins(dp(5),dp(7),dp(5),dp(7))})
  }
  item(b,"🖼  Изображение персонажа","Выбрать свой портрет из галереи") {chooseAvatar(name)}
  item(b,"✦  Отзывы","Комментарии об этом персонаже") {comments(name)}
  root.addView(button("▶  Начать RP") {chat(name)}.apply {
   textSize=20f;background=shape(Color.rgb(35,89,191),18f)
   setPadding(dp(14),dp(19),dp(14),dp(19))
  },LinearLayout.LayoutParams(-1,-2).apply {setMargins(dp(10),dp(4),dp(10),dp(10))})
 }
 private fun editCharacterField(name:String,label:String,key:String) {
  init(label)
  val b=body()
  val old=prefs.getString(key+"_"+name,"").orEmpty().ifBlank {
   if(key=="persona")prefs.getString("persona_$name","").orEmpty() else ""
  }
  val field=visibleField(label,old,b)
  field.minLines=7
  field.maxLines=20
  field.gravity=Gravity.TOP
  field.setSingleLine(false)
  root.addView(button("✓ Сохранить") {
   prefs.edit().putString(key+"_"+name,field.text.toString()).apply()
   characterDetail(name)
  }.apply {textSize=19f;background=shape(Color.rgb(35,89,191))})
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
  val entry=EditText(this).apply {hint="Твой отзыв о персонаже";setHintTextColor(muted);setTextColor(Color.WHITE);background=shape(Color.rgb(34,46,69));setPadding(dp(12),dp(12),dp(12),dp(12))}
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
   val face=avatar(name,175)
   card.addView(face,LinearLayout.LayoutParams(-1,dp(175)))
   card.addView(text(name,16f,Color.WHITE,true))
   card.setOnClickListener { characterDetail(name) };card.setOnLongClickListener { chooseAvatar(name);true }
   row.addView(card,LinearLayout.LayoutParams(dp(178),-2).apply {setMargins(dp(4),dp(5),dp(4),dp(8))})
  }
  scroller.addView(row);parent.addView(scroller)
 }
 private fun home() {
  init("Shadow RP");val b=body()
  val tabs=LinearLayout(this)
  listOf("Для вас","Аниме","Игры","ОС","Популярные").forEach{title->tabs.addView(button(title){catalog()}.apply{textSize=12f;setPadding(dp(6),dp(10),dp(6),dp(10))},LinearLayout.LayoutParams(0,-2,1f))}
  b.addView(tabs)
  val searchButton=button("⌕  Поиск по персонажам и аниме"){catalog()}.apply{gravity=Gravity.LEFT;background=shape(Color.rgb(18,35,62),14f)}
  b.addView(searchButton)
  b.addView(text("Для вас",22f,Color.WHITE,true))
  val recent=characters.filter { chats[it]?.isNotEmpty()==true }.sortedByDescending { chats[it]?.size?:0 }
  shelf(b,"Продолжить общение",recent.take(8))
  shelf(b,"✦ Популярные героини",listOf("Цунаде","Йоруичи Сихоин","Сакура Харуно","Хината Хьюга","Альфа","Дельта"))
  animeRoster.map{it[1]}.distinct().take(5).forEach { title ->
   shelf(b,title,animeRoster.filter{it[1]==title}.map{it[0]})
  }
  nav(0)
 }
 private fun catalog() {
  init("Каталог");val b=body()
  val search=EditText(this).apply{hint="⌕  Поиск по персонажам и аниме";setSingleLine(true);setTextColor(Color.WHITE);setHintTextColor(muted);background=shape(panel);setPadding(dp(15),dp(14),dp(15),dp(14))}
  b.addView(search)
  val result=LinearLayout(this).apply{orientation=1};b.addView(result)
  fun render(q:String) {
   result.removeAllViews()
   val filtered=characters.filter{it.contains(q,true)||animeRoster.firstOrNull{r->r[0]==it}?.get(1)?.contains(q,true)==true}
   val sets=if(q.isNotBlank())listOf("Результаты поиска" to filtered) else listOf("Все героини" to filtered)
   sets.forEach{(heading,names)->
    result.addView(section(heading))
    names.chunked(2).forEach{pair->
     val row=LinearLayout(this).apply{orientation=0}
     pair.forEach{name->
      val card=LinearLayout(this).apply{orientation=1;background=shape(panel,17f);setPadding(dp(5),dp(5),dp(5),dp(8))}
      card.addView(avatar(name,190),LinearLayout.LayoutParams(-1,dp(190)))
      card.addView(text(name,15f,Color.WHITE,true))
      card.addView(text(animeRoster.firstOrNull{it[0]==name}?.get(1)?:"Авторский",11f,muted))
      card.setOnClickListener{characterDetail(name)}
      card.setOnLongClickListener{chooseAvatar(name);true}
      row.addView(card,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(4),dp(4),dp(4),dp(4))})
     }
     if(pair.size==1)row.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
     result.addView(row)
    }
   }
  }
  render("")
  search.addTextChangedListener(object:android.text.TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){render(s?.toString()?:"")}
   override fun afterTextChanged(s:android.text.Editable?){}
  })
  nav(1)
 }
 private fun studio() {
  init("Творческий центр");val b=body()
  item(b,"✦ Создать персонажа","Аватар, приветствие, характер, сценарий, память") { createCharacter() }
  item(b,"♧ Создать групповой чат","Выбрать участников из своих переписок") { createRoom() }
  item(b,"♙ Моя личность","Имя, биография и роль игрока") { editIdentity() }
  item(b,"▣ Создать изображение","Пока доступен просмотр промпта по истории") { imagePrompt() }
  item(b,"▤ Лорбук","Планируется в следующих обновлениях") { AlertDialog.Builder(this).setMessage("Редактор лорбука ещё не реализован.").setPositiveButton("OK",null).show().also {styleDialog(it)} }
  nav(2)
 }
 private fun createCharacter() {
  init("Новый персонаж")
  val b=body()
  b.addView(text("Создай свою героиню или героя. Все поля оформлены в едином стиле.",15f,muted))
  val name=visibleField("Имя персонажа","",b)
  val persona=visibleField("Характер, роль, предыстория","",b)
  val appearance=visibleField("Внешность и стиль","",b)
  val scenario=visibleField("Сценарий и начало истории","",b)
  val greeting=visibleField("Приветствие персонажа","",b)
  val protocol=visibleField("Протокол: правила поведения ИИ","",b)
  val memory=visibleField("Карточка памяти: важные факты","",b)
  root.addView(button("✓ Создать персонажа") {
   val n=name.text.toString().trim()
   if(n.isBlank()){alert("Укажи имя персонажа");return@button}
   if(characters.contains(n)){alert("Персонаж с таким именем уже существует");return@button}
   characters.add(n);chats[n]=mutableListOf()
   prefs.edit().putString("persona_$n",persona.text.toString()).putString("appearance_$n",appearance.text.toString()).putString("scenario_$n",scenario.text.toString()).putString("greeting_$n",greeting.text.toString()).putString("protocol_$n",protocol.text.toString()).putString("memory_$n",memory.text.toString()).apply()
   save();chooseAvatar(n)
  }.apply {textSize=19f;setBackgroundColor(Color.rgb(39,84,165))})
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

  val profileRow=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;background=shape(Color.rgb(13,23,42),16f)}
  profileRow.addView(avatar(name,54),LinearLayout.LayoutParams(dp(54),dp(54)))
  val info=LinearLayout(this).apply{orientation=1}
  info.addView(text(name,17f,Color.WHITE,true))
  info.addView(text("● Персонаж RP",12f,Color.rgb(116,211,171)))
  profileRow.addView(info,LinearLayout.LayoutParams(0,-2,1f))
  profileRow.addView(button("⋮"){characterDetail(name)})
  b.addView(profileRow)
  if(messages.isEmpty()) {
   val greeting=prefs.getString("greeting_$name","").orEmpty().ifBlank { "✦ Начни историю с $name\\n\\nСоздай собственный сюжет и отправь первое сообщение." }
   val bubble=text(greeting,19f,Color.WHITE).apply {background=shape(panel);setPadding(dp(18),dp(18),dp(18),dp(18))}
   b.addView(bubble,LinearLayout.LayoutParams(-1,-2).apply {setMargins(dp(8),dp(12),dp(32),dp(8))})
  }
  messages.forEachIndexed { index,(role,value)->
   val bubble=text(value,20f).apply {setPadding(dp(17),dp(16),dp(17),dp(16));minHeight=dp(70);maxWidth=(resources.displayMetrics.widthPixels*0.87).toInt()}
   bubble.background=shape(if(role=="user")Color.rgb(67,49,179) else Color.rgb(27,39,61))
   val lp=LinearLayout.LayoutParams(-2,-2).apply {
    gravity=if(role=="user")Gravity.RIGHT else Gravity.LEFT
    setMargins(if(role=="user")dp(28) else dp(6),dp(7),if(role=="user")dp(6) else dp(28),dp(7))
   }
   val column=LinearLayout(this).apply{orientation=1;gravity=if(role=="user")Gravity.RIGHT else Gravity.LEFT}
   column.addView(bubble)
   val timestamp=prefs.getLong("chat_time_${name}_$index",0L)
   val timeLabel=if(timestamp>0L)java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(java.util.Date(timestamp)) else "Время неизвестно"
   column.addView(text(timeLabel,11f,muted).apply{setPadding(dp(10),dp(3),dp(10),0)})
   b.addView(column,lp)
  }
  if(localPending.contains(name)){
   val typing=text("● $name печатает… 00:00",15f,Color.rgb(194,178,255)).apply{background=shape(Color.rgb(27,39,61),18f)}
   b.addView(typing,LinearLayout.LayoutParams(-2,-2).apply{setMargins(dp(8),dp(10),dp(90),dp(10))})
   val started=localStarted[name]?:System.currentTimeMillis()
   val updater=object:Runnable{
    override fun run(){
     if(!localPending.contains(name) || active!=name || typing.parent==null)return
     val elapsed=((System.currentTimeMillis()-started)/1000).coerceAtLeast(0)
     typing.text="● $name печатает… %02d:%02d".format(elapsed/60,elapsed%60)
     timerHandler.postDelayed(this,1000)
    }
   }
   timerHandler.post(updater)
  }
  val input=EditText(this).apply { hint="Напиши сообщение…";setTextColor(Color.WHITE);setHintTextColor(muted);minLines=2;maxLines=6;setTextSize(17f);setPadding(dp(12),dp(12),dp(12),dp(12));background=shape(panel,16f) }
  val bar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),dp(7),dp(4),dp(9));background=shape(Color.rgb(10,17,32),18f) }
  bar.addView(button("▧") { imagePrompt() }.apply {textSize=21f;setPadding(dp(12),dp(18),dp(12),dp(18))})
  bar.addView(input,LinearLayout.LayoutParams(0,-2,1f))
  bar.addView(button("➤") {
   val msg=input.text.toString().trim()
   if(msg.isNotBlank()){prefs.edit().putLong("chat_time_${name}_${messages.size}",System.currentTimeMillis()).apply();messages.add("user" to msg);save();chat(name);request(name)}
  }.apply {textSize=23f;setPadding(dp(14),dp(18),dp(14),dp(18))})
  root.addView(bar)
  b.post {
   var v:android.view.View?=b.parent as? android.view.View
   while(v!=null && v !is ScrollView){v=v.parent as? android.view.View}
   (v as? ScrollView)?.fullScroll(android.view.View.FOCUS_DOWN)
  }
 }
 private fun requestLocal(name:String) {
  val file=File(File(getExternalFilesDir(null),"models/text"),prefs.getString("local_model","qwen3-0.6b-q4_k_m.gguf").orEmpty())
  if(!file.exists()||!LocalLlm.available){alert("Локальная модель или движок недоступны. Проверь настройки.");return}
  if(!localPending.add(name)){alert("Qwen уже генерирует ответ. Дождись завершения.");return}
  localStarted[name]=System.currentTimeMillis()
  if(active==name)chat(name)
  val history=chats[name]?.takeLast(5)?.toList()?:run{localPending.remove(name);localStarted.remove(name);return}
  val identity=prefs.getString("identity_name","Игрок").orEmpty()
  val persona=prefs.getString("persona_$name","").orEmpty().take(250)
  val memory=prefs.getString("memory_$name","").orEmpty().take(140)
  val recent=history.takeLast(4).joinToString(""){(role,value)->
   val tag=if(role=="user")"user" else "assistant"
   "<|im_start|>$tag\n"+value.take(300)+"<|im_end|>\n"
  }
  val prompt="<|im_start|>system\nТы $name, персонаж ролевой истории. Характер: $persona\nВажная память: $memory\nОтвечай по-русски только от лица $name. Реагируй на последнее сообщение и действия собеседника. Если тебя спросили, кто ты, представься. Не перечисляй внешность без повода, не называй собеседника игроком, не повторяй инструкции. Ответ: 1-3 естественные реплики, без анализа.<|im_end|>\n"+recent+"<|im_start|>assistant\n<think>\n\n</think>\n"
  thread {
   try{
    val response=LocalLlm().generate(file.absolutePath,prompt,96)
    if(response.startsWith("ERROR:"))error(response)
    val cleaned=response.replace(Regex("(?s)<think>.*?</think>"),"").substringAfterLast("</think>").replace("<|im_end|>","").trim()
    if(cleaned.isBlank() || cleaned.startsWith("<think>"))error("Qwen не выдала реплику персонажа. Попробуй ещё раз.")
    runOnUiThread{localPending.remove(name);localStarted.remove(name);val items=chats[name];if(items!=null){prefs.edit().putLong("chat_time_${name}_${items.size}",System.currentTimeMillis()).apply();items.add("assistant" to cleaned)};save();if(active==name)chat(name)}
   }catch(e:Throwable){runOnUiThread{localPending.remove(name);localStarted.remove(name);if(active==name)chat(name);alert("Ошибка локального ИИ: "+e.message)}}
  }
 }
 private fun request(name:String) {
  val base=prefs.getString("url","")!!.trimEnd('/')
  val model=prefs.getString("model","")!!
  val key=prefs.getString("key","")!!
  if(prefs.getBoolean("local_llm",false)){requestLocal(name);return}
  if(base.isBlank()||model.isBlank()){alert("Настрой ИИ-модель через ⚙");return}
  val snapshot=chats[name]?.toList()?:return
  thread {
   try {
    val arr=JSONArray()
    val persona=listOf("Характер: "+prefs.getString("persona_$name","").orEmpty(),"Внешность: "+prefs.getString("appearance_$name","").orEmpty(),"Сценарий: "+prefs.getString("scenario_$name","").orEmpty(),"Начальная ситуация: "+prefs.getString("opening_$name","").orEmpty(),"Правила: "+prefs.getString("protocol_$name","").orEmpty(),"Память: "+prefs.getString("memory_$name","").orEmpty()).joinToString("\n") + "\nИгрок: " + prefs.getString("identity_name","Игрок").orEmpty() + ". " + prefs.getString("identity_bio","").orEmpty()
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
    runOnUiThread { val items=chats[name];if(items!=null){prefs.edit().putLong("chat_time_${name}_${items.size}",System.currentTimeMillis()).apply();items.add("assistant" to reply)};save();if(active==name)chat(name) }
   }catch(e:Exception){runOnUiThread { alert("Ошибка модели: ${e.message}") }}
  }
 }
 private fun imagePrompt() {
  val name=active
  val scene=chats[name]?.takeLast(8)?.joinToString("\n"){"${it.first}: ${it.second}"}?:""
  val appearance=prefs.getString("appearance_$name","").orEmpty()
  val prompt="Anime illustration, character: $name. Appearance: $appearance. Scene from roleplay: $scene. Consistent character identity, coherent background, expressive composition, detailed clothing."
  val layout=LinearLayout(this).apply{orientation=1;setPadding(dp(14),dp(8),dp(14),dp(8))}
  layout.addView(text("Сцена из последних сообщений + внешность персонажа. Для img2img сначала выбери портрет в карточке.",14f,muted))
  val editor=visibleField("Промпт для генерации",prompt,layout)
  val strength=SeekBar(this).apply{max=90;progress=45}
  layout.addView(text("Сила изменений img2img: 0.45",14f,muted))
  layout.addView(strength)
  val reference=CheckBox(this).apply{text="Использовать портрет персонажа (img2img)";setTextColor(Color.WHITE);isChecked=prefs.getString("avatar_$name",null)!=null || File(File(filesDir,"verified_portraits"),java.lang.Integer.toHexString(name.hashCode())+".jpg").length()>1024}
  layout.addView(reference)
  AlertDialog.Builder(this).setTitle("✦ Изображение • $name").setView(ScrollView(this).apply{addView(layout)})
   .setPositiveButton("Генерировать"){_,_->
    generateScene(name,editor.text.toString(),reference.isChecked,strength.progress/100.0)
   }.setNeutralButton("Настроить сервер"){_,_->imageServerSettings()}
   .setNegativeButton("Отмена",null).show().also{styleDialog(it)}
 }
 private fun imageServerSettings() {
  val form=LinearLayout(this).apply{orientation=1;setPadding(dp(14),dp(8),dp(14),dp(8))}
  form.addView(text("Нужен запущенный Stable Diffusion WebUI / Forge с включённым API. Адрес укажи до /sdapi/v1, например http://192.168.1.10:7860",14f,muted))
  val url=visibleField("URL сервера изображений",prefs.getString("image_url","").orEmpty(),form)
  AlertDialog.Builder(this).setTitle("Сервер генерации").setView(form)
   .setPositiveButton("Сохранить"){_,_->prefs.edit().putString("image_url",url.text.toString().trim().trimEnd('/')).apply()}
   .setNegativeButton("Отмена",null).show().also{styleDialog(it)}
 }
 private fun generateScene(name:String,prompt:String,useReference:Boolean,denoise:Double) {
  val base=prefs.getString("image_url","").orEmpty().trimEnd('/')
  if(base.isBlank()){imageServerSettings();alert("Сначала настрой сервер изображений, затем повтори генерацию.");return}
  val uri=prefs.getString("avatar_$name",null)
  val cachedPortrait=File(File(filesDir,"verified_portraits"),java.lang.Integer.toHexString(name.hashCode())+".jpg")
  val withImage=useReference && (uri!=null || cachedPortrait.length()>1024)
  val progress=AlertDialog.Builder(this).setTitle("Создание сцены").setMessage("Генерация выполняется на подключённом сервере. Подожди…").setNegativeButton("Скрыть",null).create()
  progress.show();styleDialog(progress)
  thread {
   try{
    val request=JSONObject().put("prompt",prompt).put("negative_prompt","low quality, wrong character, distorted face, extra limbs")
     .put("steps",22).put("width",512).put("height",512).put("cfg_scale",6.5)
    if(withImage){
     val bytes=if(uri!=null)contentResolver.openInputStream(android.net.Uri.parse(uri))?.use{it.readBytes()} else if(cachedPortrait.length()>1024)cachedPortrait.readBytes() else null
     if(bytes==null)error("Не удалось открыть портрет")
     val encoded=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)
     request.put("init_images",JSONArray().put(encoded)).put("denoising_strength",denoise)
    }
    val endpoint=base+"/sdapi/v1/"+if(withImage)"img2img" else "txt2img"
    val conn=(URL(endpoint).openConnection() as HttpURLConnection).apply{
     requestMethod="POST";doOutput=true;connectTimeout=15000;readTimeout=180000
     setRequestProperty("Content-Type","application/json")
    }
    conn.outputStream.use{it.write(request.toString().toByteArray(Charsets.UTF_8))}
    val status=conn.responseCode
    val response=(if(status in 200..299)conn.inputStream else conn.errorStream).bufferedReader().use{it.readText()}
    conn.disconnect()
    if(status !in 200..299)error("HTTP $status: "+response.take(250))
    val imageBase64=JSONObject(response).getJSONArray("images").getString(0).substringAfter(",")
    val bytes=android.util.Base64.decode(imageBase64,android.util.Base64.DEFAULT)
    val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:error("Неподдерживаемый формат изображения")
    val folder=File(getExternalFilesDir(null),"generated").apply{mkdirs()}
    val saved=File(folder,"scene_"+System.currentTimeMillis()+".png")
    saved.writeBytes(bytes)
    runOnUiThread{
     progress.dismiss()
     val image=ImageView(this).apply{setImageBitmap(bitmap);adjustViewBounds=true}
     AlertDialog.Builder(this).setTitle("Сцена • $name").setView(image)
      .setMessage("Сохранено: "+saved.absolutePath)
      .setPositiveButton("Готово",null).show().also{styleDialog(it)}
    }
   }catch(e:Exception){runOnUiThread{progress.dismiss();alert("Ошибка генерации: "+e.message)}}
  }
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
  val holder=LinearLayout(this).apply {orientation=1;setPadding(dp(14),dp(8),dp(14),dp(8))}
  val field=visibleField("Описание персонажа",prefs.getString("persona_$name","").orEmpty(),holder)
  AlertDialog.Builder(this).setTitle("Описание • $name").setView(holder).setPositiveButton("Сохранить"){_,_->
   prefs.edit().putString("persona_$name",field.text.toString()).apply();characterDetail(name)
  }.setNegativeButton("Отмена",null).show().also {styleDialog(it)}
 }
 private fun editIdentity() {
  val l=LinearLayout(this).apply {orientation=1;setPadding(dp(16),dp(8),dp(16),dp(8))}
  val name=visibleField("Имя игрока",prefs.getString("identity_name","").orEmpty(),l)
  val bio=visibleField("Биография и RP-личность",prefs.getString("identity_bio","").orEmpty(),l)
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
  val candidates=characters.filter { !chats[it].isNullOrEmpty() }
  if(candidates.isEmpty()){alert("Сначала начни переписку хотя бы с одним персонажем в каталоге, затем здесь появится список участников.");return}
  val selected=linkedSetOf<String>()
  init("Участники комнаты")
  val b=body()
  b.addView(text("Выбери персонажей из своих переписок. Нажми на карточку, чтобы выделить участника.",16f,muted))
  val counter=text("Выбрано: 0",18f,Color.WHITE,true)
  b.addView(counter)
  candidates.forEach { name ->
   val row=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;background=shape(panel);setPadding(dp(8),dp(6),dp(8),dp(6))}
   row.addView(avatar(name,64),LinearLayout.LayoutParams(dp(64),dp(64)))
   val title=text(name,18f,Color.WHITE,true)
   row.addView(title,LinearLayout.LayoutParams(0,-2,1f))
   val check=CheckBox(this).apply {buttonTintList=android.content.res.ColorStateList.valueOf(blue)}
   row.addView(check)
   fun update(checked:Boolean) {
    if(checked)selected.add(name) else selected.remove(name)
    row.background=shape(if(checked)Color.rgb(36,66,120) else panel)
    counter.text="Выбрано: ${selected.size}"
   }
   check.setOnCheckedChangeListener {_,checked->update(checked)}
   row.setOnClickListener {check.isChecked=!check.isChecked}
   b.addView(row,LinearLayout.LayoutParams(-1,-2).apply {setMargins(dp(6),dp(5),dp(6),dp(5))})
  }
  root.addView(button("✓  Далее • Создать чат") {
   if(selected.isEmpty()){alert("Выбери хотя бы одного персонажа");return@button}
   init("Создать комнату")
   val form=body()
   form.addView(text("Участники: "+selected.joinToString(", "),16f,muted))
   val title=visibleField("Название комнаты","Новая история",form)
   val story=visibleField("Тема и отношения между персонажами","",form)
   val background=visibleField("Фон комнаты (ссылка на изображение, необязательно)","",form)
   val privateRoom=CheckBox(this).apply{text="Приватная комната";setTextColor(Color.WHITE);isChecked=true;buttonTintList=android.content.res.ColorStateList.valueOf(blue)}
   form.addView(privateRoom)
   root.addView(button("✦ Создать комнату"){
    val rooms=JSONArray(prefs.getString("rooms","[]"))
    val room=JSONObject().put("title",title.text.toString().trim().ifBlank{"Групповой чат"}+" #"+(rooms.length()+1))
     .put("members",selected.joinToString(", ")).put("story",story.text.toString())
     .put("background",background.text.toString().trim()).put("private",privateRoom.isChecked)
    rooms.put(room);prefs.edit().putString("rooms",rooms.toString()).apply();roomChat(room)
   }.apply{this.background=shape(Color.rgb(76,54,189),16f);textSize=19f})

  }.apply {setBackgroundColor(Color.rgb(38,81,162));textSize=19f})
 }
 private fun roomChat(room:JSONObject) {
  val title=room.optString("title")
  val members=room.optString("members").split(",").map{it.trim()}.filter{it.isNotBlank()}
  init(title);val b=body()
  val banner=LinearLayout(this).apply{orientation=1;background=shape(Color.rgb(24,18,57),20f);setPadding(dp(14),dp(16),dp(14),dp(16))}
  banner.addView(text("✦ "+title,23f,Color.WHITE,true))
  banner.addView(text("Участники: "+members.joinToString(", "),14f,muted))
  banner.addView(text("Сюжет: "+room.optString("story").ifBlank{"Свободная история"},14f,Color.rgb(195,181,255)))
  b.addView(banner)
  val key="room_chat_$title"
  val history=JSONArray(prefs.getString(key,"[]"))
  for(i in 0 until history.length()) {
   val o=history.optJSONObject(i)?:continue
   val fromUser=o.optString("role")=="Игрок"
   b.addView(text(o.optString("role")+"\n"+o.optString("text"),16f).apply{background=shape(if(fromUser)Color.rgb(67,49,179) else panel,16f)},LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(8),dp(5),dp(8),dp(5))})
  }
  val input=EditText(this).apply {hint="Твоё действие или реплика";setTextColor(Color.WHITE);setHintTextColor(muted);textSize=19f;background=shape(panel);setPadding(dp(14),dp(14),dp(14),dp(14))}
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
 private fun modelInstaller(parent:LinearLayout) {
  parent.addView(section("Локальные модели • на устройстве"))
  parent.addView(text("Модель скачивается в папку приложения. Нативный llama.cpp подключён к проекту; возможность запуска зависит от успешной сборки и совместимости устройства.",14f,muted))
  val modelName="qwen3-0.6b-q4_k_m.gguf"
  val modelUrl="https://huggingface.co/harzva/mobilecore-qwen3-0.6b-gguf/resolve/main/qwen3-0.6b-q4_k_m.gguf?download=true"
  val folder=File(getExternalFilesDir(null),"models/text").apply{mkdirs()}
  val destination=File(folder,modelName)
  val status=text(if(destination.length()>450000000L)"✓ Скачано • "+destination.length()/1048576+" МБ" else "Qwen3 0.6B Q4 • около 462 МиБ • русский текст, базовый RP",14f,muted)
  parent.addView(status)
  parent.addView(button(if(destination.length()>450000000L)"✓ Qwen3 0.6B • на устройстве" else "↓ Скачать Qwen3 0.6B (текст)") {
   if(destination.length()>450000000L){alert("Модель находится в "+destination.absolutePath+". Для RP включи переключатель локальной Qwen ниже.");return@button}
   AlertDialog.Builder(this).setTitle("Скачать текстовую модель?")
    .setMessage("Размер около 462 МиБ. Файл GGUF не требует распаковки. Загрузка через Android DownloadManager, желательно Wi-Fi. Модель сама по себе не начнёт отвечать без встроенного движка.")
    .setPositiveButton("Скачать"){_,_->
     try{
      val manager=getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
      val previous=prefs.getLong("download_qwen3",-1L)
      if(previous>=0L){
       val check=manager.query(android.app.DownloadManager.Query().setFilterById(previous))
       var running=false
       check.use{if(it.moveToFirst()){val st=it.getInt(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS));running=st==android.app.DownloadManager.STATUS_RUNNING||st==android.app.DownloadManager.STATUS_PENDING||st==android.app.DownloadManager.STATUS_PAUSED}}
       if(running){status.text="Загрузка уже запущена. Нажми «Проверить загрузку модели».";return@setPositiveButton}
       manager.remove(previous)
      }
      if(destination.exists()&&destination.length()<450000000L)destination.delete()
      val request=android.app.DownloadManager.Request(android.net.Uri.parse(modelUrl))
       .setTitle("Shadow RP • Qwen3 0.6B").setDescription("Локальная модель текста")
       .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
       .setDestinationUri(android.net.Uri.fromFile(destination))
       .setAllowedOverMetered(true)
      val id=manager.enqueue(request)
      prefs.edit().putLong("download_qwen3",id).apply()
      status.text="↓ Загрузка передана Android • проверь уведомления"
     }catch(e:Exception){alert("Не удалось начать загрузку: "+e.message)}
    }.setNegativeButton("Отмена",null).show().also{styleDialog(it)}
  }.apply{background=shape(Color.rgb(61,51,146),16f)})
  parent.addView(button("↻ Проверить загрузку модели") {
   val id=prefs.getLong("download_qwen3",-1L)
   if(id<0L){alert("Загрузка ещё не запускалась.");return@button}
   val manager=getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
   val cursor=manager.query(android.app.DownloadManager.Query().setFilterById(id))
   cursor.use {
    if(!it.moveToFirst()){alert("Задача загрузки не найдена.");return@button}
    val state=it.getInt(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
    val received=it.getLong(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
    val total=it.getLong(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
    val detail=when(state){
     android.app.DownloadManager.STATUS_SUCCESSFUL->"Загрузка завершена"
     android.app.DownloadManager.STATUS_FAILED->{val reason=it.getInt(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_REASON));"Загрузка прервана: "+when(reason){1004->"HTTP ошибка (сервер недоступен)";1005->"нет свободного места";1006->"нет соединения";1007->"устройство не может продолжить";1008->"файл уже существует";1009->"недостаточно памяти";1010->"невозможно возобновить загрузку";1011->"файл не найден";1012->"превышено число перенаправлений";1013->"системная ошибка";else->"код $reason"}}
     android.app.DownloadManager.STATUS_RUNNING->"Скачивается"
     android.app.DownloadManager.STATUS_PAUSED->"Приостановлено"
     else->"В очереди"
    }
    status.text="$detail • "+received/1048576+" / "+(if(total>0)total/1048576 else 0)+" МиБ"
   }
  })
  parent.addView(button("📁 Выбрать GGUF-файл с телефона"){
   val intent=android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply{
    addCategory(android.content.Intent.CATEGORY_OPENABLE)
    type="*/*"
    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
   }
   try{startActivityForResult(intent,ggufPicker)}catch(e:Exception){alert("Не удалось открыть файлы: "+e.message)}
  })
  parent.addView(text("Можно скачать GGUF в браузере или менеджере загрузок, затем выбрать файл здесь. Для llama.cpp он будет скопирован в папку Shadow RP; нужно свободное место под копию. Не закрывай приложение во время импорта.",13f,muted))
  val bigName="Qwen3-1.7B-Q4_K_M.gguf"
  val bigFile=File(folder,bigName)
  val bigMin=1000000000L
  val bigUrl="https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf?download=true"
  parent.addView(text("Qwen3 1.7B Q4_K_M • экспериментальный RP • около 1,11 ГБ",16f,Color.WHITE,true))
  parent.addView(text(if(bigFile.length()>bigMin)"✓ Модель скачана" else "На 4 ГБ физической ОЗУ может работать медленно или не запуститься.",13f,muted))
  parent.addView(button(if(bigFile.length()>bigMin)"✓ Выбрать Qwen3 1.7B" else "↓ Скачать Qwen3 1.7B"){
   if(bigFile.length()>bigMin){
    prefs.edit().putString("local_model",bigName).apply()
    alert("Выбрана Qwen3 1.7B. Перезапусти приложение, чтобы освободить память прежней модели.")
   }else{
    AlertDialog.Builder(this).setTitle("Скачать Qwen3 1.7B?")
     .setMessage("Около 1,11 ГБ. Модель требует больше оперативной памяти, чем 0.6B. На твоём Xiaomi 4+4 ГБ запуск не гарантирован.")
     .setPositiveButton("Скачать"){_,_->
      try{
       val manager=getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
       val previous=prefs.getLong("download_qwen17",-1L)
       if(previous>=0){
        val cursor=manager.query(android.app.DownloadManager.Query().setFilterById(previous))
        var running=false
        cursor.use{if(it.moveToFirst()){val state=it.getInt(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS));running=state==android.app.DownloadManager.STATUS_PENDING||state==android.app.DownloadManager.STATUS_RUNNING||state==android.app.DownloadManager.STATUS_PAUSED}}
        if(running){alert("Загрузка уже существует. Не запускай новую: проверь уведомления или нажми «Проверить загрузку Qwen3 1.7B».");return@setPositiveButton}
        manager.remove(previous)
       }
       if(bigFile.exists())bigFile.delete()
       val request=android.app.DownloadManager.Request(android.net.Uri.parse(bigUrl))
        .setTitle("Shadow RP • Qwen3 1.7B")
        .setDescription("Локальная модель RP")
        .setAllowedOverMetered(true)
        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationUri(android.net.Uri.fromFile(bigFile))
       prefs.edit().putLong("download_qwen17",manager.enqueue(request)).apply()
       alert("Загрузка запущена. Проверь уведомления.")
      }catch(e:Exception){alert("Ошибка загрузки: "+e.message)}
     }.setNegativeButton("Отмена",null).show().also{styleDialog(it)}
   }
  })
  parent.addView(button("↻ Проверить загрузку Qwen3 1.7B"){
   val id=prefs.getLong("download_qwen17",-1L)
   if(id<0){alert("Загрузка 1.7B ещё не запускалась.");return@button}
   val manager=getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
   val cursor=manager.query(android.app.DownloadManager.Query().setFilterById(id))
   cursor.use{
    if(!it.moveToFirst()){alert("Задача загрузки не найдена.");return@button}
    val state=it.getInt(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
    val bytes=it.getLong(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
    val total=it.getLong(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
    val reason=it.getInt(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_REASON))
    val label=when(state){
     android.app.DownloadManager.STATUS_SUCCESSFUL->"Загрузка завершена"
     android.app.DownloadManager.STATUS_FAILED->"Загрузка прервана. Код "+reason+". Проверь свободное место, интернет и ограничения батареи Xiaomi. При повторной попытке Android DownloadManager может начать файл заново."
     android.app.DownloadManager.STATUS_PAUSED->"Загрузка приостановлена. Код "+reason+". Не нажимай «Скачать» повторно: Android может продолжить текущую задачу автоматически."
     android.app.DownloadManager.STATUS_RUNNING->"Загрузка идёт в системном менеджере загрузок, даже если Shadow RP закрыт"
     else->"Ожидание загрузки"
    }
    alert(label+"\n"+bytes/1048576+" / "+(if(total>0)total/1048576 else 0)+" МиБ")
   }
  })
  parent.addView(button("↩ Выбрать Qwen3 0.6B • экономный"){
   if(destination.length()<450000000L){alert("Сначала скачай Qwen3 0.6B.");return@button}
   prefs.edit().putString("local_model",modelName).apply()
   alert("Выбрана Qwen3 0.6B. Перезапусти приложение для освобождения памяти.")
  })
  val local=CheckBox(this).apply {
   text="Использовать Qwen локально для RP"
   setTextColor(Color.WHITE)
   isChecked=prefs.getBoolean("local_llm",false)
   buttonTintList=android.content.res.ColorStateList.valueOf(violet)
   setOnCheckedChangeListener {_,checked->
    if(checked && !LocalLlm.available){isChecked=false;alert("Нативный движок недоступен в этой сборке.");return@setOnCheckedChangeListener}
    if(checked && File(folder,prefs.getString("local_model",modelName).orEmpty()).length()<(if(prefs.getString("local_model",modelName)==bigName)bigMin else if(prefs.getString("local_model",modelName).orEmpty().startsWith("imported-"))10000000L else 450000000L)){isChecked=false;alert("Сначала скачай выбранную модель GGUF.");return@setOnCheckedChangeListener}
    prefs.edit().putBoolean("local_llm",checked).apply()
   }
  }
  parent.addView(local)
  parent.addView(text("Локальная генерация выполняется на CPU и может быть медленной. Для Qwen3 рекомендуется короткая история чата.",13f,muted))
  parent.addView(text("Генерация изображений • img2img",19f,Color.WHITE,true))
  parent.addView(text("Для image-to-image нужна отдельная модель диффузии и Android-движок. Пока не установлены: кнопка загрузки будет добавлена после проверки совместимости, иначе скачанный файл не сможет генерировать изображения.",14f,muted))
 }
 private fun settings() {
  init("Настройки ИИ");val b=body()
  b.addView(section("Подключить ИИ"))
  b.addView(text("API URL  ·  ID модели  ·  API ключ",16f,Color.rgb(174,161,255),true))
  b.addView(button("✦  Настроить подключение"){editModel(0)}.apply{background=shape(Color.rgb(66,49,162),17f);textSize=19f})
  b.addView(text("Выбери тип подключения. Для облачной модели нужен API, для локальной — запущенный совместимый сервер.",14f,muted))
  val providers=listOf("OpenAI-совместимый API","OpenRouter","Локальный сервер (LM Studio / Ollama)")
  providers.forEachIndexed { index,label ->
   item(b,label,when(index){0->"Свой URL, ID модели и ключ";1->"Выбор моделей через OpenRouter API";else->"Адрес сервера в локальной сети"}) {
    editModel(index)
   }
  }
  item(b,"🎨 Генератор изображений","Stable Diffusion Forge / WebUI: txt2img и img2img"){imageServerSettings()}
  modelInstaller(b)
  val model=prefs.getString("model","").orEmpty()
  item(b,"Текущая модель",if(model.isBlank())"Не настроена" else model){editModel(0)}
  nav(4)
 }
 private fun visibleField(label:String,value:String,parent:LinearLayout):EditText {
  parent.addView(text(label,16f,Color.WHITE,true))
  val outline=GradientDrawable().apply {
   setColor(Color.rgb(34,46,69));cornerRadius=dp(12).toFloat()
   setStroke(dp(1),Color.rgb(83,126,205))
  }
  val short=label=="Имя персонажа" || label=="Название комнаты" || label=="ID модели" ||
   label=="API URL (включая /v1)" || label=="API ключ" || label=="Имя игрока"
  return EditText(this).apply {
   setText(value);hint=label;setHintTextColor(Color.rgb(151,173,207))
   setTextColor(Color.WHITE);textSize=17f
   setSingleLine(short)
   if(!short) {
    inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    minLines=4;maxLines=12;gravity=Gravity.TOP or Gravity.START
   }
   background=outline
   setPadding(dp(14),dp(14),dp(14),dp(14))
   parent.addView(this,LinearLayout.LayoutParams(-1,if(short)dp(58) else -2).apply {
    leftMargin=dp(5);rightMargin=dp(5);bottomMargin=dp(14)
   })
  }
 }
 private fun editModel(provider:Int) {
  val l=LinearLayout(this).apply {orientation=1;setPadding(dp(12),dp(8),dp(12),dp(8))}
  fun field(label:String,value:String):EditText=visibleField(label,value,l)
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
