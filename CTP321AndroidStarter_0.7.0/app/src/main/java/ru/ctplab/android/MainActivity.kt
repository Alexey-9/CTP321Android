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
    private val bg = 0xFF17191D.toInt()
    private val panel = 0xFF22252B.toInt()
    private val panel2 = 0xFF2B3038.toInt()
    private val accent = 0xFF65C8A4.toInt()
    private val fg = 0xFFE7EAF0.toInt()
    private val muted = 0xFF9AA3B2.toInt()
    private var tab = "Обзор"
    private var firmwareName = "Файл не открыт"
    private var firmwareBytes: ByteArray? = null
    private var compareName = ""
    private var compareBytes: ByteArray? = null
    private var descriptorName = ""
    private var descriptorBytes: ByteArray? = null
    private var selectedCalibration: Pair<Int, String>? = null
    private var calibrationSearch = ""
    private var firmwareDirty = false
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
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(panel)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), 0xFF30353D.toInt())
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
        add(branding, text("CTP  /  MOBILE LAB", 11f, accent, true))
        add(branding, text("Инженерная среда ЭБУ", 18f, fg, true))
        header.addView(branding)
        header.addView(action("Открыть BIN") { openFile(OPEN_FIRMWARE) })
        root.addView(header)
        gap(root, 10)
        status = text("Январь 7.2 / J7ES  •  Прошивка не выбрана", 12f, muted)
        add(root, status)
        gap(root, 8)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Обзор", "Калибровки", "HEX", "Инструменты").forEach { name ->
            val item = TextView(this).apply {
                text = name
                textSize = 11.5f
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(12), dp(5), dp(12))
                setTextColor(if (tab == name) accent else muted)
                setTypeface(typeface, if (tab == name) Typeface.BOLD else Typeface.NORMAL)
                setOnClickListener { tab = name; render() }
            }
            tabs.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(tabs)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(18))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(text("CTP Mobile Lab 0.7.0  •  локальная работа с файлами", 10f, muted).apply { gravity = Gravity.CENTER })
        setContentView(root)
        render()
    }

    private fun render() {
        body.removeAllViews()
        when (tab) {
            "Обзор" -> overview()
            "Калибровки" -> calibrations()
            "HEX" -> hexView()
            else -> toolsView()
        }
    }

    private fun overview() {
        add(body, text("Рабочее пространство", 22f, fg, true))
        add(body, text("Прошивки и калибровочные данные в одном месте.", 13f, muted))
        gap(body)
        val f = card()
        add(f, text("ТЕКУЩАЯ ПРОШИВКА", 11f, accent, true))
        gap(f, 5)
        add(f, text(firmwareName, 16f, fg, true))
        val bytes = firmwareBytes
        add(f, text(if (bytes == null) "Размер: —" else "Размер: ${bytes.size} байт  •  ${"%.2f".format(bytes.size / 1024.0)} KiB", 12f, muted))
        add(f, text(if (bytes == null) "SHA-256: —" else "SHA-256: ${sha256(bytes)}", 10f, muted))
        gap(f, 10)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(action("Открыть файл") { openFile(OPEN_FIRMWARE) }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        row.addView(action("Сохранить копию") { saveCopy() }, LinearLayout.LayoutParams(0, -2, 1f))
        f.addView(row)
        add(body, f)
        gap(body)
        val q = card()
        add(q, text("БЫСТРЫЙ ДОСТУП", 11f, accent, true))
        gap(q, 7)
        add(q, action("Сравнить с другой прошивкой") { openFile(OPEN_COMPARE) })
        gap(q, 7)
        add(q, action("Перейти к HEX-просмотру") { tab = "HEX"; render() })
        gap(q, 7)
        add(q, action("Область калибровок") { tab = "Калибровки"; render() })
        add(body, q)
        gap(body)
        val note = card()
        add(note, text("СОСТОЯНИЕ РЕДАКТОРА", 11f, accent, true))
        gap(note, 4)
        add(note, text("Открытие, копирование, SHA-256 и побайтовое сравнение доступны. Декодер карт Январь 7.2/J7ES ещё не реализован.", 13f, muted))
        add(body, note)
    }

    private fun calibrations() {
        val selected = selectedCalibration
        if (selected != null) {
            calibrationDetail(selected)
            return
        }
        add(body, text("Калибровки", 22f, fg, true))
        add(body, text("Список формируется из загруженного описания .j7 — без подставных названий и значений.", 13f, muted))
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
        add(summary, action("Показать данные BIN рядом с описанием") {
            showCalibrationBytes(entry.first)
        })
        add(body, summary)
        gap(body)
        val table = card()
        add(table, text("ТАБЛИЦА ЗНАЧЕНИЙ", 11f, accent, true))
        gap(table, 6)
        add(table, text("Ожидает декодирования формата .j7", 16f, fg, true))
        add(table, text("Следующий этап — связать дескриптор с реальными адресами и форматами данных прошивки. После этого здесь появятся таблица, редактирование ячеек и сохранение файла.", 13f, muted))
        add(body, table)
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
        val about = card()
        add(about, text("О программе", 14f, fg, true))
        add(about, text("CTP Mobile Lab 0.7.0\nAndroid • Январь 7.2 / J7ES\nЛокальное открытие, HEX-просмотр, SHA-256 и сравнение байтов.", 12f, muted))
        add(body, about)
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
        found.take(150).forEach { (offset, value) ->
            report.append("0x%08X  ".format(offset))
            report.append(value)
            report.append('\n')
        }
        if (found.isEmpty()) report.append("Читаемые строки не найдены.")
        if (found.size > 150) report.append("\n… показаны первые 150 фрагментов.")
        report.append("\n\nНазвания доступны во вкладке «Калибровки». Значения таблиц пока не декодируются.")
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
        for (i in 0 until limit) {
            if (a[i] != b[i]) {
                different++
                if (first < 0) first = i
            }
        }
        val message = "Файл A: ${a.size} байт\nФайл B: ${b.size} байт\nРазличий в общей части: $different\nПервое отличие: ${if (first < 0) "нет" else "0x%X".format(first)}\nРазница размера: ${a.size - b.size} байт\n\nПобайтовое сравнение, не сравнение карт калибровок."
        AlertDialog.Builder(this).setTitle("Результат сравнения").setMessage(message)
            .setPositiveButton("OK", null).show()
    }
}
