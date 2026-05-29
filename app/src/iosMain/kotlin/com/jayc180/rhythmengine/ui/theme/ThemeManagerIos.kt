package com.jayc180.rhythmengine.ui.theme

import kotlinx.cinterop.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.*

/**
 * Theme + background persistence for iOS using NSUserDefaults and FileManager.
 * Mirrors the Android ThemeManager API surface.
 */
@OptIn(ExperimentalForeignApi::class)
class ThemeManagerIos {

    private val prefs = NSUserDefaults.standardUserDefaults

    private val userThemeDir: String = run {
        @Suppress("UNCHECKED_CAST")
        val docs = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: ""
        "$docs/themes".also {
            NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null)
        }
    }

    private val bgDir: String = run {
        @Suppress("UNCHECKED_CAST")
        val docs = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: ""
        "$docs/background".also {
            NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null)
        }
    }

    private val _availableThemes = MutableStateFlow<List<ThemeConfig>>(emptyList())
    val availableThemes: StateFlow<List<ThemeConfig>> = _availableThemes.asStateFlow()

    private val _activeTheme = MutableStateFlow(BuiltInThemes.ObsidianDark)
    val activeTheme: StateFlow<ThemeConfig> = _activeTheme.asStateFlow()

    private val _bgConfig = MutableStateFlow(loadBgConfig())
    val bgConfig: StateFlow<BackgroundConfig> = _bgConfig.asStateFlow()

    private val _panelAlpha = MutableStateFlow(
        if (prefs.objectForKey("panel_alpha") != null)
            prefs.floatForKey("panel_alpha")
        else BuiltInThemes.ObsidianDark.defaultPanelAlpha
    )
    val panelAlpha: StateFlow<Float> = _panelAlpha.asStateFlow()

    init {
        loadAllThemes()
        restoreActiveTheme()
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    private fun loadAllThemes() {
        @Suppress("UNCHECKED_CAST")
        val userThemes = (NSFileManager.defaultManager
            .contentsOfDirectoryAtPath(userThemeDir, null) as? List<String>)
            ?.filter { it.endsWith(".json") }
            ?.mapNotNull { name ->
                val path = "$userThemeDir/$name"
                val json = NSString.stringWithContentsOfFile(
                    path, encoding = NSUTF8StringEncoding, error = null
                ) as? String ?: return@mapNotNull null
                runCatching { parseThemeJson(json) }.getOrNull()
            } ?: emptyList()
        _availableThemes.value = userThemes + BuiltInThemes.all
    }

    private fun restoreActiveTheme() {
        val saved = prefs.stringForKey("active_theme") ?: return
        _availableThemes.value.firstOrNull { it.name == saved }
            ?.let { _activeTheme.value = it }
    }

    fun setTheme(theme: ThemeConfig) {
        _activeTheme.value  = theme
        _panelAlpha.value   = theme.defaultPanelAlpha
        prefs.setObject(theme.name, "active_theme")
        prefs.setFloat(theme.defaultPanelAlpha, "panel_alpha")
    }

    fun setPanelAlpha(alpha: Float) {
        val v = alpha.coerceIn(0f, 1f)
        _panelAlpha.value = v
        prefs.setFloat(v, "panel_alpha")
    }

    fun importTheme(path: String): Boolean {
        return try {
            val json = NSString.stringWithContentsOfFile(
                path, encoding = NSUTF8StringEncoding, error = null
            ) as? String ?: return false
            val theme = parseThemeJson(json)
            val dest  = "$userThemeDir/${theme.name}.json"
            val bytes = json.encodeToByteArray()
            bytes.usePinned { pinned ->
                val f = fopen(dest, "wb") ?: return false
                fwrite(pinned.addressOf(0), 1UL, bytes.size.toULong(), f)
                fclose(f)
            }
            loadAllThemes(); true
        } catch (_: Exception) { false }
    }

    fun deleteUserTheme(theme: ThemeConfig) {
        NSFileManager.defaultManager.removeItemAtPath(
            "$userThemeDir/${theme.name}.json", null)
        loadAllThemes()
        if (_activeTheme.value.name == theme.name) setTheme(BuiltInThemes.ObsidianDark)
    }

    // ── Background ────────────────────────────────────────────────────────────

    fun importBackground(sourcePath: String): Boolean {
        return try {
            val ext  = sourcePath.substringAfterLast('.', "jpg")
            val ts   = NSDate().timeIntervalSince1970.toLong()
            val dest = "$bgDir/bg_$ts.$ext"
            val fm   = NSFileManager.defaultManager
            @Suppress("UNCHECKED_CAST")
            (fm.contentsOfDirectoryAtPath(bgDir, null) as? List<String>)
                ?.forEach { fm.removeItemAtPath("$bgDir/$it", null) }
            fm.copyItemAtPath(sourcePath, dest, null)
            val c = _bgConfig.value.copy(imagePath = dest)
            saveBgConfig(c); _bgConfig.value = c; true
        } catch (_: Exception) { false }
    }

    fun setFitMode(mode: BgFitMode)  { update { copy(fitMode = mode) } }
    fun setDim(dim: Float)            { update { copy(dim = dim.coerceIn(0f, 1f)) } }
    fun setPanX(x: Float)             { update { copy(panX = x.coerceIn(0f, 1f)) } }
    fun setPanY(y: Float)             { update { copy(panY = y.coerceIn(0f, 1f)) } }
    fun setPanScale(s: Float)         { update { copy(panScale = s.coerceIn(0.2f, 3f)) } }

    fun removeBackground() {
        val fm = NSFileManager.defaultManager
        @Suppress("UNCHECKED_CAST")
        (fm.contentsOfDirectoryAtPath(bgDir, null) as? List<String>)
            ?.forEach { fm.removeItemAtPath("$bgDir/$it", null) }
        val c = BackgroundConfig(); saveBgConfig(c); _bgConfig.value = c
    }

    private inline fun update(transform: BackgroundConfig.() -> BackgroundConfig) {
        val c = _bgConfig.value.transform(); saveBgConfig(c); _bgConfig.value = c
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun loadBgConfig(): BackgroundConfig {
        val hasImage = prefs.boolForKey("bg_has_image")
        val path     = prefs.stringForKey("bg_image_path")
        val fitIdx   = prefs.integerForKey("bg_fit_mode").toInt()
        val dim      = if (prefs.objectForKey("bg_dim") != null) prefs.floatForKey("bg_dim") else 0.4f
        val panX     = if (prefs.objectForKey("bg_pan_x") != null) prefs.floatForKey("bg_pan_x") else 0.5f
        val panY     = if (prefs.objectForKey("bg_pan_y") != null) prefs.floatForKey("bg_pan_y") else 0.5f
        val panScale = if (prefs.objectForKey("bg_pan_scale") != null) prefs.floatForKey("bg_pan_scale") else 1f
        val validPath = if (hasImage && path != null &&
            NSFileManager.defaultManager.fileExistsAtPath(path)) path else null
        return BackgroundConfig(
            imagePath = validPath,
            fitMode   = BgFitMode.entries.getOrElse(fitIdx) { BgFitMode.Fill },
            dim = dim, panX = panX, panY = panY, panScale = panScale,
        )
    }

    private fun saveBgConfig(c: BackgroundConfig) {
        prefs.setBool(c.hasImage,  "bg_has_image")
        prefs.setObject(c.imagePath, "bg_image_path")
        prefs.setInteger(BgFitMode.entries.indexOf(c.fitMode).toLong(), "bg_fit_mode")
        prefs.setFloat(c.dim,      "bg_dim")
        prefs.setFloat(c.panX,     "bg_pan_x")
        prefs.setFloat(c.panY,     "bg_pan_y")
        prefs.setFloat(c.panScale, "bg_pan_scale")
    }
}
