package io.kinescope.flutter_kinescope_sdk

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import io.kinescope.sdk.settings.KinescopeSettingsParameterView
import io.kinescope.sdk.settings.KinescopeSettingsView
import io.kinescope.sdk.view.KinescopePlayerView

/**
 * Re-applies [KinescopeSettingsView] popup sizing for Flutter embedded players.
 *
 * The SDK sizes the popup from [KinescopeSettingsView.getHeight] and a fixed 48dp bottom
 * offset. In a short inline PlatformView that leaves too little room and clips the last rows.
 * This helper mirrors SDK formulas but uses the PlatformView container height and re-applies
 * when the popup opens, navigates, or the container bounds change — not on every pre-draw.
 */
internal object KinescopeSettingsEmbedHelper {
    private const val SDK_PACKAGE = "io.kinescope.sdk"
    private const val EMBED_SCREEN_SCROLL_TAG = "kinescope_flutter_embed_settings_scroll"
    private const val OPTIONS_BUTTON_ID = "kinescope_settings"
    private const val LIST_BOTTOM_PADDING_DP = 8

    fun prepare(playerView: KinescopePlayerView, container: ViewGroup) {
        playerView.post {
            val settings = playerView.settingsMenu ?: return@post
            settings.setFullscreenMode(false)
            hookSettingsOpen(playerView, container, settings)
            wireBoundsEnforcement(playerView, container, settings)
        }
        playerView.postDelayed({
            val settings = playerView.settingsMenu ?: return@postDelayed
            settings.setFullscreenMode(false)
            chainNavigationCallback(settings) {
                positionPopupWithinBounds(settings, container, playerView)
            }
        }, 100L)
    }

