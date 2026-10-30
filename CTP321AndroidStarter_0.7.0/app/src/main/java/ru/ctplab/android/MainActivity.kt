package ru.ctplab.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.security.MessageDigest
import kotlin.math.min

class MainActivity : Activity() {
    private val bg = 0xFF101214.toInt()
    private val panel = 0xFF1A1D20.toInt()
    private val panel2 = 0xFF24292E.toInt()
    private val accent = 0xFF38D6A5.toInt()
    private val fg = 0xFFF1F4F3.toInt()
    private val muted = 0xFF9AA6A2.toInt()
    private var tab = "Обзор"
    private var firmwareName = "Файл не открыт"
    private var firmwareBytes: ByteArray? = null
    private var compareName = ""
    private var compareBytes: ByteArray? = null
    private var descriptorName = ""
    private var descriptorBytes: ByteArray? = null
    private var selectedCalibration: Pair<Int, String>? = null
    private var calibrationSearch = ""
    private var editorMapAddress: Int? = null
    private var editorMapName = ""
    private var firmwareDirty = false
    private val undoStack = java.util.ArrayDeque<ByteArray>()
    private val redoStack = java.util.ArrayDeque<ByteArray>()
    private lateinit var body: LinearLayout
    private lateinit var status: TextView

    companion object {
        private const val OPEN_FIRMWARE = 101
        private const val OPEN_COMPARE = 102
        private const val SAVE_COPY = 103
        private const val OPEN_DESCRIPTOR = 104
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildShell()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float = 14f, color: Int = fg, bold: Boolean = false) =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

