package com.jayc180.rhythmengine.ui

import platform.Foundation.*
import platform.PhotosUI.*
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * Wraps PHPickerViewController (iOS 14+, no permission required).
 * Must be remember{}ed to prevent early GC (UIKit holds only a weak delegate ref).
 * Loads the selected image as JPEG data and writes it to a temp path before invoking callback.
 */
class PhotosPickerHelper : NSObject(), PHPickerViewControllerDelegateProtocol {

    private var callback: ((String) -> Unit)? = null

    fun pickImage(from: UIViewController, onPick: (String) -> Unit) {
        callback = onPick
        val config = PHPickerConfiguration()
        config.filter = PHPickerFilter.imagesFilter
        config.selectionLimit = 1
        val picker = PHPickerViewController(configuration = config)
        picker.delegate = this
        picker.modalPresentationStyle = UIModalPresentationFullScreen
        from.presentViewController(picker, animated = true, completion = null)
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) { callback = null; return }

        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.jpeg") { data, _ ->
            data?.let { nsData ->
                val ts   = NSDate().timeIntervalSince1970.toLong()
                val path = "${NSTemporaryDirectory()}bg_photo_$ts.jpg"
                nsData.writeToFile(path, atomically = true)
                callback?.invoke(path)
            }
            callback = null
        }
    }
}
