/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.framework.demos

import android.app.Activity
import android.os.Bundle
import androidx.animation.ColorPropKey
import androidx.animation.FloatPropKey
import androidx.animation.transitionDefinition
import androidx.ui.animation.Transition
import androidx.ui.core.CraneWrapper
import androidx.ui.core.Layout
import androidx.ui.core.Draw
import androidx.ui.core.PxPosition
import androidx.ui.core.gesture.PressGestureDetector
import androidx.ui.core.min
import androidx.ui.engine.geometry.Offset
import androidx.ui.painting.Color
import androidx.ui.painting.Paint
import androidx.compose.Composable
import androidx.compose.composer
import androidx.compose.setContent
import androidx.compose.state
import androidx.compose.unaryPlus

/* Demo app created to study the interaction of animations, gestures and semantics. */
class AnimationGestureSemanticsActivity : Activity() {

    private enum class ComponentState { Pressed, Released }

    private val colorKey = ColorPropKey()
    private val sizeKey = FloatPropKey()
    private val transitionDefinition = transitionDefinition {
        state(ComponentState.Pressed) {
            this[colorKey] = Color.fromARGB(255, 200, 0, 0)
            this[sizeKey] = 0.2f
        }
        state(ComponentState.Released) {
            this[colorKey] = Color.fromARGB(255, 0, 200, 0)
            this[sizeKey] = 1.0f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CraneWrapper {
                // This component does not use Semantics.
                // WithoutSemanticActions()

                // This component is a sample using the Level 1 API.
                // Level1Api()

                // TODO(ralu): Add Level 2 API Sample. (Need to implement node merging).

                // This component is a sample using the Level 3 API, with the built-in defaults.
                // Level3Api()

                // This component is a sample using the Level 3 API, along with extra parameters.
                Level3ApiExtras()
            }
        }
    }

    /**
     * This component does not use Semantics. The gesture detector triggers the animation.
     */
    @Suppress("FunctionName", "Unused")
    @Composable
    fun WithoutSemanticActions() {
        val animationEndState = +state { ComponentState.Released }
        PressGestureDetector(
            onPress = { animationEndState.value = ComponentState.Pressed },
            onRelease = { animationEndState.value = ComponentState.Released }) {
            Animation(animationEndState = animationEndState.value)
        }
    }

    /**
     * This component uses the level 1 Semantics API.
     */
    @Suppress("FunctionName", "Unused")
    @Composable
    fun Level1Api() {
        val animationEndState = +state { ComponentState.Released }

        val pressedAction = SemanticAction<PxPosition>(
            phrase = "Pressed",
            defaultParam = PxPosition.Origin,
            types = setOf(AccessibilityAction.Primary, PolarityAction.Negative)
        ) {
            animationEndState.value = ComponentState.Pressed
        }

        val releasedAction = SemanticAction<Unit>(
            phrase = "Released",
            defaultParam = Unit,
            types = setOf(AccessibilityAction.Secondary, PolarityAction.Positive)
        ) { animationEndState.value = ComponentState.Released }

        Semantics(
            properties = setOf(Label("Animating Circle"), Visibility.Visible),
            actions = setOf(pressedAction, releasedAction)
        ) {
            PressGestureDetectorWithActions(
                onPress = pressedAction,
                onRelease = releasedAction
            ) { Animation(animationEndState = animationEndState.value) }
        }
    }

    /**
     * This component uses the level 3 Semantics API. The [ClickInteraction] provides default
     * parameters for the [SemanticAction]s. The developer has to provide the callback lambda.
     */
    @Suppress("FunctionName", "Unused")
    @Composable
    fun Level3Api() {
        val animationEndState = +state { ComponentState.Released }
        ClickInteraction(
            press = { action { animationEndState.value = ComponentState.Pressed } },
            release = { action { animationEndState.value = ComponentState.Released } }
        ) { Animation(animationEndState = animationEndState.value) }
    }

    /**
     * This component uses the level 3 Semantics API. Instead of using the default parameter that
     * [ClickInteraction] provides, we provide a custom action phrase and a set of types.
     */
    @Suppress("FunctionName", "Unused")
    @Composable
    fun Level3ApiExtras() {
        val animationEndState = +state { ComponentState.Released }
        ClickInteraction(
            press = {
                label = "Shrink"
                types = setOf(AccessibilityAction.Primary, PolarityAction.Negative)
                action = { animationEndState.value = ComponentState.Pressed }
            },
            release = {
                label = "Enlarge"
                types = setOf(AccessibilityAction.Secondary, PolarityAction.Positive)
                action = { animationEndState.value = ComponentState.Released }
            }) { Animation(animationEndState = animationEndState.value) }
    }

    @Suppress("FunctionName")
    @Composable
    private fun Animation(animationEndState: ComponentState) {
        Layout(children = {
            Transition(
                definition = transitionDefinition,
                toState = animationEndState
            ) { state ->
                Circle(color = state[colorKey], sizeRatio = state[sizeKey])
            }
        }, layoutBlock = { _, constraints ->
            layout(constraints.maxWidth, constraints.maxHeight) {}
        })
    }

    @Suppress("FunctionName")
    @Composable
    fun Circle(color: Color, sizeRatio: Float) {
        Draw { canvas, parentSize ->
            canvas.drawCircle(
                c = Offset(parentSize.width.value / 2, parentSize.height.value / 2),
                radius = min(parentSize.height, parentSize.width).value * sizeRatio / 2,
                paint = Paint().apply { this.color = color })
        }
    }
}