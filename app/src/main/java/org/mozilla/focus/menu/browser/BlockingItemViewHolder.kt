/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.menu.browser

import android.view.View
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.findTabOrCustomTabOrSelectedTab
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl
import mozilla.components.support.utils.ThreadUtils
import org.mozilla.focus.R
import org.mozilla.focus.exceptions.ExceptionDomains
import org.mozilla.focus.ext.components
import org.mozilla.focus.fragment.BrowserFragment
import org.mozilla.focus.telemetry.TelemetryWrapper

private const val THUMB_ANIMATION_DURATION = 250L

internal class BlockingItemViewHolder(
    itemView: View,
    private val fragment: BrowserFragment
) : BrowserMenuViewHolder(itemView), CompoundButton.OnCheckedChangeListener {
    private val trackerCounter: TextView

    init {
        val switchView = itemView.findViewById<Switch>(R.id.blocking_switch)

        switchView.isChecked = fragment.requireContext().components.store.state.findTabOrCustomTabOrSelectedTab(
            fragment.tab.id
        )!!.trackingProtection.ignoredOnTrackingProtection.not()

        switchView.setOnCheckedChangeListener(this)

        val helpView = itemView.findViewById<View>(R.id.help_trackers)
        helpView.setOnClickListener(this)

        trackerCounter = itemView.findViewById(R.id.trackers_count)

        updateTrackers(fragment.tab.trackingProtection.blockedTrackers.size)
    }

    fun updateTrackers(trackers: Int) {
        if (!fragment.tab.trackingProtection.ignoredOnTrackingProtection) {
            updateTrackingCount(trackerCounter, trackers)
        } else {
            disableTrackingCount(trackerCounter)
        }
    }

    private fun updateTrackingCount(view: TextView, count: Int) {
        ThreadUtils.postToMainThread(Runnable { view.text = count.toString() })
    }

    private fun disableTrackingCount(view: TextView) {
        ThreadUtils.postToMainThread(Runnable { view.setText(R.string.content_blocking_disabled) })
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        TelemetryWrapper.blockingSwitchEvent(isChecked)

        // Delay closing the menu and reloading the website a bit so that the user can actually see
        // the switch change its state.
        ThreadUtils.postToMainThreadDelayed(Runnable {
            menu.dismiss()

            buttonView.context.components.apply {
                if (isChecked) {
                    trackingProtectionUseCases.removeException(fragment.tab.id)
                } else {
                    trackingProtectionUseCases.addException(fragment.tab.id)
                }
                updateExceptionsLocalList(isChecked)
                sessionUseCases.reload(fragment.tab.id)
            }
        }, THUMB_ANIMATION_DURATION)
    }

    private fun updateExceptionsLocalList(isChecked: Boolean) {
        fragment.lifecycle.coroutineScope.launch(Dispatchers.IO) {
            val domain = fragment.tab.content.url.tryGetHostFromUrl()
            fragment.context?.let {
                if (isChecked) {
                    ExceptionDomains.remove(it, listOf(domain))
                } else {
                    ExceptionDomains.add(it, domain)
                }
            }
        }
    }

    companion object {
        const val LAYOUT_ID = R.layout.menu_blocking_switch
    }
}
