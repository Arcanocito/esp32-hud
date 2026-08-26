package com.maisonsmd.catdrive.lib

import android.app.Notification
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.service.notification.StatusBarNotification
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.view.children
import android.util.Log
import org.json.JSONObject
import timber.log.Timber
import android.graphics.Bitmap

const val GMAPS_PACKAGE = "com.google.android.apps.maps"

enum class ContentViewType {
    NORMAL,
    BIG,
    BEST,
}

internal class GMapsNotification(cx: Context, sbn: StatusBarNotification) : NavigationNotification(cx, sbn) {
    init {
        val normalContent = getContentView(ContentViewType.NORMAL)
        if (normalContent != null)
            parseRemoteView(getRemoteViewGroup(normalContent))

        val bestContentView = getContentView(ContentViewType.BEST)
        if (bestContentView != normalContent)
            parseRemoteView(getRemoteViewGroup(bestContentView))
    }

    private fun getContentView(type: ContentViewType = ContentViewType.BEST): RemoteViews? {
        if (type == ContentViewType.BIG || type == ContentViewType.BEST) {
            val remoteViews = Notification.Builder.recoverBuilder(mContext, mNotification).createBigContentView()

            if (remoteViews != null || type == ContentViewType.BIG)
                return remoteViews
        }

        return Notification.Builder.recoverBuilder(mContext, mNotification).createContentView()
    }

    private fun collectTextViews(view: View): List<String> {
        val result = mutableListOf<String>()

        if (view is TextView) {
            val text = view.text?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                result.add(text)
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                result.addAll(collectTextViews(view.getChildAt(i)))
            }
        }