    private fun action(value: String, click: () -> Unit) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(fg)
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(11), dp(10), dp(11))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(panel2)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), 0xFF3A414B.toInt())
        }
        setOnClickListener { click() }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(panel)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), 0xFF303638.toInt())
        }
    }

    private fun gap(parent: LinearLayout, h: Int = 10) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(h)))
    }

    private fun add(parent: LinearLayout, child: View) {
        parent.addView(child, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val branding = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        add(branding, text("CTP321", 27f, fg, true))
        add(branding, text("ENGINEERING TOOL  /  J7ES", 10f, accent, true))
        header.addView(branding)
        header.addView(action(if (firmwareBytes == null) "＋  BIN" else "▣  BIN открыт") { openFile(OPEN_FIRMWARE) })
        root.addView(header)
        gap(root, 10)
        status = text(if (firmwareBytes == null) "Январь 7.2 / J7ES  •  BIN не выбран" else "Январь 7.2 / J7ES  •  ${firmwareBytes!!.size} байт загружено", 12f, muted)
        add(root, status)
        gap(root, 8)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(18))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(2))
            setBackgroundColor(0xFF0C171D.toInt())
        }
        listOf("Обзор" to "⌂", "Калибровки" to "▦", "Редактор" to "▤", "HEX" to "⌗", "Инструменты" to "⚙").forEach { (name, glyph) ->
            val selected = tab == name
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(5), dp(3), dp(5))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (selected) 0xFF17352F.toInt() else android.graphics.Color.TRANSPARENT)
                    cornerRadius = dp(10).toFloat()
                }
                setOnClickListener { tab = name; buildShell() }
            }
            add(item, text(glyph, 18f, if (selected) accent else muted, true).apply { gravity = Gravity.CENTER })
            add(item, text(name, 9f, if (selected) accent else muted, selected).apply { gravity = Gravity.CENTER })
            bottomNav.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(bottomNav)
        root.addView(text("CTP Mobile Lab 0.8.6 • локальная работа с файлами", 9f, muted).apply { gravity = Gravity.CENTER })
        setContentView(root)
        render()
    }

    private fun render() {
        body.removeAllViews()
        when (tab) {
            "Обзор" -> overview()
            "Калибровки" -> calibrations()
            "Редактор" -> calibrationEditor()
            "HEX" -> hexView()
            else -> toolsView()
        }
    }


    private fun calibrationEditor() {
        val bytes = firmwareBytes
        add(body, text("Редактор калибровок", 22f, fg, true))
        add(body, text("Табличный интерфейс CTP Mobile Lab. Редактирование включается только для областей, чья сигнатура совпала с BIN.", 13f, muted))
        gap(body)
        if (bytes == null) {
            val c = card()
            add(c, text("Сначала открой BIN прошивки", 17f, fg, true))
            add(c, action("Открыть BIN") { openFile(OPEN_FIRMWARE) })
            add(body, c)
            return
        }
        val maps = knownCalibrationCatalog()
        val listCard = card()
        add(listCard, text("КАРТЫ С ПРОВЕРЯЕМОЙ СИГНАТУРОЙ", 11f, accent, true))
        maps.forEach { map ->
            val valid = map.address + map.bytesExpected <= bytes.size &&
                map.signature.indices.all { (bytes[map.address + it].toInt() and 255) == map.signature[it] }
            gap(listCard, 5)
            add(listCard, action("${if (valid) "●" else "○"}  ${map.name}  •  0x%04X  •  %s".format(map.address, if (valid) "проверена" else "не совпала")) {
                if (valid) {
                    editorMapAddress = map.address
                    editorMapName = map.name
                    render()
                } else {
                    Toast.makeText(this, "Сигнатура не совпала — редактирование заблокировано.", Toast.LENGTH_LONG).show()
                }
            })
        }
        add(body, listCard)
        val address = editorMapAddress ?: return
        val map = maps.firstOrNull { it.address == address } ?: return
        val valid = address + map.bytesExpected <= bytes.size &&
            map.signature.indices.all { (bytes[address + it].toInt() and 255) == map.signature[it] }
        if (!valid) {
            editorMapAddress = null
            return
        }
        gap(body)
        val detail = card()
        add(detail, text(editorMapName, 18f, fg, true))
        add(detail, text("BIN 0x%04X • ${map.bytesExpected} байт • UINT8 • исходные значения".format(address), 12f, muted))
        add(detail, text("Физическая шкала и значения оси ещё не подтверждены; числа показаны как сырые байты.", 12f, muted))
        gap(detail, 8)
        val editActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        editActions.addView(action("↶ Отменить") { undoLastEdit() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        editActions.addView(action("↷ Повторить") { redoLastEdit() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        gap(detail, 4)
        add(detail, editActions)
        gap(detail, 6)
        val dataActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dataActions.addView(action("Копировать карту") { copyRawMap(address, map.bytesExpected) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dataActions.addView(action("Вставить значения") { pasteRawMap(address, map.bytesExpected) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(detail, dataActions)
        gap(detail, 8)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (row in 0 until 3) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until 13) {
                val i = row * 13 + col
                val idx = address + i
                val value = bytes[idx].toInt() and 255
                val cell = TextView(this).apply {
                    text = "%02X\n%d".format(value, value)
                    gravity = Gravity.CENTER
                    textSize = 11f
                    setTextColor(fg)
                    setPadding(dp(3), dp(8), dp(3), dp(8))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(android.graphics.Color.rgb(25 + value * 180 / 255, 70 + value * 100 / 255, 100 - value * 70 / 255))
                        setStroke(dp(1), bg)
                        cornerRadius = dp(4).toFloat()
                    }
                    setOnClickListener { editRawCell(editorMapName, idx, 1) { render() } }
                }
                line.addView(cell, LinearLayout.LayoutParams(0, dp(54), 1f))
            }
            grid.addView(line)
            gap(grid, 3)
        }
        add(detail, grid)
        gap(detail, 10)
        add(detail, text("ГРАФИК СЫРЫХ ЗНАЧЕНИЙ", 11f, accent, true))
        add(detail, text("Ось X — индекс байта в карте; ось Y — значение 0–255. Это не физические единицы двигателя.", 11f, muted))
        val plot = object : View(this) {
            private val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = accent; strokeWidth = dp(2).toFloat(); style = android.graphics.Paint.Style.STROKE
            }
            private val gridPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF394150.toInt(); strokeWidth = dp(1).toFloat()
            }
            private val pointPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = fg; style = android.graphics.Paint.Style.FILL
            }
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val left = dp(8).toFloat(); val right = width - dp(8).toFloat()
                val top = dp(8).toFloat(); val bottom = height - dp(8).toFloat()
                for (n in 0..4) {
                    val y = top + (bottom - top) * n / 4f
                    canvas.drawLine(left, y, right, y, gridPaint)
                }
                val path = android.graphics.Path()
                for (i in 0 until map.bytesExpected) {
                    val v = bytes[address + i].toInt() and 255
                    val x = left + (right-left) * i / (map.bytesExpected-1).toFloat()
                    val y = bottom - (bottom-top) * v / 255f
                    if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                canvas.drawPath(path, linePaint)
            }
        }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(150)) }
        add(detail, plot)
        gap(detail, 8)
        add(detail, action("Сохранить копию BIN") { saveCopy() })
        gap(detail, 5)
        add(detail, action("Сбросить выбор карты") { editorMapAddress = null; render() })
        add(body, detail)
    }

    private fun overview() {
        val hero = card()
        hero.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF0D2C48.toInt(), 0xFF071827.toInt())
        ).apply { cornerRadius = dp(18).toFloat(); setStroke(dp(1), 0xFF17496B.toInt()) }
        add(hero, text("CTP321", 28f, fg, true))
        add(hero, text("МОБИЛЬНАЯ ЛАБОРАТОРИЯ", 11f, accent, true))
        add(hero, text("Настройка ЭБУ Январь 7.2 / J7ES", 15f, fg, true))
        gap(hero, 8)
        add(hero, text("Январь 7.2  /  J7ES", 12f, muted))
        add(body, hero)
        gap(body)
        // Live canvas chart on dashboard; values are sampled from BIN when loaded.
        val chartCard = card()
        add(chartCard, text("ГРАФИК ПРОШИВКИ", 11f, accent, true))
        add(chartCard, text(if (firmwareBytes == null) "Демонстрационный профиль до открытия BIN" else "Среднее значение байтов по участкам загруженного BIN", 12f, muted))
        gap(chartCard, 6)
        val chart = object : View(this) {
            private val gridPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF34414A.toInt(); strokeWidth = dp(1).toFloat() }
            private val curvePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = accent; strokeWidth = dp(2).toFloat(); style = android.graphics.Paint.Style.STROKE; strokeJoin = android.graphics.Paint.Join.ROUND }
            private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x4438D6A5; style = android.graphics.Paint.Style.FILL }
            private val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = dp(10).toFloat() }
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val l = dp(30).toFloat(); val r = width - dp(8).toFloat(); val t = dp(10).toFloat(); val b = height - dp(22).toFloat()
                for (n in 0..4) { val y = t + (b-t)*n/4f; canvas.drawLine(l,y,r,y,gridPaint); canvas.drawText((255-n*64).coerceAtLeast(0).toString(), 0f, y+dp(3), labelPaint) }
                val data = firmwareBytes
                val count = 72
                val path = android.graphics.Path(); val area = android.graphics.Path()
                for (i in 0 until count) {
                    val v = if (data == null || data.isEmpty()) (128 + 54*kotlin.math.sin(i*0.22) + 22*kotlin.math.sin(i*0.57)).toInt().coerceIn(0,255) else {
                        val start = i * data.size / count; val end = ((i+1)*data.size/count).coerceAtLeast(start+1).coerceAtMost(data.size)
                        var sum = 0L
                        for (j in start until end) sum += data[j].toInt() and 255
                        (sum / (end-start)).toInt()
                    }
                    val x = l + (r-l)*i/(count-1).toFloat(); val y = b - (b-t)*v/255f
                    if (i == 0) { path.moveTo(x,y); area.moveTo(x,b); area.lineTo(x,y) } else { path.lineTo(x,y); area.lineTo(x,y) }
                }
                area.lineTo(r,b); area.close(); canvas.drawPath(area,fillPaint); canvas.drawPath(path,curvePaint)
                canvas.drawText("0", l, height-dp(5).toFloat(), labelPaint)
                canvas.drawText(if(data==null) "DEMO" else "BIN", r-dp(28), height-dp(5).toFloat(), labelPaint)
            }
        }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(165)) }
        add(chartCard, chart)
        add(body, chartCard)
        gap(body)
        val f = card()
        add(f, text("ФАЙЛ ПРОШИВКИ  /  BIN", 11f, accent, true))
        gap(f, 6)
        add(f, text(if (firmwareBytes == null) "Прошивка не открыта" else firmwareName, 16f, fg, true))
        val bytes = firmwareBytes
        add(f, text(if (bytes == null) "Выбери BIN-файл для начала работы" else "Размер: ${bytes.size} байт  •  ${"%.2f".format(bytes.size / 1024.0)} KiB", 12f, muted))
        if (bytes != null) {
            gap(f, 5)
            add(f, text("SHA-256: ${sha256(bytes)}", 9.5f, muted))
        }
        gap(f, 10)
        add(f, action("▣   Открыть BIN") { openFile(OPEN_FIRMWARE) })
        gap(f, 7)
        add(f, action("↧   Сохранить копию") { saveCopy() })
        add(body, f)
        gap(body)
        val q = card()
        add(q, text("РАБОЧИЕ РАЗДЕЛЫ", 11f, accent, true))
        gap(q, 8)
        add(q, action("▦   Калибровки                                      ›") { tab = "Калибровки"; render() })
        gap(q, 7)
        add(q, action("⌁   Редактор таблиц                               ›") { tab = "Редактор"; render() })
        gap(q, 7)
        add(q, action("⌗   HEX-просмотр                                  ›") { tab = "HEX"; render() })
        gap(q, 7)
        add(q, action("⇄   Сравнить прошивки                            ›") { openFile(OPEN_COMPARE) })
        gap(body)
        val note = card()
        add(note, text("СТАТУС ПРОЕКТА", 11f, accent, true))
        gap(note, 5)
        add(note, text(if (bytes == null) "Готов к открытию BIN и описания карт J7." else "BIN загружен • ${bytes.size} байт • изменения сохраняются только в копию.", 12f, muted))
        add(body, note)
    }

    private fun calibrations() {
        val selected = selectedCalibration
        if (selected != null) {
            calibrationDetail(selected)
            return
        }
        add(body, text("Калибровки", 22f, fg, true))
        add(body, text("Справочные адреса J7ES и диагностический просмотр кандидатов. Адреса ASM не считаются файловыми смещениями, пока не подтверждена карта памяти конкретной прошивки.", 13f, muted))
        gap(body, 6)
        val firmwareForMaps = firmwareBytes
        val quickMaps = card()
        add(quickMaps, text("БЫСТРЫЙ ПРОСМОТР КАРТ-КАНДИДАТОВ", 11f, accent, true))
        add(quickMaps, text(if (firmwareForMaps == null) "Открой BIN, чтобы посмотреть байты по справочным адресам." else "Загружен $firmwareName • ${firmwareForMaps.size} байт. Просмотр ниже диагностический, не готовые значения калибровок.", 12f, muted))
        gap(quickMaps, 4)
        add(quickMaps, text("ПРЯМОЕ СОПОСТАВЛЕНИЕ С J7ES ASM", 11f, accent, true))
        add(quickMaps, text("Открываются участки по подтверждённым адресам ASM. Для одномерных карт показаны сырые байты; для 16×16 — форма предварительная, пока не декодированы оси. Значения не пересчитываются в AFR/градусы без формулы.", 12f, muted))
        gap(quickMaps, 4)
        val knownMaps = listOf(
            arrayOf("0x6953", "Смесь: обороты × дроссель", "16", "16"),
            arrayOf("0x6A53", "Базовый состав смеси", "39", "1"),
            arrayOf("0x6A7A", "Коррекция базового состава", "39", "1"),
            arrayOf("0x6AC8", "УОЗ: обороты × давление", "16", "16"),
            arrayOf("0x7CF7", "Поправка циклового наполнения", "16", "16"),
            arrayOf("0x8193", "Весовая коррекция топливоподачи (FF)", "16", "16"),
            arrayOf("0x83BA", "Смесь: обороты × давление", "16", "16"),
            arrayOf("0x8F4A", "Коэффициент топливной плёнки", "39", "1")
        )
        knownMaps.forEach { m ->
            val addr = m[0].removePrefix("0x").toInt(16)
            val rows = m[2].toInt()
            val cols = m[3].toInt()
            add(quickMaps, action("${m[1]} • ${m[0]}") {
                openKnownMap(addr, m[1], rows, cols)
            })
            gap(quickMaps, 4)
        }
        add(body, quickMaps)
        gap(body)
        gap(body)
        val fileCard = card()
        add(fileCard, text("ФАЙЛЫ КАЛИБРОВОК", 11f, accent, true))
        gap(fileCard, 6)
        add(fileCard, text(if (descriptorBytes == null) "Описание карт не загружено" else "$descriptorName • ${descriptorBytes!!.size} байт", 13f, fg, true))
        add(fileCard, action("Открыть описание карт .j7") { openFile(OPEN_DESCRIPTOR) })
        gap(body)
        val bytes = descriptorBytes
        if (bytes == null) {
            val c = card()
            add(c, text("Сначала загрузи файл описания .j7, в котором находятся названия калибровок.", 13f, muted))
            add(c, action("Выбрать .j7") { openFile(OPEN_DESCRIPTOR) })
            add(body, c)
            return
        }

        val markers = decodeJ7Markers()
        val markerCard = card()
        add(markerCard, text("МЕТКИ ДЕСКРИПТОРА", 11f, accent, true))
        val markerTypes = markers.mapNotNull { it.typeCodeCandidate }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        add(markerCard, text("Найдено меток: ${markers.size}. Формат токена: !ID-XXXX[-YY]. XXXX — поле-кандидат адреса, YY — неизвестный код записи (не размер карты). Ни одно поле пока не считается доказанным смещением BIN.", 12f, muted))
        if (markerTypes.isNotEmpty()) {
            add(markerCard, text("Коды YY в этом дескрипторе: " + markerTypes.joinToString { "%02X × %d".format(it.key, it.value) }, 11f, muted))
            gap(markerCard, 4)
        }
        gap(markerCard, 6)
        if (markers.isEmpty()) {
            add(markerCard, text("Метки адресов не найдены.", 11f, muted))
        } else {
            markers.take(80).forEach { marker ->
                add(markerCard, action("${marker.label}   →   0x%04X".format(marker.address)) { showBinCandidate(marker) })
                gap(markerCard, 4)
            }
        }
        add(body, markerCard)
        gap(body)

        val referenceCard = card()
        add(referenceCard, text("СПРАВОЧНЫЕ КАРТЫ J7ES", 11f, accent, true))
        add(referenceCard, text("Адреса взяты из открытого проекта cmgt/j7es (j7es_data_area.asm). Это справочник для сравнения, а не подтверждённые адреса твоего BIN: версии прошивки могут отличаться.", 12f, muted))
        gap(referenceCard, 6)
        val references = listOf(
            Triple(0x6953, "Состав смеси от оборотов и дросселя", "AFR • обороты × положение дросселя"),
            Triple(0x6A53, "Базовый состав смеси", "AFR/целевой состав смеси • температура двигателя"),
            Triple(0x6A7A, "Коррекция базового состава смеси", "Коэффициент коррекции • температура двигателя"),
            Triple(0x6AC8, "УОЗ от давления", "Угол зажигания • обороты × абсолютное давление"),
            Triple(0x7CF7, "Поправка циклового наполнения", "Коэффициент • обороты × положение дросселя"),
            Triple(0x8193, "Весовая коррекция топливоподачи", "Коэффициент • обороты × положение дросселя"),
            Triple(0x83BA, "Состав смеси от оборотов и давления", "AFR • обороты × абсолютное давление"),
            Triple(0x8F4A, "Коэффициент топливной плёнки по температуре", "Коэффициент коррекции • температура двигателя")
        )
        references.forEach { (address, name, detail) ->
            add(referenceCard, action("$name  •  0x%04X".format(address)) {
                showReferenceMapCandidate(address, name, detail)
            })
            add(referenceCard, action("Открыть таблицу-кандидат: $name") {
                rawTableSetup(name)
            })
            gap(referenceCard, 4)
        }
        add(referenceCard, action("Проверить карты по сигнатурам ASM") { showCalibrationDecoderReport() })
        add(body, referenceCard)
        gap(body)

        val search = EditText(this).apply {
            setSingleLine(true)
            hint = "Поиск по названиям калибровок"
            setTextColor(fg)
            setHintTextColor(muted)
            textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackground(android.graphics.drawable.GradientDrawable().apply {
                setColor(panel)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), 0xFF3A414B.toInt())
            })
            setText(calibrationSearch)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    calibrationSearch = s?.toString() ?: ""
                    renderCalibrationList()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        add(body, search)
        gap(body, 8)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.tag = "calibration_list"
        add(body, list)
        renderCalibrationList()
        gap(body)
        val note = card()
        add(note, text("СЛЕДУЮЩИЙ ЭТАП", 11f, accent, true))
        add(note, text("Названия уже берутся из .j7. Для таблиц с реальными значениями, осями и единицами измерения нужно подключить декодер формата карт; пока значения не подменяются демонстрационными числами.", 12f, muted))
        add(body, note)
    }

    private fun renderCalibrationList() {
        val list = body.findViewWithTag<LinearLayout>("calibration_list") ?: return
        list.removeAllViews()
        val entries = descriptorStrings()
            .distinctBy { it.second.lowercase() }
            .filter { it.second.contains(calibrationSearch, ignoreCase = true) }
        add(list, text("Найдено фрагментов: ${entries.size}", 11f, muted))
        gap(list, 5)
        if (entries.isEmpty()) {
            add(list, card().apply { add(this, text("Совпадений нет. Попробуй изменить запрос.", 13f, muted)) })
            return
        }
        entries.take(300).forEach { entry ->
            val item = card()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val title = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            add(title, text(entry.second, 14f, fg, true))
            add(title, text("Смещение в .j7: 0x%X".format(entry.first), 10f, muted))
            row.addView(title)
            row.addView(text("›", 25f, accent, true))
            item.addView(row)
            item.setOnClickListener { selectedCalibration = entry; render() }
            gap(list, 6)
            add(list, item)
        }
        if (entries.size > 300) add(list, text("Показаны первые 300 результатов.", 11f, muted))
    }

    private fun calibrationDetail(entry: Pair<Int, String>) {
        add(body, action("← Назад к списку") { selectedCalibration = null; render() })
        gap(body)
        add(body, text(entry.second, 21f, fg, true))
        add(body, text("Описание .j7 • смещение 0x%X".format(entry.first), 12f, muted))
        gap(body)
        val summary = card()
        add(summary, text("КАРТА КАЛИБРОВКИ", 11f, accent, true))
        gap(summary, 6)
        add(summary, text("Название успешно прочитано из файла описания.", 14f, fg, true))
        add(summary, text("Адрес данных BIN, тип элементов, размеры таблицы, оси, масштаб и единицы измерения пока не определены декодером. Поэтому здесь не показываются выдуманные числа и не разрешается редактирование.", 13f, muted))
        gap(summary, 10)
        add(summary, action("Показать байты записи в .j7") {
            showCalibrationBytes(entry.first)
        })
        add(summary, action("Найти адресные метки рядом с записью") {
            showMarkersNearDescriptorOffset(entry.first)
        })
        add(body, summary)
        gap(body)
        val table = card()
        add(table, text("ДЕКОДИРОВАНИЕ КАРТЫ", 11f, accent, true))
        gap(table, 6)
        add(table, text("Формат конкретной карты ещё не подтверждён", 16f, fg, true))
        add(table, text("Добавлен диагностический редактор сырых байтов: можно задать смещение BIN, размеры таблицы, просматривать ячейки и редактировать их. Это НЕ автоматическое декодирование .j7: масштаб, оси, знаковость и соответствие ASM-адреса BIN нужно подтвердить отдельно. Сохраняй только копию.", 13f, muted))
        add(body, table)
    }

    private fun rawTableSetup(title: String) {
        val bytes = firmwareBytes
        if (bytes == null) {
            AlertDialog.Builder(this).setTitle("Сначала открой BIN")
                .setMessage("Открой файл прошивки, затем запусти редактор таблицы-кандидата.")
                .setPositiveButton("Открыть BIN") { _, _ -> openFile(OPEN_FIRMWARE) }
                .setNegativeButton("Отмена", null).show()
            return
        }
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(6), dp(20), dp(4)) }
        fun field(hintText: String, initial: String): EditText = EditText(this).apply {
            hint = hintText; setSingleLine(true); setText(initial); setTextColor(fg); textSize = 16f
        }
        val offset = field("BIN offset HEX, например 6953", "6953")
        val rows = field("Строки", "16")
        val cols = field("Столбцы", "16")
        val width = field("Байт на ячейку: 1 или 2", "1")
        listOf("Смещение BIN (HEX)", "Число строк (1–64)", "Число столбцов (1–64)", "Байт на ячейку (1 или 2)").forEachIndexed { i, label ->
            add(form, text(label, 12f, muted))
            add(form, listOf(offset, rows, cols, width)[i])
        }
        AlertDialog.Builder(this).setTitle("Сырая таблица: $title")
            .setMessage("Начальное смещение по умолчанию — только кандидат, не гарантированный адрес карты.")
            .setView(form).setNegativeButton("Отмена", null)
            .setPositiveButton("Показать", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        try {
                            val off = offset.text.toString().trim().removePrefix("0x").removePrefix("0X").toInt(16)
                            val rr = rows.text.toString().toInt()
                            val cc = cols.text.toString().toInt()
                            val ww = width.text.toString().toInt()
                            require(off >= 0 && rr in 1..64 && cc in 1..64 && ww in 1..2 && off.toLong() + rr.toLong()*cc*ww <= bytes.size)
                            dialog.dismiss()
                            showRawTable(title, off, rr, cc, ww)
                        } catch (_: Exception) {
                            Toast.makeText(this, "Проверь смещение, размеры и тип ячейки", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showRawTable(title: String, offset: Int, rows: Int, cols: Int, width: Int) {
        val bytes = firmwareBytes ?: return
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(4)) }
        add(outer, text("BIN offset 0x%X • %d×%d • %d байт/ячейку".format(offset, rows, cols, width), 11f, muted))
        add(outer, text("Значения без масштаба/единиц. Нажми ячейку для редактирования.", 11f, muted))
        val gridScroll = HorizontalScrollView(this)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gridScroll.addView(grid)
        outer.addView(gridScroll, LinearLayout.LayoutParams(-1, dp(320)))
        fun readCell(index: Int): Int = if (width == 1) bytes[index].toInt() and 255 else ((bytes[index].toInt() and 255) shl 8) or (bytes[index+1].toInt() and 255)
        val graphPreview = TextView(this).apply { setTextColor(fg); textSize = 11f; typeface = Typeface.MONOSPACE }
        fun refreshGraph() {
            val values = (0 until rows*cols).map { readCell(offset + it*width) }
            val maxValue = (values.maxOrNull() ?: 1).coerceAtLeast(1)
            graphPreview.text = values.take(64).mapIndexed { i, v ->
                "%02d %5d %s".format(i, v, "▮".repeat((v.toDouble()/maxValue*18).toInt().coerceIn(0,18)))
            }.joinToString("\\n")
        }
        fun draw() {
            grid.removeAllViews()
            for (r in 0 until rows) {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (c in 0 until cols) {
                    val idx = offset + (r * cols + c) * width
                    val value = readCell(idx)
                    val cell = TextView(this).apply {
                        text = if (width == 1) "%02X".format(value) else "%04X".format(value)
                        gravity = Gravity.CENTER
                        textSize = 11f
                        typeface = Typeface.MONOSPACE
                        setTextColor(fg)
                        setPadding(dp(5), dp(9), dp(5), dp(9))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.rgb(35 + value % 45, 55 + value % 65, 55 + value % 45))
                            setStroke(dp(1), 0xFF17191D.toInt())
                        }
                        setOnClickListener { editRawCell(title, idx, width) { draw(); refreshGraph() } }
                    }
                    line.addView(cell, LinearLayout.LayoutParams(dp(if (width == 1) 39 else 49), dp(38)))
                }
                grid.addView(line)
            }
        }
        draw()
        refreshGraph()
        add(outer, graphPreview)
        AlertDialog.Builder(this).setTitle("Таблица-кандидат: $title").setView(outer)
            .setPositiveButton("Готово", null)
            .setNeutralButton("Сохранить копию BIN", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { dialog.dismiss(); saveCopy() }
                }
                dialog.show()
            }
    }

    private fun copyRawMap(address: Int, length: Int) {
        val bytes = firmwareBytes ?: return
        if (address < 0 || length <= 0 || address + length > bytes.size) {
            Toast.makeText(this, "Карта выходит за границы BIN", Toast.LENGTH_LONG).show()
            return
        }
        val values = (address until address + length).joinToString(" ") { (bytes[it].toInt() and 255).toString() }
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CTP raw calibration", values))
        Toast.makeText(this, "Скопировано $length сырых значений", Toast.LENGTH_SHORT).show()
    }

    private fun pasteRawMap(address: Int, length: Int) {
        val bytes = firmwareBytes ?: return
        if (address < 0 || length <= 0 || address + length > bytes.size) {
            Toast.makeText(this, "Карта выходит за границы BIN", Toast.LENGTH_LONG).show()
            return
        }
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clipText = if (clipboard.hasPrimaryClip()) clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty() else ""
        val input = EditText(this).apply {
            setText(clipText)
            setTextColor(fg)
            hint = "Числа 0–255 через пробел или запятую"
            minLines = 3
            gravity = Gravity.TOP
        }
        AlertDialog.Builder(this).setTitle("Вставить сырые значения")
            .setMessage("Нужно ровно $length целых чисел от 0 до 255. Это сырые байты, не физические единицы.")
            .setView(input).setNegativeButton("Отмена", null)
            .setPositiveButton("Применить", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val raw = input.text.toString().trim()
                        val tokens = raw.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
                        val values = tokens.map { it.toIntOrNull() }
                        if (values.size != length || values.any { it == null || it !in 0..255 }) {
                            Toast.makeText(this, "Нужно ровно $length чисел в диапазоне 0–255", Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        undoStack.addLast(bytes.copyOf())
                        while (undoStack.size > 20) undoStack.removeFirst()
                        redoStack.clear()
                        values.forEachIndexed { i, value -> bytes[address + i] = value!!.toByte() }
                        firmwareDirty = true
                        status.text = "$firmwareName • есть изменения"
                        dialog.dismiss()
                        Toast.makeText(this, "Заменено $length байт; можно отменить", Toast.LENGTH_SHORT).show()
                        render()
                    }
                }
                dialog.show()
            }
    }

    private fun undoLastEdit() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "Нет изменений для отмены", Toast.LENGTH_SHORT).show()
            return
        }
        redoStack.addLast(firmwareBytes?.copyOf() ?: return)
        while (redoStack.size > 20) redoStack.removeFirst()
        firmwareBytes = undoStack.removeLast()
        firmwareDirty = true
        status.text = "$firmwareName • изменение отменено"
        Toast.makeText(this, "Последнее изменение отменено", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun redoLastEdit() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "Нет изменений для повтора", Toast.LENGTH_SHORT).show()
            return
        }
        val current = firmwareBytes ?: return
        undoStack.addLast(current.copyOf())
        while (undoStack.size > 20) undoStack.removeFirst()
        firmwareBytes = redoStack.removeLast()
        firmwareDirty = true
        status.text = "$firmwareName • изменение восстановлено"
        Toast.makeText(this, "Изменение повторено", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun editRawCell(title: String, index: Int, width: Int, onChanged: () -> Unit) {
        val bytes = firmwareBytes ?: return
        val old = if (width == 1) bytes[index].toInt() and 255 else ((bytes[index].toInt() and 255) shl 8) or (bytes[index+1].toInt() and 255)
        val input = EditText(this).apply {
            setSingleLine(true); setText(old.toString()); setTextColor(fg)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        AlertDialog.Builder(this).setTitle("Изменить ячейку • 0x%X".format(index))
            .setMessage("Калибровка: $title\\nСтарое сырое значение: $old")
            .setView(input).setNegativeButton("Отмена", null)
            .setPositiveButton("Применить", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val v = input.text.toString().toIntOrNull()
                        val max = if (width == 1) 255 else 65535
                        if (v == null || v !in 0..max) {
                            Toast.makeText(this, "Допустимый диапазон 0–$max", Toast.LENGTH_SHORT).show()
                        } else {
                            undoStack.addLast(bytes.copyOf())
                            while (undoStack.size > 20) undoStack.removeFirst()
                            redoStack.clear()
                            if (width == 1) bytes[index] = v.toByte() else {
                                bytes[index] = (v shr 8).toByte(); bytes[index+1] = v.toByte()
                            }
                            firmwareDirty = true
                            status.text = "$firmwareName • есть изменения"
                            dialog.dismiss()
                            onChanged()
                        }
                    }
                }
                dialog.show()
            }
    }


    /**
     * Diagnostic preview only: public J7ES ASM addresses are not yet proven to be
     * direct offsets in every BIN variant. Never use this preview for editing/flashing.
     */
    /**
     * Opens a known J7ES ASM region as a raw-byte view. The region start is grounded
     * in the public data-area listing; dimensions are explicitly marked provisional
     * where the descriptor axes have not been decoded. Never converts raw values.
     */
    private fun openKnownMap(address: Int, name: String, rows: Int, cols: Int) {
        val bytes = firmwareBytes
        if (bytes == null) {
            AlertDialog.Builder(this).setTitle("Сначала открой BIN")
                .setMessage("Калибровка: $name\\nСправочный адрес ASM: 0x%04X".format(address))
                .setPositiveButton("Открыть BIN") { _, _ -> openFile(OPEN_FIRMWARE) }
                .setNegativeButton("Отмена", null).show()
            return
        }
        val count = rows * cols
        if (address < 0 || address.toLong() + count > bytes.size) {
            AlertDialog.Builder(this).setTitle("Область за пределами BIN")
                .setMessage("Карта $name\\nАдрес 0x%04X, длина $count байт; размер BIN ${bytes.size} байт. Изменения не внесены.".format(address))
                .setPositiveButton("OK", null).show()
            return
        }
        val expected = when (address) {
            0x6A53 -> byteArrayOf(0x48,0x48,0x48,0x48,0x48,0x48,0x48,0x48).map { it.toByte() }.toByteArray()
            0x6A7A -> byteArrayOf(0x83.toByte(),0x87.toByte(),0x8D.toByte(),0x97.toByte(),0xA4.toByte(),0xB4.toByte(),0xC3.toByte(),0xD1.toByte(),0xDD.toByte(),0xE7.toByte(),0xF1.toByte(),0xFA.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte())
            0x8F4A -> byteArrayOf(0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0E,0x0D,0x0C,0x0B,0x0A,0x09,0x08,0x07,0x06)
            0x8193 -> ByteArray(16) { 0xFF.toByte() }
            else -> null
        }
        val signatureMatches = expected?.let { sig ->
            address.toLong() + sig.size <= bytes.size &&
                sig.indices.all { bytes[address + it] == sig[it] }
        }
        val note = when {
            address == 0x8193 && signatureMatches == true ->
                "Первые 16 байт совпали с ASM (все 0xFF). Эта область выглядит незаполненной; редактор оставлен доступным только как сырой просмотр, не считай её рабочей картой."
            address == 0x8193 ->
                "В BIN сигнатура 0xFF из ASM не совпала. Не редактируй эту область как карту: возможно, версия или адресное отображение отличаются."
            signatureMatches == true ->
                "Проверка пройдена: начальная сигнатура BIN совпадает с j7es_data_area.asm. Размерность/оси и физический масштаб пока не декодированы."
            expected != null ->
                "Проверка не пройдена: байты BIN по этому адресу не совпадают с сигнатурой ASM. Не используй этот участок как подтверждённую карту; возможны другая версия или смещение образа."
            else ->
                "Для этого адреса нет проверочной сигнатуры в приложении. Адрес ASM сам по себе не доказывает, что это файловое смещение BIN. Значения показаны только для диагностики."
        }
        val canEditKnown = expected != null && signatureMatches == true &&
            address != 0x8193 && (address == 0x6A53 || address == 0x6A7A || address == 0x8F4A)
        val dialog = AlertDialog.Builder(this).setTitle(name)
            .setMessage("ASM/BIN-кандидат: 0x%04X\\nРазмер просмотра: %d×%d = %d байт\\n\\n%s\\n\\nРедактор показывает исходные байты HEX/DEC. Физические единицы и ось температуры пока не декодированы.".format(address, rows, cols, count, note))
            .setPositiveButton(if (canEditKnown) "Открыть редактор" else "Только закрыть", null)
            .setNegativeButton("Закрыть", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                if (canEditKnown) showRawTable(name, address, rows, cols, 1)
                else Toast.makeText(this, "Редактирование заблокировано: карта не подтверждена для этого BIN.", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    private data class KnownCalibration(
        val address: Int,
        val name: String,
        val detail: String,
        val signature: IntArray,
        val bytesExpected: Int
    )

    private fun knownCalibrationCatalog(): List<KnownCalibration> = listOf(
        KnownCalibration(0x6953, "Смесь: обороты × дроссель", "AFR; оси: обороты и положение дросселя", intArrayOf(0x80,0x80,0x7C,0x7A,0x7A,0x7D,0x81,0x85), 256),
        KnownCalibration(0x6A53, "Базовый состав смеси", "AFR; ось: температура двигателя", intArrayOf(0x48,0x48,0x48,0x48,0x48,0x48,0x48,0x48), 39),
        KnownCalibration(0x6A7A, "Коррекция базового состава", "Коэффициент; ось: температура двигателя", intArrayOf(0x83,0x87,0x8D,0x97,0xA4,0xB4,0xC3,0xD1), 39),
        KnownCalibration(0x6AC8, "УОЗ от давления", "Угол зажигания; оси: обороты и абсолютное давление", intArrayOf(0x33,0x35,0x37,0x39,0x3E,0x43,0x47,0x4A), 256),
        KnownCalibration(0x8193, "Весовая коррекция топливоподачи", "Область из FF в открытом исходнике; вероятно, не настроена", intArrayOf(0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF), 256),
        KnownCalibration(0x83BA, "Смесь: обороты × давление", "AFR; оси: обороты и абсолютное давление", intArrayOf(0x7B,0x7B,0x7B,0x7B,0x7E,0x82,0x83,0x84), 256),
        KnownCalibration(0x8F4A, "Коэффициент топливной плёнки", "Коэффициент; ось: температура двигателя", intArrayOf(0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0E), 39)
    )

    private fun showCalibrationDecoderReport() {
        val bytes = firmwareBytes
        if (bytes == null) {
            AlertDialog.Builder(this).setTitle("Нужен BIN")
                .setMessage("Сначала открой файл прошивки, затем запусти проверку карт.")
                .setPositiveButton("Открыть BIN") { _, _ -> openFile(OPEN_FIRMWARE) }
                .setNegativeButton("Отмена", null).show()
            return
        }
        val report = knownCalibrationCatalog().joinToString("\n\n") { map ->
            val inRange = map.address >= 0 && map.address.toLong() + map.bytesExpected <= bytes.size
            val matches = inRange && map.signature.indices.all { (bytes[map.address + it].toInt() and 255) == map.signature[it] }
            val actual = if (inRange) (0 until min(8, map.bytesExpected)).joinToString(" ") { "%02X".format(bytes[map.address + it].toInt() and 255) } else "вне файла"
            val hits = mutableListOf<Int>()
            if (map.signature.size >= 6 && bytes.size >= map.signature.size) {
                for (p in 0..bytes.size - map.signature.size) {
                    var same = true
                    for (j in map.signature.indices) {
                        if ((bytes[p + j].toInt() and 255) != map.signature[j]) { same = false; break }
                    }
                    if (same) {
                        hits.add(p)
                        if (hits.size >= 6) break
                    }
                }
            }
            val hitText = when {
                hits.isEmpty() -> "Совпадения сигнатуры в BIN: не найдены"
                hits.size >= 6 -> "Совпадения первых байтов в BIN (первые 6): " + hits.joinToString { "0x%04X".format(it) } + "…"
                else -> "Совпадения первых байтов в BIN: " + hits.joinToString { "0x%04X".format(it) }
            }
            val status = when {
                !inRange -> "ВНЕ BIN"
                matches && map.address == 0x8193 -> "СОВПАДЕНИЕ, область FF"
                matches -> "СИГНАТУРА СОВПАЛА ПО АДРЕСУ"
                else -> "ПО АДРЕСУ НЕ СОВПАЛА — адрес не подтверждён"
            }
            "%s\nАдрес ASM 0x%04X • длина: %d байт\n%s\n%s\nBIN по адресу: %s\n%s".format(map.name, map.address, map.bytesExpected, map.detail, status, actual, hitText)
        }
        AlertDialog.Builder(this).setTitle("Декодер J7ES • проверка сигнатур")
            .setMessage(report)
            .setPositiveButton("OK", null).show()
    }

    private fun showReferenceMapCandidate(address: Int, name: String, detail: String) {
        val bytes = firmwareBytes
        if (bytes == null) {
            AlertDialog.Builder(this).setTitle("Сначала открой BIN")
                .setMessage("Справочный адрес ASM: 0x%04X\nОткрой свою прошивку, чтобы посмотреть байты-кандидаты.".format(address))
                .setPositiveButton("Открыть BIN") { _, _ -> openFile(OPEN_FIRMWARE) }
                .setNegativeButton("Отмена", null).show()
            return
        }
        if (address < 0 || address + 256 > bytes.size) {
            AlertDialog.Builder(this).setTitle("Адрес вне файла")
                .setMessage("ASM: 0x%04X; размер BIN: 0x%X. Нужна проверка адресного отображения.".format(address, bytes.size))
                .setPositiveButton("OK", null).show()
            return
        }
        val out = StringBuilder()
        out.append("ASM: 0x%04X • предварительное файловое смещение: 0x%04X\n".format(address, address))
        out.append("BIN: $firmwareName\n$name\n$detail\n\n")
        out.append("16×16 байт-кандидатов (HEX):\n")
        for (row in 0 until 16) {
            val off = address + row * 16
            out.append("%04X  ".format(off))
            for (col in 0 until 16) out.append("%02X ".format(bytes[off + col].toInt() and 0xFF))
            out.append('\n')
        }
        out.append("\nВАЖНО: это только диагностический просмотр байтов по тому же числу смещения. Адрес ASM ≠ гарантированное смещение BIN. Размер таблицы, оси и масштаб не подтверждены; не редактируй и не прошивай по этому просмотру.")
        AlertDialog.Builder(this).setTitle("Кандидат карты • HEX")
            .setMessage(out.toString())
            .setPositiveButton("OK", null).show()
    }

    private data class J7MapMarker(val label: String, val address: Int, val typeCodeCandidate: Int?, val descriptorOffset: Int)

    private fun decodeJ7Markers(): List<J7MapMarker> {
        val bytes = descriptorBytes ?: return emptyList()
        val source = String(bytes, java.nio.charset.Charset.forName("windows-1251"))
        val pattern = Regex("!([0-9]{3})-([0-9A-Fa-f]{4})(?:-([0-9A-Fa-f]{2}))?")
        return pattern.findAll(source).mapNotNull { match ->
            val address = match.groupValues[2].toIntOrNull(16) ?: return@mapNotNull null
            val length = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull(16)
            J7MapMarker(match.value, address, length, match.range.first)
        }.distinctBy { it.label }.toList()
    }


    private fun showBinCandidate(marker: J7MapMarker) {
        val bytes = firmwareBytes
        if (bytes == null) {
            AlertDialog.Builder(this).setTitle("Сначала открой BIN")
                .setMessage("Метка ${marker.label}, адрес-кандидат 0x%04X.".format(marker.address))
                .setPositiveButton("Открыть BIN") { _, _ -> openFile(OPEN_FIRMWARE) }
                .setNegativeButton("Отмена", null).show()
            return
        }
        if (marker.address !in bytes.indices) {
            AlertDialog.Builder(this).setTitle("Адрес вне BIN")
                .setMessage("Адрес 0x%04X, размер файла 0x%X. Возможно, адрес в .j7 не является прямым файловым смещением.".format(marker.address, bytes.size))
                .setPositiveButton("OK", null).show()
            return
        }
        val end = min(marker.address + 256, bytes.size)
        val dump = StringBuilder()
        var i = marker.address
        while (i < end) {
            dump.append("%08X  ".format(i))
            for (j in i until min(i + 16, end)) dump.append("%02X ".format(bytes[j].toInt() and 255))
            dump.append('\n')
            i += 16
        }
        AlertDialog.Builder(this).setTitle("BIN: адрес-кандидат")
            .setMessage("Метка ${marker.label}\nФайл: ${firmwareName}\n\n".format(marker.address) + dump.toString() + "\nHEX-дамп не является декодированной картой.")
            .setPositiveButton("OK", null).show()
    }

    private fun showMarkersNearDescriptorOffset(entryOffset: Int) {
        val bytes = descriptorBytes ?: return
        val source = String(bytes, java.nio.charset.Charset.forName("windows-1251"))
        val pattern = Regex("!([0-9]{3})-([0-9A-Fa-f]{4})(?:-([0-9A-Fa-f]{2}))?")
        val matches = pattern.findAll(source).toList()
        val nearby = matches.filter { kotlin.math.abs(it.range.first - entryOffset) <= 512 }
        val report = if (nearby.isEmpty()) {
            "В пределах 512 байт от строки с названием адресные метки не найдены.\n\nЭто не означает, что у калибровки нет адреса: структура .j7 ещё не декодирована полностью."
        } else {
            nearby.joinToString("\n") { m ->
                val delta = m.range.first - entryOffset
                "%s • смещение записи 0x%X • Δ %+d байт".format(m.value, m.range.first, delta)
            } + "\n\nВажно: метка рядом с названием — только кандидат. Связь метки с конкретной таблицей пока не подтверждена."
        }
        AlertDialog.Builder(this).setTitle("Адресные метки рядом с названием")
            .setMessage(report).setPositiveButton("OK", null).show()
    }

    private fun showCalibrationBytes(entryOffset: Int) {
        val bytes = descriptorBytes ?: return
        val from = (entryOffset - 16).coerceAtLeast(0)
        val to = (entryOffset + 96).coerceAtMost(bytes.size)
        val dump = StringBuilder()
        var i = from
        while (i < to) {
            dump.append("%08X  ".format(i))
            val end = min(i + 16, to)
            for (j in i until end) dump.append("%02X ".format(bytes[j].toInt() and 255))
            dump.append('\n')
            i = end
        }
        AlertDialog.Builder(this)
            .setTitle("Фрагмент .j7")
            .setMessage("Это байты файла описания, не значения карты BIN.\n\n" + dump.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun descriptorStrings(): List<Pair<Int, String>> {
        val bytes = descriptorBytes ?: return emptyList()
        val cp1251 = java.nio.charset.Charset.forName("windows-1251")
        val found = mutableListOf<Pair<Int, String>>()
        var start = -1
        val current = java.io.ByteArrayOutputStream()
        fun flush() {
            if (current.size() >= 4) {
                val decoded = String(current.toByteArray(), cp1251).trim()
                if (decoded.length >= 4 && decoded.any { it.isLetter() }) found.add(start to decoded)
            }
            current.reset()
            start = -1
        }
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 255
            if (b in 32..126 || b in 0xC0..0xFF || b in 0xA8..0xB8) {
                if (start < 0) start = i
                current.write(b)
            } else flush()
        }
        flush()
        return found
    }

    private fun hexView() {
        add(body, text("HEX-просмотр", 22f, fg, true))
        add(body, text("Первые 512 байт выбранного файла", 13f, muted))
        gap(body)
        val bytes = firmwareBytes
        if (bytes == null) {
            val c = card()
            add(c, text("Файл не открыт", 16f, fg, true))
            add(c, action("Открыть BIN / ROM") { openFile(OPEN_FIRMWARE) })
            add(body, c)
            return
        }
        val c = card()
        add(c, text("OFFSET        HEX", 11f, accent, true))
        gap(c, 5)
        val out = StringBuilder()
        val count = min(bytes.size, 512)
        var i = 0
        while (i < count) {
            out.append("%08X  ".format(i))
            for (j in 0 until 16) {
                if (i + j < count) out.append("%02X ".format(bytes[i + j].toInt() and 255)) else out.append("   ")
            }
            out.append(" ")
            for (j in 0 until 16) {
                if (i + j < count) {
                    val b = bytes[i + j].toInt() and 255
                    out.append(if (b in 32..126) b.toChar() else '.')
                }
            }
            out.append('\n')
            i += 16
        }
        val hex = text(out.toString(), 10f, fg).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        add(c, hex)
        gap(c, 7)
        add(c, text("Показано $count из ${bytes.size} байт. ${if (firmwareDirty) "Есть несохранённые изменения." else "Файл без изменений."}", 11f, muted))
        gap(c, 8)
        add(c, action("Изменить байт по адресу") { editByteDialog() })
        gap(c, 6)
        add(c, action("Сохранить изменённую копию") { saveCopy() })
        add(body, c)
    }

    private fun toolsView() {
        add(body, text("Инструменты", 22f, fg, true))
        add(body, text("Проверка файлов прошивок", 13f, muted))
        gap(body)
        val c = card()
        add(c, text("СРАВНЕНИЕ ФАЙЛОВ", 11f, accent, true))
        gap(c, 7)
        add(c, action("Выбрать файл для сравнения") { openFile(OPEN_COMPARE) })
        gap(c, 7)
        add(c, text(if (compareBytes == null) "Второй файл не выбран." else "Файл: $compareName • ${compareBytes!!.size} байт", 12f, muted))
        if (firmwareBytes != null && compareBytes != null) {
            gap(c, 7)
            add(c, action("Показать результат сравнения") { compareResult() })
        }
        add(body, c)
        gap(body)
        val h = card()
        add(h, text("КОНТРОЛЬНАЯ СУММА", 11f, accent, true))
        add(h, text(if (firmwareBytes == null) "Открой файл, чтобы рассчитать SHA-256." else sha256(firmwareBytes!!), 10f, muted))
        add(body, h)
        gap(body)
        val structure = card()
        add(structure, text("АНАЛИЗ СТРУКТУРЫ BIN", 11f, accent, true))
        add(structure, text("Поиск пустых областей и статистика байтов. Это помогает понять компоновку файла, но не определяет адреса калибровок автоматически.", 12f, muted))
        gap(structure, 7)
        add(structure, action("Анализировать открытый BIN") { analyzeFirmwareStructure() })
        add(body, structure)
        gap(body)
        val about = card()
        add(about, text("О программе", 14f, fg, true))
        add(about, text("CTP Mobile Lab 0.7.0\nAndroid • Январь 7.2 / J7ES\nЛокальное открытие, HEX-просмотр, SHA-256 и сравнение байтов.", 12f, muted))
        add(body, about)
    }

    private fun analyzeFirmwareStructure() {
        val bytes = firmwareBytes
        if (bytes == null || bytes.isEmpty()) {
            Toast.makeText(this, "Сначала открой BIN", Toast.LENGTH_SHORT).show()
            return
        }
        val zeroCount = bytes.count { (it.toInt() and 0xFF) == 0 }
        val ffCount = bytes.count { (it.toInt() and 0xFF) == 0xFF }
        val otherCount = bytes.size - zeroCount - ffCount
        val runs = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < bytes.size) {
            val value = bytes[i].toInt() and 0xFF
            if (value == 0 || value == 0xFF) {
                val start = i
                while (i < bytes.size && (bytes[i].toInt() and 0xFF) == value) i++
                if (i - start >= 32) runs.add(Pair(start, i - start))
            } else {
                i++
            }
        }
        val report = buildString {
            append("Файл: ").append(firmwareName).append("\n")
            append("Размер: ").append(bytes.size).append(" байт (0x").append("%X".format(bytes.size)).append(")\n")
            append("Нулевые байты: ").append(zeroCount).append(" (").append("%.1f".format(zeroCount * 100.0 / bytes.size)).append("%)\n")
            append("Байты FF: ").append(ffCount).append(" (").append("%.1f".format(ffCount * 100.0 / bytes.size)).append("%)\n")
            append("Остальные: ").append(otherCount).append(" (").append("%.1f".format(otherCount * 100.0 / bytes.size)).append("%)\n\n")
            append("Длинные заполненные области (от 32 байт):\n")
            if (runs.isEmpty()) append("Не найдены.") else runs.take(30).forEach { (start, length) ->
                append("0x").append("%04X".format(start)).append("–0x").append("%04X".format(start + length - 1))
                    .append("  ").append(length).append(" байт  ")
                    .append(if ((bytes[start].toInt() and 0xFF) == 0) "00" else "FF").append("\n")
            }
            if (runs.size > 30) append("… и ещё ").append(runs.size - 30).append(" областей.\n")
            append("\nВажно: пустые области не доказывают, что файл сжат или что карта находится по конкретному адресу.")
        }
        AlertDialog.Builder(this)
            .setTitle("Структура BIN")
            .setMessage(report)
            .setPositiveButton("Закрыть", null)
            .show()
    }
    private fun openFile(request: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, request)
    }

    private fun editByteDialog() {
        val bytes = firmwareBytes
        if (bytes == null) {
            Toast.makeText(this, "Сначала открой BIN", Toast.LENGTH_SHORT).show()
            return
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val offsetInput = EditText(this).apply {
            hint = "Адрес HEX, например 0x100"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(fg)
        }
        val valueInput = EditText(this).apply {
            hint = "Новое значение байта HEX, например FF"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(fg)
        }
        add(form, text("Адрес байта в файле (HEX)", 12f, muted))
        add(form, offsetInput)
        gap(form, 8)
        add(form, text("Новое значение от 00 до FF", 12f, muted))
        add(form, valueInput)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Редактирование BIN")
            .setMessage("Изменяется только выбранный байт. Контрольные суммы ЭБУ автоматически не пересчитываются.")
            .setView(form)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Применить", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val offText = offsetInput.text.toString().trim().removePrefix("0x").removePrefix("0X")
                    val offset = offText.toLong(16)
                    val valueText = valueInput.text.toString().trim().removePrefix("0x").removePrefix("0X")
                    val value = valueText.toInt(16)
                    if (offset < 0 || offset >= bytes.size || value !in 0..255 || valueText.isBlank()) {
                        throw IllegalArgumentException("Адрес или значение вне допустимого диапазона")
                    }
                    // Record a full snapshot so byte edits participate in Undo/Redo.
                    undoStack.addLast(bytes.copyOf())
                    while (undoStack.size > 20) undoStack.removeFirst()
                    redoStack.clear()
                    bytes[offset.toInt()] = value.toByte()
                    firmwareDirty = true
                    status.text = "$firmwareName • ${bytes.size} байт • есть изменения"
                    dialog.dismiss()
                    render()
                    Toast.makeText(this, "Байт по адресу 0x%X изменён".format(offset), Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Проверь адрес и значение HEX", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun saveCopy() {
        val bytes = firmwareBytes
        if (bytes == null) {
            Toast.makeText(this, "Сначала открой прошивку", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, firmwareName.substringBeforeLast('.', firmwareName) + if (firmwareDirty) "_edited.bin" else "_copy.bin")
        }
        startActivityForResult(intent, SAVE_COPY)
    }

    @Deprecated("Kept for broad Android compatibility in this prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                OPEN_FIRMWARE -> {
                    firmwareBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    firmwareDirty = false
                    firmwareName = displayName(uri) ?: "firmware.bin"
                    status.text = "$firmwareName  •  ${firmwareBytes?.size ?: 0} байт"
                    render()
                }
                OPEN_COMPARE -> {
                    compareBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    compareName = displayName(uri) ?: "compare.bin"
                    render()
                    if (firmwareBytes != null && compareBytes != null) compareResult()
                }
                SAVE_COPY -> {
                    val bytes = firmwareBytes ?: return
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    firmwareDirty = false
                    Toast.makeText(this, "Копия сохранена. Проверь контрольные суммы перед использованием в ЭБУ.", Toast.LENGTH_LONG).show()
                    render()
                }
                OPEN_DESCRIPTOR -> {
                    descriptorBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    descriptorName = displayName(uri) ?: "calibrations.j7"
                    render()
                    showDescriptorAnalysis()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка файла: ${e.message ?: "неизвестная ошибка"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun displayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    private fun showDescriptorAnalysis() {
        val bytes = descriptorBytes ?: return
        val found = descriptorStrings()
        val report = StringBuilder()
        val magic = if (bytes.size >= 3) String(bytes, 0, 3, Charsets.US_ASCII) else "?"
        report.append("Файл: " + descriptorName + "\nРазмер: " + bytes.size + " байт\nФормат: " + magic + "\nSHA-256: " + sha256(bytes) + "\n")
        report.append("Текстовые фрагменты (Windows-1251): " + found.size + "\n\n")
        val markers = decodeJ7Markers()
        report.append("СТРУКТУРНЫЙ АНАЛИЗ\n")
        report.append("Метки адресов: ${markers.size}. Метки !NNN-AAAA[-LL] извлечены как кандидаты; сами по себе они не доказывают адрес карты в BIN.\n")
        val calibrationTerms = listOf("состав смеси", "отношение воздух", "температура", "угол опережения", "дроссел", "давлен", "оборотов", "топлив", "зажиган")
        val candidates = found.filter { (_, value) -> calibrationTerms.any { value.contains(it, ignoreCase = true) } }
        report.append("Строки, похожие на названия/оси калибровок: ${candidates.size}\n")
        candidates.take(100).forEach { (offset, value) ->
            val nearby = markers.minByOrNull { kotlin.math.abs(it.descriptorOffset - offset) }
            report.append("• 0x%08X  ".format(offset))
            report.append(value)
            if (nearby != null) report.append("  [ближайшая метка по позиции .j7: ${nearby.label}; файловая позиция записи 0x%X; связь требует проверки]".format(nearby.descriptorOffset))
            report.append('\n')
        }
        report.append("\nПОЛНЫЙ СПИСОК СТРОК\n")
        found.take(150).forEach { (offset, value) ->
            report.append("0x%08X  ".format(offset))
            report.append(value)
            report.append('\n')
        }
        if (found.isEmpty()) report.append("Читаемые строки не найдены.")
        if (found.size > 150) report.append("\n… показаны первые 150 фрагментов.")
        report.append("\n\nВажно: декодированы строки и адресные метки контейнера .j7, но не все бинарные поля записи карты. Для вывода осей, размерности и масштаба требуется подтверждённая структура записи и адресное отображение BIN.")
        AlertDialog.Builder(this).setTitle("Анализ .j7").setMessage(report.toString())
            .setPositiveButton("Открыть калибровки") { _, _ -> tab = "Калибровки"; selectedCalibration = null; render() }
            .setNegativeButton("Закрыть", null).show()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun compareResult() {
        val a = firmwareBytes ?: return
        val b = compareBytes ?: return
        var different = 0
        var first = -1
        val limit = min(a.size, b.size)
        val samples = ArrayList<String>()
        var runStart = -1
        var previous = -2
        val ranges = ArrayList<Pair<Int, Int>>()
        for (i in 0 until limit) {
            if (a[i] != b[i]) {
                different++
                if (first < 0) first = i
                if (samples.size < 100) samples.add("0x%04X: %02X → %02X".format(i, a[i].toInt() and 255, b[i].toInt() and 255))
                if (runStart < 0) runStart = i
                if (i != previous + 1 && previous >= 0) {
                    if (runStart >= 0) ranges.add(Pair(runStart, previous))
                    runStart = i
                }
                previous = i
            } else if (runStart >= 0) {
                ranges.add(Pair(runStart, previous))
                runStart = -1
                previous = -2
            }
        }
        if (runStart >= 0) ranges.add(Pair(runStart, previous))
        val detail = if (samples.isEmpty()) "Совпадений нет: файлы одинаковы в общей части." else
            "Первые отличающиеся байты (A → B):\n" + samples.joinToString("\n") +
            (if (different > samples.size) "\n… ещё ${different - samples.size} отличий." else "")
        val rangeText = ranges.take(30).joinToString("\n") { (start, end) ->
            if (start == end) "0x%04X".format(start) else "0x%04X–0x%04X".format(start, end)
        }
        val message = "Файл A: ${a.size} байт\nФайл B: ${b.size} байт\nРазличий в общей части: $different\nПервое отличие: ${if (first < 0) "нет" else "0x%X".format(first)}\nРазница размера: ${a.size - b.size} байт\n\nУчастки отличий:\n${rangeText.ifBlank { "нет" }}\n\n$detail\n\nПобайтовое сравнение, не сравнение карт калибровок."
        AlertDialog.Builder(this).setTitle("Результат сравнения").setMessage(message)
            .setPositiveButton("OK", null).show()
    }
}
