/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_POINTER_DOWN
import android.view.MotionEvent.ACTION_POINTER_UP
import android.view.MotionEvent.ACTION_UP
import android.view.ViewGroup
import androidx.ui.compositing.Scene
import androidx.ui.core.Duration
import androidx.ui.engine.geometry.Size
import androidx.ui.engine.window.Window
import androidx.ui.engine.window.WindowPadding
import androidx.ui.flow.CompositorContext
import androidx.ui.foundation.Key
import androidx.ui.painting.Canvas
import androidx.ui.skia.SkMatrix
import androidx.ui.ui.pointer.PointerChange
import androidx.ui.ui.pointer.PointerData
import androidx.ui.ui.pointer.PointerDataPacket
import androidx.ui.vectormath64.Matrix4
import androidx.ui.widgets.binding.WidgetsFlutterBinding
import androidx.ui.widgets.binding.runApp
import androidx.ui.widgets.framework.Widget
import androidx.ui.widgets.view.ViewHost
import kotlinx.coroutines.runBlocking

@SuppressLint("ViewConstructor")
class CraneView(
    context: Context,
    widget: Widget
) : ViewGroup(context) {

    private var widgetRoot: ViewHost
    private var scene: Scene? = null
    private var initialized: Boolean = false
    private val window = Window()

    init {
        setWillNotDraw(false)
        widgetRoot = ViewHost(this, Key.createKey("viewHost"), widget)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        updateMetrics()
        if (!initialized) {
            initialized = true
            window.renderDelegate = { newScene ->
                scene = newScene
                invalidate()
            }
            runApp(widgetRoot, WidgetsFlutterBinding.create(window))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // TODO (njawad/Migration) skipping implementation to support simultaneous measure+ layout
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas?) {
        // TODO (njawad/Migration) skipping implementation to ensure proper interleaved draw
        // ordering of Crane Widgets and traditional Views
        // CraneView will draw it's widgets first view onDraw and default ViewGroup behavior will
        // draw child Views after it draws itself
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val change =
            when (event.actionMasked) {
                ACTION_DOWN, ACTION_POINTER_DOWN -> PointerChange.down
                ACTION_MOVE -> PointerChange.move
                ACTION_UP, ACTION_POINTER_UP -> PointerChange.up
                ACTION_CANCEL -> PointerChange.cancel
                else -> null
            }

        change?.let {
            if (it == PointerChange.move) {
                for (i in 0 until event.pointerCount) {
                    processInput(event, i, it)
                }
            } else {
                processInput(event, event.actionIndex, it)
            }
        }
        return true
    }

    private fun processInput(event: MotionEvent, pointerIndex: Int, change: PointerChange) {
        val pointerDataPacket = PointerDataPacket()
        val id = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex).toDouble()
        val y = event.getY(pointerIndex).toDouble()
        pointerDataPacket.data.add(
            PointerData(
                timeStamp = Duration.create(milliseconds = event.eventTime),
                change = change,
                physicalX = x,
                physicalY = y,
                pointerId = id
            )
        )
        runBlocking {
            window.onPointerDataPacket.send(pointerDataPacket)
        }
    }

    private fun updateMetrics() {
        val devicePixelRatio = resources.displayMetrics.density.toDouble()
        val size = Size(measuredWidth.toDouble(), measuredHeight.toDouble())
        val padding = WindowPadding(paddingLeft.toDouble(), paddingTop.toDouble(),
                paddingRight.toDouble(), paddingBottom.toDouble())
        if (window.devicePixelRatio != devicePixelRatio ||
                window.physicalSize != size ||
                window.padding != padding) {
            window.updateWindowMetrics(
                    devicePixelRatio = devicePixelRatio,
                    width = size.width,
                    height = size.height,
                    paddingTop = padding.top,
                    paddingRight = padding.right,
                    paddingBottom = padding.bottom,
                    paddingLeft = padding.left,
                    viewInsetTop = 0.0,
                    viewInsetRight = 0.0,
                    viewInsetBottom = 0.0,
                    viewInsetLeft = 0.0
            )
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(frameworkCanvas: android.graphics.Canvas) {
        scene?.let { scene ->
            val canvas = Canvas(frameworkCanvas)
            val surfaceTransformation = SkMatrix(Matrix4.identity())
            val frame = CompositorContext().AcquireFrame(canvas, surfaceTransformation)
            frame.Raster(scene.takeLayerTree())
            frame.destructor()
        }
    }
}