package com.mousy.windfall.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for cold-start / gallery browsing.
 *
 * Run on a rooted device / API 33+ emulator:
 * `.\gradlew.bat :app:generateReleaseBaselineProfile`
 *
 * CI keeps the checked-in `app/src/main/baseline-prof.txt` and does not require
 * this module's emulator harness.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.mousy.windfall",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5_000)
        // Light interaction so profile covers Compose startup + first frame.
        device.waitForIdle()
    }
}
