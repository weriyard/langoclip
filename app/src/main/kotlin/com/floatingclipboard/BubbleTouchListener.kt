package com.floatingclipboard

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs

/**
 * Distinguishes click from drag:
 * - if finger movement < touchSlop and duration < longPressTimeout -> click (onClick)
 * - otherwise updates the view position in WindowManager
 */
class BubbleTouchListener(
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val onClick: () -> Unit
) : View.OnTouchListener {

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var downTime = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                downTime = System.currentTimeMillis()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = initialX + (event.rawX - initialTouchX).toInt()
                params.y = initialY + (event.rawY - initialTouchY).toInt()
                runCatching { windowManager.updateViewLayout(view, params) }
                true
            }
            MotionEvent.ACTION_UP -> {
                val dx = abs(event.rawX - initialTouchX)
                val dy = abs(event.rawY - initialTouchY)
                val elapsed = System.currentTimeMillis() - downTime
                val isClick = dx < touchSlop &&
                    dy < touchSlop &&
                    elapsed < ViewConfiguration.getLongPressTimeout()
                if (isClick) {
                    view.performClick()
                    onClick()
                }
                true
            }
            else -> false
        }
    }
}
