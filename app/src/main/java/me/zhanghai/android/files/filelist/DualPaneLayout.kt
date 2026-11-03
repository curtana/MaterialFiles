package me.zhanghai.android.files.filelist

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.AttrRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.dpToDimensionPixelSize
import me.zhanghai.android.files.util.isLayoutDirectionRtl
import kotlin.math.abs

/**
 * Holds the two file list panes, shows both of them side by side when the file list area is wide
 * enough or only the active one otherwise, and recognizes the swipe that toggles the active pane
 * and the touches that activate a pane.
 *
 * Must not fit system windows, so that the insets reach the panes unconsumed.
 */
class DualPaneLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, @AttrRes defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {
    var listener: Listener? = null

    /** Whether the second pane exists, which enables both the swipe and the dual pane mode. */
    var isSwipeEnabled = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            requestLayout()
        }

    var activePane = 0
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (!isDualPane) {
                applyChildVisibility()
                requestLayout()
            }
        }

    /** The drawer view of the persistent drawer layout, or `null` for a modal drawer. */
    var persistentDrawerView: View? = null

    /** The target state of the persistent drawer, instead of its animated state. */
    var isPersistentDrawerOpen = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            requestLayout()
        }

    private var isDualPane = false

    private var reportedMode: PaneMode? = null

    private var lastInsets: WindowInsets? = null

    private val touchSlop: Int
    private val minFlingVelocity: Int
    private val swipeMinDistance = context.dpToDimensionPixelSize(SWIPE_MIN_DISTANCE_DP)
    private val fastScrollTouchWidth = context.dpToDimensionPixelSize(FAST_SCROLL_TOUCH_WIDTH_DP)

    private var downX = 0f
    private var downY = 0f
    private var downPane = -1
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var swipeState = SwipeState.IDLE
    private var isTap = false
    private var isDisallowIntercept = false
    private var velocityTracker: VelocityTracker? = null

    private val longPressRunnable = Runnable {
        if (!isTap) {
            return@Runnable
        }
        // Dragging after a long press isn't a swipe.
        swipeState = SwipeState.IGNORED
        listener?.onPaneActivated(downPane)
    }

    private val tempRect = Rect()

    private val tempLocation = IntArray(2)

    init {
        val viewConfiguration = ViewConfiguration.get(context)
        touchSlop = viewConfiguration.scaledTouchSlop
        minFlingVelocity = viewConfiguration.scaledMinimumFlingVelocity
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        check(childCount == 2) { "DualPaneLayout must have exactly 2 children" }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        velocityTracker = VelocityTracker.obtain()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        velocityTracker?.recycle()
        velocityTracker = null

        super.onDetachedFromWindow()
    }

    override fun shouldDelayChildPressedState(): Boolean = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val drawerRange = persistentDrawerView?.let {
            it.measuredWidth + it.marginLeft + it.marginRight
        }
        val parentWidth = (parent as? View)?.measuredWidth ?: width
        val availableWidth =
            computeAvailableWidthPx(width, parentWidth, drawerRange, isPersistentDrawerOpen)
        val density = resources.displayMetrics.density
        // Pick mode always has a single pane.
        val newIsDualPane = isSwipeEnabled && isDualPaneWidth(availableWidth, density)
        val modeChanged = newIsDualPane != isDualPane
        isDualPane = newIsDualPane
        applyChildVisibility()
        if (modeChanged) {
            lastInsets?.let { dispatchChildInsets(it) }
        }
        val paneWidthDp =
            ((if (isDualPane) availableWidth / 2 else availableWidth) / density).toInt()
        val mode = PaneMode(isDualPane, paneWidthDp)
        if (mode != reportedMode) {
            reportedMode = mode
            // Don't let the observers touch the views while measuring.
            post { listener?.onPaneModeChanged(mode) }
        }
        val heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        if (isDualPane) {
            val startWidth = width / 2
            getChildAt(0).measure(
                MeasureSpec.makeMeasureSpec(startWidth, MeasureSpec.EXACTLY), heightSpec
            )
            getChildAt(1).measure(
                MeasureSpec.makeMeasureSpec(width - startWidth, MeasureSpec.EXACTLY), heightSpec
            )
        } else {
            getChildAt(activePane).measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightSpec
            )
        }
    }

    private fun applyChildVisibility() {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val visibility = if (isDualPane || index == activePane) View.VISIBLE else View.GONE
            if (child.visibility != visibility) {
                child.visibility = visibility
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val height = b - t
        if (isDualPane) {
            val pane1 = getChildAt(0)
            val pane2 = getChildAt(1)
            val pane1Width = pane1.measuredWidth
            if (isLayoutDirectionRtl) {
                pane1.layout(width - pane1Width, 0, width, height)
                pane2.layout(0, 0, width - pane1Width, height)
            } else {
                pane1.layout(0, 0, pane1Width, height)
                pane2.layout(pane1Width, 0, width, height)
            }
        } else {
            getChildAt(activePane).layout(0, 0, width, height)
        }
    }

    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        lastInsets = insets
        dispatchChildInsets(insets)
        @Suppress("DEPRECATION")
        return insets.consumeSystemWindowInsets()
    }

    @Suppress("DEPRECATION")
    private fun dispatchChildInsets(insets: WindowInsets) {
        if (!isDualPane) {
            // The hidden pane also gets the insets, so that it has the right padding once shown.
            for (index in 0 until childCount) {
                getChildAt(index).dispatchApplyWindowInsets(insets)
            }
            return
        }
        val leftInsets = insets.replaceSystemWindowInsets(
            insets.systemWindowInsetLeft, insets.systemWindowInsetTop, 0,
            insets.systemWindowInsetBottom
        )
        val rightInsets = insets.replaceSystemWindowInsets(
            0, insets.systemWindowInsetTop, insets.systemWindowInsetRight,
            insets.systemWindowInsetBottom
        )
        val isRtl = isLayoutDirectionRtl
        getChildAt(0).dispatchApplyWindowInsets(if (isRtl) rightInsets else leftInsets)
        getChildAt(1).dispatchApplyWindowInsets(if (isRtl) leftInsets else rightInsets)
    }

    private val swipeSign: Int
        get() = if (isLayoutDirectionRtl) 1 else -1

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        velocityTracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeState = SwipeState.IDLE
                isTap = true
                isDisallowIntercept = false
                downX = ev.x
                downY = ev.y
                activePointerId = ev.getPointerId(0)
                downPane = findPaneIndexAt(ev.x, ev.y)
                removeCallbacks(longPressRunnable)
                // This must be posted before the children receive the down event, so that it runs
                // before their own long press check and the selection starts in the active pane.
                if (isDualPane && downPane >= 0 && downPane != activePane) {
                    postDelayed(
                        longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong()
                    )
                }
                swipeState = if (isExcludedFromSwipe(ev)) {
                    SwipeState.EXCLUDED
                } else {
                    SwipeState.IDLE
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Only the first pointer is tracked.
                if (swipeState == SwipeState.IDLE) {
                    swipeState = SwipeState.IGNORED
                }
                isTap = false
                removeCallbacks(longPressRunnable)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex >= 0 && isTap) {
                    val dx = abs(ev.getX(pointerIndex) - downX)
                    val dy = abs(ev.getY(pointerIndex) - downY)
                    if (dx > touchSlop || dy > touchSlop) {
                        isTap = false
                        removeCallbacks(longPressRunnable)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                // Activate the pane before the children handle the click, so that the click runs
                // in the active pane.
                if (isTap && swipeState != SwipeState.DRAGGING && isDualPane && downPane >= 0) {
                    listener?.onPaneActivated(downPane)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                isTap = false
            }
        }
        val handled = super.dispatchTouchEvent(ev)
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            velocityTracker?.clear()
        }
        return handled
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isDisallowIntercept) {
            return false
        }
        if (swipeState == SwipeState.EXCLUDED || swipeState == SwipeState.IGNORED) {
            return false
        }
        if (ev.actionMasked == MotionEvent.ACTION_MOVE) {
            return updateSwipe(ev)
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            // No child consumed the down event (e.g. empty list, error text or padding), and we
            // need to receive it to receive the following move events.
            MotionEvent.ACTION_DOWN -> return isSwipeEnabled && swipeState == SwipeState.IDLE
            MotionEvent.ACTION_MOVE -> updateSwipe(ev)
            MotionEvent.ACTION_UP -> {
                if (swipeState == SwipeState.DRAGGING) {
                    finishSwipe(ev)
                }
                swipeState = SwipeState.IDLE
            }
            MotionEvent.ACTION_CANCEL -> swipeState = SwipeState.IDLE
        }
        return true
    }

    private fun updateSwipe(ev: MotionEvent): Boolean {
        if (swipeState != SwipeState.IDLE) {
            return swipeState == SwipeState.DRAGGING
        }
        val pointerIndex = ev.findPointerIndex(activePointerId)
        if (pointerIndex < 0) {
            return false
        }
        val dx = ev.getX(pointerIndex) - downX
        val dy = ev.getY(pointerIndex) - downY
        val absDx = abs(dx)
        val absDy = abs(dy)
        if (absDy > touchSlop && absDy >= absDx) {
            // Vertical first, ignore for the rest of this gesture.
            swipeState = SwipeState.IGNORED
            return false
        }
        if (absDx > touchSlop && absDx > absDy) {
            // Don't disallow the parent from intercepting, because the drawer is opened in the
            // opposite direction.
            return if (dx * swipeSign > 0) {
                swipeState = SwipeState.DRAGGING
                true
            } else {
                swipeState = SwipeState.IGNORED
                false
            }
        }
        return false
    }

    private fun finishSwipe(ev: MotionEvent) {
        val pointerIndex = ev.findPointerIndex(activePointerId)
        if (pointerIndex < 0) {
            return
        }
        val swipeSign = swipeSign
        val distance = (ev.getX(pointerIndex) - downX) * swipeSign
        val velocity = velocityTracker?.let {
            it.computeCurrentVelocity(1000)
            it.getXVelocity(activePointerId) * swipeSign
        } ?: 0f
        if (distance >= swipeMinDistance ||
            (distance > touchSlop && velocity >= minFlingVelocity)) {
            listener?.onSwipeToTogglePane()
        }
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && swipeState != SwipeState.DRAGGING) {
            swipeState = SwipeState.EXCLUDED
        }
        isDisallowIntercept = disallowIntercept
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private fun findPaneIndexAt(x: Float, y: Float): Int {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.VISIBLE && x >= child.left && x < child.right &&
                y >= child.top && y < child.bottom) {
                return index
            }
        }
        return -1
    }

    private fun isExcludedFromSwipe(ev: MotionEvent): Boolean {
        // (e) Pick mode.
        if (!isSwipeEnabled) {
            return true
        }
        // (a) System gesture areas.
        if (isInSystemGestureArea(ev)) {
            return true
        }
        if (downPane < 0) {
            return false
        }
        val pane = getChildAt(downPane)
        val x = ev.x.toInt()
        val y = ev.y.toInt()
        // (b) The app bar, including the toolbars and the breadcrumb.
        val appBarLayout = pane.findViewById<View>(R.id.appBarLayout)
        if (appBarLayout != null && getDescendantRect(appBarLayout).contains(x, y)) {
            return true
        }
        // (c) The fast scroller at the end edge of the list, even if it's not shown.
        val recyclerView = pane.findViewById<View>(R.id.recyclerView)
        if (recyclerView != null) {
            val rect = getDescendantRect(recyclerView)
            if (isLayoutDirectionRtl) {
                rect.right = rect.left + fastScrollTouchWidth
            } else {
                rect.left = rect.right - fastScrollTouchWidth
            }
            if (rect.contains(x, y)) {
                return true
            }
        }
        // (d) Horizontally scrollable containers. Content scrolls in the opposite direction of the
        // finger.
        return canChildScrollHorizontally(this, ev.x, ev.y, -swipeSign)
    }

    private fun getDescendantRect(view: View): Rect =
        tempRect.apply {
            set(0, 0, view.width, view.height)
            offsetDescendantRectToMyCoords(view, this)
        }

    /**
     * Only checks descendants that are [ViewGroup]s (actual horizontal scroll containers), so that
     * a swipe can start on a marquee text view.
     */
    private fun canChildScrollHorizontally(
        viewGroup: ViewGroup,
        x: Float,
        y: Float,
        direction: Int
    ): Boolean {
        for (index in viewGroup.childCount - 1 downTo 0) {
            val child = viewGroup.getChildAt(index) as? ViewGroup ?: continue
            if (child.visibility != View.VISIBLE) {
                continue
            }
            val childX = x + viewGroup.scrollX - child.left
            val childY = y + viewGroup.scrollY - child.top
            if (childX < 0 || childX >= child.width || childY < 0 || childY >= child.height) {
                continue
            }
            if (child.canScrollHorizontally(direction) ||
                canChildScrollHorizontally(child, childX, childY, direction)) {
                return true
            }
        }
        return false
    }

    private fun isInSystemGestureArea(ev: MotionEvent): Boolean {
        // There are no system edge gestures before Android 10, and WindowInsetsCompat returns the
        // system window insets as the system gesture insets there.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }
        val insets = ViewCompat.getRootWindowInsets(this)
            ?.getInsets(WindowInsetsCompat.Type.systemGestures()) ?: return false
        getLocationInWindow(tempLocation)
        val windowX = ev.x + tempLocation[0]
        val windowY = ev.y + tempLocation[1]
        val rootView = rootView
        return windowX < insets.left || windowX >= rootView.width - insets.right ||
            windowY >= rootView.height - insets.bottom
    }

    override fun onRequestSendAccessibilityEvent(child: View, event: AccessibilityEvent): Boolean {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && isDualPane) {
            listener?.onPaneActivated(indexOfChild(child))
        }
        return super.onRequestSendAccessibilityEvent(child, event)
    }

    private enum class SwipeState {
        IDLE,
        EXCLUDED,
        IGNORED,
        DRAGGING
    }

    interface Listener {
        fun onPaneModeChanged(mode: PaneMode)
        fun onSwipeToTogglePane()
        fun onPaneActivated(index: Int)
    }

    companion object {
        const val DUAL_PANE_MIN_WIDTH_DP = 600
        const val SWIPE_MIN_DISTANCE_DP = 48
        const val FAST_SCROLL_TOUCH_WIDTH_DP = 48
    }
}

/**
 * Whether the available width is wide enough for two panes. This is a file level function so that
 * it can be tested without loading the view class.
 */
internal fun isDualPaneWidth(availableWidthPx: Int, density: Float): Boolean =
    availableWidthPx / density >= DualPaneLayout.DUAL_PANE_MIN_WIDTH_DP

/**
 * Computes the width available to the panes. The persistent drawer is subtracted by its target
 * state from the parent width, so that the mode doesn't flicker while the drawer animates.
 *
 * @param persistentDrawerRangePx the width of the persistent drawer including its margins, or
 * `null` for a modal drawer which overlaps the content
 */
internal fun computeAvailableWidthPx(
    ownWidthPx: Int,
    parentWidthPx: Int,
    persistentDrawerRangePx: Int?,
    isPersistentDrawerOpen: Boolean
): Int {
    if (persistentDrawerRangePx == null) {
        return ownWidthPx
    }
    val drawerWidthPx = if (isPersistentDrawerOpen) persistentDrawerRangePx else 0
    return (parentWidthPx - drawerWidthPx).coerceAtLeast(0)
}