        return result
    }

    private fun getRemoteViewGroup(remoteViews: RemoteViews?): ViewGroup {
        if (remoteViews == null) {
            throw Exception("Impossible to create notification view")
        }

        val layoutInflater = mAppSourceContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val viewGroup = layoutInflater.inflate(remoteViews.layoutId, null) as ViewGroup?
            ?: throw Exception("Impossible to inflate viewGroup")

        remoteViews.reapply(mAppSourceContext, viewGroup)

        return viewGroup
    }

    private fun getEntryName(item: View): String {
        val entryName: String = try {
            if (item.id > 0)
                mAppSourceContext.resources.getResourceEntryName(item.id)
            else ""
        } catch (e: Exception) {
            ""
        }

        return entryName
    }

    private fun getEntryJsonKey(item: View): String {
        return "${item.javaClass.simpleName}:${getEntryName(item)}"
    }

    private fun findChildByName(group: ViewGroup, name: CharSequence): View? {
        for (child in group.children) {
            val entryName = getEntryName(child)

            if (entryName == name)
                return child

            if (child is ViewGroup) {
                val c = findChildByName(child, name)
                if (c != null)
                    return c
            }
        }

        return null
    }

    private fun parseRemoteView(group: ViewGroup): NavigationData {
        val data = navigationData

        /*
         * Get all visible text from the Google Maps notification.
         *
         * Current Google Maps layout examples:
         *
         * "200 m · Links abbiegen auf Halberstädter Str."
         * "Ankunft um 05:18"
         *
         * or:
         *
         * "Richtung Borchener Str. starten"
         * "Ankunft um 05:13"
         */
        val allTexts = collectTextViews(group)

        Log.d("MapsParser", "Notification texts:")
        allTexts.forEach {
            Log.d("MapsParser", " -> $it")
        }

        var distanceToNext: String? = null
        var instruction: String? = null
        var arrivalTime: String? = null

        /*
         * Matches for example:
         *
         * 200 m · Links abbiegen auf Halberstädter Str.
         * 2,6 km · Rechts abbiegen auf Musterstraße
         */
        val distanceAndInstructionRegex =
            Regex(
                """^\s*(\d+(?:[.,]\d+)?\s*(?:m|km))\s*[·•]\s*(.+)\s*$""",
                RegexOption.IGNORE_CASE
            )

        /*
         * Matches:
         *
         * Ankunft um 05:18
         */
        val arrivalRegex =
            Regex(
                """Ankunft\s+um\s+(\d{1,2}:\d{2})""",
                RegexOption.IGNORE_CASE
            )

        for (rawText in allTexts) {

            /*
             * Google Maps sometimes uses a non-breaking space.
             * Convert it to a normal space before parsing.
             */
            val text = rawText
                .replace('\u00A0', ' ')
                .trim()

            /*
             * Example:
             *
             * 200 m · Links abbiegen auf Halberstädter Str.
             */
            val navMatch = distanceAndInstructionRegex.find(text)

            if (navMatch != null) {
                distanceToNext = navMatch.groupValues[1].trim()
                instruction = navMatch.groupValues[2].trim()
                continue
            }

            /*
             * Example:
             *
             * Ankunft um 05:18
             */
            val arrivalMatch = arrivalRegex.find(text)

            if (arrivalMatch != null) {
                arrivalTime = arrivalMatch.groupValues[1]
                continue
            }

            /*
             * Some navigation states have no distance.
             *
             * Example:
             *
             * Richtung Borchener Str. starten
             */
            if (
                instruction == null &&
                (
                        text.startsWith("Richtung ", ignoreCase = true) ||
                                text.contains("abbiegen", ignoreCase = true) ||
                                text.contains("weiter", ignoreCase = true) ||
                                text.contains("nehmen", ignoreCase = true) ||
                                text.contains("fahren", ignoreCase = true) ||
                                text.contains("wenden", ignoreCase = true) ||
                                text.contains("halten", ignoreCase = true) ||
                                text.contains("Ausfahrt", ignoreCase = true) ||
                                text.contains("Kreisverkehr", ignoreCase = true)
                        )
            ) {
                instruction = text
            }
        }

        Log.d(
            "MapsParser",
            "distanceToNext = $distanceToNext"
        )

        Log.d(
            "MapsParser",
            "instruction = $instruction"
        )

        Log.d(
            "MapsParser",
            "arrivalTime = $arrivalTime"
        )

        /*
         * Update navigation information.
         *
         * Google Maps currently only gives us the arrival time.
         * ETE / remaining total distance are no longer present
         * in this notification layout.
         */
        arrivalTime?.let { time ->
            parseEtaText("Ankunft um $time")?.let { parsedEta ->
                data.eta = parsedEta
            }
        }

        data.nextDirection = NavigationDirection(
            instruction.orEmpty(),       // navigation instruction
            "",                          // no separate description anymore
            distanceToNext.orEmpty()     // distance to next maneuver
        )

        /*
         * Maneuver icon.
         *
         * This still appears to use right_icon, so we can leave
         * the existing icon parser unchanged.
         */
        val rightIcon =
            findChildByName(group, "right_icon") as? ImageView

        (rightIcon?.drawable as? BitmapDrawable)?.bitmap?.let { bitmap ->
            val bitmapConfig =
                bitmap.config ?: Bitmap.Config.ARGB_8888

            data.actionIcon = NavigationIcon(
                bitmap.copy(bitmapConfig, false)
            )
        }

        return data
    }

    private fun parseEtaText(text: CharSequence?): NavigationEta? {
        val rawText = text
            ?.toString()
            ?.replace('\u00A0', ' ')
            ?.trim()
            .orEmpty()

        if (rawText.isEmpty()) {
            return null
        }

        /*
         * New German Google Maps format:
         *
         * Maps · Ankunft um 15:19
         *
         * Some English variants are supported as well:
         *
         * Maps · Arrival at 3:19 PM
         * Maps · Arrive at 3:19 PM
         */
        val newFormatMatch = Regex(
            pattern = """(?:Ankunft\s+um|Arrival\s+at|Arrive\s+at)\s+(.+)$""",
            option = RegexOption.IGNORE_CASE
        ).find(rawText)

        if (newFormatMatch != null) {
            val arrivalTime = newFormatMatch
                .groupValues[1]
                .trim()

            return NavigationEta(
                arrivalTime,
                "", // Remaining travel time is not present in the new layout.
                ""  // Remaining total distance is not present in the new layout.
            )
        }

        /*
         * Old Google Maps format:
         *
         * 12 min · 5 km · 15:19 ETA
         */
        val etaParts = rawText
            .split('·')
            .map { it.trim() }

        if (etaParts.size >= 3) {
            val remainingTime = etaParts[0]
            val remainingDistance = etaParts[1]

            val arrivalTime = etaParts
                .drop(2)
                .joinToString(" · ")
                .replace(
                    Regex(
                        pattern = """\s*ETA\s*$""",
                        option = RegexOption.IGNORE_CASE
                    ),
                    ""
                )
                .trim()

            return NavigationEta(
                arrivalTime,
                remainingTime,
                remainingDistance
            )
        }

        Timber.w("Unknown Google Maps ETA format: %s", rawText)

        return null
    }

    // for debugging
    private fun parseRemoteViewToJson(group: ViewGroup, json: JSONObject? = null): JSONObject {
        var rawJson = json ?: JSONObject()

        val parentGroupKey = getEntryJsonKey(group)
        rawJson.apply {
            put(parentGroupKey, JSONObject())
        }

        for (child in group.children) {
            val entryName = getEntryName(child)
            when (child) {
                is ImageView -> {
                    rawJson.getJSONObject(parentGroupKey).apply { put(getEntryJsonKey(child), entryName) }
                }
                is Button -> {
                    // result.getJSONObject(parentGroupKey).apply { put(getEntryJsonKey(child), child.text) }
                }
                is TextView -> {
                    rawJson.getJSONObject(parentGroupKey).apply {
                        put(getEntryJsonKey(child), JSONObject().apply {
                            put(
                                "rawText",
                                child.text
                            )
                        })
                    }
                }
                is ViewGroup -> {
                    rawJson = parseRemoteViewToJson(child, rawJson)
                }
            }
        }

        return rawJson
    }
}
