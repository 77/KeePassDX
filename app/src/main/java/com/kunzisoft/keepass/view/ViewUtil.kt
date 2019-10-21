/*
 * Copyright 2019 Jeremy Jamet / Kunzisoft.
 *
 * This file is part of KeePass DX.
 *
 *  KeePass DX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeePass DX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePass DX.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.kunzisoft.keepass.view

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.snackbar.Snackbar
import com.kunzisoft.keepass.R

/**
 * Replace font by monospace, must be called after seText()
 */
fun TextView.applyFontVisibility() {
    val typeFace = Typeface.createFromAsset(context.assets, "fonts/FiraMono-Regular.ttf")
    typeface = typeFace
}

fun Snackbar.asError(): Snackbar {
    this.view.apply {
        setBackgroundColor(Color.RED)
        findViewById<TextView>(R.id.snackbar_text).setTextColor(Color.WHITE)
    }
    return this
}

fun Toolbar.collapse(animate: Boolean = true) {
    val recordBarHeight = layoutParams.height
    val slideAnimator = ValueAnimator.ofInt(height, 0)
    if (animate)
        slideAnimator.duration = 300L
    slideAnimator.addUpdateListener { animation ->
        layoutParams.height = animation.animatedValue as Int
        if (layoutParams.height <= 1) {
            visibility = View.GONE
            layoutParams.height = recordBarHeight
        }
        requestLayout()
    }
    AnimatorSet().apply {
        play(slideAnimator)
        interpolator = AccelerateDecelerateInterpolator()
    }.start()
}

fun Toolbar.expand(animate: Boolean = true)  {
    visibility = View.VISIBLE
    val actionBarHeight = layoutParams.height
    val slideAnimator = ValueAnimator
            .ofInt(0, actionBarHeight)
    if (animate)
        slideAnimator.duration = 300L
    slideAnimator.addUpdateListener { animation ->
        layoutParams.height = animation.animatedValue as Int
        requestLayout()
    }
    AnimatorSet().apply {
        play(slideAnimator)
        interpolator = AccelerateDecelerateInterpolator()
    }.start()
}