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

        val directionText = findChildByName(group, "text") as? TextView
        val etaText = findChildByName(group, "header_text") as? TextView
        val titleText = findChildByName(group, "title") as? TextView
        val rightIcon = findChildByName(group, "right_icon") as? ImageView

        /*
         * New Google Maps notification:
         *
         * header_text:
         * Maps · Ankunft um 15:19
         *
         * Old Google Maps notification:
         * 12 min · 5 km · 15:19 ETA
         */
        parseEtaText(etaText?.text)?.let {
            data.eta = it
        }

        /*
         * New Google Maps notification:
         *
         * title:
         * 600 m · Rechts abbiegen auf Giselastraße
         *
         * Old Google Maps notification:
         *
         * title:
         * 600 m
         *
         * text:
         * Rechts abbiegen auf Giselastraße
         */
        var nextDistance = ""
        var titleDirection = ""

        titleText?.text
            ?.toString()
            ?.replace('\u00A0', ' ')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { title ->
                val titleParts = title.split('·', limit = 2)

                if (titleParts.size == 2) {
                    nextDistance = titleParts[0].trim()
                    titleDirection = titleParts[1].trim()
                } else {
                    // Old layout: title contains only the maneuver distance.
                    nextDistance = title
                }
            }

        var nextRoad = ""
        var nextRoadDesc = ""

        if (titleDirection.isNotEmpty()) {
            /*
             * New layout: the complete direction is already contained in title.
             *
             * Example:
             * "Rechts abbiegen auf Giselastraße"
             */
            nextRoad = titleDirection
        } else {
            /*
             * Old layout: parse the separate styled direction text.
             */
            val directionContent = directionText?.text

            if (directionContent !is Spanned) {
                // For example: "Rerouting..."
                nextRoad = directionContent?.toString().orEmpty()

                if (nextRoad.isNotEmpty()) {
                    Timber.w(
                        "Direction Text is not Spanned, text: %s",
                        nextRoad
                    )
                }
            } else {
                /*
                 * Road names are in Typeface.BOLD.
                 * Additional direction text is in Typeface.NORMAL.
                 */
                val directionList = ParserHelper.splitByStyleSpan(
                    directionContent,
                    Typeface.NORMAL,
                    2
                )

                if (directionList.isNotEmpty()) {
                    val nextRoadList = mutableListOf(directionList.first())
                    val nextRoadDescList =
                        mutableListOf<ParserHelper.SpanSplitResult>()

                    val rest = directionList.drop(1)

                    val index = rest.indexOfFirst {
                        it.isKeySpan && it.text.trim() != "/"
                    }

                    if (index == -1) {
                        nextRoadList.addAll(rest)
                    } else {
                        nextRoadList.addAll(rest.subList(0, index))
                        nextRoadDescList.addAll(
                            rest.subList(index, rest.size)
                        )
                    }

                    nextRoad = nextRoadList
                        .joinToString(" ") { it.text }
                        .trim()

                    nextRoadDesc = nextRoadDescList
                        .joinToString(" ") { it.text }
                        .trim()
                }
            }
        }

        data.nextDirection = NavigationDirection(
            nextRoad,
            nextRoadDesc,
            nextDistance
        )

        /*
         * Copy the maneuver icon.
         */
        (rightIcon?.drawable as? BitmapDrawable)?.bitmap?.let { bitmap ->
            val bitmapConfig = bitmap.config ?: Bitmap.Config.ARGB_8888

            data.actionIcon = NavigationIcon(
                bitmap.copy(bitmapConfig, false)
            )
        }

        // Timber.v("$data")

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