    private fun hookSettingsOpen(
        playerView: KinescopePlayerView,
        container: ViewGroup,
        settings: KinescopeSettingsView,
    ) {
        val buttonId = playerView.resources.getIdentifier(OPTIONS_BUTTON_ID, "id", SDK_PACKAGE)
        val button = if (buttonId != 0) playerView.findViewById<View>(buttonId) else null
        button?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                playerView.post { positionPopupWithinBounds(settings, container, playerView) }
            }
            false
        }

        var wasVisible = settings.isVisible
        settings.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val visible = settings.isVisible
            if (visible && !wasVisible) {
                playerView.post { positionPopupWithinBounds(settings, container, playerView) }
            }
            wasVisible = visible
        }
    }

    private fun wireBoundsEnforcement(
        playerView: KinescopePlayerView,
        container: ViewGroup,
        settings: KinescopeSettingsView,
    ) {
        val onBoundsChanged = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (settings.isVisible) {
                positionPopupWithinBounds(settings, container, playerView)
            }
        }
        container.addOnLayoutChangeListener(onBoundsChanged)
        playerView.addOnLayoutChangeListener(onBoundsChanged)
        settings.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (settings.isVisible) {
                positionPopupWithinBounds(settings, container, playerView)
            }
        }
        val popup = findSdkView(settings, "settings_popup_container")
        popup?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (settings.isVisible) {
                positionPopupWithinBounds(settings, container, playerView)
            }
        }
    }

    private fun chainNavigationCallback(
        settings: KinescopeSettingsView,
        enforce: () -> Unit,
    ) {
        val existing = settings.onNavigationChanged
        if (existing is ChainedNavigationCallback) {
            return
        }
        settings.onNavigationChanged = ChainedNavigationCallback(existing, enforce)
    }

    private class ChainedNavigationCallback(
        private val delegate: (() -> Unit)?,
        private val enforce: () -> Unit,
    ) : () -> Unit {
        override fun invoke() {
            delegate?.invoke()
            enforce()
        }
    }

    private fun positionPopupWithinBounds(
        settings: KinescopeSettingsView,
        container: ViewGroup,
        playerView: KinescopePlayerView,
    ) {
        val popup = findSdkView(settings, "settings_popup_container") as? ViewGroup ?: return
        if (!popup.isVisible || settings.width == 0) {
            return
        }

        val boundsHeight = resolveBoundsHeight(container, playerView, settings)
        if (boundsHeight == 0) {
            return
        }

        val resources = settings.resources
        val endMargin = dimen(resources, "kinescope_settings_popup_edge_margin")
        val bottomOffset = embeddedBottomOffset(boundsHeight, resources)
        val topMargin = endMargin
        val desiredWidth = dimen(resources, "kinescope_settings_popup_width")
        val rowHeight = dimen(resources, "kinescope_settings_row_height")
        val hardMaxOptions = dimen(resources, "kinescope_settings_options_max_height")

        val maxPopupHeight = calculateMaxPopupHeight(boundsHeight, bottomOffset, endMargin, resources)
        val screenContainer = findSdkView(settings, "settings_screen_container") as? ViewGroup ?: return
        val verticalPadding = popup.paddingTop + popup.paddingBottom
        val maxContentHeight = (maxPopupHeight - verticalPadding).coerceAtLeast(rowHeight)

        val popupWidth = desiredWidth.coerceAtMost((settings.width - endMargin * 2).coerceAtLeast(0))
        if (popupWidth == 0) {
            return
        }
        val widthSpec = View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY)

        val screenView = prepareScreenForEmbedded(
            screenContainer = screenContainer,
            maxContentHeight = maxContentHeight,
            rowHeight = rowHeight,
            widthSpec = widthSpec,
        ) ?: return

        applyOptionsScrollHeights(
            screenView = screenView,
            maxPopupHeight = maxPopupHeight,
            verticalPadding = verticalPadding,
            rowHeight = rowHeight,
            hardMaxOptions = hardMaxOptions,
        )

        val measuredContentHeight = resolveScreenContentHeight(
            screenView = screenView,
            widthSpec = widthSpec,
            rowHeight = rowHeight,
            maxContentHeight = maxContentHeight,
        )
        val popupHeight = (measuredContentHeight + verticalPadding).coerceAtMost(maxPopupHeight)

        screenContainer.clipChildren = true
        screenContainer.clipToPadding = true

        popup.clipChildren = true
        popup.clipToPadding = true

        val isRtl = ViewCompat.getLayoutDirection(settings) == ViewCompat.LAYOUT_DIRECTION_RTL
        val left = if (isRtl) {
            endMargin
        } else {
            settings.width - popupWidth - endMargin
        }
        val top = (boundsHeight - popupHeight - bottomOffset)
            .coerceIn(topMargin, (boundsHeight - popupHeight).coerceAtLeast(0))

        val layoutParams = (popup.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(popupWidth, popupHeight)
        val popupChanged = layoutParams.width != popupWidth ||
            layoutParams.height != popupHeight ||
            layoutParams.topMargin != top ||
            layoutParams.leftMargin != left
        layoutParams.width = popupWidth
        layoutParams.height = popupHeight
        layoutParams.gravity = Gravity.NO_GRAVITY
        layoutParams.setMargins(left, top, 0, 0)
        if (popupChanged) {
            popup.layoutParams = layoutParams
        }

        val screenHeightChanged = screenContainer.layoutParams.height != measuredContentHeight
        if (screenHeightChanged) {
            screenContainer.layoutParams = screenContainer.layoutParams.apply {
                height = measuredContentHeight
            }
        }
    }

    /** Prefer the PlatformView container height — it tracks Flutter widget size changes. */
    private fun resolveBoundsHeight(
        container: ViewGroup,
        playerView: KinescopePlayerView,
        settings: KinescopeSettingsView,
    ): Int {
        val containerHeight = container.height.takeIf { it > 0 }
            ?: container.measuredHeight.takeIf { it > 0 }
        val playerHeight = playerView.height.takeIf { it > 0 }
            ?: playerView.measuredHeight.takeIf { it > 0 }
        return when {
            containerHeight != null && playerHeight != null -> minOf(containerHeight, playerHeight)
            containerHeight != null -> containerHeight
            playerHeight != null -> playerHeight
            else -> settings.height.takeIf { it > 0 } ?: settings.measuredHeight
        }
    }

    /** SDK calculateMaxPopupHeight(), with an embedded bottom offset passed in. */
    private fun calculateMaxPopupHeight(
        boundsHeight: Int,
        bottomOffset: Int,
        endMargin: Int,
        resources: android.content.res.Resources,
    ): Int {
        val hardMax = dimen(resources, "kinescope_settings_popup_max_height")
        return (boundsHeight - bottomOffset - endMargin).coerceAtMost(hardMax).coerceAtLeast(0)
    }

    /**
     * SDK uses 48dp below the popup. On a short inline player that consumes most of the height.
     * Scale the offset with player height so the popup can shrink with the PlatformView.
     */
    private fun embeddedBottomOffset(
        boundsHeight: Int,
        resources: android.content.res.Resources,
    ): Int {
        val sdkOffset = dimen(resources, "kinescope_settings_popup_bottom_offset")
        val minOffset = dpToPx(resources, 8)
        val scaledOffset = (boundsHeight * 0.10f).toInt().coerceAtLeast(minOffset)
        return sdkOffset.coerceAtMost(scaledOffset)
    }

    private fun resolveScreenContentHeight(
        screenView: View,
        widthSpec: Int,
        rowHeight: Int,
        maxContentHeight: Int,
    ): Int {
        if (screenView is KinescopeEmbedScrollView && screenView.tag == EMBED_SCREEN_SCROLL_TAG) {
            val viewportHeight = screenView.layoutParams.height
            if (viewportHeight > 0) {
                return viewportHeight
            }
        }

        val heightSpec = View.MeasureSpec.makeMeasureSpec(maxContentHeight, View.MeasureSpec.AT_MOST)
        screenView.measure(widthSpec, heightSpec)
        return screenView.measuredHeight.takeIf { it > 0 } ?: rowHeight
    }

    private fun optionsScrollMaxHeight(
        optionCount: Int,
        maxPopupHeight: Int,
        verticalPadding: Int,
        rowHeight: Int,
        hardMaxOptions: Int,
        headerHeight: Int = rowHeight,
    ): Int {
        val optionsContentHeight = optionCount.coerceAtLeast(1) * rowHeight
        val availableInPopup = (maxPopupHeight - verticalPadding - headerHeight).coerceAtLeast(rowHeight)
        return optionsContentHeight.coerceAtMost(hardMaxOptions).coerceAtMost(availableInPopup)
    }

    private fun prepareScreenForEmbedded(
        screenContainer: ViewGroup,
        maxContentHeight: Int,
        rowHeight: Int,
        widthSpec: Int,
    ): View? {
        val child = screenContainer.getChildAt(0) ?: return null

        if (child is KinescopeEmbedScrollView && child.tag == EMBED_SCREEN_SCROLL_TAG) {
            val content = child.getChildAt(0) ?: return child
            val neededHeight = measureScreenContentHeight(content, rowHeight, widthSpec)
            val listBottomPadding = dpToPx(content, LIST_BOTTOM_PADDING_DP)
            val viewportHeight = rowAlignedViewport(
                neededHeight = neededHeight + listBottomPadding,
                maxContentHeight = maxContentHeight,
                rowHeight = measureRowHeight(content, rowHeight, widthSpec),
            )
            child.layoutParams = child.layoutParams.apply { height = viewportHeight }
            applyScrollContentPadding(content, scrollable = true)
            configureScrollChrome(child)
            configureFlutterScrollTouches(child)
            return child
        }

        if (hasSdkOptionsScrollView(child)) {
            return child
        }

        val mainList = findMainSettingsList(child)
        if (mainList != null) {
            ensureParameterRowHeights(mainList, rowHeight)
        }

        val neededHeight = measureScreenContentHeight(child, rowHeight, widthSpec)
        val mustScroll = neededHeight > maxContentHeight
        if (!mustScroll) {
            unwrapScreenScroll(screenContainer, child)
            applyScrollContentPadding(screenContainer.getChildAt(0), scrollable = false)
            return screenContainer.getChildAt(0) ?: child
        }

        val effectiveRowHeight = measureRowHeight(child, rowHeight, widthSpec)
        val listBottomPadding = dpToPx(child, LIST_BOTTOM_PADDING_DP)
        val viewportHeight = rowAlignedViewport(
            neededHeight = neededHeight + listBottomPadding,
            maxContentHeight = maxContentHeight,
            rowHeight = effectiveRowHeight,
        )

        return wrapScreenInScroll(
            screenContainer = screenContainer,
            content = child,
            viewportHeight = viewportHeight,
        )
    }

    private fun measureScreenContentHeight(
        view: View,
        rowHeight: Int,
        widthSpec: Int,
    ): Int {
        val mainList = findMainSettingsList(view)
        if (mainList != null) {
            val countedHeight = mainList.childCount * rowHeight
            val measuredHeight = measureMainListHeight(mainList, widthSpec)
            return maxOf(countedHeight, measuredHeight)
        }

        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredHeight.takeIf { it > 0 } ?: rowHeight
    }

    private fun hasSdkOptionsScrollView(view: View): Boolean {
        if (view !is LinearLayout || view.orientation != LinearLayout.VERTICAL) {
            return false
        }
        for (index in 0 until view.childCount) {
            val child = view.getChildAt(index)
            if (child is ScrollView && child.tag != EMBED_SCREEN_SCROLL_TAG) {
                return true
            }
        }
        return false
    }

    private fun wrapScreenInScroll(
        screenContainer: ViewGroup,
        content: View,
        viewportHeight: Int,
    ): KinescopeEmbedScrollView {
        screenContainer.removeView(content)
        applyScrollContentPadding(content, scrollable = true)
        val scrollView = KinescopeEmbedScrollView(content.context).apply {
            tag = EMBED_SCREEN_SCROLL_TAG
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                viewportHeight,
            )
            addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        configureScrollChrome(scrollView)
        configureFlutterScrollTouches(scrollView)
        screenContainer.addView(scrollView)
        return scrollView
    }

    private fun applyScrollContentPadding(view: View?, scrollable: Boolean) {
        val list = view as? LinearLayout ?: return
        val bottom = if (scrollable) dpToPx(list, LIST_BOTTOM_PADDING_DP) else 0
        list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, bottom)
    }

    private fun ensureParameterRowHeights(list: LinearLayout, rowHeight: Int) {
        for (index in 0 until list.childCount) {
            val row = list.getChildAt(index)
            val layoutParams = row.layoutParams ?: continue
            if (layoutParams.height < rowHeight) {
                layoutParams.height = rowHeight
                row.layoutParams = layoutParams
            }
        }
    }

    private fun measureMainListHeight(list: LinearLayout, widthSpec: Int): Int {
        if (list.childCount == 0) {
            return 0
        }
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        list.measure(widthSpec, heightSpec)
        return list.measuredHeight
    }

    private fun measureRowHeight(
        view: View,
        fallback: Int,
        widthSpec: Int,
    ): Int {
        val child = when (view) {
            is LinearLayout -> view.getChildAt(0)
            else -> view
        } ?: return fallback
        val heightSpec = View.MeasureSpec.makeMeasureSpec(fallback, View.MeasureSpec.EXACTLY)
        child.measure(widthSpec, heightSpec)
        return child.measuredHeight
            .takeIf { it > 0 }
            ?: child.layoutParams.height.takeIf { it > 0 }
            ?: fallback
    }

    private fun unwrapScreenScroll(screenContainer: ViewGroup, child: View) {
        val scroll = child as? KinescopeEmbedScrollView ?: return
        if (scroll.tag != EMBED_SCREEN_SCROLL_TAG) {
            return
        }
        val content = scroll.getChildAt(0) ?: return
        applyScrollContentPadding(content, scrollable = false)
        screenContainer.removeView(scroll)
        screenContainer.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun applyOptionsScrollHeights(
        screenView: View,
        maxPopupHeight: Int,
        verticalPadding: Int,
        rowHeight: Int,
        hardMaxOptions: Int,
    ) {
        if (screenView !is LinearLayout) {
            return
        }
        for (index in 0 until screenView.childCount) {
            val child = screenView.getChildAt(index)
            if (child is ScrollView && child.tag != EMBED_SCREEN_SCROLL_TAG) {
                val list = child.getChildAt(0) as? ViewGroup ?: continue
                val headerHeight = measureHeaderHeight(screenView, child)
                val scrollHeight = optionsScrollMaxHeight(
                    optionCount = list.childCount,
                    maxPopupHeight = maxPopupHeight,
                    verticalPadding = verticalPadding,
                    rowHeight = rowHeight,
                    hardMaxOptions = hardMaxOptions,
                    headerHeight = headerHeight,
                )
                val params = child.layoutParams
                if (params.height != scrollHeight) {
                    child.layoutParams = params.apply { height = scrollHeight }
                }
                configureScrollChrome(child)
                configureFlutterScrollTouches(child)
            }
        }
    }

    private fun measureHeaderHeight(screenRoot: LinearLayout, scrollView: ScrollView): Int {
        var headerHeight = 0
        for (index in 0 until screenRoot.childCount) {
            val child = screenRoot.getChildAt(index)
            if (child === scrollView) {
                break
            }
            headerHeight += child.layoutParams.height.takeIf { it > 0 } ?: child.measuredHeight
        }
        val rowHeight = dimen(screenRoot.resources, "kinescope_settings_row_height")
        return headerHeight.coerceAtLeast(rowHeight)
    }

    private fun rowAlignedViewport(
        neededHeight: Int,
        maxContentHeight: Int,
        rowHeight: Int,
    ): Int {
        if (neededHeight <= maxContentHeight) {
            return neededHeight
        }
        val visibleRows = maxContentHeight / rowHeight
        return (visibleRows * rowHeight).coerceAtLeast(rowHeight)
    }

    private fun findMainSettingsList(root: View?): LinearLayout? {
        if (root == null) {
            return null
        }
        if (root is KinescopeEmbedScrollView && root.tag == EMBED_SCREEN_SCROLL_TAG) {
            return root.getChildAt(0) as? LinearLayout
        }
        return if (isMainSettingsScreen(root)) root as LinearLayout else null
    }

    private fun isMainSettingsScreen(view: View): Boolean {
        if (view !is LinearLayout || view.orientation != LinearLayout.VERTICAL || view.childCount == 0) {
            return false
        }
        for (index in 0 until view.childCount) {
            if (view.getChildAt(index) !is KinescopeSettingsParameterView) {
                return false
            }
        }
        return true
    }

    private fun configureScrollChrome(scrollView: ScrollView) {
        scrollView.isVerticalScrollBarEnabled = false
        scrollView.isHorizontalScrollBarEnabled = false
        scrollView.scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        scrollView.scrollBarSize = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scrollView.scrollIndicators = 0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scrollView.verticalScrollbarThumbDrawable = ColorDrawable(Color.TRANSPARENT)
            scrollView.verticalScrollbarTrackDrawable = ColorDrawable(Color.TRANSPARENT)
        }
    }

    private fun configureFlutterScrollTouches(scrollView: ScrollView) {
        scrollView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                disallowAncestorsIntercept(scrollView, true)
            }
            false
        }
    }

    private fun disallowAncestorsIntercept(view: View, disallow: Boolean) {
        var parent: ViewParent? = view.parent
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow)
            parent = parent.parent
        }
    }

    private fun dpToPx(view: View, dp: Int): Int {
        return dpToPx(view.resources, dp)
    }

    private fun dpToPx(resources: android.content.res.Resources, dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun findSdkView(root: View, idName: String): View? {
        val id = root.resources.getIdentifier(idName, "id", SDK_PACKAGE)
        return if (id != 0) root.findViewById(id) else null
    }

    private fun dimen(resources: android.content.res.Resources, name: String): Int {
        val id = resources.getIdentifier(name, "dimen", SDK_PACKAGE)
        return if (id != 0) resources.getDimensionPixelSize(id) else 0
    }
}
