package ie.tus.himbavision.utility.OCRAndTextSimilarity

import android.util.Log
import java.util.Locale

fun jaccardSimilarity(text1: String, text2: String): Double {
    // Convert both texts to lowercase to make the comparison case-insensitive
    val lowerText1 = text1.lowercase(Locale.ROOT)
    val lowerText2 = text2.lowercase(Locale.ROOT)

    // Split text1 into a set of unique words, using a regex to split on non-word characters
    val set1 = lowerText1.split(Regex("\\W+")).toSet()
    // Split text2 into a set of unique words, using the same regex
    val set2 = lowerText2.split(Regex("\\W+")).toSet()

    // Find the size of the intersection of set1 and set2 (common elements between both sets)
    val intersection = set1.intersect(set2).size
    // Find the size of the union of set1 and set2 (all unique elements from both sets)
    val union = set1.union(set2).size

    // Calculate the Jaccard similarity score as the ratio of intersection to union
    val similarityScore = if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()

    // Check if 70% of either text1 or text2 exists in the other
    val text1Words = lowerText1.split(Regex("\\W+"))
    val text2Words = lowerText2.split(Regex("\\W+"))
    val text1InText2 = text1Words.count { it in text2Words }.toDouble() / text1Words.size
    val text2InText1 = text2Words.count { it in text1Words }.toDouble() / text2Words.size

    // If 70% of either text exists in the other, return a similarity score greater than 0.3
    if (text1InText2 >= 0.7 || text2InText1 >= 0.7) {
        return 0.31
    }

    // Log the input texts and the calculated similarity score for debugging purposes
    Log.d("Similarity Score", text1)
    Log.d("Similarity Score", text2)
    Log.d("Similarity Score", similarityScore.toString())

    // Return the Jaccard similarity score
    return similarityScore
}
