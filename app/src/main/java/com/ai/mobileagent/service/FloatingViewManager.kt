package com.ai.mobileagent.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.ai.mobileagent.engine.AgentEngine
import com.ai.mobileagent.engine.AgentStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FloatingViewManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val agentEngine = AgentEngine.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    private var statusJob: Job? = null

    private var rootView: LinearLayout? = null
    private var collapsedView: LinearLayout? = null
    private var expandedView: LinearLayout? = null

    private var statusTextView: TextView? = null
    private var goalEditText: EditText? = null

    private var isExpanded = false

    private val windowParams = WindowManager.LayoutParams().apply {
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = Gravity.TOP or Gravity.START
        x = 50
        y = 200
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (rootView != null) return

        rootView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        buildCollapsedView()
        buildExpandedView()

        rootView?.addView(collapsedView)
        rootView?.addView(expandedView)
        expandedView?.visibility = View.GONE

        // Make floating overlay draggable
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        rootView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = windowParams.x
                    initialY = windowParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    windowParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    windowParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(rootView, windowParams)
                    } catch (e: Exception) { }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(rootView, windowParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        listenToAgentStatus()
    }

    private fun buildCollapsedView() {
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 48f
            setColor(Color.parseColor("#1F2937"))
            setStroke(2, Color.parseColor("#3B82F6"))
        }

        collapsedView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            this.background = background
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconText = TextView(context).apply {
            text = "🤖 AI"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { toggleExpand() }
        }

        statusTextView = TextView(context).apply {
            text = " Ready"
            setTextColor(Color.parseColor("#93C5FD"))
            textSize = 12f
            setOnClickListener { toggleExpand() }
        }

        val stopButton = Button(context).apply {
            text = "⏹ STOP"
            setTextColor(Color.WHITE)
            textSize = 11f
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(Color.parseColor("#EF4444"))
            }
            this.background = btnBg
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                agentEngine.stopTask()
            }
        }

        collapsedView?.addView(iconText)
        collapsedView?.addView(statusTextView)
        collapsedView?.addView(stopButton)
    }

    private fun buildExpandedView() {
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32f
            setColor(Color.parseColor("#111827"))
            setStroke(3, Color.parseColor("#3B82F6"))
        }

        expandedView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            this.background = background
            val params = LinearLayout.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
        }

        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(context).apply {
            text = "Gemini Mobile Agent"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = Button(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { toggleExpand() }
        }

        headerLayout.addView(title)
        headerLayout.addView(closeBtn)

        goalEditText = EditText(context).apply {
            hint = "Ask AI (e.g. Open YouTube & play lofi)..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 13f
            val editBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(Color.parseColor("#1F2937"))
            }
            this.background = editBg
            setPadding(20, 16, 20, 16)
        }

        val buttonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        val runBtn = Button(context).apply {
            text = "▶ Run Agent"
            setTextColor(Color.WHITE)
            textSize = 13f
            val runBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.parseColor("#2563EB"))
            }
            this.background = runBg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val goal = goalEditText?.text?.toString()?.trim().orEmpty()
                if (goal.isNotBlank()) {
                    agentEngine.startTask(goal)
                    toggleExpand() // Collapse back to pill
                }
            }
        }

        buttonsLayout.addView(runBtn)

        expandedView?.addView(headerLayout)
        expandedView?.addView(goalEditText)
        expandedView?.addView(buttonsLayout)
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        if (isExpanded) {
            windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            expandedView?.visibility = View.VISIBLE
        } else {
            windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            expandedView?.visibility = View.GONE
        }
        try {
            windowManager.updateViewLayout(rootView, windowParams)
        } catch (e: Exception) { }
    }

    private fun listenToAgentStatus() {
        statusJob = scope.launch {
            agentEngine.status.collect { status ->
                when (status) {
                    is AgentStatus.Idle -> statusTextView?.text = " Ready"
                    is AgentStatus.Running -> statusTextView?.text = " [${status.step}/${status.maxSteps}]"
                    is AgentStatus.Completed -> statusTextView?.text = " Done!"
                    is AgentStatus.Failed -> statusTextView?.text = " Error"
                    is AgentStatus.Stopped -> statusTextView?.text = " Stopped"
                }
            }
        }
    }

    fun hide() {
        statusJob?.cancel()
        rootView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) { }
            rootView = null
        }
    }
}
