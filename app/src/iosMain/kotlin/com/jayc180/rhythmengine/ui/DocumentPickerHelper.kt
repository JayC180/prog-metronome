package com.jayc180.rhythmengine.ui

import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject
import platform.UIKit.UIDocumentPickerDelegateProtocol

/**
 * Wraps UIDocumentPickerViewController for use from Compose.
 * Must be remember{}ed in the composable to prevent early GC
 * (UIKit holds only a weak delegate reference).
 */
class DocumentPickerHelper : NSObject(), UIDocumentPickerDelegateProtocol {

    private var callback: ((String) -> Unit)? = null

    /** Pick any file (for .rhy import). */
    fun pickAny(from: UIViewController, onPick: (String) -> Unit) =
        present(from, listOf("public.data"), onPick)

    /** Pick a WAV audio file (for sound import). */
    fun pickWav(from: UIViewController, onPick: (String) -> Unit) =
        present(from, listOf("com.microsoft.waveform-audio", "public.audio"), onPick)

    /** Pick a JSON file (for theme import). */
    fun pickJson(from: UIViewController, onPick: (String) -> Unit) =
        present(from, listOf("public.json", "public.data"), onPick)

    /** Pick an image file (for background import). */
    fun pickImage(from: UIViewController, onPick: (String) -> Unit) =
        present(from, listOf("public.image"), onPick)

    private fun present(from: UIViewController, utIdentifiers: List<String>, onPick: (String) -> Unit) {
        val types = utIdentifiers.mapNotNull { UTType.typeWithIdentifier(it) }
        if (types.isEmpty()) return
        callback = onPick
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        picker.delegate = this
        picker.allowsMultipleSelection = false
        from.presentViewController(picker, animated = true, completion = null)
    }

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val path = (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.path ?: return
        callback?.invoke(path)
        callback = null
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        callback = null
    }
}
