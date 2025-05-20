package ie.tus.himbavision.localsecurestorage


import android.content.Context
import android.content.SharedPreferences

class SegmentationKeyStorageHelper(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "segmentation_key_prefs",
        Context.MODE_PRIVATE
    )

    fun saveSegmentationKey(segmentationKey: String) {
        sharedPreferences.edit().putString("segmentation_key", segmentationKey).apply()
    }

    fun getSegmentationKey(): String? {
        return sharedPreferences.getString("segmentation_key", null)
    }

    fun clearSegmentationKey() {
        sharedPreferences.edit().remove("segmentation_key").apply()
    }
}
