package com.jayc180.rhythmengine.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * ThemeManager — androidMain.
 * Owns theme and background config state, persists to SharedPreferences
 *
 * It's kind of doing a lot, maybe split to smaller pieces?
 */
class ThemeManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("rhythm_theme", Context.MODE_PRIVATE)

    private val userThemeDir = File(context.filesDir, "themes").also { it.mkdirs() }
    private val bgDir        = File(context.filesDir, "background").also { it.mkdirs() }

    private val _availableThemes = MutableStateFlow<List<ThemeConfig>>(emptyList())
    val availableThemes: StateFlow<List<ThemeConfig>> = _availableThemes.asStateFlow()

    private val _activeTheme = MutableStateFlow(BuiltInThemes.ObsidianDark)
    val activeTheme: StateFlow<ThemeConfig> = _activeTheme.asStateFlow()

    private val _bgConfig = MutableStateFlow(loadBgConfig())
    val bgConfig: StateFlow<BackgroundConfig> = _bgConfig.asStateFlow()

    // maybe dont need panel alpha since prev idea abandoned
    private val _panelAlpha = MutableStateFlow(
        prefs.getFloat("panel_alpha", BuiltInThemes.ObsidianDark.defaultPanelAlpha)
    )
    val panelAlpha: StateFlow<Float> = _panelAlpha.asStateFlow()

    init {
        loadAllThemes()
        restoreActiveTheme()
    }

    private fun loadAllThemes() {
        val userThemes = userThemeDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { parseThemeJson(file.readText()) }.getOrNull()
            }
            ?: emptyList()
        _availableThemes.value = userThemes + BuiltInThemes.all
    }

    private fun restoreActiveTheme() {
        val savedName = prefs.getString("active_theme", null) ?: return
        val theme = _availableThemes.value.firstOrNull { it.name == savedName } ?: return
        _activeTheme.value = theme
    }

    fun setTheme(theme: ThemeConfig) {
        android.util.Log.d("ThemeDebug", "setTheme: ${theme.name}, beatActiveBg=${theme.beatActiveBg}")
        _activeTheme.value = theme
        _panelAlpha.value = theme.defaultPanelAlpha
        prefs.edit()
            .putString("active_theme", theme.name)
            .putFloat("panel_alpha", theme.defaultPanelAlpha)
            .apply()
    }

    fun setPanelAlpha(alpha: Float) {
        val v = alpha.coerceIn(0f, 1f)
        _panelAlpha.value = v
        prefs.edit().putFloat("panel_alpha", v).apply()
    }

    fun importTheme(uri: Uri): Boolean {
        return try {
            val json  = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText() ?: return false
            val theme = parseThemeJson(json)
            File(userThemeDir, "${theme.name}.json").writeText(json)
            loadAllThemes()
            true
        } catch (e: Exception) {
            android.util.Log.e("ThemeManager", "Theme import failed: ${e.message}")
            false
        }
    }

    // no one's gonna rice this for now so impl later?
    fun deleteUserTheme(theme: ThemeConfig) {
        File(userThemeDir, "${theme.name}.json").delete()
        loadAllThemes()
        if (_activeTheme.value.name == theme.name) setTheme(BuiltInThemes.ObsidianDark)
    }

    fun importBackground(uri: Uri): Boolean {
        return try {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext      = mimeType.substringAfterLast('/')
                .let { if (it == "jpeg") "jpg" else it }
            // unique filename
            val dest = File(bgDir, "bg_${System.currentTimeMillis()}.$ext")
            bgDir.listFiles()?.forEach { it.delete() }
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            val newConfig = _bgConfig.value.copy(imagePath = dest.absolutePath)
            saveBgConfig(newConfig)
            _bgConfig.value = newConfig
            true
        } catch (e: Exception) {
            android.util.Log.e("ThemeManager", "Background import failed: ${e.message}")
            false
        }
    }

    fun setFitMode(mode: BgFitMode) {
        val c = _bgConfig.value.copy(fitMode = mode)
        saveBgConfig(c); _bgConfig.value = c
    }

    fun setDim(dim: Float) {
        val c = _bgConfig.value.copy(dim = dim.coerceIn(0f, 1f))
        saveBgConfig(c); _bgConfig.value = c
    }

    fun setPanX(x: Float) {
        val c = _bgConfig.value.copy(panX = x.coerceIn(0f, 1f))
        saveBgConfig(c); _bgConfig.value = c
    }

    fun setPanY(y: Float) {
        val c = _bgConfig.value.copy(panY = y.coerceIn(0f, 1f))
        saveBgConfig(c); _bgConfig.value = c
    }

    fun setPanScale(scale: Float) {
        val c = _bgConfig.value.copy(panScale = scale.coerceIn(0.2f, 3f))
        saveBgConfig(c); _bgConfig.value = c
    }

    fun removeBackground() {
        bgDir.listFiles()?.forEach { it.delete() }
        val c = BackgroundConfig()
        saveBgConfig(c); _bgConfig.value = c
    }

    private fun loadBgConfig(): BackgroundConfig {
        val hasImage = prefs.getBoolean("bg_has_image", false)
        val path     = prefs.getString("bg_image_path", null)
        val fitIdx   = prefs.getInt("bg_fit_mode", 0)
        val dim      = prefs.getFloat("bg_dim", 0.4f)
        val panX     = prefs.getFloat("bg_pan_x", 0.5f)
        val panY     = prefs.getFloat("bg_pan_y", 0.5f)
        val panScale = prefs.getFloat("bg_pan_scale", 1f)
        return BackgroundConfig(
            imagePath = if (hasImage && path != null && File(path).exists()) path else null,
            fitMode   = BgFitMode.entries.getOrElse(fitIdx) { BgFitMode.Fill },
            dim       = dim,
            panX      = panX,
            panY      = panY,
            panScale  = panScale,
        )
    }

    private fun saveBgConfig(config: BackgroundConfig) {
        prefs.edit()
            .putBoolean("bg_has_image", config.hasImage)
            .putString("bg_image_path", config.imagePath)
            .putInt("bg_fit_mode", BgFitMode.entries.indexOf(config.fitMode))
            .putFloat("bg_dim", config.dim)
            .putFloat("bg_pan_x", config.panX)
            .putFloat("bg_pan_y", config.panY)
            .putFloat("bg_pan_scale", config.panScale)
            .apply()
    }
}